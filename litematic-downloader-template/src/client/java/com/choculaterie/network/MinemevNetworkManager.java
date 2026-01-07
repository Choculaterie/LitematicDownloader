package com.choculaterie.network;

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
	private static final String BASE_URL = "https://www.minemev.com/api";
	private static final String VENDORS_ENDPOINT = BASE_URL + "/vendors";
	private static final String SEARCH_ENDPOINT = BASE_URL + "/search";
	private static final String DETAILS_ENDPOINT = BASE_URL + "/details";
	private static final String FILES_ENDPOINT = BASE_URL + "/files";

	private static final Gson GSON = new Gson();
	private static final int TIMEOUT = 10000;
	private static final int DEFAULT_PAGE = 1;
	private static final String DEFAULT_VENDOR = "minemev";

	public static CompletableFuture<String[]> getVendors() {
		return supplyAsync(() -> {
			String response = makeGetRequest(VENDORS_ENDPOINT);
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
		String url = String.format("%s/%s/%s", DETAILS_ENDPOINT, vendor, uuid);
		return parsePostDetail(makeGetRequest(url));
	}

	private static MinemevFileInfo[] getPostFilesInternal(String vendor, String uuid) throws IOException {
		String url = String.format("%s/%s/%s", FILES_ENDPOINT, vendor, uuid);
		return parseFileList(makeGetRequest(url));
	}

	private static String buildSearchUrl(String query, String sort, int cleanUuid, int page,
										 String tag, String versions, String excludeVendor) {
		StringBuilder url = new StringBuilder(SEARCH_ENDPOINT)
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
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP error: " + conn.getResponseCode());
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
		} finally {
			conn.disconnect();
		}
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
				getString(obj, "post_name"),
				getString(obj, "description"),
				getString(obj, "User"),
				getInt(obj, "downloads"),
				getString(obj, "published_at"),
				getStringArray(obj, "tags"),
				getStringArray(obj, "versions"),
				getString(obj, "vendor"),
				getStringArray(obj, "images"),
				getString(obj, "thumbnail_url"),
				getString(obj, "user_picture"),
				getString(obj, "yt_link")
		);
	}

	private static MinemevPostDetailInfo parsePostDetail(String json) {
		JsonObject obj = GSON.fromJson(json, JsonObject.class);

		return new MinemevPostDetailInfo(
				getString(obj, "uuid"),
				getString(obj, "post_name"),
				getString(obj, "description"),
				getString(obj, "description_md"),
				getString(obj, "User"),
				getInt(obj, "downloads"),
				getString(obj, "published_at"),
				getStringArray(obj, "tags"),
				getStringArray(obj, "versions"),
				getStringArray(obj, "images"),
				getString(obj, "yt_link"),
				getBoolean(obj, "owner"),
				getString(obj, "creators")
		);
	}

	private static MinemevFileInfo[] parseFileList(String json) {
		JsonArray filesArray = GSON.fromJson(json, JsonArray.class);
		List<MinemevFileInfo> files = new ArrayList<>();

		for (int i = 0; i < filesArray.size(); i++) {
			JsonObject obj = filesArray.get(i).getAsJsonObject();
			files.add(new MinemevFileInfo(
					getInt(obj, "id"),
					getString(obj, "default_file_name"),
					getString(obj, "file"),
					getLong(obj),
					getStringArray(obj, "versions"),
					getInt(obj, "downloads"),
					getString(obj, "file_type"),
					getBoolean(obj, "is_verified")
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

	private static int getInt(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : 0;
	}

	private static long getLong(JsonObject obj) {
		return (obj.has("file_size") && !obj.get("file_size").isJsonNull()) ? obj.get("file_size").getAsLong() : 0L;
	}

	private static boolean getBoolean(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) && obj.get(key).getAsBoolean();
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
