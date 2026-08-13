package com.mcqq.bridge.qq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mcqq.bridge.MCQQBridge;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QQ 官方机器人 WebSocket Gateway 长连接。
 *
 * <p>遵循官方协议：连接 -> Hello(op10) -> Identify(op2)/Resume(op6) -> READY/RESUMED
 * -> 心跳(op1/op11) -> DISPATCH(op0) 事件。断线后自动退避重连。</p>
 *
 * <p>使用 {@code Java-WebSocket} 库，因为它的连接/回调运行在独立的线程上，
 * 不依赖 JDK {@code HttpClient} 的 {@code ForkJoinPool.commonPool}。
 * 在 Minecraft 服务器中，commonPool 会被服务器 tick/实体/区块任务占满，
 * 导致 JDK WebSocket 的 READY 等消息回调饿死、连接无法鉴权。</p>
 *
 * <p>官方文档：https://bot.q.qq.com/wiki/develop/api-v2/dev-prepare/event-emit/websocket.html</p>
 */
public class QQGateway {
	/** 通用 WSS 接入点（经 /gateway 接口获取，与 REST API 域名不同！）。 */
	public static final String GATEWAY_URL = "wss://api.sgroup.qq.com/websocket";
	/** 官方网关地址查询接口（动态获取，避免硬编码过期）。 */
	private static final String GATEWAY_DISCOVERY_URL = "https://api.bot.qq.com/gateway";

	/** 事件订阅 Intents：GROUP_AND_C2C_EVENT = 1 &lt;&lt; 25（群聊 + 单聊消息）。 */
	public static final int INTENT_GROUP_AND_C2C = 1 << 25;

	/** 网关事件回调。 */
	public interface Listener {
		/** 收到业务事件（op=0 且 t != READY/RESUMED）。 */
		void onDispatch(JsonObject data, String type);

		/** 鉴权成功。 */
		void onReady(JsonObject data);

		/** 连接已关闭（内部会自动重连）。 */
		void onClosed(String reason);
	}

	private final QQApi api;
	private final Listener listener;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "mcqq-gateway");
		t.setDaemon(true);
		return t;
	});

	private volatile WebSocketClient webSocket;
	private volatile boolean running;
	private volatile boolean sessionValid;      // 当前 session 是否可 Resume
	private volatile String sessionId;
	private volatile int lastSeq;
	private volatile long heartbeatInterval;
	private volatile ScheduledFuture<?> heartbeatTask;
	private volatile ScheduledFuture<?> reconnectTask;
	private final AtomicInteger reconnectCount = new AtomicInteger();

	public QQGateway(QQApi api, Listener listener) {
		this.api = api;
		this.listener = listener;
	}

	/** 启动网关连接。 */
	public void start() {
		if (running) {
			return;
		}
		running = true;
		connect();
	}

	/** 停止网关连接。 */
	public void stop() {
		running = false;
		cancelHeartbeat();
		cancelReconnect();
		WebSocketClient ws = webSocket;
		webSocket = null;
		if (ws != null) {
			try {
				ws.close(1000, "mod stopped");
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * 动态获取官方 WSS 网关地址；失败时回退到内置地址。
	 * 每个机器人一天内可查询次数有限，因此成功后缓存。
	 */
	private String resolveGatewayUrl() {
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.build();
			HttpRequest req = HttpRequest.newBuilder(URI.create(GATEWAY_DISCOVERY_URL))
					.header("Authorization", "QQBot " + api.getAccessToken())
					.GET()
					.timeout(Duration.ofSeconds(10))
					.build();
			HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
				JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
				String url = json.has("url") ? json.get("url").getAsString() : null;
				if (url != null && !url.isBlank()) {
					MCQQBridge.LOGGER.info("[MCQQBridge] 从官方接口获取网关地址：{}", url);
					return url;
				}
			}
		} catch (Exception e) {
			MCQQBridge.LOGGER.warn("[MCQQBridge] 获取网关地址失败（{}），使用内置地址。", e.getMessage());
		}
		return GATEWAY_URL;
	}

	private synchronized void connect() {
		if (!running) {
			return;
		}
		cancelReconnect();
		try {
			String token = api.getAccessToken();
			if (token == null || token.isBlank()) {
				MCQQBridge.LOGGER.warn("[MCQQBridge] 缺少有效 access_token，5 秒后重试 ...");
				scheduleReconnect(5_000);
				return;
			}
			String url = resolveGatewayUrl();
			MCQQBridge.LOGGER.info("[MCQQBridge] 正在连接 QQ Gateway（{}）...", url);

			webSocket = new GatewayClient(URI.create(url), Map.of(
					"Authorization", "QQBot " + token,
					"Accept-Encoding", "gzip, deflate"
			));
			// Java-WebSocket 自行创建/管理连接线程，不依赖 commonPool
			webSocket.connect();
		} catch (Exception e) {
			MCQQBridge.LOGGER.error("[MCQQBridge] 连接 QQ Gateway 失败：{}", e.getMessage());
			scheduleReconnect(5_000);
		}
	}

	private void scheduleReconnect(long delayMillis) {
		if (!running) {
			return;
		}
		cancelReconnect();
		reconnectTask = executor.schedule(this::connect, delayMillis, TimeUnit.MILLISECONDS);
	}

	private void cancelReconnect() {
		ScheduledFuture<?> task = reconnectTask;
		if (task != null) {
			task.cancel(false);
			reconnectTask = null;
		}
	}

	private void cancelHeartbeat() {
		ScheduledFuture<?> task = heartbeatTask;
		if (task != null) {
			task.cancel(false);
			heartbeatTask = null;
		}
	}

	private void startHeartbeat() {
		cancelHeartbeat();
		if (heartbeatInterval <= 0) {
			return;
		}
		heartbeatTask = executor.scheduleAtFixedRate(() -> {
			try {
				JsonObject hb = new JsonObject();
				hb.addProperty("op", 1);
				hb.add("d", lastSeq > 0 ? JsonParser.parseString(String.valueOf(lastSeq)) : null);
				WebSocketClient ws = webSocket;
				if (ws != null && ws.isOpen()) {
					ws.send(hb.toString());
				}
			} catch (Exception e) {
				MCQQBridge.LOGGER.warn("[MCQQBridge] 发送心跳失败：{}", e.getMessage());
			}
		}, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
	}

	private void sendIdentify() {
		JsonObject d = new JsonObject();
		d.addProperty("token", "QQBot " + api.getAccessToken());
		d.addProperty("intents", INTENT_GROUP_AND_C2C);
		d.add("shard", JsonParser.parseString("[0,1]"));
		JsonObject props = new JsonObject();
		props.addProperty("$os", System.getProperty("os.name", "unknown"));
		props.addProperty("$browser", "mcqqbridge");
		props.addProperty("$device", "mcqqbridge");
		d.add("properties", props);

		JsonObject payload = new JsonObject();
		payload.addProperty("op", 2);
		payload.add("d", d);

		sessionValid = false;
		WebSocketClient ws = webSocket;
		if (ws != null && ws.isOpen()) {
			ws.send(payload.toString());
		}
		MCQQBridge.LOGGER.info("[MCQQBridge] 已发送 Identify。");
	}

	private void sendResume() {
		JsonObject d = new JsonObject();
		d.addProperty("token", "QQBot " + api.getAccessToken());
		d.addProperty("session_id", sessionId);
		d.addProperty("seq", lastSeq);

		JsonObject payload = new JsonObject();
		payload.addProperty("op", 6);
		payload.add("d", d);

		WebSocketClient ws = webSocket;
		if (ws != null && ws.isOpen()) {
			ws.send(payload.toString());
		}
		MCQQBridge.LOGGER.info("[MCQQBridge] 已发送 Resume（session={}）。", sessionId);
	}

	// ------------------------------------------------------------------
	// 消息处理
	// ------------------------------------------------------------------

	private void handlePayload(String payload) {
		try {
			JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
			int op = json.has("op") ? json.get("op").getAsInt() : -1;
			switch (op) {
				case 10 -> handleHello(json);
				case 0 -> handleDispatch(json);
				case 11 -> { /* 心跳 ACK，无需处理 */ }
				case 7 -> {
					MCQQBridge.LOGGER.warn("[MCQQBridge] 收到 Reconnect(op=7)，重新连接 ...");
					closeSocket();
					scheduleReconnect(0);
				}
				case 9 -> {
					MCQQBridge.LOGGER.warn("[MCQQBridge] Invalid Session(op=9)，重新鉴权 ...");
					sessionValid = false;
					cancelHeartbeat();
					sendIdentify();
				}
				default -> MCQQBridge.LOGGER.debug("[MCQQBridge] 未处理 op={}", op);
			}
		} catch (Exception e) {
			MCQQBridge.LOGGER.warn("[MCQQBridge] 解析 Gateway payload 失败：{}", e.getMessage());
		}
	}

	private void handleHello(JsonObject json) {
		JsonObject d = json.has("d") ? json.getAsJsonObject("d") : null;
		heartbeatInterval = d != null && d.has("heartbeat_interval") ? d.get("heartbeat_interval").getAsLong() : 0;
		MCQQBridge.LOGGER.info("[MCQQBridge] 收到 Hello，心跳周期 {} ms。", heartbeatInterval);

		if (sessionValid && sessionId != null) {
			sendResume();
		} else {
			sendIdentify();
		}
		startHeartbeat();
	}

	private void handleDispatch(JsonObject json) {
		String type = json.has("t") ? json.get("t").getAsString() : "";
		if (json.has("s")) {
			lastSeq = json.get("s").getAsInt();
		}
		JsonObject d = json.has("d") && json.get("d").isJsonObject() ? json.getAsJsonObject("d") : null;

		switch (type) {
			case "READY" -> {
				sessionValid = true;
				if (d != null && d.has("session_id")) {
					sessionId = d.get("session_id").getAsString();
				}
				reconnectCount.set(0);
				MCQQBridge.LOGGER.info("[MCQQBridge] QQ Gateway 鉴权成功（READY），session={}。", sessionId);
				listener.onReady(d);
			}
			case "RESUMED" -> {
				reconnectCount.set(0);
				MCQQBridge.LOGGER.info("[MCQQBridge] QQ Gateway 会话恢复成功（RESUMED）。");
			}
			default -> {
				if (d != null) {
					listener.onDispatch(d, type);
				}
			}
		}
	}

	private void closeSocket() {
		cancelHeartbeat();
		WebSocketClient ws = webSocket;
		webSocket = null;
		if (ws != null) {
			try {
				ws.close();
			} catch (Exception ignored) {
			}
		}
	}

	private void onConnectionClosed(String reason) {
		closeSocket();
		if (!running) {
			return;
		}
		listener.onClosed(reason);
		long delay = Math.min(30_000, 1_000L * (1 << Math.min(reconnectCount.incrementAndGet(), 5)));
		MCQQBridge.LOGGER.info("[MCQQBridge] 连接关闭（{}），{} ms 后重连 ...", reason, delay);
		scheduleReconnect(delay);
	}

	// ------------------------------------------------------------------
	// Java-WebSocket 客户端
	// ------------------------------------------------------------------

	private final class GatewayClient extends WebSocketClient {
		GatewayClient(URI uri, Map<String, String> headers) {
			super(uri, headers);
			// 连接丢失检测超时（秒）
			setConnectionLostTimeout(30);
		}

		@Override
		public void onOpen(ServerHandshake handshakedata) {
			MCQQBridge.LOGGER.info("[MCQQBridge] QQ Gateway 已连接，等待 Hello ...");
		}

		@Override
		public void onMessage(String message) {
			handlePayload(message);
		}

		@Override
		public void onClose(int code, String reason, boolean remote) {
			MCQQBridge.LOGGER.warn("[MCQQBridge] Gateway 连接关闭：{} {}", code, reason);
			onConnectionClosed("closed(" + code + ")");
		}

		@Override
		public void onError(Exception ex) {
			MCQQBridge.LOGGER.error("[MCQQBridge] Gateway 错误：{}", ex.getMessage());
			if (!webSocket.isOpen()) {
				onConnectionClosed("error");
			}
		}
	}
}
