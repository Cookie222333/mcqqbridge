package com.mcqq.bridge.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置。存储于 {@code config/mcqqbridge.json}。
 *
 * <p>包含：QQ 开放平台机器人凭证（AppID / ClientSecret）、互通开关、
 * 目标群 OpenID 列表、消息格式模板等。</p>
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	/** 总开关。 */
	public boolean enabled = true;

	/** QQ 开放平台机器人 AppID。 */
	@SerializedName("app_id")
	public String appId = "";

	/** QQ 开放平台机器人 AppSecret（client_secret）。 */
	@SerializedName("client_secret")
	public String clientSecret = "";

	/** QQ -> Minecraft 方向配置。 */
	@SerializedName("qq_to_mc")
	public QQToMc qqToMc = new QQToMc();

	/** Minecraft -> QQ 方向配置。 */
	@SerializedName("mc_to_qq")
	public MCToQq mcToQq = new MCToQq();

	/** 从 QQ 转发到游戏内时，消息前缀（黄色系统消息显示）。 */
	@SerializedName("mc_broadcast_prefix")
	public String mcBroadcastPrefix = "[QQ]";

	/** 在 QQ 里触发互通命令的前缀（例如 QQ 里发送 "/mc status"）。 */
	@SerializedName("qq_command_prefix")
	public String qqCommandPrefix = "/mc";

	/**
	 * QQ 端自定义指令列表：在 QQ 群发送 {@code command} 时，
	 * 会在 MC 服务端主线程执行 {@code mcCommand} 并捕获输出返回给 QQ。
	 */
	@SerializedName("qq_commands")
	public List<QQCommand> qqCommands = new ArrayList<>();

	/** QQ 端自定义指令配置。 */
	public static class QQCommand {
		/** QQ 里发送的触发词（不含前缀，例如 {@code list} 则发送 {@code /mc list}）。 */
		@SerializedName("command")
		public String command = "";

		/** 要在 MC 服务端执行的命令（含斜杠前缀，例如 {@code /list}）。 */
		@SerializedName("mc_command")
		public String mcCommand = "";

		/** 指令说明（显示在 /mc help 中）。 */
		@SerializedName("description")
		public String description = "";

		/** 命令超时（毫秒），超时后回复超时提示。 */
		@SerializedName("timeout_ms")
		public int timeoutMs = 3000;
	}

	/** QQ -> Minecraft 方向。 */
	public static class QQToMc {
		/** 是否允许 QQ 消息转发到游戏内。 */
		public boolean enabled = true;

		/** 允许转发消息的群 OpenID 列表。为空表示不限制。 */
		public List<String> groups = new ArrayList<>();

		/** 是否接收「群内 @机器人」消息。 */
		@SerializedName("accept_group_at")
		public boolean acceptGroupAt = true;

		/** 是否接收「群内普通消息」（需要机器人有群消息权限）。 */
		@SerializedName("accept_group_message")
		public boolean acceptGroupMessage = true;

		/** 是否接收「单聊（私聊）」消息。 */
		@SerializedName("accept_private_message")
		public boolean acceptPrivateMessage = true;

		/** 转发到游戏内的消息格式：{sender} 发送者昵称，{message} 消息内容。 */
		@SerializedName("format")
		public String format = "{sender}: {message}";
	}

	/** Minecraft -> QQ 方向。 */
	public static class MCToQq {
		/** 是否允许游戏内消息转发到 QQ。 */
		public boolean enabled = true;

		/** 转发到这些群 OpenID。 */
		@SerializedName("target_groups")
		public List<String> targetGroups = new ArrayList<>();

		/** 是否转发玩家聊天消息。 */
		@SerializedName("forward_chat")
		public boolean forwardChat = true;

		/** 是否转发游戏系统消息（进服/退服/死亡/成就等）。 */
		@SerializedName("forward_game_message")
		public boolean forwardGameMessage = false;

		/** 是否转发玩家进服/退服通知（如「xxx 加入了游戏」「xxx 离开了游戏」）。 */
		@SerializedName("forward_join_leave")
		public boolean forwardJoinLeave = false;

		/** 是否转发玩家死亡通知（如「xxx 被苦力怕炸死了」）。 */
		@SerializedName("forward_death")
		public boolean forwardDeath = false;

		/** 是否转发命令广播消息（/say、/me）。 */
		@SerializedName("forward_command_message")
		public boolean forwardCommandMessage = false;

		/** 发送到 QQ 的消息格式：{player} 玩家名，{message} 消息内容。 */
		@SerializedName("format")
		public String format = "[MC] {player}: {message}";
	}

	/** 从配置目录加载配置；文件不存在时创建默认配置。 */
	public static ModConfig load() {
		Path path = getConfigPath();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ModConfig cfg = GSON.fromJson(reader, ModConfig.class);
				if (cfg != null) {
					cfg.normalize();
					return cfg;
				}
			} catch (IOException e) {
				com.mcqq.bridge.MCQQBridge.LOGGER.error("[MCQQBridge] 读取配置文件失败：{}", path, e);
			}
		}

		ModConfig cfg = new ModConfig();
		cfg.save();
		return cfg;
	}

	/** 保存配置到磁盘。 */
	public void save() {
		Path path = getConfigPath();
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			com.mcqq.bridge.MCQQBridge.LOGGER.error("[MCQQBridge] 写入配置文件失败：{}", path, e);
		}
	}

	/** 保证内部字段非空。 */
	private void normalize() {
		if (appId == null) appId = "";
		if (clientSecret == null) clientSecret = "";
		if (qqToMc == null) qqToMc = new QQToMc();
		if (mcToQq == null) mcToQq = new MCToQq();
		if (qqToMc.groups == null) qqToMc.groups = new ArrayList<>();
		if (mcToQq.targetGroups == null) mcToQq.targetGroups = new ArrayList<>();
		if (mcBroadcastPrefix == null) mcBroadcastPrefix = "[QQ]";
		if (qqCommandPrefix == null) qqCommandPrefix = "/mc";
		if (qqCommands == null) qqCommands = new ArrayList<>();
		qqCommands.removeIf(c -> c == null || c.command == null || c.command.isBlank());
	}

	/** 凭证是否完整（AppID 与 AppSecret 均非空）。 */
	public boolean isValid() {
		return appId != null && !appId.isBlank()
				&& clientSecret != null && !clientSecret.isBlank();
	}

	/** 总开关是否生效。 */
	public boolean isEnabled() {
		return enabled && isValid();
	}

	private static volatile java.nio.file.Path configDir;

	/** 设置配置目录（由 Paper 插件主类注入，通常是插件 dataFolder）。 */
	public static void setConfigDir(java.nio.file.Path dir) {
		configDir = dir;
	}

	private static Path getConfigPath() {
		java.nio.file.Path dir = configDir;
		if (dir == null) {
			dir = java.nio.file.Paths.get("plugins", "MCQQBridge");
		}
		return dir.resolve("mcqqbridge.json");
	}
}
