package com.mcqq.bridge.qq;

import com.google.gson.JsonObject;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.config.ModConfig;

import net.minecraft.server.MinecraftServer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * QQ 官方机器人客户端协调器。
 *
 * <p>负责：启动/停止网关连接，解析群聊/单聊消息事件，维护昵称缓存，
 * 处理 QQ 端命令（如 {@code /mc status}），以及将 MC 侧消息发送到目标群。</p>
 */
public class QQBot {
	/** MC 侧接收 QQ 消息的出口（由 ChatBridge 实现，负责广播到游戏内）。 */
	public interface McSink {
		void onQqMessage(QQMessage message);
	}

	private volatile ModConfig config;
	private volatile QQApi api;
	private volatile QQGateway gateway;
	private volatile McSink sink;

	/** 被动回复序号。 */
	private final AtomicInteger replySeq = new AtomicInteger(1);
	/** 昵称缓存：openid 组合 -> 昵称。 */
	private final Map<String, String> nicknameCache = new ConcurrentHashMap<>();

	public QQBot(ModConfig config) {
		this.config = config;
		this.api = new QQApi(config.appId, config.clientSecret);
	}

	/** 设置 MC 侧消息出口。 */
	public void setSink(McSink sink) {
		this.sink = sink;
	}

	public synchronized void start() {
		if (gateway != null) {
			return;
		}
		gateway = new QQGateway(api, new QQGateway.Listener() {
			@Override
			public void onDispatch(JsonObject data, String type) {
				handleDispatch(data, type);
			}

			@Override
			public void onReady(JsonObject data) {
				MCQQBridge.LOGGER.info("[MCQQBridge] QQ 机器人已上线，开始接收消息事件 ...");
			}

			@Override
			public void onClosed(String reason) {
				// 网关内部会自动重连
			}
		});
		gateway.start();
	}

	public synchronized void stop() {
		if (gateway != null) {
			gateway.stop();
			gateway = null;
		}
	}

	/** 重新加载配置（凭证变更时重建连接）。 */
	public synchronized void reload(ModConfig newConfig) {
		stop();
		this.config = newConfig;
		this.api = new QQApi(newConfig.appId, newConfig.clientSecret);
		this.nicknameCache.clear();
		if (newConfig.isEnabled()) {
			start();
		}
	}

	public boolean isGatewayRunning() {
		return gateway != null;
	}

	// ------------------------------------------------------------------
	// 事件分发
	// ------------------------------------------------------------------

	private void handleDispatch(JsonObject d, String type) {
		switch (type) {
			case "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE" -> handleGroupMessage(d, type);
			case "C2C_MESSAGE_CREATE" -> handleC2CMessage(d);
			default -> MCQQBridge.LOGGER.debug("[MCQQBridge] 忽略事件：{}", type);
		}
	}

	private void handleGroupMessage(JsonObject d, String type) {
		if (!config.qqToMc.enabled) {
			return;
		}
		boolean isAt = "GROUP_AT_MESSAGE_CREATE".equals(type);
		if (isAt && !config.qqToMc.acceptGroupAt) {
			return;
		}
		if (!isAt && !config.qqToMc.acceptGroupMessage) {
			return;
		}

		String groupOpenId = d.has("group_openid") ? d.get("group_openid").getAsString() : "";
		if (!isGroupAllowed(groupOpenId)) {
			return;
		}

		String msgId = d.has("id") ? d.get("id").getAsString() : "";
		JsonObject author = d.has("author") && d.get("author").isJsonObject() ? d.getAsJsonObject("author") : null;
		String memberOpenId = author != null && author.has("member_openid")
				? author.get("member_openid").getAsString() : "";
		// 事件自带的发送者昵称（无需额外权限接口）
		String authorNickname = author != null && author.has("username") && !author.get("username").isJsonNull()
				? author.get("username").getAsString() : "";
		String rawContent = d.has("content") ? d.get("content").getAsString() : "";
		long timestamp = d.has("timestamp") ? parseTimestamp(d.get("timestamp").getAsString()) : System.currentTimeMillis();

		// 先处理 QQ 端命令（/mc xxx）
		if (handleQqCommand(groupOpenId, rawContent, msgId)) {
			return;
		}

		String content = cleanContent(rawContent);
		if (content.isBlank()) {
			return;
		}

		String sender = resolveGroupSender(groupOpenId, memberOpenId, authorNickname);
		QQMessage msg = new QQMessage(QQMessage.Type.GROUP, msgId, groupOpenId, memberOpenId, sender, content, timestamp);
		if (sink != null) {
			sink.onQqMessage(msg);
		}
	}

	private void handleC2CMessage(JsonObject d) {
		if (!config.qqToMc.enabled || !config.qqToMc.acceptPrivateMessage) {
			return;
		}
		String userOpenId = d.has("user_openid") ? d.get("user_openid").getAsString() : "";
		JsonObject author = d.has("author") && d.get("author").isJsonObject() ? d.getAsJsonObject("author") : null;
		String authorNickname = author != null && author.has("username") && !author.get("username").isJsonNull()
				? author.get("username").getAsString() : "";
		String msgId = d.has("id") ? d.get("id").getAsString() : "";
		String rawContent = d.has("content") ? d.get("content").getAsString() : "";

		String content = cleanContent(rawContent);
		if (content.isBlank()) {
			return;
		}
		String sender = resolvePrivateSender(userOpenId, authorNickname);
		QQMessage msg = new QQMessage(QQMessage.Type.PRIVATE, msgId, null, userOpenId, sender, content, System.currentTimeMillis());
		if (sink != null) {
			sink.onQqMessage(msg);
		}
	}

	private boolean isGroupAllowed(String groupOpenId) {
		List<String> allowed = config.qqToMc.groups;
		if (allowed == null || allowed.isEmpty()) {
			return true; // 未配置则不限制
		}
		for (String g : allowed) {
			if (g != null && g.trim().equals(groupOpenId)) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// 发送到 QQ
	// ------------------------------------------------------------------

	/** 将消息发送到所有目标群（主动消息）。 */
	public void sendToGroups(String formatted) {
		if (!config.mcToQq.enabled || formatted == null || formatted.isBlank()) {
			return;
		}
		for (String group : config.mcToQq.targetGroups) {
			if (group == null || group.isBlank()) {
				continue;
			}
			api.sendGroupMessage(group.trim(), formatted, null, replySeq.incrementAndGet());
		}
	}

	// ------------------------------------------------------------------
	// QQ 端命令
	// ------------------------------------------------------------------

	private boolean handleQqCommand(String groupOpenId, String rawContent, String msgId) {
		String prefix = config.qqCommandPrefix == null ? "/mc" : config.qqCommandPrefix.trim();
		if (prefix.isEmpty() || rawContent == null) {
			return false;
		}
		String trimmed = rawContent.trim();
		if (!trimmed.startsWith(prefix)) {
			return false;
		}

		String cmd = trimmed.substring(prefix.length()).trim().toLowerCase();
		String reply;

		// 先匹配用户自定义指令（config.qq_commands）
		ModConfig.QQCommand custom = findCustomCommand(cmd);
		if (custom != null) {
			reply = executeCustomCommand(custom);
		} else {
			switch (cmd) {
				case "status" -> reply = buildStatusText();
				case "online" -> reply = buildOnlineText();
				case "help" -> reply = buildHelpText();
				default -> reply = "未知指令。发送 /mc help 查看可用指令。";
			}
		}

		api.sendGroupMessage(groupOpenId, reply, msgId, replySeq.incrementAndGet());
		return true;
	}

	/** 在配置的 qq_commands 中查找匹配的自定义指令。 */
	private ModConfig.QQCommand findCustomCommand(String cmd) {
		if (config.qqCommands == null) {
			return null;
		}
		for (ModConfig.QQCommand c : config.qqCommands) {
			if (c.command != null && c.command.trim().equalsIgnoreCase(cmd)) {
				return c;
			}
		}
		return null;
	}

	/** 在 MC 服务器主线程执行自定义指令对应的 MC 命令，并捕获输出。 */
	private String executeCustomCommand(ModConfig.QQCommand custom) {
		MinecraftServer server = MCQQBridge.getServer();
		if (server == null) {
			return "【MC 状态】服务器未运行。";
		}
		String mcCommand = (custom.mcCommand == null || custom.mcCommand.isBlank())
				? "/" + custom.command : custom.mcCommand.trim();
		if (!mcCommand.startsWith("/")) {
			mcCommand = "/" + mcCommand;
		}
		String finalCommand = mcCommand;
		java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
		server.execute(() -> {
			String out = com.mcqq.bridge.mc.CommandCapture.execute(server, finalCommand);
			future.complete(out.isBlank() ? "（命令无输出）" : out);
		});
		int timeout = custom.timeoutMs > 0 ? custom.timeoutMs : 3000;
		try {
			return future.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
		} catch (java.util.concurrent.TimeoutException e) {
			return "【命令执行超时】";
		} catch (Exception e) {
			return "【命令执行异常】" + e.getMessage();
		}
	}

	private String buildStatusText() {
		StringBuilder sb = new StringBuilder("【MCQQ 互通状态】\n");
		sb.append("QQ 网关连接：").append(isGatewayRunning() ? "已启动" : "未启动").append("\n");
		sb.append("QQ→MC 转发：").append(config.qqToMc.enabled ? "开" : "关").append("\n");
		sb.append("MC→QQ 转发：").append(config.mcToQq.enabled ? "开" : "关");
		return sb.toString();
	}

	private String buildOnlineText() {
		var server = MCQQBridge.getServer();
		if (server == null) {
			return "【MC 状态】服务器未运行。";
		}
		List<?> players = server.getPlayerList().getPlayers();
		StringBuilder sb = new StringBuilder("【MC 在线玩家】\n");
		sb.append("在线人数：").append(players.size()).append("\n");
		for (Object p : players) {
			if (p instanceof net.minecraft.server.level.ServerPlayer sp) {
				sb.append("- ").append(sp.getGameProfile().name()).append("\n");
			}
		}
		return sb.toString().trim();
	}

	private String buildHelpText() {
		StringBuilder sb = new StringBuilder("【MCQQ 互通指令】\n");
		sb.append("/mc status —— 查看互通状态\n");
		sb.append("/mc online —— 查看 MC 在线玩家\n");
		// 追加自定义指令说明
		if (config.qqCommands != null) {
			for (ModConfig.QQCommand c : config.qqCommands) {
				String desc = (c.description == null || c.description.isBlank())
						? c.command : c.description;
				sb.append("/mc ").append(c.command).append(" —— ").append(desc).append("\n");
			}
		}
		sb.append("/mc help —— 查看本帮助");
		return sb.toString();
	}

	// ------------------------------------------------------------------
	// 昵称解析与内容清洗
	// ------------------------------------------------------------------

	private String resolveGroupSender(String groupOpenId, String memberOpenId, String eventNickname) {
		// 优先使用事件自带的 QQ 账户名（无需额外接口权限）
		if (eventNickname != null && !eventNickname.isBlank()) {
			return eventNickname;
		}
		String key = "g:" + groupOpenId + ":" + memberOpenId;
		String cached = nicknameCache.get(key);
		if (cached != null) {
			return cached;
		}
		String nick = api.getGroupMemberNickname(groupOpenId, memberOpenId);
		if (nick != null && !nick.isBlank()) {
			nicknameCache.put(key, nick);
			return nick;
		}
		return "群友" + shortId(memberOpenId);
	}

	private String resolvePrivateSender(String userOpenId, String eventNickname) {
		// 优先使用事件自带的 QQ 账户名
		if (eventNickname != null && !eventNickname.isBlank()) {
			return eventNickname;
		}
		String key = "u:" + userOpenId;
		String cached = nicknameCache.get(key);
		if (cached != null) {
			return cached;
		}
		String nick = api.getPrivateUserNickname(userOpenId);
		if (nick != null && !nick.isBlank()) {
			nicknameCache.put(key, nick);
			return nick;
		}
		return "用户" + shortId(userOpenId);
	}

	private static String shortId(String openId) {
		if (openId == null || openId.isBlank()) {
			return "未知";
		}
		return openId.length() > 6 ? openId.substring(0, 6) : openId;
	}

	/** 清洗 QQ 消息文本：移除 @提及、emoji、富文本标记，合并空白。 */
	private static String cleanContent(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("<@!?[0-9a-zA-Z]+>", "")
				.replaceAll("<emoji:[^>]*>", "")
				.replaceAll("<[^>]*>", "")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static long parseTimestamp(String ts) {
		try {
			return OffsetDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toEpochSecond();
		} catch (Exception e) {
			return System.currentTimeMillis();
		}
	}
}
