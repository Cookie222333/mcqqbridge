package com.mcqq.bridge;

import com.mcqq.bridge.config.ModConfig;
import com.mcqq.bridge.mc.ChatBridge;
import com.mcqq.bridge.mc.ModCommands;
import com.mcqq.bridge.qq.QQBot;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCQQBridge 主类。
 *
 * <p>Minecraft 26.2 + Fabric 环境下的 Minecraft ↔ QQ 官方机器人消息互通模组。</p>
 */
public final class MCQQBridge implements ModInitializer {
	public static final String MOD_ID = "mcqqbridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;
	private static QQBot qqBot;
	private static ChatBridge chatBridge;
	private static volatile MinecraftServer server;

	@Override
	public void onInitialize() {
		LOGGER.info("[MCQQBridge] 正在初始化 Minecraft ↔ QQ 官方机器人互通模组 ...");

		// 1. 加载配置文件（config/mcqqbridge.json）
		config = ModConfig.load();
		if (!config.isValid()) {
			LOGGER.warn("[MCQQBridge] 配置不完整（缺少 appId / clientSecret），QQ 连接不会启动。");
			LOGGER.warn("[MCQQBridge] 请编辑 config/mcqqbridge.json 填写 QQ 开放平台机器人凭证，");
			LOGGER.warn("[MCQQBridge] 然后使用 /mcqq reload 重新加载。");
		}

		// 2. 创建 QQ 官方机器人客户端（Access Token 刷新 + WebSocket 网关 + 消息发送）
		qqBot = new QQBot(config);

		// 3. 注册聊天桥接（MC -> QQ）
		chatBridge = new ChatBridge(qqBot);
		chatBridge.register();

		// 4. 注册命令：/mcqq status /mcqq reload /mcqq help
		ModCommands.register(qqBot);

		// 5. 跟随服务器生命周期启动/停止 QQ 连接
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			MCQQBridge.server = server;
			if (config.isEnabled()) {
				qqBot.start();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			qqBot.stop();
			MCQQBridge.server = null;
		});

		LOGGER.info("[MCQQBridge] 初始化完成。");
	}

	/** 当前运行的 MinecraftServer（可能为 null）。 */
	public static MinecraftServer getServer() {
		return server;
	}

	/** 更新配置引用（由 /mcqq reload 调用）。 */
	public static void reloadConfig(ModConfig newConfig) {
		config = newConfig;
	}

	public static ModConfig getConfig() {
		return config;
	}

	public static QQBot getQQBot() {
		return qqBot;
	}

	public static ChatBridge getChatBridge() {
		return chatBridge;
	}
}
