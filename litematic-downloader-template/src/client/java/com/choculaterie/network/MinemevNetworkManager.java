package com.choculaterie.network;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.models.*;
import com.choculaterie.plugin.PluginHttp;
import com.choculaterie.plugin.PluginManifest;
import com.choculaterie.plugin.PluginRegistry;
import com.choculaterie.plugin.PluginSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class MinemevNetworkManager {
	private static final String MINEMEV_BASE_URL = "https://www.minemev.com/api";
	private static final String CHOCULATERIE_BASE_URL = "https://api.choculaterie.com/api/FallbackModAPI";

	private static String getBaseUrl() {
		return DownloadSettings.getInstance().isUseChoculaterieAPI() ? CHOCULATERIE_BASE_URL : MINEMEV_BASE_URL;
	}

	private static String getVendorsEndpoint() {
		return getBaseUrl() + "/vendors";
	}

	private static String getSearchEndpoint() {
		return getBaseUrl() + "/search";
	}

	private static String getDetailsEndpoint() {
		return getBaseUrl() + "/details";
	}

	private static String getFilesEndpoint() {
		return getBaseUrl() + "/files";
	}

	private static final int TIMEOUT = 10000;
	private static final int DEFAULT_PAGE = 1;
	private static final String DEFAULT_VENDOR = "minemev";
	private static final int DEFAULT_PAGE_SIZE = 20;

	private static volatile String[] apiVendors = new String[0];

	public static CompletableFuture<String[]> getVendors() {
		return supplyAsync(() -> {
			List<String> vendors = new ArrayList<>();
			IOException apiFailure = null;
			try {
				String[] fetched = MinemevParsers.parseVendorList(makeGetRequest(getVendorsEndpoint()));
				apiVendors = fetched;
				vendors.addAll(Arrays.asList(fetched));
			} catch (IOException e) {
				apiFailure = e;
			}
			for (PluginSource plugin : PluginRegistry.enabled()) {
				if (!vendors.contains(plugin.id())) {
					vendors.add(plugin.id());
				}
			}
			if (apiFailure != null && vendors.isEmpty()) {
				throw apiFailure;
			}
			return vendors.toArray(new String[0]);
		});
	}

	public static CompletableFuture<MinemevSearchResponse> searchPosts(String query, String sort, int cleanUuid) {
		return searchPosts(query, sort, cleanUuid, DEFAULT_PAGE);
	}

	public static CompletableFuture<MinemevSearchResponse> searchPosts(
			String query, String sort, int cleanUuid, int page) {
		return searchPostsAdvanced(query, sort, cleanUuid, page, null, null, null, DEFAULT_PAGE_SIZE);
	}

	public static CompletableFuture<MinemevSearchResponse> searchPostsAdvanced(
			String query, String sort, int cleanUuid, int page,
			String tag, String versions, String excludeVendor, int pageSize) {
		List<PluginSource> plugins = activePlugins(excludeVendor);
		if (plugins.isEmpty()) {
			return supplyAsync(() -> {
				String url = buildSearchUrl(query, sort, cleanUuid, page, tag, versions, excludeVendor, pageSize);
				return MinemevParsers.parseSearchResponse(makeGetRequest(url));
			});
		}

		String apiExclude = excludeWithPluginVendors(excludeVendor, plugins);
		int activeApiVendors = countActiveApiVendors(apiExclude);
		int apiStreams = activeApiVendors == 0 ? 0 : 1;
		int slice = Math.max(1, pageSize / Math.max(1, apiStreams + plugins.size()));
		int apiSize = activeApiVendors == 0 ? 0 : Math.max(1, pageSize - slice * plugins.size());

		AtomicReference<Throwable> apiError = new AtomicReference<>();
		CompletableFuture<MinemevSearchResponse> apiFuture = apiSize == 0
				? CompletableFuture.completedFuture(null)
				: supplyAsync(() -> {
			String url = buildSearchUrl(query, sort, cleanUuid, page, tag, versions, apiExclude, apiSize);
			return MinemevParsers.parseSearchResponse(makeGetRequest(url));
		}).exceptionally(t -> {
			apiError.set(t);
			return null;
		});

		PluginSource.Result empty = new PluginSource.Result(List.of(), -1, -1);
		List<CompletableFuture<PluginSource.Result>> pluginFutures = new ArrayList<>();
		for (PluginSource plugin : plugins) {
			pluginFutures.add(CompletableFuture.supplyAsync(() -> {
				try {
					return plugin.search(query, sort, page, slice, tag, versions);
				} catch (Exception e) {
					System.err.println("[Plugin] " + plugin.id() + " search failed: " + e.getMessage());
					return empty;
				}
			}).completeOnTimeout(empty, PluginHttp.TIMEOUT_MS + 2000L, TimeUnit.MILLISECONDS));
		}

		CompletableFuture<?>[] all = Stream.concat(Stream.of(apiFuture), pluginFutures.stream())
				.toArray(CompletableFuture[]::new);

		return CompletableFuture.allOf(all).thenApply(ignored -> {
			List<MinemevPostInfo> merged = new ArrayList<>();
			int totalItems = 0;
			int totalPages = 1;
			boolean unknownTotal = false;

			Set<String> seen = new HashSet<>();

			MinemevSearchResponse api = apiFuture.join();
			if (api != null) {
				totalPages = Math.max(totalPages, api.totalPages());
				if (api.totalPages() <= 0 || page <= api.totalPages()) {
					addNew(merged, seen, Arrays.asList(api.posts()));
					totalItems += api.totalItems();
				}
			}
			for (CompletableFuture<PluginSource.Result> future : pluginFutures) {
				PluginSource.Result result = future.join();
				boolean spent = result.totalPages() > 0 && page > result.totalPages();
				List<MinemevPostInfo> posts = spent ? List.of() : result.posts();
				int added = addNew(merged, seen, posts);
				if (result.totalItems() >= 0) {
					totalItems += result.totalItems();
				} else {
					totalItems += added;
				}
				if (result.totalPages() > 0) {
					totalPages = Math.max(totalPages, result.totalPages());
				} else if (!spent) {
					unknownTotal = true;
				}
			}
			if (merged.isEmpty() && apiError.get() != null) {
				throw new CompletionException(apiError.get());
			}
			if (unknownTotal) {
				totalPages = -1;
			}
			return new MinemevSearchResponse(merged.toArray(new MinemevPostInfo[0]), totalPages, totalItems, pageSize);
		});
	}

	private static String excludeWithPluginVendors(String excludeVendor, List<PluginSource> plugins) {
		Set<String> known = new HashSet<>();
		for (String vendor : apiVendors) {
			known.add(vendor.toLowerCase());
		}
		List<String> parts = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		if (excludeVendor != null && !excludeVendor.isEmpty()) {
			for (String entry : excludeVendor.split(",")) {
				String trimmed = entry.trim();
				if (!trimmed.isEmpty() && seen.add(trimmed.toLowerCase())) {
					parts.add(trimmed);
				}
			}
		}
		for (PluginSource plugin : plugins) {
			PluginManifest manifest = plugin.manifest();
			String vendor = manifest.vendor != null && !manifest.vendor.isBlank()
					? manifest.vendor : plugin.id();
			if (known.contains(vendor.toLowerCase()) && seen.add(vendor.toLowerCase())) {
				parts.add(vendor);
			}
		}
		return String.join(",", parts);
	}

	private static int addNew(List<MinemevPostInfo> merged, Set<String> seen, List<MinemevPostInfo> posts) {
		int added = 0;
		for (MinemevPostInfo post : posts) {
			String key = post.uuid();
			if (key == null || key.isEmpty() || seen.add(key)) {
				merged.add(post);
				added++;
			}
		}
		return added;
	}

	private static int countActiveApiVendors(String excludeVendor) {
		Set<String> excluded = new HashSet<>();
		if (excludeVendor != null && !excludeVendor.isEmpty()) {
			for (String entry : excludeVendor.split(",")) {
				excluded.add(entry.trim().toLowerCase());
			}
		}
		String[] known = apiVendors;
		if (known.length == 0) {
			return excluded.isEmpty() ? 1 : 0;
		}
		int active = 0;
		for (String vendor : known) {
			if (!excluded.contains(vendor.toLowerCase())) {
				active++;
			}
		}
		return active;
	}

	private static List<PluginSource> activePlugins(String excludeVendor) {
		Set<String> excluded = new HashSet<>();
		if (excludeVendor != null && !excludeVendor.isEmpty()) {
			for (String entry : excludeVendor.split(",")) {
				excluded.add(entry.trim().toLowerCase());
			}
		}
		List<PluginSource> plugins = new ArrayList<>();
		for (PluginSource plugin : PluginRegistry.enabled()) {
			if (!excluded.contains(plugin.id().toLowerCase())) {
				plugins.add(plugin);
			}
		}
		return plugins;
	}

	public static CompletableFuture<MinemevPostDetailInfo> getPostDetails(String vendorUuid) {
		return supplyAsync(() -> {
			String[] parts = parseVendorUuid(vendorUuid);
			return getPostDetailsInternal(parts[0], parts[1]);
		});
	}

	public static CompletableFuture<MinemevPostDetailInfo> getPostDetails(String vendor, String uuid) {
		return supplyAsync(() -> getPostDetailsInternal(vendor, uuid));
	}

	public static CompletableFuture<MinemevFileInfo[]> getPostFiles(String vendorUuid) {
		return supplyAsync(() -> {
			String[] parts = parseVendorUuid(vendorUuid);
			return getPostFilesInternal(parts[0], parts[1]);
		});
	}

	public static CompletableFuture<MinemevFileInfo[]> getPostFiles(String vendor, String uuid) {
		return supplyAsync(() -> getPostFilesInternal(vendor, uuid));
	}

	private static <T> CompletableFuture<T> supplyAsync(SupplierWithException<T> supplier) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return supplier.get();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static MinemevPostDetailInfo getPostDetailsInternal(String vendor, String uuid) throws IOException {
		PluginSource plugin = PluginRegistry.get(vendor);
		if (plugin != null) {
			return plugin.details(uuid);
		}
		if (PluginRegistry.isKnown(vendor)) {
			throw new IOException(PluginRegistry.nameOf(vendor) + " is disabled. Enable it in Plugins.");
		}
		String url = String.format("%s/%s/%s", getDetailsEndpoint(), vendor, uuid);
		return MinemevParsers.parsePostDetail(makeGetRequest(url));
	}

	private static MinemevFileInfo[] getPostFilesInternal(String vendor, String uuid) throws IOException {
		PluginSource plugin = PluginRegistry.get(vendor);
		if (plugin != null) {
			return plugin.files(uuid);
		}
		if (PluginRegistry.isKnown(vendor)) {
			throw new IOException(PluginRegistry.nameOf(vendor) + " is disabled. Enable it in Plugins.");
		}
		String url = String.format("%s/%s/%s", getFilesEndpoint(), vendor, uuid);
		return MinemevParsers.parseFileList(makeGetRequest(url));
	}

	private static String buildSearchUrl(String query, String sort, int cleanUuid, int page,
										 String tag, String versions, String excludeVendor, int pageSize) {
		StringBuilder url = new StringBuilder(getSearchEndpoint())
				.append("?clean_uuid=").append(cleanUuid);

		if (query != null && !query.isEmpty()) {
			url.append("&search=").append(encode(query));
		}
		if (sort != null && !sort.isEmpty()) {
			url.append("&sort=").append(sort);
		}
		if (page > 0) {
			url.append("&page=").append(page);
		}
		url.append("&pagesize=").append(pageSize);
		if (tag != null && !tag.isEmpty()) {
			url.append("&tag=").append(encode(tag));
		}
		if (versions != null && !versions.isEmpty() && !versions.equals("all")) {
			url.append("&versions=").append(encode(versions));
		}
		if (excludeVendor != null && !excludeVendor.isEmpty()) {
			url.append("&exclude_vendor=").append(encode(excludeVendor));
		}

		return url.toString();
	}

	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return value;
		}
	}

	private static String[] parseVendorUuid(String vendorUuid) {
		if (vendorUuid.contains("/")) {
            return vendorUuid.split("/", 2);
		}
		return new String[]{DEFAULT_VENDOR, vendorUuid};
	}

	private static String makeGetRequest(String urlString) throws IOException {
		try {
			return makeGetRequestInternal(urlString);
		} catch (IOException primaryError) {
			System.err.println("[HTTP] ERROR - Primary API request failed: " + primaryError.getMessage());

			String fallbackUrl = getFallbackUrl(urlString);
			if (fallbackUrl != null) {
				try {
					System.out.println("[HTTP] Trying fallback API...");
					return makeGetRequestInternal(fallbackUrl);
				} catch (IOException fallbackError) {
					System.err.println("[HTTP] ERROR - Fallback API also failed: " + fallbackError.getMessage());
					throw primaryError;
				}
			}

			throw primaryError;
		}
	}

	private static String makeGetRequestInternal(String urlString) throws IOException {
		System.out.println("[HTTP] GET " + urlString);
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");

			int responseCode = conn.getResponseCode();

			if (responseCode != HttpURLConnection.HTTP_OK) {
				System.err.println("[HTTP] ERROR - HTTP " + responseCode + ": " + conn.getResponseMessage());
				throw new IOException("HTTP error: " + responseCode);
			}

			StringBuilder response = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
			}
			return response.toString();
		} catch (IOException e) {
			System.err.println("[HTTP] ERROR - Exception during request: " + e.getMessage());
			throw e;
		} finally {
			conn.disconnect();
		}
	}

	private static String getFallbackUrl(String originalUrl) {
		boolean usingChoculaterie = originalUrl.contains(CHOCULATERIE_BASE_URL);

		if (usingChoculaterie) {
			return originalUrl.replace(CHOCULATERIE_BASE_URL, MINEMEV_BASE_URL);
		} else if (originalUrl.contains(MINEMEV_BASE_URL)) {
			return originalUrl.replace(MINEMEV_BASE_URL, CHOCULATERIE_BASE_URL);
		}

		return null;
	}

	@FunctionalInterface
	private interface SupplierWithException<T> {
		T get() throws Exception;
	}
}
