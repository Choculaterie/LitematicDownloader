package com.choculaterie.network;

import com.choculaterie.models.QuickShareResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Network manager for Choculaterie API operations
 * Handles quick share uploads and other Choculaterie-specific API calls
 *
 * <p>Usage example for quick share:</p>
 * <pre>
 * File litematicFile = new File("path/to/schematic.litematic");
 * ChoculaterieNetworkManager.uploadLitematic(litematicFile)
 *     .thenAccept(response -> {
 *         String shortUrl = response.getShortUrl();
 *         System.out.println("Share URL: " + shortUrl);
 *         // Copy to clipboard, display to user, etc.
 *     })
 *     .exceptionally(error -> {
 *         System.err.println("Upload failed: " + error.getMessage());
 *         return null;
 *     });
 * </pre>
 *
 * <p>The short URL can be shared with other players and will open the litematic
 * in the Choculaterie viewer. The file is stored temporarily on the server.</p>
 */
public class ChoculaterieNetworkManager {
    private static final String BASE_URL = "https://choculaterie.com/api/LitematicDownloaderModAPI";
    private static final Gson GSON = new Gson();
    private static final int TIMEOUT = 30000; // 30 seconds for file uploads
    private static final String BOUNDARY = "----WebKitFormBoundary" + System.currentTimeMillis();

    /**
     * Upload a litematic file to Choculaterie for quick sharing
     * @param file The litematic file to upload
     * @return CompletableFuture with the quick share response containing the short URL
     */
    public static CompletableFuture<QuickShareResponse> uploadLitematic(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate file
                if (file == null || !file.exists()) {
                    throw new IllegalArgumentException("File does not exist");
                }

                if (!file.getName().toLowerCase().endsWith(".litematic")) {
                    throw new IllegalArgumentException("Only .litematic files are allowed");
                }

                // Read file bytes
                byte[] fileBytes = Files.readAllBytes(file.toPath());

                // Upload the file
                String jsonResponse = uploadMultipartFile(file.getName(), fileBytes);

                // Parse response
                return parseQuickShareResponse(jsonResponse);

            } catch (Exception e) {
                throw new RuntimeException("Failed to upload litematic file", e);
            }
        });
    }

    /**
     * Upload a file using multipart/form-data
     * @param fileName The name of the file
     * @param fileBytes The file content as bytes
     * @return The JSON response as a string
     * @throws IOException If the upload fails
     */
    private static String uploadMultipartFile(String fileName, byte[] fileBytes) throws IOException {
        String urlString = BASE_URL + "/upload";
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            // Build multipart form data
            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                // Write file part
                outputStream.writeBytes("--" + BOUNDARY + "\r\n");
                outputStream.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
                outputStream.writeBytes("Content-Type: application/octet-stream\r\n");
                outputStream.writeBytes("\r\n");
                outputStream.write(fileBytes);
                outputStream.writeBytes("\r\n");

                // End boundary
                outputStream.writeBytes("--" + BOUNDARY + "--\r\n");
                outputStream.flush();
            }

            // Read response
            int responseCode = connection.getResponseCode();

            // Read the response body (either from input or error stream)
            InputStream inputStream;
            if (responseCode >= 200 && responseCode < 300) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }

            StringBuilder response = new StringBuilder();
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
            }

            // Check for HTTP errors
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode + ", response: " + response);
            }

            return response.toString();

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Parse the quick share response JSON
     * @param json The JSON response string
     * @return QuickShareResponse object
     */
    private static QuickShareResponse parseQuickShareResponse(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);

        if (!root.has("shortUrl")) {
            throw new RuntimeException("Response does not contain shortUrl");
        }

        String shortUrl = root.get("shortUrl").getAsString();
        return new QuickShareResponse(shortUrl);
    }

    /**
     * Upload a litematic file by path
     * @param filePath The path to the litematic file
     * @return CompletableFuture with the quick share response
     */
    public static CompletableFuture<QuickShareResponse> uploadLitematic(String filePath) {
        return uploadLitematic(new File(filePath));
    }
}

