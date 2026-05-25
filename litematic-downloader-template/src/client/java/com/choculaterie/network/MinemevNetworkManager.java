package com.choculaterie.network;

import com.choculaterie.models.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MinemevNetworkManager {
	//47.93.147.71:10003
	private static final String BASE_URL = "https://redenmc.com/api";
	private static final String YISIBITE_ENDPOINT = BASE_URL + "/mc-services/yisibite";
	private static final String SEARCH_ENDPOINT = BASE_URL + "/mc-services/litematica/search";
	private static final String DEVICE_AUTH_ENDPOINT = BASE_URL + "/auth/device";

	private static final String CLIENT_ID = "client_Wi8wEDtPLp2tu0fwQ_9H";
	private static final String AUTH_KEY = "EHWg37PqkBi1Zcr4dlxT1UagAI5mJ6m3tIByBWUxDp0";
	private static final String CLIENT_NAME = "choculaterie.com";

	private static final Gson GSON = new Gson();
	private static final int TIMEOUT = 10000;
	private static final int DEFAULT_PAGE = 1;
	private static final String DEFAULT_LANG = "zh_cn";

	private static String accessToken = null;
	private static long tokenExpiresAt = 0;
	private static String username = null;
	private static int userId = 0;

	static {
		trustAllCertificates();
		loadSavedAuthentication();
	}

	private static void loadSavedAuthentication() {
		try {
			com.choculaterie.config.DownloadSettings settings = com.choculaterie.config.DownloadSettings.getInstance();
			String savedToken = settings.getAccessToken();
			long savedExpiresAt = settings.getTokenExpiresAt();
			String savedUsername = settings.getUsername();
			int savedUserId = settings.getUserId();

			if (savedToken != null && !savedToken.isEmpty() && savedExpiresAt > System.currentTimeMillis()) {
				accessToken = savedToken;
				tokenExpiresAt = savedExpiresAt;
				username = savedUsername;
				userId = savedUserId;
			}
		} catch (Exception e) {
			System.err.println("[MinemevNetworkManager] Failed to load saved authentication: " + e.getMessage());
		}
	}

	private static void trustAllCertificates() {
		try {
			TrustManager[] trustAllCerts = new TrustManager[]{
					new X509TrustManager() {
						public X509Certificate[] getAcceptedIssuers() { return null; }
						public void checkClientTrusted(X509Certificate[] certs, String authType) {}
						public void checkServerTrusted(X509Certificate[] certs, String authType) {}
					}
			};
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static CompletableFuture<DeviceFlowStartResponse> startDeviceFlow() {
		return supplyAsync(() -> {
			JsonObject payload = new JsonObject();
			payload.addProperty("client_id", CLIENT_ID);
			payload.addProperty("auth_key", AUTH_KEY);
			payload.addProperty("redirect_url", "https://redenmc.com/auth/device");

			System.out.println("[MinemevNetworkManager] Request URL: " + DEVICE_AUTH_ENDPOINT + "/start");

			String response = makePostRequest(DEVICE_AUTH_ENDPOINT + "/start", payload.toString());

			JsonObject root = GSON.fromJson(response, JsonObject.class);

			DeviceFlowStartResponse result = new DeviceFlowStartResponse(
				getString(root, "redirect_url"),
				getString(root, "query_token"),
				getInt(root, "expires_in")
			);


			return result;
		});
	}

	public static CompletableFuture<DeviceFlowQueryResponse> queryDeviceAuthorization(String queryToken) {
		return supplyAsync(() -> {
			System.out.println("[MinemevNetworkManager] Request URL: " + DEVICE_AUTH_ENDPOINT + "/query");

			JsonObject payload = new JsonObject();
			payload.addProperty("query_token", queryToken);

			String response = makePostRequest(DEVICE_AUTH_ENDPOINT + "/query", payload.toString());

			JsonObject root = GSON.fromJson(response, JsonObject.class);

			String status = getString(root, "status");

			if ("approved".equals(status)) {
				Integer userId = getInt(root, "user_id");
				String username = getString(root, "username");
				String accessToken = getString(root, "access_token");
				String tokenType = getString(root, "token_type");
				Long expiresIn = getLong(root, "expires_in");

				MinemevNetworkManager.accessToken = accessToken;
				MinemevNetworkManager.tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000);
				MinemevNetworkManager.username = username;
				MinemevNetworkManager.userId = userId;

				saveAuthentication();

				return new DeviceFlowQueryResponse(status, userId, username, accessToken, tokenType, expiresIn);
			}

			return new DeviceFlowQueryResponse(status, null, null, null, null, null);
		});
	}

	public static boolean isAuthenticated() {
		return accessToken != null && System.currentTimeMillis() < tokenExpiresAt;
	}

	public static String getAccessToken() {
		return accessToken;
	}

	public static String getUsername() {
		return username;
	}

	public static int getUserId() {
		return userId;
	}

	public static void clearAuthentication() {
		accessToken = null;
		tokenExpiresAt = 0;
		username = null;
		userId = 0;

		try {
			com.choculaterie.config.DownloadSettings settings = com.choculaterie.config.DownloadSettings.getInstance();
			settings.setAccessToken("");
			settings.setTokenExpiresAt(0);
			settings.setUsername("");
			settings.setUserId(0);
		} catch (Exception e) {
			System.err.println("[MinemevNetworkManager] Failed to clear authentication: " + e.getMessage());
		}
	}

	private static void saveAuthentication() {
		try {
			com.choculaterie.config.DownloadSettings settings = com.choculaterie.config.DownloadSettings.getInstance();
			settings.setAccessToken(accessToken);
			settings.setTokenExpiresAt(tokenExpiresAt);
			settings.setUsername(username);
			settings.setUserId(userId);
		} catch (Exception e) {
			System.err.println("[MinemevNetworkManager] Failed to save authentication: " + e.getMessage());
		}
	}

	public static CompletableFuture<DeviceFlowQueryResponse> pollForAuthorization(
			String queryToken, int maxAttempts, int intervalSeconds) {
		return supplyAsync(() -> {
			for (int attempt = 0; attempt < maxAttempts; attempt++) {
				try {
					DeviceFlowQueryResponse response = queryDeviceAuthorization(queryToken).get();

					if ("approved".equals(response.status) || "denied".equals(response.status)) {
						return response;
					}

					if (attempt < maxAttempts - 1) {
						Thread.sleep(intervalSeconds * 1000L);
					}
				} catch (Exception e) {
					if (e.getMessage() != null && e.getMessage().contains("410")) {
						return new DeviceFlowQueryResponse("expired", null, null, null, null, null);
					}
					throw new RuntimeException(e);
				}
			}

			return new DeviceFlowQueryResponse("timeout", null, null, null, null, null);
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
			String sortKey = "downloads";
			if ("newest".equals(sort)) {
				sortKey = "createdAt";
			} else if ("downloads".equals(sort)) {
				sortKey = "downloads";
			}

			String url;
			if (query == null || query.trim().isEmpty()) {
				url = String.format("%s/?lang=%s&page=%d&order=%s", YISIBITE_ENDPOINT, DEFAULT_LANG, page, sortKey);
			} else {
				url = String.format("%s?lang=%s&q=%s&page=%d&order=%s", SEARCH_ENDPOINT, DEFAULT_LANG, encode(query), page, sortKey);
			}
			System.out.println("[MinemevNetworkManager] Request URL: " + url);

			String response = makeGetRequest(url);
			return parseRedenMCSearchResponse(response, 20);
		});
	}

	public static CompletableFuture<MinemevPostDetailInfo> getPostDetails(String key) {
		return supplyAsync(() -> getPostDetailsInternal(key));
	}

	public static CompletableFuture<MinemevFileInfo[]> getPostFiles(String key) {
		return supplyAsync(() -> getPostFilesInternal(key));
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

	private static MinemevPostDetailInfo getPostDetailsInternal(String key) throws IOException {
		String url = String.format("%s/%s/info/%s", YISIBITE_ENDPOINT, key, DEFAULT_LANG);
		System.out.println("[MinemevNetworkManager] Request URL: " + url);
		return parseRedenMCPostDetail(makeGetRequest(url));
	}

	private static MinemevFileInfo[] getPostFilesInternal(String key) throws IOException {
		String url = String.format("https://redenmc.com/api/mc-services/yisibite/%s/files", key);
		System.out.println("[MinemevNetworkManager] Request URL: " + url);
		return parseRedenMCFileList(makeGetRequest(url));
	}

	public static CompletableFuture<String> downloadGeneratedLitematica(String key, Map<String, Integer> sizes, String filename) {
		return supplyAsync(() -> {
			StringBuilder urlBuilder = new StringBuilder();
			urlBuilder.append("https://redenmc.com/api/mc-services/yisibite/").append(key);

			boolean first = true;
			for (Map.Entry<String, Integer> entry : sizes.entrySet()) {
				if (first) {
					urlBuilder.append("?");
					first = false;
				} else {
					urlBuilder.append("&");
				}
				urlBuilder.append(entry.getKey()).append("Size=").append(entry.getValue());
			}

			String generationUrl = urlBuilder.toString();
			System.out.println("[MinemevNetworkManager] Request URL: " + generationUrl);

			return downloadLitematicaFile(generationUrl, filename);
		});
	}

	private static String downloadLitematicaFile(String downloadUrl, String filename) throws IOException, InterruptedException {
		java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(30))
			.followRedirects(java.net.http.HttpClient.Redirect.ALWAYS)
			.build();

		java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
			.uri(java.net.URI.create(downloadUrl))
			.GET()
			.header("User-Agent", "LitematicDownloader/1.0")
			.header("Referer", "https://www.minemev.com")
			.build();

		java.net.http.HttpResponse<byte[]> response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + ": " + new String(response.body()));
		}

		java.nio.file.Path schematicsPath = java.nio.file.Paths.get(com.choculaterie.config.DownloadSettings.getInstance().getAbsoluteDownloadPath());
		java.io.File schematicsDir = schematicsPath.toFile();
		if (!schematicsDir.exists()) {
			schematicsDir.mkdirs();
		}

		String fileName = filename;
		if (!fileName.endsWith(".litematic")) {
			fileName += ".litematic";
		}

		java.io.File outputFile = new java.io.File(schematicsDir, fileName);

		int counter = 1;
		while (outputFile.exists()) {
			String baseName = filename;
			outputFile = new java.io.File(schematicsDir, baseName + "_" + counter + ".litematic");
			counter++;
		}

		try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
			fos.write(response.body());
		}

		return outputFile.getAbsolutePath();
	}

	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		} catch (Exception e) {
			return value;
		}
	}

	private static String makePostRequest(String urlString, String jsonBody) throws IOException {
		System.out.println("[MinemevNetworkManager] Request URL: " + urlString);

		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
			conn.setRequestProperty("Referer", "https://www.minemev.com");


			try (java.io.OutputStream os = conn.getOutputStream()) {
				byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
				os.write(input, 0, input.length);
			}

			int responseCode = conn.getResponseCode();

			if (responseCode != HttpURLConnection.HTTP_OK) {
				String errorBody = null;
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
					StringBuilder error = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						error.append(line);
					}
					errorBody = error.toString();
				} catch (Exception e) {
				}

				if (errorBody != null && !errorBody.isEmpty()) {
					throw new IOException("HTTP " + responseCode + ": " + errorBody);
				} else {
					throw new IOException("HTTP error: " + responseCode);
				}
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

	private static String makeGetRequest(String urlString) throws IOException {
		URL url = new URL(urlString);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);
			conn.setRequestProperty("User-Agent", "LitematicDownloader/1.0");
			conn.setRequestProperty("Referer", "https://www.minemev.com");


			int responseCode = conn.getResponseCode();

			if (responseCode != HttpURLConnection.HTTP_OK) {
				String errorBody = null;
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
					StringBuilder error = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) {
						error.append(line);
					}
					errorBody = error.toString();
				} catch (Exception e) {
				}

				if (errorBody != null && !errorBody.isEmpty()) {
					throw new IOException("HTTP " + responseCode + ": " + errorBody);
				} else {
					throw new IOException("HTTP error: " + responseCode);
				}
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

	private static MinemevSearchResponse parseRedenMCSearchResponse(String json, int itemsPerPage) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);
		JsonArray postsArray = root.getAsJsonArray("d");
		int totalItems = root.get("count").getAsInt();
		int totalPages = (totalItems + itemsPerPage - 1) / itemsPerPage;

		List<MinemevPostInfo> posts = new ArrayList<>();
		for (int i = 0; i < postsArray.size(); i++) {
			posts.add(parseRedenMCPostInfo(postsArray.get(i).getAsJsonObject()));
		}

		return new MinemevSearchResponse(posts.toArray(new MinemevPostInfo[0]), totalPages, totalItems);
	}

	private static MinemevPostInfo parseRedenMCPostInfo(JsonObject obj) {
		JsonObject authorObj = obj.has("author") ? obj.getAsJsonObject("author") : null;
		String author = authorObj != null ? getString(authorObj, "username") : "Unknown";

		JsonArray featureTagsArray = obj.has("featureTags") ? obj.getAsJsonArray("featureTags") : null;
		String[] tags = new String[0];
		if (featureTagsArray != null && featureTagsArray.size() > 0) {
			tags = new String[featureTagsArray.size()];
			for (int i = 0; i < featureTagsArray.size(); i++) {
				JsonObject tagObj = featureTagsArray.get(i).getAsJsonObject();
				tags[i] = getString(tagObj, "name");
			}
		}

		String[] images = getStringArray(obj, "images");
		if (images.length == 0 && obj.has("imageUrl") && !obj.get("imageUrl").isJsonNull()) {
			images = new String[]{getString(obj, "imageUrl")};
		}

		String type = getString(obj, "type");

		return new MinemevPostInfo(
				getString(obj, "key"),
				getString(obj, "name"),
				getString(obj, "description"),
				author,
				getInt(obj, "downloads"),
				null,
				tags,
				getStringArray(obj, "versions"),
				"redenmc",
				images,
				getString(obj, "thumbnailUrl"),
				authorObj != null ? getString(authorObj, "avatarUrl") : null,
				getString(obj, "link"),
				type
		);
	}

	private static MinemevPostDetailInfo parseRedenMCPostDetail(String json) {
		JsonObject root = GSON.fromJson(json, JsonObject.class);

		JsonObject obj;
		if (root.has("d") && root.get("d").isJsonArray()) {
			JsonArray dataArray = root.getAsJsonArray("d");
			if (dataArray.size() == 0) {
				throw new RuntimeException("Post not found - empty data array");
			}
			obj = dataArray.get(0).getAsJsonObject();
		} else {
			obj = root;
		}

		JsonObject authorObj = obj.has("author") ? obj.getAsJsonObject("author") : null;
		String author = authorObj != null ? getString(authorObj, "username") : "Unknown";

		JsonArray featureTagsArray = obj.has("featureTags") ? obj.getAsJsonArray("featureTags") : null;
		String[] tags = new String[0];
		if (featureTagsArray != null && featureTagsArray.size() > 0) {
			tags = new String[featureTagsArray.size()];
			for (int i = 0; i < featureTagsArray.size(); i++) {
				JsonObject tagObj = featureTagsArray.get(i).getAsJsonObject();
				tags[i] = getString(tagObj, "name");
			}
		}

		String[] images = getStringArray(obj, "images");
		if (images.length == 0) {
			List<String> imageList = new ArrayList<>();
			if (obj.has("imageUrl") && !obj.get("imageUrl").isJsonNull()) {
				String imageUrl = getString(obj, "imageUrl");
				if (imageUrl != null && !imageUrl.isEmpty()) {
					imageList.add(imageUrl);
				}
			}
			if (obj.has("thumbnailUrl") && !obj.get("thumbnailUrl").isJsonNull()) {
				String thumbnailUrl = getString(obj, "thumbnailUrl");
				if (thumbnailUrl != null && !thumbnailUrl.isEmpty() && !imageList.contains(thumbnailUrl)) {
					imageList.add(thumbnailUrl);
				}
			}
			images = imageList.toArray(new String[0]);
		}


		String type = getString(obj, "type");

		Map<String, List<String>> conditions = new HashMap<>();
		boolean hasX = false;
		boolean hasY = false;
		boolean hasZ = false;

		if ("LitematicaGen".equals(type) && obj.has("conditions")) {
			JsonObject conditionsObj = obj.getAsJsonObject("conditions");

			if (conditionsObj.has("x")) {
				JsonArray xArray = conditionsObj.getAsJsonArray("x");
				List<String> xConditions = new ArrayList<>();
				for (int i = 0; i < xArray.size(); i++) {
					xConditions.add(xArray.get(i).getAsString());
				}
				conditions.put("x", xConditions);
			}

			if (conditionsObj.has("y")) {
				JsonArray yArray = conditionsObj.getAsJsonArray("y");
				List<String> yConditions = new ArrayList<>();
				for (int i = 0; i < yArray.size(); i++) {
					yConditions.add(yArray.get(i).getAsString());
				}
				conditions.put("y", yConditions);
			}

			if (conditionsObj.has("z")) {
				JsonArray zArray = conditionsObj.getAsJsonArray("z");
				List<String> zConditions = new ArrayList<>();
				for (int i = 0; i < zArray.size(); i++) {
					zConditions.add(zArray.get(i).getAsString());
				}
				conditions.put("z", zConditions);
			}

			hasX = obj.has("hasX") && obj.get("hasX").getAsBoolean();
			hasY = obj.has("hasY") && obj.get("hasY").getAsBoolean();
			hasZ = obj.has("hasZ") && obj.get("hasZ").getAsBoolean();
		}

		return new MinemevPostDetailInfo(
				getString(obj, "key"),
				getString(obj, "name"),
				getString(obj, "description"),
				getString(obj, "description"),
				author,
				getInt(obj, "downloads"),
				null,
				tags,
				getStringArray(obj, "versions"),
				images,
				getString(obj, "link"),
				false,
				author,
				type,
				conditions,
				hasX,
				hasY,
				hasZ
		);
	}

	private static MinemevFileInfo[] parseRedenMCFileList(String json) {
		JsonArray filesArray = GSON.fromJson(json, JsonArray.class);
		List<MinemevFileInfo> files = new ArrayList<>();

		for (int i = 0; i < filesArray.size(); i++) {
			JsonObject obj = filesArray.get(i).getAsJsonObject();
			files.add(new MinemevFileInfo(
					i,
					getString(obj, "name"),
					getString(obj, "url"),
					getLong(obj, "size"),
					new String[0],
					0,
					"litematic",
					true
			));
		}

		return files.toArray(new MinemevFileInfo[0]);
	}

	private static String getString(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
	}

	private static int getInt(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : 0;
	}

	private static long getLong(JsonObject obj, String key) {
		return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : 0L;
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

	public static class DeviceFlowStartResponse {
		public final String redirectUrl;
		public final String queryToken;
		public final int expiresIn;

		public DeviceFlowStartResponse(String redirectUrl, String queryToken, int expiresIn) {
			this.redirectUrl = redirectUrl;
			this.queryToken = queryToken;
			this.expiresIn = expiresIn;
		}
	}

	public static class DeviceFlowQueryResponse {
		public final String status;
		public final Integer userId;
		public final String username;
		public final String accessToken;
		public final String tokenType;
		public final Long expiresIn;

		public DeviceFlowQueryResponse(String status, Integer userId, String username,
									   String accessToken, String tokenType, Long expiresIn) {
			this.status = status;
			this.userId = userId;
			this.username = username;
			this.accessToken = accessToken;
			this.tokenType = tokenType;
			this.expiresIn = expiresIn;
		}
	}

	@FunctionalInterface
	private interface SupplierWithException<T> {
		T get() throws Exception;
	}
}
