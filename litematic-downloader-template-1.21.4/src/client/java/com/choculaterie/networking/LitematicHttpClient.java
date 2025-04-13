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

public class LitematicHttpClient {
    private static final String API_URL = "https://choculaterie.com/api/LitematicDownloaderModAPI/GetAll";
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

    public static List<SchematicInfo> fetchSchematicList() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LitematicHttpClient.API_URL))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematics: " + response.statusCode());
            }

            // Parse JSON response
            JsonArray jsonArray = gson.fromJson(response.body(), JsonArray.class);
            List<SchematicInfo> schematics = new ArrayList<>();

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject json = jsonArray.get(i).getAsJsonObject();
                SchematicInfo schematic = new SchematicInfo(
                        json.get("id").getAsString(),
                        json.get("name").getAsString(),
                        json.get("description").isJsonNull() ? "" : json.get("description").getAsString(),
                        json.get("viewCount").getAsInt(),
                        json.get("downloadCount").getAsInt(),
                        json.get("username").getAsString()
                );
                schematics.add(schematic);
            }

            return schematics;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>(); // Return empty list on error
        }
    }

    public static SchematicDetailInfo fetchSchematicDetail(String id) {
        try {
            String url = "https://choculaterie.com/api/LitematicDownloaderModAPI/Getbyid/" + id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematic details: " + response.statusCode());
            }

            // Parse JSON response
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
            // Try to get Minecraft's game directory
            File gameDir = MinecraftClient.getInstance().runDirectory;
            String savePath;

            // Set the appropriate path based on whether we're in development or production
            if (gameDir != null) {
                // Use the actual game directory if available
                savePath = new File(gameDir, "schematics").getAbsolutePath() + File.separator;
            } else {
                // Fall back to hardcoded paths if game directory isn't available
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

            // Create directory if it doesn't exist
            File directory = new File(savePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Save the file
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
            String url = "https://choculaterie.com/api/LitematicDownloaderModAPI/GetLitematicFiles/" + id;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to fetch schematic file: " + response.statusCode());
            }

            // Parse JSON response
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
}