package com.choculaterie.networking;

import com.choculaterie.models.MinemevPostInfo;
import com.choculaterie.models.MinemevPostDetailInfo;
import com.choculaterie.models.MinemevFileInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MinemevHttpClient {
    private static final String BASE_URL = "https://www.minemev.com/api";
    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static class MinemevSearchResult {
        private final ReferenceImmutableList<MinemevPostInfo> posts;
        private final int currentPage;
        private final boolean hasNextPage;
        private final int totalResults;
        private final int totalPages;

        public MinemevSearchResult(ArrayList<MinemevPostInfo> posts, int currentPage,
                                   boolean hasNextPage, int totalResults, int totalPages) {
            this.posts = new ReferenceImmutableList<>(posts);
            this.currentPage = currentPage;
            this.hasNextPage = hasNextPage;
            this.totalResults = totalResults;
            this.totalPages = totalPages;
        }

        public ReferenceImmutableList<MinemevPostInfo> getPosts() { return posts; }
        public int getCurrentPage() { return currentPage; }
        public boolean hasNextPage() { return hasNextPage; }
        public int getTotalResults() { return totalResults; }
        public int getTotalPages() { return totalPages; }
    }

    public static MinemevSearchResult searchPosts(String tag, String search, String versions,
                                                  String sort, int page) {
        ArrayList<MinemevPostInfo> posts = new ArrayList<>();
        MinemevSearchResult result = new MinemevSearchResult(posts, page, false, 0, 1);

        try {
            StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/search?clean_uuid=1&page=" + page);

            if (tag != null && !tag.trim().isEmpty()) {
                urlBuilder.append("&tag=").append(URLEncoder.encode(tag, StandardCharsets.UTF_8));
            }
            if (search != null && !search.trim().isEmpty()) {
                urlBuilder.append("&search=").append(URLEncoder.encode(search, StandardCharsets.UTF_8));
            }
            if (versions != null && !versions.trim().isEmpty()) {
                urlBuilder.append("&versions=").append(URLEncoder.encode(versions, StandardCharsets.UTF_8));
            }
            if (sort != null && !sort.trim().isEmpty()) {
                urlBuilder.append("&sort=").append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
            }

            String url = urlBuilder.toString();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            System.out.println("Minemev Search URL: " + url);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Minemev Search Response Status: " + response.statusCode());
            System.out.println("Minemev Search Response Body: " + response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to search Minemev posts: " + response.statusCode());
            }

            JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            if (jsonResponse == null) {
                return result;
            }

            // Parse posts array
            if (jsonResponse.has("posts")) {
                JsonArray postsArray = jsonResponse.getAsJsonArray("posts");
                for (int i = 0; i < postsArray.size(); i++) {
                    try {
                        JsonObject postJson = postsArray.get(i).getAsJsonObject();

                        // Parse tags array
                        String[] tags = new String[0];
                        if (postJson.has("tags") && postJson.get("tags").isJsonArray()) {
                            JsonArray tagsArray = postJson.getAsJsonArray("tags");
                            tags = new String[tagsArray.size()];
                            for (int j = 0; j < tagsArray.size(); j++) {
                                tags[j] = tagsArray.get(j).getAsString();
                            }
                        }

                        // Parse versions array
                        String[] versions_arr = new String[0];
                        if (postJson.has("versions") && postJson.get("versions").isJsonArray()) {
                            JsonArray versionsArray = postJson.getAsJsonArray("versions");
                            versions_arr = new String[versionsArray.size()];
                            for (int j = 0; j < versionsArray.size(); j++) {
                                versions_arr[j] = versionsArray.get(j).getAsString();
                            }
                        }

                        String uuid = postJson.has("uuid") ? postJson.get("uuid").getAsString() : "";
                        String title = postJson.has("post_name") ? postJson.get("post_name").getAsString() :
                                (postJson.has("title") ? postJson.get("title").getAsString() : "Unknown");
                        String author = postJson.has("User") ? postJson.get("User").getAsString() :
                                (postJson.has("author") ? postJson.get("author").getAsString() : "Unknown");
                        String desc = postJson.has("description") && !postJson.get("description").isJsonNull()
                                ? postJson.get("description").getAsString() : "";
                        int downloads = postJson.has("downloads") ? postJson.get("downloads").getAsInt() : 0;
                        String createdAt = postJson.has("published_at") ? postJson.get("published_at").getAsString() :
                                (postJson.has("created_at") ? postJson.get("created_at").getAsString() : "");
                        String vendor = postJson.has("vendor") && !postJson.get("vendor").isJsonNull()
                                ? postJson.get("vendor").getAsString() : "";

                        MinemevPostInfo post = new MinemevPostInfo(
                                uuid, title, desc, author, downloads, createdAt, tags, versions_arr, vendor
                        );
                        posts.add(post);
                    } catch (Exception e) {
                        System.err.println("Error parsing Minemev post " + i + ": " + e.getMessage());
                    }
                }
            }

            int totalResults = jsonResponse.has("total_items") ? jsonResponse.get("total_items").getAsInt() :
                    (jsonResponse.has("total") ? jsonResponse.get("total").getAsInt() : posts.size());
            int totalPages = jsonResponse.has("total_pages") ? jsonResponse.get("total_pages").getAsInt() : 1;
            boolean hasNextPage = jsonResponse.has("has_next_page") ? jsonResponse.get("has_next_page").getAsBoolean() :
                    (page < totalPages);

            result = new MinemevSearchResult(posts, page, hasNextPage, totalResults, totalPages);
            return result;

        } catch (Exception e) {
            System.err.println("Error in Minemev searchPosts: " + e.getMessage());
            if (e.getCause() != null) System.err.println("cause: " + e.getCause());
            return result;
        }
    }

    public static MinemevPostDetailInfo fetchPostDetails(String uuid, String vendor) {
        try {
            String url = BASE_URL + "/details/" + uuid;
            if(Objects.equals(vendor, "minemev")){
                 url = BASE_URL + "/minemev/details/" + uuid;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch Minemev post details: " + response.statusCode());
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            // Parse tags array
            String[] tags = new String[0];
            if (json.has("tags") && json.get("tags").isJsonArray()) {
                JsonArray tagsArray = json.getAsJsonArray("tags");
                tags = new String[tagsArray.size()];
                for (int i = 0; i < tagsArray.size(); i++) {
                    tags[i] = tagsArray.get(i).getAsString();
                }
            }

            // Parse versions array
            String[] versions = new String[0];
            if (json.has("versions") && json.get("versions").isJsonArray()) {
                JsonArray versionsArray = json.getAsJsonArray("versions");
                versions = new String[versionsArray.size()];
                for (int i = 0; i < versionsArray.size(); i++) {
                    versions[i] = versionsArray.get(i).getAsString();
                }
            }

            // Parse images array
            String[] images = new String[0];
            if (json.has("images") && json.get("images").isJsonArray()) {
                JsonArray imagesArray = json.getAsJsonArray("images");
                images = new String[imagesArray.size()];
                for (int i = 0; i < imagesArray.size(); i++) {
                    images[i] = imagesArray.get(i).getAsString();
                }
            }

            String title = json.has("post_name") ? json.get("post_name").getAsString() :
                    (json.has("title") ? json.get("title").getAsString() : "Unknown");
            String author = json.has("User") ? json.get("User").getAsString() :
                    (json.has("author") ? json.get("author").getAsString() : "Unknown");
            String createdAt = json.has("published_at") ? json.get("published_at").getAsString() :
                    (json.has("created_at") ? json.get("created_at").getAsString() : "");

            return new MinemevPostDetailInfo(
                    json.has("uuid") ? json.get("uuid").getAsString() : uuid,
                    title,
                    json.has("description") && !json.get("description").isJsonNull() ? json.get("description").getAsString() : "",
                    author,
                    json.has("downloads") ? json.get("downloads").getAsInt() : 0,
                    createdAt,
                    json.has("updated_at") ? json.get("updated_at").getAsString() : "",
                    tags,
                    versions,
                    images,
                    json.has("thumbnail_url") && !json.get("thumbnail_url").isJsonNull() ? json.get("thumbnail_url").getAsString() : ""
            );

        } catch (Exception e) {
            System.err.println("Failed to fetch Minemev post details: " + e.getMessage());
            if (e.getCause() != null) System.err.println("cause: " + e.getCause());
            throw new RuntimeException("Failed to fetch Minemev post details", e);
        }
    }

    public static List<MinemevFileInfo> fetchPostFiles(String uuid, String vendor) {
        List<MinemevFileInfo> files = new ArrayList<>();

        try {
            String url = BASE_URL + "/files/" + uuid;

            if(Objects.equals(vendor, "minemev")){
                url = BASE_URL + "/minemev/files/" + uuid;
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch Minemev post files: " + response.statusCode());
            }

            // Accept both array and object forms
            com.google.gson.JsonElement root = gson.fromJson(response.body(), com.google.gson.JsonElement.class);
            JsonArray filesArray = null;
            if (root != null) {
                if (root.isJsonArray()) {
                    filesArray = root.getAsJsonArray();
                } else if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    if (obj.has("files") && obj.get("files").isJsonArray()) {
                        filesArray = obj.getAsJsonArray("files");
                    } else if (obj.has("data") && obj.get("data").isJsonArray()) {
                        filesArray = obj.getAsJsonArray("data");
                    } else {
                        // Some API variants return an object with numeric keys like { "0": {...}, "1": {...} }
                        // or return mixed properties where values are objects/arrays. Collect those into an array.
                        JsonArray collected = new JsonArray();
                        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
                            com.google.gson.JsonElement val = entry.getValue();
                            if (val == null || val.isJsonNull()) continue;
                            if (val.isJsonObject()) {
                                collected.add(val);
                            } else if (val.isJsonArray()) {
                                // if a nested array is present under a key, flatten it
                                for (com.google.gson.JsonElement el : val.getAsJsonArray()) {
                                    if (el != null && el.isJsonObject()) collected.add(el);
                                }
                            }
                        }
                        if (!collected.isEmpty()) filesArray = collected;
                     }
                 }
             }

            if (filesArray != null) {
                for (int i = 0; i < filesArray.size(); i++) {
                    try {
                        JsonObject fileJson = filesArray.get(i).getAsJsonObject();
                        String[] versions = new String[0];
                        if (fileJson.has("versions") && fileJson.get("versions").isJsonArray()) {
                            JsonArray versionsArray = fileJson.getAsJsonArray("versions");
                            versions = new String[versionsArray.size()];
                            for (int j = 0; j < versionsArray.size(); j++) {
                                versions[j] = versionsArray.get(j).getAsString();
                            }
                        }

                        // Support multiple field names from API variants
                        String name = null;
                        if (fileJson.has("name") && !fileJson.get("name").isJsonNull()) name = fileJson.get("name").getAsString();
                        else if (fileJson.has("default_file_name") && !fileJson.get("default_file_name").isJsonNull()) name = fileJson.get("default_file_name").getAsString();
                        if (name == null) name = "Unknown";

                        String durl = null;
                        if (fileJson.has("download_url") && !fileJson.get("download_url").isJsonNull()) durl = fileJson.get("download_url").getAsString();
                        else if (fileJson.has("file") && !fileJson.get("file").isJsonNull()) durl = fileJson.get("file").getAsString();
                        if (durl == null) durl = "";

                        long size = 0L;
                        if (fileJson.has("size") && !fileJson.get("size").isJsonNull()) size = fileJson.get("size").getAsLong();
                        else if (fileJson.has("file_size") && !fileJson.get("file_size").isJsonNull()) size = fileJson.get("file_size").getAsLong();

                        String type = "unknown";
                        if (fileJson.has("type") && !fileJson.get("type").isJsonNull()) type = fileJson.get("type").getAsString();
                        else if (fileJson.has("file_type") && !fileJson.get("file_type").isJsonNull()) type = fileJson.get("file_type").getAsString();

                        int dcount = fileJson.has("download_count") && !fileJson.get("download_count").isJsonNull() ? fileJson.get("download_count").getAsInt() :
                                (fileJson.has("downloads") && !fileJson.get("downloads").isJsonNull() ? fileJson.get("downloads").getAsInt() : 0);

                        MinemevFileInfo file = new MinemevFileInfo(
                                name,
                                durl,
                                size,
                                versions,
                                dcount,
                                type
                        );
                        files.add(file);
                    } catch (Exception e) {
                        System.err.println("Error parsing Minemev file " + i + ": " + e.getMessage());
                    }
                }
            }

            return files;

        } catch (Exception e) {
            System.err.println("Failed to fetch Minemev post files: " + e.getMessage());
            if (e.getCause() != null) System.err.println("cause: " + e.getCause());
            throw new RuntimeException("Failed to fetch Minemev post files", e);
        }
    }

    // Resolve the actual download URL for a file
    public static String getDownloadUrl(String postUuid, String fileName, String downloadUrlMaybeEmpty) {
        try {
            // Prefer provided URL if present
            if (downloadUrlMaybeEmpty != null && !downloadUrlMaybeEmpty.trim().isEmpty()) {
                return normalizeExternalUrl(downloadUrlMaybeEmpty.trim());
            }

            // Primary: call a resolver endpoint
            String encodedUuid = URLEncoder.encode(postUuid, StandardCharsets.UTF_8);
            String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            String resolverUrl = BASE_URL + "/minemev/getDownloadUrl?uuid=" + encodedUuid + "&filename=" + encodedName;

            System.out.println("Minemev getDownloadUrl: " + resolverUrl);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(resolverUrl))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body() != null) {
                    String body = response.body().trim();
                    String url = null;
                    if (body.startsWith("{") || body.startsWith("[")) {
                        try {
                            JsonObject json = gson.fromJson(body, JsonObject.class);
                            if (json != null) {
                                if (json.has("download_url")) url = json.get("download_url").getAsString();
                                else if (json.has("url")) url = json.get("url").getAsString();
                                else if (json.has("downloadUrl")) url = json.get("downloadUrl").getAsString();
                            }
                        } catch (Exception ignored) {}
                    } else if (body.startsWith("http")) {
                        url = body;
                    }

                    if (url != null && !url.isEmpty()) {
                        return normalizeExternalUrl(url);
                    }
                }
            } catch (Exception e) {
                System.err.println("Resolver call failed: " + e.getMessage());
            }

            // Fallback candidates (best-effort)
            String cand1 = BASE_URL + "/minemev/download/" + encodedUuid + "?filename=" + encodedName;
            String cand2 = BASE_URL + "/minemev/download?uuid=" + encodedUuid + "&filename=" + encodedName;

            if (isReachable(cand1)) return cand1;
            if (isReachable(cand2)) return cand2;

            throw new IllegalArgumentException("No download URL available for this file");
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve download URL: " + e.getMessage(), e);
        }
    }

    private static boolean isReachable(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeExternalUrl(String url) {
        if (url == null) return "";
        String u = url.trim();
        if (u.isEmpty()) return u;
        if (u.startsWith("http://") || u.startsWith("https://")) return u;
        if (u.startsWith("//")) return "https:" + u;
        if (u.startsWith("/")) return "https://www.minemev.com" + u;
        if (u.matches("[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")) return "https://" + u;
        return u;
    }

    // Convenience method for simple search
    public static MinemevSearchResult searchPosts(String searchQuery, int page) {
        return searchPosts(null, searchQuery, null, "newest", page);
    }

    // Convenience method for searching by tag
    public static MinemevSearchResult searchByTag(String tag, int page) {
        return searchPosts(tag, null, null, "newest", page);
    }
}
