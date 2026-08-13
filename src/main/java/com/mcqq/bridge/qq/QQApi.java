package com.mcqq.bridge.qq;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mcqq.bridge.MCQQBridge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * QQ 官方机器人 REST API 客户端。
 *
 * <p>负责：获取/刷新 Access Token（OAuth），发送群聊/单聊消息，查询成员昵称。</p>
 *
 * <p>官方文档：https://bot.q.qq.com/wiki/develop/api-v2/</p>
 */
public class QQApi {
	public static final String API_BASE = "https://api.bot.qq.com";
	private static final String TOKEN_URL = API_BASE + "/app/getAppAccessToken";

	private final HttpClient httpClient;
	private final Gson gson = new Gson();

	private final String appId;
	private final String clientSecret;

	/** 当前有效的 access_token 及其过期时间（epoch 毫秒，0 表示无效）。 */
	private volatile String accessToken;
	private volatile long tokenExpireAtMillis;

	public QQApi(String appId, String clientSecret) {
		this.appId = appId;
		this.clientSecret = clientSecret;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	/** 获取有效的 access_token；临近过期（提前 60 秒）时自动刷新。 */
	public synchronized String getAccessToken() {
		if (accessToken != null && tokenExpireAtMillis - 60_000 > System.currentTimeMillis()) {
			return accessToken;
		}
		refreshToken();
		return accessToken;
	}

	/** 调用 OAuth 接口获取 access_token（默认有效期 7200 秒）。 */
	private void refreshToken() {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("appId", appId);
			body.addProperty("clientSecret", clientSecret);

			String resp = postRaw(TOKEN_URL, body, null);
			JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
			if (!json.has("access_token")) {
				MCQQBridge.LOGGER.error("[MCQQBridge] 获取 access_token 失败，响应：{}", resp);
				accessToken = null;
				tokenExpireAtMillis = 0;
				return;
			}
			accessToken = json.get("access_token").getAsString();
			int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 7200;
			tokenExpireAtMillis = System.currentTimeMillis() + expiresIn * 1000L;
			MCQQBridge.LOGGER.info("[MCQQBridge] 已获取 QQ access_token（有效期 {} 秒）。", expiresIn);
		} catch (Exception e) {
			MCQQBridge.LOGGER.error("[MCQQBridge] 获取 QQ access_token 失败：{}", e.getMessage());
			accessToken = null;
			tokenExpireAtMillis = 0;
		}
	}

	/**
	 * 发送群聊文本消息。
	 *
	 * @param groupOpenId 群 OpenID
	 * @param content     文本内容
	 * @param msgId       被动回复的消息 ID（可为 null，表示主动消息）
	 * @param msgSeq      回复序号（与 msgId 联合去重）
	 */
	public boolean sendGroupMessage(String groupOpenId, String content, String msgId, int msgSeq) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("msg_type", 0);
			body.addProperty("content", content);
			if (msgId != null && !msgId.isBlank()) {
				body.addProperty("msg_id", msgId);
				body.addProperty("msg_seq", msgSeq);
			}
			String resp = postRaw(API_BASE + "/v2/groups/" + groupOpenId + "/messages", body, getAccessToken());
			if (resp == null) {
				return false;
			}
			MCQQBridge.LOGGER.debug("[MCQQBridge] 群消息发送成功：{}", resp);
			return true;
		} catch (Exception e) {
			MCQQBridge.LOGGER.error("[MCQQBridge] 发送群消息失败（{}）：{}", groupOpenId, e.getMessage());
			return false;
		}
	}

	/**
	 * 发送单聊文本消息。
	 *
	 * @param userOpenId 用户 OpenID
	 * @param content    文本内容
	 * @param msgId      被动回复的消息 ID（可为 null）
	 * @param msgSeq     回复序号
	 */
	public boolean sendPrivateMessage(String userOpenId, String content, String msgId, int msgSeq) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("msg_type", 0);
			body.addProperty("content", content);
			if (msgId != null && !msgId.isBlank()) {
				body.addProperty("msg_id", msgId);
				body.addProperty("msg_seq", msgSeq);
			}
			String resp = postRaw(API_BASE + "/v2/users/" + userOpenId + "/messages", body, getAccessToken());
			if (resp == null) {
				return false;
			}
			MCQQBridge.LOGGER.debug("[MCQQBridge] 单聊消息发送成功：{}", resp);
			return true;
		} catch (Exception e) {
			MCQQBridge.LOGGER.error("[MCQQBridge] 发送单聊消息失败（{}）：{}", userOpenId, e.getMessage());
			return false;
		}
	}

	/** 获取群成员昵称；失败返回 null。 */
	public String getGroupMemberNickname(String groupOpenId, String memberOpenId) {
		try {
			String resp = getRawSilent(API_BASE + "/v2/groups/" + groupOpenId + "/members/" + memberOpenId,
					getAccessToken());
			if (resp == null) {
				return null;
			}
			JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
			JsonObject member = json.has("member") ? json.getAsJsonObject("member") : null;
			if (member != null && member.has("nickname")) {
				return member.get("nickname").getAsString();
			}
		} catch (Exception e) {
			MCQQBridge.LOGGER.debug("[MCQQBridge] 获取群成员昵称失败：{}", e.getMessage());
		}
		return null;
	}

	/** 获取单聊用户昵称；失败返回 null。 */
	public String getPrivateUserNickname(String userOpenId) {
		try {
			String resp = getRawSilent(API_BASE + "/v2/users/" + userOpenId, getAccessToken());
			if (resp == null) {
				return null;
			}
			JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
			JsonObject user = json.has("user") ? json.getAsJsonObject("user") : null;
			if (user != null && user.has("username")) {
				return user.get("username").getAsString();
			}
		} catch (Exception e) {
			MCQQBridge.LOGGER.debug("[MCQQBridge] 获取单聊用户昵称失败：{}", e.getMessage());
		}
		return null;
	}

	/**
	 * 发起带鉴权的 GET 请求并返回响应文本（供网关发现等场景使用）。
	 * 与内部 getRaw 等价，但对网关发现接口开放。
	 */
	public String getRawPublic(String url) throws Exception {
		return getRaw(url, getAccessToken());
	}

	// ------------------------------------------------------------------
	// 底层 HTTP 封装
	// ------------------------------------------------------------------

	private String getRaw(String url, String token) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(10));
		if (token != null) {
			builder.header("Authorization", "QQBot " + token);
		}
		HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
			return resp.body();
		}
		MCQQBridge.LOGGER.error("[MCQQBridge] GET {} 失败：HTTP {} - {}", url, resp.statusCode(), resp.body());
		return null;
	}

	/**
	 * 静默 GET：非 2xx 时返回 null 但不打印 ERROR 日志。
	 * 用于昵称查询等非关键请求（如未开通权限时避免刷屏）。
	 */
	private String getRawSilent(String url, String token) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(10));
		if (token != null) {
			builder.header("Authorization", "QQBot " + token);
		}
		HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
		return resp.statusCode() >= 200 && resp.statusCode() < 300 ? resp.body() : null;
	}

	private String postRaw(String url, JsonObject body, String token) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/json")
				.timeout(Duration.ofSeconds(10));
		if (token != null) {
			builder.header("Authorization", "QQBot " + token);
		}
		String jsonBody = gson.toJson(body);
		HttpResponse<String> resp = httpClient.send(
				builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
				HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
			return resp.body();
		}
		MCQQBridge.LOGGER.error("[MCQQBridge] POST {} 失败：HTTP {} - {}（请求体：{}）",
				url, resp.statusCode(), resp.body(), jsonBody);
		return null;
	}
}
