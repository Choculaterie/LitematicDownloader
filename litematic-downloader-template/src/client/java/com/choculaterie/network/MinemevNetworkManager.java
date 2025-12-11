package com.choculaterie.network;

import com.choculaterie.models.MinemevFileInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevSearchResponse;
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
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT = 10000; // 10 seconds

    /**
     * Get list of available vendors
     * @return CompletableFuture with array of vendor names
     */
    public static CompletableFuture<String[]> getVendors() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String urlString = "https://www.minemev.com/api/vendors";
                String jsonResponse = makeGetRequest(urlString);
                return parseVendorList(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get vendors", e);
            }
        });
    }

    /**
     * Search for posts on Minemev
     * @param searchQuery The search query string
     * @param sort Sort method (e.g., "popular", "recent")
     * @param cleanUuid Whether to use clean UUID format (1 or 0)
     * @param page The page number to fetch (1-based)
     * @return CompletableFuture with the search response
     */
    public static CompletableFuture<MinemevSearchResponse> searchPosts(String searchQuery, String sort, int cleanUuid, int page) {
        return searchPostsAdvanced(searchQuery, sort, cleanUuid, page, null, null, null);
    }

    /**
     * Search for posts on Minemev with advanced filtering options
     * @param searchQuery The search query string
     * @param sort Sort method: "popular", "newest", "oldest", or "downloads"
     * @param cleanUuid Whether to use clean UUID format (1 or 0)
     * @param page The page number to fetch (1-based)
     * @param tag Optional tag filter (prefix with "server_" for server filtering)
     * @param versions Optional version filter (comma-separated, or "all")
     * @param excludeVendor Optional vendor to exclude from results
     * @return CompletableFuture with the search response
     */
    public static CompletableFuture<MinemevSearchResponse> searchPostsAdvanced(
            String searchQuery, String sort, int cleanUuid, int page,
            String tag, String versions, String excludeVendor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder();
                urlBuilder.append(BASE_URL).append("/search?clean_uuid=").append(cleanUuid);

                if (searchQuery != null && !searchQuery.isEmpty()) {
                    urlBuilder.append("&search=").append(URLEncoder.encode(searchQuery, StandardCharsets.UTF_8));
                }
                if (sort != null && !sort.isEmpty()) {
                    urlBuilder.append("&sort=").append(sort);
                }
                if (page > 0) {
                    urlBuilder.append("&page=").append(page);
                }
                if (tag != null && !tag.isEmpty()) {
                    urlBuilder.append("&tag=").append(URLEncoder.encode(tag, StandardCharsets.UTF_8));
                }
                if (versions != null && !versions.isEmpty() && !versions.equals("all")) {
                    urlBuilder.append("&versions=").append(URLEncoder.encode(versions, StandardCharsets.UTF_8));
                }
                if (excludeVendor != null && !excludeVendor.isEmpty()) {
                    urlBuilder.append("&exclude_vendor=").append(URLEncoder.encode(excludeVendor, StandardCharsets.UTF_8));
                }

                String finalUrl = urlBuilder.toString();
                System.out.println("[MinemevNetworkManager] Request URL: " + finalUrl);

                String jsonResponse = makeGetRequest(finalUrl);
                return parseSearchResponse(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to search posts", e);
            }
        });
    }

    /**
     * Search for posts on Minemev (defaults to page 1)
     * @param searchQuery The search query string
     * @param sort Sort method (e.g., "popular", "recent")
     * @param cleanUuid Whether to use clean UUID format (1 or 0)
     * @return CompletableFuture with the search response
     */
    public static CompletableFuture<MinemevSearchResponse> searchPosts(String searchQuery, String sort, int cleanUuid) {
        return searchPosts(searchQuery, sort, cleanUuid, 1);
    }

    /**
     * Get detailed information about a specific post
     * @param vendorUuid The full vendor/uuid string (e.g., "minemev/abc-123-def") or just the UUID
     * @return CompletableFuture with the post detail information
     */
    public static CompletableFuture<MinemevPostDetailInfo> getPostDetails(String vendorUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = parseVendorUuid(vendorUuid);
                String vendor = parts[0];
                String uuid = parts[1];
                String urlString = String.format("%s/details/%s/%s", BASE_URL, vendor, uuid);
                String jsonResponse = makeGetRequest(urlString);
                return parsePostDetail(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get post details", e);
            }
        });
    }

    /**
     * Get detailed information about a specific post
     * @param vendor The vendor name (e.g., "minemev")
     * @param uuid The post UUID
     * @return CompletableFuture with the post detail information
     */
    public static CompletableFuture<MinemevPostDetailInfo> getPostDetails(String vendor, String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String urlString = String.format("%s/details/%s/%s", BASE_URL, vendor, uuid);
                String jsonResponse = makeGetRequest(urlString);
                return parsePostDetail(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get post details", e);
            }
        });
    }

    /**
     * Get files associated with a specific post
     * @param vendorUuid The full vendor/uuid string (e.g., "minemev/abc-123-def") or just the UUID
     * @return CompletableFuture with array of file information
     */
    public static CompletableFuture<MinemevFileInfo[]> getPostFiles(String vendorUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String[] parts = parseVendorUuid(vendorUuid);
                String vendor = parts[0];
                String uuid = parts[1];
                String urlString = String.format("%s/files/%s/%s", BASE_URL, vendor, uuid);
                String jsonResponse = makeGetRequest(urlString);
                return parseFileList(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get post files", e);
            }
        });
    }

    /**
     * Get files associated with a specific post
     * @param vendor The vendor name (e.g., "minemev")
     * @param uuid The post UUID
     * @return CompletableFuture with array of file information
     */
    public static CompletableFuture<MinemevFileInfo[]> getPostFiles(String vendor, String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String urlString = String.format("%s/files/%s/%s", BASE_URL, vendor, uuid);
                String jsonResponse = makeGetRequest(urlString);
                return parseFileList(jsonResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to get post files", e);
            }
        });
    }

    /**
     * Parse a vendor/uuid string into separate parts
     * @param vendorUuid The vendor/uuid string (e.g., "minemev/abc-123-def")
     * @return Array with [vendor, uuid]
     */
    private static String[] parseVendorUuid(String vendorUuid) {
        if (vendorUuid.contains("/")) {
            String[] parts = vendorUuid.split("/", 2);
            return new String[]{parts[0], parts[1]};
        }
        // If no vendor prefix, assume minemev
        return new String[]{"minemev", vendorUuid};
    }

    /**
     * Make a GET request to the specified URL
     * @param urlString The URL to request
     * @return The response body as a string
     * @throws IOException If the request fails
     */
    private static String makeGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse search response JSON into MinemevSearchResponse object
     */
    private static MinemevSearchResponse parseSearchResponse(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        JsonArray postsArray = root.getAsJsonArray("posts");
        int totalPages = root.get("total_pages").getAsInt();
        int totalItems = root.get("total_items").getAsInt();
        
        List<MinemevPostInfo> posts = new ArrayList<>();
        for (int i = 0; i < postsArray.size(); i++) {
            JsonObject postObj = postsArray.get(i).getAsJsonObject();
            posts.add(parsePostInfo(postObj));
        }
        
        return new MinemevSearchResponse(posts.toArray(new MinemevPostInfo[0]), totalPages, totalItems);
    }

    /**
     * Parse a single post info from JSON
     */
    private static MinemevPostInfo parsePostInfo(JsonObject postObj) {
        String uuid = getStringOrNull(postObj, "uuid");
        String title = getStringOrNull(postObj, "post_name");
        String description = getStringOrNull(postObj, "description");
        String author = getStringOrNull(postObj, "User");
        int downloads = getIntOrZero(postObj, "downloads");
        String createdAt = getStringOrNull(postObj, "published_at");
        String[] tags = parseStringArray(postObj, "tags");
        String[] versions = parseStringArray(postObj, "versions");
        String vendor = getStringOrNull(postObj, "vendor");
        String[] images = parseStringArray(postObj, "images");
        String thumbnailUrl = getStringOrNull(postObj, "thumbnail_url");
        String userPicture = getStringOrNull(postObj, "user_picture");
        String ytLink = getStringOrNull(postObj, "yt_link");
        
        return new MinemevPostInfo(uuid, title, description, author, downloads, createdAt,
                tags, versions, vendor, images, thumbnailUrl, userPicture, ytLink);
    }

    /**
     * Parse post detail response JSON
     */
    private static MinemevPostDetailInfo parsePostDetail(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        
        String uuid = getStringOrNull(root, "uuid");
        String title = getStringOrNull(root, "post_name");
        String description = getStringOrNull(root, "description");
        String descriptionMd = getStringOrNull(root, "description_md");
        String author = getStringOrNull(root, "User");
        int downloads = getIntOrZero(root, "downloads");
        String createdAt = getStringOrNull(root, "published_at");
        String[] tags = parseStringArray(root, "tags");
        String[] versions = parseStringArray(root, "versions");
        String[] images = parseStringArray(root, "images");
        String ytLink = getStringOrNull(root, "yt_link");
        boolean owner = getBooleanOrFalse(root, "owner");
        String creators = getStringOrNull(root, "creators");
        
        return new MinemevPostDetailInfo(uuid, title, description, descriptionMd, author,
                downloads, createdAt, tags, versions, images, ytLink, owner, creators);
    }

    /**
     * Parse file list response JSON
     */
    private static MinemevFileInfo[] parseFileList(String json) {
        JsonArray filesArray = GSON.fromJson(json, JsonArray.class);
        List<MinemevFileInfo> files = new ArrayList<>();
        
        for (int i = 0; i < filesArray.size(); i++) {
            JsonObject fileObj = filesArray.get(i).getAsJsonObject();
            
            int id = getIntOrZero(fileObj, "id");
            String defaultFileName = getStringOrNull(fileObj, "default_file_name");
            String downloadUrl = getStringOrNull(fileObj, "file");
            long fileSize = getLongOrZero(fileObj, "file_size");
            String[] versions = parseStringArray(fileObj, "versions");
            int downloads = getIntOrZero(fileObj, "downloads");
            String fileType = getStringOrNull(fileObj, "file_type");
            boolean isVerified = getBooleanOrFalse(fileObj, "is_verified");
            
            files.add(new MinemevFileInfo(id, defaultFileName, downloadUrl, fileSize,
                    versions, downloads, fileType, isVerified));
        }
        
        return files.toArray(new MinemevFileInfo[0]);
    }

    // Helper methods for safe JSON parsing
    
    private static String getStringOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
    
    private static int getIntOrZero(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsInt() : 0;
    }
    
    private static long getLongOrZero(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsLong() : 0L;
    }
    
    private static boolean getBooleanOrFalse(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() && obj.get(key).getAsBoolean();
    }
    
    private static String[] parseStringArray(JsonObject obj, String key) {
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

    /**
     * Parse vendor list response JSON
     * Format: {"vendors":["minemev","redenmc","choculaterie"]}
     */
    private static String[] parseVendorList(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        if (!root.has("vendors") || root.get("vendors").isJsonNull()) {
            return new String[0];
        }

        JsonArray vendorsArray = root.getAsJsonArray("vendors");
        String[] result = new String[vendorsArray.size()];
        for (int i = 0; i < vendorsArray.size(); i++) {
            result[i] = vendorsArray.get(i).getAsString();
        }
        return result;
    }
}
