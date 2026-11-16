package com.choculaterie.network;

import com.choculaterie.vanilib.models.ModMessage;
import com.choculaterie.models.PromotionInfo;
import com.choculaterie.models.QuickShareDownloadResult;
import com.choculaterie.models.QuickShareLinkResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChoculaterieNetworkManager {
	private static final String BASE_URL = "https://api.choculaterie.com/api/LitematicDownloaderModAPI";
	private static final String UPLOAD_ENDPOINT = BASE_URL + "/upload";
	private static final String MESSAGE_ENDPOINT = BASE_URL + "/GetModMessage";
	private static final String PROMOTIONS_ENDPOINT = BASE_URL + "/promotions";
	private static final String PROMO_CLICK_BASE = "https://backend.choculaterie.com/api/Promo/";
	private static final String QS_BACKEND_BASE = "https://backend.choculaterie.com/qs/";
	private static final long PROMOTIONS_TTL_MS = 10 * 60 * 1000;

	private static volatile List<PromotionInfo> cachedPromotions;
	private static volatile long promotionsFetchedAt;

	private static final Gson GSON = new Gson();
	private static final int TIMEOUT_STANDARD = 10000;
	private static final int TIMEOUT_UPLOAD = 30000;
	private static final String BOUNDARY = "----WebKitFormBoundary" + System.currentTimeMillis();
	private static final String LITEMATIC_EXTENSION = ".litematic";

	public static CompletableFuture<QuickShareDownloadResult> downloadQuickShare(String code) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String downloadUrl = QS_BACKEND_BASE + code + "/litematic";
				System.out.println("[QuickShare] Downloading from: " + downloadUrl);

				URL url = new URL(downloadUrl);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();

				try {
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(TIMEOUT_STANDARD);
					conn.setReadTimeout(TIMEOUT_UPLOAD);
					conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");

					int responseCode = conn.getResponseCode();
					if (responseCode != HttpURLConnection.HTTP_OK) {
						if (responseCode == 404) {
							throw new IOException("Quick-share link not found or has no file attached");
						}
						throw new IOException("HTTP error: " + responseCode);
					}

					String filename = code + ".litematic";

					byte[] data;
					try (InputStream is = conn.getInputStream();
						 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
						byte[] buffer = new byte[8192];
						int bytesRead;
						while ((bytesRead = is.read(buffer)) != -1) {
							baos.write(buffer, 0, bytesRead);
						}
						data = baos.toByteArray();
					}

					System.out.println("[QuickShare] Downloaded " + data.length + " bytes, filename: " + filename);
					return new QuickShareDownloadResult(data, filename);
				} finally {
					conn.disconnect();
				}
			} catch (IOException e) {
				throw new RuntimeException("Failed to download quick-share file", e);
			}
		});
	}

	public static CompletableFuture<QuickShareLinkResponse> uploadLitematic(File file) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				validateFile(file);
				byte[] fileBytes = Files.readAllBytes(file.toPath());
				String jsonResponse = uploadMultipartFile(file.getName(), fileBytes);
				return parseQuickShareResponse(jsonResponse);
			} catch (IOException e) {
				throw new RuntimeException("Failed to upload litematic file", e);
			}
		});
	}

	public static CompletableFuture<ModMessage> getModMessage() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				String jsonResponse = makeGetRequest(MESSAGE_ENDPOINT);
				return parseModMessage(jsonResponse);
			} catch (Exception e) {
				return new ModMessage(false, null, null, null);
			}
		});
	}

	public static CompletableFuture<List<PromotionInfo>> getPromotions() {
		List<PromotionInfo> cached = cachedPromotions;
		if (cached != null && System.currentTimeMillis() - promotionsFetchedAt < PROMOTIONS_TTL_MS) {
			return CompletableFuture.completedFuture(cached);
		}
		return CompletableFuture.supplyAsync(() -> {
			try {
				List<PromotionInfo> promotions = parsePromotions(makeGetRequest(PROMOTIONS_ENDPOINT));
				cachedPromotions = promotions;
				promotionsFetchedAt = System.currentTimeMillis();
				return promotions;
			} catch (Exception e) {
				return List.of();
			}
		});
	}

	public static void recordPromotionClick(String key) {
		if (key == null || key.isEmpty()) return;
		CompletableFuture.runAsync(() -> {
			try {
				URL url = new URL(PROMO_CLICK_BASE + URLEncoder.encode(key, StandardCharsets.UTF_8) + "/click");
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				try {
					conn.setRequestMethod("POST");
					conn.setDoOutput(true);
					conn.setConnectTimeout(TIMEOUT_STANDARD);
					conn.setReadTimeout(TIMEOUT_STANDARD);
					conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
					conn.setFixedLengthStreamingMode(0);
					conn.getOutputStream().close();
					conn.getResponseCode();
				} finally {
					conn.disconnect();
				}
			} catch (IOException ignored) {
			}
		});
	}

	private static void validateFile(File file) throws IOException {
		if (file == null || !file.exists()) {
			throw new IOException("File does not exist");
		}
		if (!file.getName().toLowerCase().endsWith(LITEMATIC_EXTENSION)) {
			throw new IOException("Only .litematic files are allowed");
		}
	}

	private static String uploadMultipartFile(String fileName, byte[] fileBytes) throws IOException {
		URL url = new URL(UPLOAD_ENDPOINT);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setConnectTimeout(TIMEOUT_UPLOAD);
			conn.setReadTimeout(TIMEOUT_UPLOAD);
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
				out.writeBytes("--" + BOUNDARY + "\r\n");
				out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
				out.writeBytes("Content-Type: application/octet-stream\r\n");
				out.writeBytes("\r\n");
				out.write(fileBytes);
				out.writeBytes("\r\n");
				out.writeBytes("--" + BOUNDARY + "--\r\n");
				out.flush();
			}

			int responseCode = conn.getResponseCode();
			InputStream stream = responseCode >= 200 && responseCode < 300
				? conn.getInputStream()
				: conn.getErrorStream();

			String response = readStream(stream);

			if (responseCode != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP error: " + responseCode + ", response: " + response);
			}

			return response;
		} finally {
			conn.disconnect();
		}
	}

	private static String makeGetRequest(String endpoint) throws IOException {
		URL url = new URL(endpoint);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT_STANDARD);
			conn.setReadTimeout(TIMEOUT_STANDARD);
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");

			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				throw new IOException("HTTP error: " + conn.getResponseCode());
			}

			return readStream(conn.getInputStream());
		} finally {
			conn.disconnect();
		}
	}

	private static String readStream(InputStream stream) throws IOException {
		if (stream == null) {
			return "";
		}
		StringBuilder response = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
		}
		return response.toString();
	}

	private static QuickShareLinkResponse parseQuickShareResponse(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if (!root.has("shortUrl")) {
			throw new RuntimeException("Response does not contain shortUrl");
		}
		return new QuickShareLinkResponse(root.get("shortUrl").getAsString());
	}

	private static ModMessage parseModMessage(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		boolean hasMessage = getBoolean(root);

		if (!hasMessage) {
			return new ModMessage(false, null, null, null);
		}

		return new ModMessage(
			true,
			getInt(root),
			getString(root, "message"),
			getString(root, "type")
		);
	}

	private static List<PromotionInfo> parsePromotions(String json) {
		List<PromotionInfo> promotions = new ArrayList<>();
		JsonArray array = GSON.fromJson(json, JsonArray.class);
		if (array == null) return promotions;
		for (JsonElement element : array) {
			if (!element.isJsonObject()) continue;
			JsonObject obj = element.getAsJsonObject();
			String key = getString(obj, "key");
			String title = getString(obj, "title");
			String linkUrl = getString(obj, "linkUrl");
			if (key == null || title == null || linkUrl == null) continue;
			promotions.add(new PromotionInfo(key, getString(obj, "kicker"), title, getString(obj, "description"),
				linkUrl, getString(obj, "ctaText"), getString(obj, "imageUrl")));
		}
		return List.copyOf(promotions);
	}

	private static String getString(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
	}

	private static Integer getInt(JsonObject obj) {
		return (obj.has("id") && !obj.get("id").isJsonNull()) ? obj.get("id").getAsInt() : null;
	}

	private static boolean getBoolean(JsonObject obj) {
		return (obj.has("hasMessage") && !obj.get("hasMessage").isJsonNull()) && obj.get("hasMessage").getAsBoolean();
	}
}

