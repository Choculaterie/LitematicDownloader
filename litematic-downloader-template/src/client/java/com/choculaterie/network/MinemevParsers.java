package com.choculaterie.network;

import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class MinemevParsers {
	private static final Gson GSON = new Gson();

	private MinemevParsers() {
	}

	private static int intOrUnknown(JsonObject root, String key) {
		if (root == null || !root.has(key)) {
			return -1;
		}
		com.google.gson.JsonElement value = root.get(key);
		if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
			return -1;
		}
		try {
			return value.getAsInt();
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static MinemevSearchResponse parseSearchResponse(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonArray postsArray = root.getAsJsonArray("posts");
		int totalItems = intOrUnknown(root, "total_items");
		int reportedPages = intOrUnknown(root, "total_pages");
		int vendorPagesize = Math.max(0, intOrUnknown(root, "vendor_pagesize"));
		int vendorCount = Math.max(0, intOrUnknown(root, "vendor_count"));
		int effectivePerPage = (vendorPagesize > 0 && vendorCount > 0)
				? vendorPagesize * vendorCount
				: Math.max(0, reportedPages);
		int totalPages = (totalItems >= 0 && effectivePerPage > 0)
				? (int) Math.ceil((double) totalItems / effectivePerPage)
				: reportedPages;

		List<MinemevPostInfo> posts = new ArrayList<>();
		if (postsArray != null) {
			for (int i = 0; i < postsArray.size(); i++) {
				posts.add(parsePostInfo(postsArray.get(i).getAsJsonObject()));
			}
		}

		return new MinemevSearchResponse(posts.toArray(new MinemevPostInfo[0]), totalPages, totalItems, effectivePerPage);
	}

	public static MinemevPostInfo parsePostInfo(JsonObject obj) {
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

	public static MinemevPostDetailInfo parsePostDetail(String json) {
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

	public static MinemevFileInfo[] parseFileList(String json) {
		JsonArray filesArray = GSON.fromJson(json, JsonArray.class);
		if (filesArray == null) {
			return new MinemevFileInfo[0];
		}

		List<MinemevFileInfo> files = new ArrayList<>();
		for (int i = 0; i < filesArray.size(); i++) {
			JsonObject obj = filesArray.get(i).getAsJsonObject();
			files.add(new MinemevFileInfo(
					getString(obj, "id"),
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

	public static String[] parseVendorList(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonArray vendorsArray = root != null ? root.getAsJsonArray("vendors") : null;
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
}
