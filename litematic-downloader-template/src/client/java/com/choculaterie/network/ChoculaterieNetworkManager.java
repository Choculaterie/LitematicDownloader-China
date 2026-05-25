package com.choculaterie.network;

import com.choculaterie.models.ModMessage;
import com.choculaterie.models.QuickShareResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class ChoculaterieNetworkManager {
	private static final String BASE_URL = "https://choculaterie.com/api/LitematicDownloaderModAPI";
	private static final String UPLOAD_ENDPOINT = BASE_URL + "/upload";
	private static final String MESSAGE_ENDPOINT = BASE_URL + "/GetModMessage";

	private static final Gson GSON = new Gson();
	private static final int TIMEOUT_STANDARD = 10000;
	private static final int TIMEOUT_UPLOAD = 30000;
	private static final String BOUNDARY = "----WebKitFormBoundary" + System.currentTimeMillis();
	private static final String LITEMATIC_EXTENSION = ".litematic";

	public static CompletableFuture<QuickShareResponse> uploadLitematic(File file) {
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
				String jsonResponse = makeGetRequest();
				return parseModMessage(jsonResponse);
			} catch (Exception e) {
				return new ModMessage(false, null, null, null);
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

	private static String makeGetRequest() throws IOException {
		URL url = new URL(ChoculaterieNetworkManager.MESSAGE_ENDPOINT);
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

	private static QuickShareResponse parseQuickShareResponse(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		if (!root.has("shortUrl")) {
			throw new RuntimeException("Response does not contain shortUrl");
		}
		return new QuickShareResponse(root.get("shortUrl").getAsString());
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

