package com.choculaterie.networking;

import com.choculaterie.models.SchematicDetailInfo;
import com.choculaterie.models.SchematicInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.net.ssl.HttpsURLConnection;

public class LitematicHttpClient {
    // Change this URL for development/production environments
    private static final String BASE_URL = "http://localhost:5036/api/LitematicDownloaderModAPI";
    // For production, change to: "https://choculaterie.com/api/LitematicDownloaderModAPI"

    private static final Gson gson = new Gson();
    private static final HttpClient client;

    static {
        try {
            // Create a trust manager that trusts all certificates (for development only)
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            // Initialize SSL context to trust all certificates
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            // Create HttpClient with the custom SSL context
            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize HttpClient with custom SSL context", e);
        }
    }

    public static class PaginatedResult {
        private final List<SchematicInfo> items;
        private final int totalPages;
        private final int currentPage;
        private final int totalItems;

        public PaginatedResult(List<SchematicInfo> items, int totalPages, int currentPage, int totalItems) {
            this.items = items;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
            this.totalItems = totalItems;
        }

        public List<SchematicInfo> getItems() { return items; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getTotalItems() { return totalItems; }
    }

    public static PaginatedResult fetchSchematicsPaginated(int page, int pageSize) {
        try {
            String url = BASE_URL + "/GetPaginated?page=" + page + "&pageSize=" + pageSize;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Pagination API Response Status: " + response.statusCode());
            System.out.println("Pagination API Response Body: " + response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematics: " + response.statusCode());
            }

            if (response.body() == null || response.body().trim().isEmpty()) {
                System.out.println("Empty response body");
                return new PaginatedResult(new ArrayList<>(), 0, 0, 0);
            }

            // Parse as JsonObject since your API returns a paginated object
            JsonObject jsonResponse;
            try {
                jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                System.err.println("Failed to parse JSON response as object: " + e.getMessage());
                System.err.println("Response body: " + response.body());
                return new PaginatedResult(new ArrayList<>(), 0, 0, 0);
            }

            if (jsonResponse == null) {
                System.err.println("JsonObject is null");
                return new PaginatedResult(new ArrayList<>(), 0, 0, 0);
            }

            // Extract the schematics array
            JsonArray schematicsArray = null;
            if (jsonResponse.has("schematics")) {
                schematicsArray = jsonResponse.getAsJsonArray("schematics");
            }

            if (schematicsArray == null) {
                System.err.println("Schematics array is null or missing");
                return new PaginatedResult(new ArrayList<>(), 0, 0, 0);
            }

            List<SchematicInfo> schematics = new ArrayList<>();
            for (int i = 0; i < schematicsArray.size(); i++) {
                try {
                    JsonObject json = schematicsArray.get(i).getAsJsonObject();
                    SchematicInfo schematic = new SchematicInfo(
                            json.has("id") ? json.get("id").getAsString() : "",
                            json.has("name") ? json.get("name").getAsString() : "Unknown",
                            json.has("description") && !json.get("description").isJsonNull() ?
                                    json.get("description").getAsString() : "",
                            json.has("viewCount") ? json.get("viewCount").getAsInt() : 0,
                            json.has("downloadCount") ? json.get("downloadCount").getAsInt() : 0,
                            json.has("username") ? json.get("username").getAsString() : "Unknown"
                    );
                    schematics.add(schematic);
                } catch (Exception e) {
                    System.err.println("Error parsing schematic item " + i + ": " + e.getMessage());
                }
            }

            // Extract pagination metadata
            int totalPages = jsonResponse.has("totalPages") ? jsonResponse.get("totalPages").getAsInt() : 1;
            int currentPage = jsonResponse.has("currentPage") ? jsonResponse.get("currentPage").getAsInt() : 1;
            int totalCount = jsonResponse.has("totalCount") ? jsonResponse.get("totalCount").getAsInt() : schematics.size();

            return new PaginatedResult(schematics, totalPages, currentPage, totalCount);
        } catch (Exception e) {
            System.err.println("Error in fetchSchematicsPaginated: " + e.getMessage());
            e.printStackTrace();
            return new PaginatedResult(new ArrayList<>(), 0, 0, 0);
        }
    }

    public static List<SchematicInfo> searchSchematics(String query) {
        try {
            String encodedQuery = java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = BASE_URL + "/Search?query=" + encodedQuery;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Search API Response Status: " + response.statusCode());
            System.out.println("Search API Response Body: " + response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to search schematics: " + response.statusCode());
            }

            if (response.body() == null || response.body().trim().isEmpty()) {
                System.out.println("Empty search response body");
                return new ArrayList<>();
            }

            // Try to parse as paginated object first (same format as pagination)
            try {
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

                if (jsonResponse != null && jsonResponse.has("schematics")) {
                    // Parse as paginated response
                    JsonArray schematicsArray = jsonResponse.getAsJsonArray("schematics");

                    List<SchematicInfo> schematics = new ArrayList<>();
                    for (int i = 0; i < schematicsArray.size(); i++) {
                        try {
                            JsonObject json = schematicsArray.get(i).getAsJsonObject();
                            SchematicInfo schematic = new SchematicInfo(
                                    json.has("id") ? json.get("id").getAsString() : "",
                                    json.has("name") ? json.get("name").getAsString() : "Unknown",
                                    json.has("description") && !json.get("description").isJsonNull() ?
                                            json.get("description").getAsString() : "",
                                    json.has("viewCount") ? json.get("viewCount").getAsInt() : 0,
                                    json.has("downloadCount") ? json.get("downloadCount").getAsInt() : 0,
                                    json.has("username") ? json.get("username").getAsString() : "Unknown"
                            );
                            schematics.add(schematic);
                        } catch (Exception e) {
                            System.err.println("Error parsing search result item " + i + ": " + e.getMessage());
                        }
                    }
                    return schematics;
                }
            } catch (Exception e) {
                System.err.println("Failed to parse as paginated object, trying direct array: " + e.getMessage());
            }

            // Fallback: try to parse as direct array
            try {
                JsonArray jsonArray = gson.fromJson(response.body(), JsonArray.class);

                if (jsonArray == null) {
                    System.err.println("Search JsonArray is null");
                    return new ArrayList<>();
                }

                List<SchematicInfo> schematics = new ArrayList<>();
                for (int i = 0; i < jsonArray.size(); i++) {
                    try {
                        JsonObject json = jsonArray.get(i).getAsJsonObject();
                        SchematicInfo schematic = new SchematicInfo(
                                json.has("id") ? json.get("id").getAsString() : "",
                                json.has("name") ? json.get("name").getAsString() : "Unknown",
                                json.has("description") && !json.get("description").isJsonNull() ?
                                        json.get("description").getAsString() : "",
                                json.has("viewCount") ? json.get("viewCount").getAsInt() : 0,
                                json.has("downloadCount") ? json.get("downloadCount").getAsInt() : 0,
                                json.has("username") ? json.get("username").getAsString() : "Unknown"
                        );
                        schematics.add(schematic);
                    } catch (Exception e) {
                        System.err.println("Error parsing search result item " + i + ": " + e.getMessage());
                    }
                }
                return schematics;
            } catch (Exception e) {
                System.err.println("Failed to parse search response as array: " + e.getMessage());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("Error in searchSchematics: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // Keep the old method for backward compatibility, but use pagination
    @Deprecated
    public static List<SchematicInfo> fetchSchematicList() {
        PaginatedResult result = fetchSchematicsPaginated(1, 50);
        return result.getItems();
    }

    // Rest of the methods remain the same...
    public static SchematicDetailInfo fetchSchematicDetail(String id) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/Getbyid/" + id))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematic details: " + response.statusCode());
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);

            return new SchematicDetailInfo(
                    json.get("id").getAsString(),
                    json.get("name").getAsString(),
                    json.has("description") && !json.get("description").isJsonNull() ?
                            json.get("description").getAsString() : "",
                    json.get("viewCount").getAsInt(),
                    json.get("downloadCount").getAsInt(),
                    json.has("coverPicture") && !json.get("coverPicture").isJsonNull() ?
                            json.get("coverPicture").getAsString() : "",
                    json.get("username").getAsString(),
                    json.get("publishDate").getAsString()
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to fetch schematic detail", e);
        }
    }

    public static String downloadSchematic(String base64Data, String fileName) {
        try {
            File gameDir = MinecraftClient.getInstance().runDirectory;
            String savePath;

            if (gameDir != null) {
                savePath = new File(gameDir, "schematics").getAbsolutePath() + File.separator;
            } else {
                boolean isDevelopment = System.getProperty("dev.env", "false").equals("true");
                String homeDir = System.getProperty("user.home");

                if (isDevelopment) {
                    savePath = homeDir + File.separator + "Downloads" + File.separator +
                            "litematic-downloader-template-1.21.4" + File.separator +
                            "run" + File.separator + "schematics" + File.separator;
                } else {
                    savePath = homeDir + File.separator + "AppData" + File.separator +
                            "Roaming" + File.separator + ".minecraft" + File.separator +
                            "schematics" + File.separator;
                }
            }

            File directory = new File(savePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String filePath = savePath + fileName + ".litematic";
            byte[] decodedData = Base64.getDecoder().decode(base64Data);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(decodedData);
            }

            return filePath;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to download schematic: " + e.getMessage(), e);
        }
    }

    public static String fetchAndDownloadSchematic(String id, String fileName) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/GetLitematicFiles/" + id))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematic file: " + response.statusCode());
            }

            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (json.has("files") && json.getAsJsonArray("files").size() > 0) {
                String base64Data = json.getAsJsonArray("files").get(0).getAsString();
                return downloadSchematic(base64Data, fileName);
            } else {
                throw new RuntimeException("No schematic file found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to download schematic: " + e.getMessage(), e);
        }
    }

    public static void uploadLitematicFile(File file, UploadCallback callback) {
        new Thread(() -> {
            try {
                String boundary = "Boundary-" + System.currentTimeMillis();
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(("--" + boundary + "\r\n").getBytes());
                baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" +
                        file.getName() + "\"\r\n").getBytes());
                baos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes());
                baos.write(fileBytes);
                baos.write("\r\n".getBytes());
                baos.write(("--" + boundary + "--\r\n").getBytes());

                byte[] requestBody = baos.toByteArray();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/upload"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    String fileUrl = json.get("fileUrl").getAsString();
                    String viewerUrl = json.get("viewerUrl").getAsString();

                    MinecraftClient.getInstance().execute(() -> {
                        MinecraftClient.getInstance().keyboard.setClipboard(viewerUrl);
                        callback.onSuccess(viewerUrl);
                    });
                } else {
                    MinecraftClient.getInstance().execute(() ->
                            callback.onError("Upload failed: " + response.statusCode() + " " + response.body()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() ->
                        callback.onError("Upload failed: " + e.getMessage()));
            }
        }).start();
    }

    public interface UploadCallback {
        void onSuccess(String url);
        void onError(String message);
    }
}