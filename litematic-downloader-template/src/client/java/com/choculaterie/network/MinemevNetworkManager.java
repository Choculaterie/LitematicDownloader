package com.choculaterie.network;

import com.choculaterie.config.DownloadSettings;
import com.choculaterie.models.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MinemevNetworkManager {
	private static final String MINEMEV_BASE_URL = "https://www.minemev.com/api";
	private static final String CHOCULATERIE_BASE_URL = "https://choculaterie.com/api/FallbackModAPI";

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

	private static final Gson GSON = new Gson();
	private static final int TIMEOUT = 10000;
	private static final int DEFAULT_PAGE = 1;
	private static final String DEFAULT_VENDOR = "minemev";

	public static CompletableFuture<String[]> getVendors() {
		return supplyAsync(() -> {
			String response = makeGetRequest(getVendorsEndpoint());
			return parseVendorList(response);
		});
	}

	public static CompletableFuture<MinemevSearchResponse> searchPosts(String query, String sort, int cleanUuid) {
		return searchPosts(query, sort, cleanUuid, DEFAULT_PAGE);
	}

	public static CompletableFuture<MinemevSearchResponse> searchPosts(
			String query, String sort, int cleanUuid, int page) {
		return searchPostsAdvanced(query, sort, cleanUuid, page, null, null, null);
	}

	public static CompletableFuture<MinemevSearchResponse> searchPostsAdvanced(
			String query, String sort, int cleanUuid, int page,
			String tag, String versions, String excludeVendor) {
		return supplyAsync(() -> {
			String url = buildSearchUrl(query, sort, cleanUuid, page, tag, versions, excludeVendor);
			return parseSearchResponse(makeGetRequest(url));
		});
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
		String url = String.format("%s/%s/%s", getDetailsEndpoint(), vendor, uuid);
		return parsePostDetail(makeGetRequest(url));
	}

	private static MinemevFileInfo[] getPostFilesInternal(String vendor, String uuid) throws IOException {
		String url = String.format("%s/%s/%s", getFilesEndpoint(), vendor, uuid);
		return parseFileList(makeGetRequest(url));
	}

	private static String buildSearchUrl(String query, String sort, int cleanUuid, int page,
										 String tag, String versions, String excludeVendor) {
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

	private static MinemevSearchResponse parseSearchResponse(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonArray postsArray = root.getAsJsonArray("posts");
		int totalPages = root.get("total_pages").getAsInt();
		int totalItems = root.get("total_items").getAsInt();

		List<MinemevPostInfo> posts = new ArrayList<>();
		for (int i = 0; i < postsArray.size(); i++) {
			posts.add(parsePostInfo(postsArray.get(i).getAsJsonObject()));
		}

		return new MinemevSearchResponse(posts.toArray(new MinemevPostInfo[0]), totalPages, totalItems);
	}

	private static MinemevPostInfo parsePostInfo(JsonObject obj) {
		return new MinemevPostInfo(
				getString(obj, "uuid"),
				getStringEither(obj, "post_name", "postName"),
				getString(obj, "description"),
				getString(obj, "User"),
				getInt(obj, "downloads"),
				getStringEither(obj, "published_at", "publishedAt"),
				getStringArray(obj, "tags"),
				getStringArray(obj, "versions"),
				getString(obj, "vendor"),
				getStringArray(obj, "images"),
				getStringEither(obj, "thumbnail_url", "thumbnailUrl"),
				getStringEither(obj, "user_picture", "userPicture"),
				getStringEither(obj, "yt_link", "ytLink"),
				getStringEither(obj, "url_redirect", "urlRedirect")
		);
	}

	private static MinemevPostDetailInfo parsePostDetail(String json) {
		JsonObject obj = GSON.fromJson(json, JsonObject.class);

		return new MinemevPostDetailInfo(
				getString(obj, "uuid"),
				getStringEither(obj, "post_name", "postName"),
				getString(obj, "description"),
				getStringEither(obj, "description_md", "descriptionMd"),
				getString(obj, "User"),
				getInt(obj, "downloads"),
				getStringEither(obj, "published_at", "publishedAt"),
				getStringArray(obj, "tags"),
				getStringArray(obj, "versions"),
				getStringArray(obj, "images"),
				getStringEither(obj, "yt_link", "ytLink"),
				getBoolean(obj, "owner"),
				getString(obj, "creators"),
				getStringEither(obj, "url_redirect", "urlRedirect")
		);
	}

	private static MinemevFileInfo[] parseFileList(String json) {
		JsonArray filesArray = GSON.fromJson(json, JsonArray.class);
		if (filesArray == null) {
			System.err.println("[MinemevNetworkManager] ERROR - filesArray is null");
			return new MinemevFileInfo[0];
		}

		List<MinemevFileInfo> files = new ArrayList<>();

		for (int i = 0; i < filesArray.size(); i++) {
			JsonObject obj = filesArray.get(i).getAsJsonObject();
			files.add(new MinemevFileInfo(
					getInt(obj, "id"),
					getStringEither(obj, "default_file_name", "defaultFileName"),
					getString(obj, "file"),
					getLongEither(obj, "file_size", "fileSize"),
					getStringArray(obj, "versions"),
					getInt(obj, "downloads"),
					getStringEither(obj, "file_type", "fileType"),
					getBooleanEither(obj, "is_verified", "isVerified")
			));
		}

		return files.toArray(new MinemevFileInfo[0]);
	}

	private static String[] parseVendorList(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonArray vendorsArray = root.getAsJsonArray("vendors");
		if (vendorsArray == null) {
			return new String[0];
		}

		String[] result = new String[vendorsArray.size()];
		for (int i = 0; i < vendorsArray.size(); i++) {
			result[i] = vendorsArray.get(i).getAsString();
		}
		return result;
	}

	private static String getString(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
	}

	private static String getStringEither(JsonObject obj, String snakeKey, String camelKey) {
		if (obj.has(snakeKey) && !obj.get(snakeKey).isJsonNull()) {
			return obj.get(snakeKey).getAsString();
		}
		if (obj.has(camelKey) && !obj.get(camelKey).isJsonNull()) {
			return obj.get(camelKey).getAsString();
		}
		return null;
	}

	private static int getInt(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : 0;
	}

	private static long getLong(JsonObject obj) {
		return (obj.has("file_size") && !obj.get("file_size").isJsonNull()) ? obj.get("file_size").getAsLong() : 0L;
	}

	private static long getLongEither(JsonObject obj, String snakeKey, String camelKey) {
		if (obj.has(snakeKey) && !obj.get(snakeKey).isJsonNull()) {
			return obj.get(snakeKey).getAsLong();
		}
		if (obj.has(camelKey) && !obj.get(camelKey).isJsonNull()) {
			return obj.get(camelKey).getAsLong();
		}
		return 0L;
	}

	private static boolean getBoolean(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) && obj.get(key).getAsBoolean();
	}

	private static boolean getBooleanEither(JsonObject obj, String snakeKey, String camelKey) {
		if (obj.has(snakeKey) && !obj.get(snakeKey).isJsonNull()) {
			return obj.get(snakeKey).getAsBoolean();
		}
		if (obj.has(camelKey) && !obj.get(camelKey).isJsonNull()) {
			return obj.get(camelKey).getAsBoolean();
		}
		return false;
	}

	private static String[] getStringArray(JsonObject obj, String key) {
		if (!obj.has(key) || obj.get(key).isJsonNull()) {
			return new String[0];
		}

		JsonArray array = obj.getAsJsonArray(key);
		String[] result = new String[array.size()];
		for (int i = 0; i < array.size(); i++) {
			result[i] = array.get(i).getAsString();
		}
		return result;
	}

	@FunctionalInterface
	private interface SupplierWithException<T> {
		T get() throws Exception;
	}
}
