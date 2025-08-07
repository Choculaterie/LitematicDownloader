package com.choculaterie.networking;

import com.choculaterie.models.SchematicCreateDTO;
import com.choculaterie.models.SchematicDetailInfo;
import com.choculaterie.models.SchematicInfo;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ReferenceImmutableList;
import net.minecraft.client.MinecraftClient;
import com.choculaterie.config.SettingsManager;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LitematicHttpClient {
    // Change this URL for development/production environments
    private static final String BASE_URL = "https://localhost:7282/api/LitematicDownloaderModAPI";
    //private static final String BASE_URL = "https://choculaterie.com/api/LitematicDownloaderModAPI";

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
        private final ReferenceImmutableList<SchematicInfo> items;
        private final int totalPages;
        private final int currentPage;
        private final int totalItems;

        public PaginatedResult(ArrayList<SchematicInfo> items, int totalPages, int currentPage, int totalItems) {
            this.items = new ReferenceImmutableList<>(items);
            this.totalPages = totalPages;
            this.currentPage = currentPage;
            this.totalItems = totalItems;
        }

        public ReferenceImmutableList<SchematicInfo> getItems() { return items; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getTotalItems() { return totalItems; } // TODO: Verify if useful
    }

    public static PaginatedResult fetchSchematicsPaginated(int page, int pageSize) {
        final ArrayList<SchematicInfo> schematicItems = new ArrayList<>();
        PaginatedResult result = new PaginatedResult(schematicItems, 0, 0, 0);

        try {
            String url = BASE_URL + "/GetPaginatedFtp?page=" + page + "&pageSize=" + pageSize;
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
                return result;
            }

            // Parse as JsonObject since your API returns a paginated object
            JsonObject jsonResponse;
            try {
                jsonResponse = gson.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                System.err.println("Failed to parse JSON response as object: " + e.getMessage());
                System.err.println("Response body: " + response.body());
                return result;
            }

            if (jsonResponse == null) {
                System.err.println("JsonObject is null");
                return result;
            }

            // Extract the schematics array
            JsonArray schematicsArray = null;
            if (jsonResponse.has("schematics")) {
                schematicsArray = jsonResponse.getAsJsonArray("schematics");
            }

            if (schematicsArray == null) {
                System.err.println("Schematics array is null or missing");
                return result;
            }

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
                    schematicItems.add(schematic);
                } catch (Exception e) {
                    System.err.println("Error parsing schematic item " + i + ": " + e.getMessage());
                }
            }

            // Extract pagination metadata
            int totalPages = jsonResponse.has("totalPages") ? jsonResponse.get("totalPages").getAsInt() : 1;
            int currentPage = jsonResponse.has("currentPage") ? jsonResponse.get("currentPage").getAsInt() : 1;
            int totalCount = jsonResponse.has("totalCount") ? jsonResponse.get("totalCount").getAsInt() : schematicItems.size();
            result = new PaginatedResult(schematicItems, totalPages, currentPage, totalCount);

            return result;
        } catch (Exception e) {
            System.err.println("Error in fetchSchematicsPaginated: " + e.getMessage());
            e.printStackTrace();
            return result;
        }
    }

    public static List<SchematicInfo> searchSchematics(String query) {
        List<SchematicInfo> schematics = new ArrayList<>();
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
                return schematics;
            }

            // Try to parse as paginated object first (same format as pagination)
            try {
                JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

                if (jsonResponse != null && jsonResponse.has("schematics")) {
                    // Parse as paginated response
                    JsonArray schematicsArray = jsonResponse.getAsJsonArray("schematics");

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
                    return schematics;
                }

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
                return schematics;
            }
        } catch (Exception e) {
            System.err.println("Error in searchSchematics: " + e.getMessage());
            e.printStackTrace();
            return schematics;
        }
    }

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

    /**
     * Downloads a file from URL to local path
     */
    public static String downloadFileFromUrl(String downloadUrl, String fileName) {
        try {
            // Use SettingsManager to get the configured schematics path
            String savePath = SettingsManager.getSchematicsPath() + File.separator;

            File directory = new File(savePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String filePath = savePath + fileName + ".litematic";

            // Properly encode the URL to handle spaces and special characters
            String encodedUrl;
            try {
                // Parse the URL to extract components
                java.net.URL url = new java.net.URL(downloadUrl);

                // Encode only the path part to preserve the scheme and host
                String encodedPath = java.net.URLEncoder.encode(url.getPath(), StandardCharsets.UTF_8)
                    .replace("%2F", "/"); // Keep forward slashes unencoded for path separators

                // Reconstruct the URL with encoded path
                encodedUrl = url.getProtocol() + "://" + url.getHost() +
                    (url.getPort() != -1 ? ":" + url.getPort() : "") + encodedPath;

                System.out.println("Original URL: " + downloadUrl);
                System.out.println("Encoded URL: " + encodedUrl);
            } catch (Exception e) {
                // Fallback: if URL parsing fails, try simple encoding
                encodedUrl = downloadUrl.replace(" ", "%20");
                System.out.println("Fallback encoding - Original: " + downloadUrl + ", Encoded: " + encodedUrl);
            }

            // Create HTTP request to download the file
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(encodedUrl))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download file from URL: " + response.statusCode());
            }

            // Write the downloaded bytes to file
            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                fos.write(response.body());
            }

            return filePath;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to download file: " + e.getMessage(), e);
        }
    }

    // Keep the old method for backward compatibility, but mark as deprecated
    @Deprecated
    public static String downloadSchematic(String base64Data, String fileName) {
        try {
            // Use SettingsManager to get the configured schematics path
            String savePath = SettingsManager.getSchematicsPath() + File.separator;

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
                // Get first file from the response - now expecting URL format
                JsonObject firstFile = json.getAsJsonArray("files").get(0).getAsJsonObject();

                if (firstFile.has("url")) {
                    String downloadUrl = firstFile.get("url").getAsString();
                    return downloadFileFromUrl(downloadUrl, fileName);
                } else {
                    throw new RuntimeException("No download URL found in response");
                }
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

    /**
     * Creates a new schematic using the CreateSchematic API endpoint
     * @param schematicDto The schematic data to upload
     * @param apiKey The user's API key for authentication
     * @param callback Callback for handling success/error responses
     */
    public static void createSchematic(SchematicCreateDTO schematicDto, String apiKey, CreateSchematicCallback callback) {
        new Thread(() -> {
            try {
                String boundary = "Boundary-" + System.currentTimeMillis();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                // === COMPREHENSIVE HTTP CLIENT DEBUG LOGGING ===
                System.out.println("=== HTTP CLIENT DEBUG START ===");
                System.out.println("Creating multipart request with boundary: " + boundary);
                System.out.println("DTO contents:");
                System.out.println("  - Name: " + schematicDto.getName());
                System.out.println("  - Description: " + schematicDto.getDescription());
                System.out.println("  - SchematicsPictureFiles: " + schematicDto.getSchematicsPictureFiles().size() + " files");
                for (int i = 0; i < schematicDto.getSchematicsPictureFiles().size(); i++) {
                    File file = schematicDto.getSchematicsPictureFiles().get(i);
                    System.out.println("    [" + i + "] " + file.getName() + " (" + file.length() + " bytes)");
                }
                System.out.println("  - LitematicFiles: " + schematicDto.getLitematicFiles().size() + " files");
                for (int i = 0; i < schematicDto.getLitematicFiles().size(); i++) {
                    File file = schematicDto.getLitematicFiles().get(i);
                    System.out.println("    [" + i + "] " + file.getName() + " (" + file.length() + " bytes)");
                }
                System.out.println("  - CoverImageIndex: " + schematicDto.getCoverImageIndex());
                System.out.println("  - Tags: " + schematicDto.getTags());
                System.out.println("  - DownloadLinkMediaFire: " + schematicDto.getDownloadLinkMediaFire());
                System.out.println("  - YoutubeLink: " + schematicDto.getYoutubeLink());

                // Add text fields
                System.out.println("Adding form fields to multipart request...");
                addFormField(baos, boundary, "Name", schematicDto.getName());
                System.out.println("  Added Name field: " + schematicDto.getName());

                // Only send Description field if it has content (treat like other optional fields)

                // Set description only if provided (not required by server)
                addFormField(baos, boundary, "Description",
                        schematicDto.getDescription() != null ? schematicDto.getDescription() : "");
                System.out.println("  Added Description field: " + (schematicDto.getDescription() != null ? schematicDto.getDescription() : ""));

                if (schematicDto.getDownloadLinkMediaFire() != null) {
                    addFormField(baos, boundary, "DownloadLinkMediaFire", schematicDto.getDownloadLinkMediaFire());
                    System.out.println("  Added DownloadLinkMediaFire field: " + schematicDto.getDownloadLinkMediaFire());
                }
                if (schematicDto.getYoutubeLink() != null) {
                    addFormField(baos, boundary, "YoutubeLink", schematicDto.getYoutubeLink());
                    System.out.println("  Added YoutubeLink field: " + schematicDto.getYoutubeLink());
                }
                if (schematicDto.getTags() != null) {
                    addFormField(baos, boundary, "Tags", schematicDto.getTags());
                    System.out.println("  Added Tags field: " + schematicDto.getTags());
                }
                if (schematicDto.getCoverImageIndex() != null) {
                    addFormField(baos, boundary, "CoverImageIndex", schematicDto.getCoverImageIndex().toString());
                    System.out.println("  Added CoverImageIndex field: " + schematicDto.getCoverImageIndex().toString());
                }

                // Add schematic picture files
                System.out.println("Adding picture files to multipart request...");
                for (int i = 0; i < schematicDto.getSchematicsPictureFiles().size(); i++) {
                    File pictureFile = schematicDto.getSchematicsPictureFiles().get(i);
                    System.out.println("  Adding picture file [" + i + "]: " + pictureFile.getName() + " (" + pictureFile.length() + " bytes)");
                    // Use the exact field name the server expects (not indexed)
                    addFileField(baos, boundary, "SchematicsPictureFiles", pictureFile);
                }

                // Add litematic files
                System.out.println("Adding litematic files to multipart request...");
                for (int i = 0; i < schematicDto.getLitematicFiles().size(); i++) {
                    File litematicFile = schematicDto.getLitematicFiles().get(i);
                    System.out.println("  Adding litematic file [" + i + "]: " + litematicFile.getName() + " (" + litematicFile.length() + " bytes)");
                    // Use the exact field name the server expects (not indexed)
                    addFileField(baos, boundary, "LitematicFiles", litematicFile);
                }

                // Close the boundary
                baos.write(("--" + boundary + "--\r\n").getBytes());

                byte[] requestBody = baos.toByteArray();
                System.out.println("Final multipart request size: " + requestBody.length + " bytes");
                System.out.println("=== HTTP CLIENT DEBUG END ===");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/CreateSchematic"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .header("X-API-Key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                System.out.println("Server response status: " + response.statusCode());
                System.out.println("Server response body: " + response.body());
                System.out.println("Server response headers: " + response.headers().map());

                if (response.statusCode() == 201) {
                    // Parse the response to get the created schematic details
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                    MinecraftClient.getInstance().execute(() -> {
                        callback.onSuccess(json);
                    });
                } else {
                    String errorMessage = "Creation failed: " + response.statusCode();
                    String responseBody = response.body();

                    // Enhanced error parsing for better debugging
                    try {
                        if (responseBody != null && !responseBody.trim().isEmpty()) {
                            if (responseBody.trim().startsWith("{")) {
                                // Try to parse as JSON
                                JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                                if (errorJson.has("error")) {
                                    errorMessage = errorJson.get("error").getAsString();
                                }
                                if (errorJson.has("message")) {
                                    errorMessage += " - " + errorJson.get("message").getAsString();
                                }
                                if (errorJson.has("details")) {
                                    errorMessage += " - " + errorJson.get("details").getAsString();
                                }
                                if (errorJson.has("errors")) {
                                    errorMessage += " - " + errorJson.get("errors").toString();
                                }
                            } else {
                                // Plain text response
                                errorMessage += " - " + responseBody;
                            }
                        } else {
                            errorMessage += " - No response body";
                        }

                        // Add common 400 error explanations
                        if (response.statusCode() == 400) {
                            errorMessage += " (Bad Request - check required fields, file formats, or data validation)";
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to parse error response: " + e.getMessage());
                        errorMessage += " - Raw response: " + responseBody;
                    }

                    System.err.println("Publishing failed with detailed error: " + errorMessage);

                    final String finalErrorMessage = errorMessage;
                    MinecraftClient.getInstance().execute(() ->
                            callback.onError(finalErrorMessage));
                }
            } catch (Exception e) {
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() ->
                        callback.onError("Creation failed: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Helper method to add a text form field to the multipart request
     */
    private static void addFormField(ByteArrayOutputStream baos, String boundary, String name, String value) throws Exception {
        baos.write(("--" + boundary + "\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes());
        baos.write("\r\n".getBytes());
        baos.write(value.getBytes(StandardCharsets.UTF_8));
        baos.write("\r\n".getBytes());
    }

    /**
     * Helper method to add a file field to the multipart request
     */
    private static void addFileField(ByteArrayOutputStream baos, String boundary, String fieldName, File file) throws Exception {
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        baos.write(("--" + boundary + "\r\n").getBytes());
        baos.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes());

        // Set appropriate content type based on file extension
        String contentType = "application/octet-stream";
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            contentType = "image/png";
        } else if (fileName.endsWith(".litematic")) {
            contentType = "application/octet-stream";
        }

        baos.write(("Content-Type: " + contentType + "\r\n").getBytes());
        baos.write("\r\n".getBytes());
        baos.write(fileBytes);
        baos.write("\r\n".getBytes());
    }

    public interface CreateSchematicCallback {
        void onSuccess(JsonObject responseData);
        void onError(String message);
    }

    public interface UploadCallback {
        void onSuccess(String url);
        void onError(String message);
    }

    // Static method to access the HTTP client from other classes
    public static HttpClient getClient() {
        return client;
    }
}
