package com.mcqq.bridge.paper;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.config.ModConfig;
import com.mcqq.bridge.qq.QQBot;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * MCQQBridge Paper 插件主类。
 *
 * <p>负责：加载配置、创建并启动 QQ 官方机器人客户端、注册事件监听器与命令。</p>
 */
public class MCQQBridgePlugin extends JavaPlugin {
	private static MCQQBridgePlugin instance;

	private volatile ModConfig config;
	private volatile QQBot qqBot;
	private volatile ChatListener chatListener;

	public MCQQBridgePlugin() {
		instance = this;
	}

	public static MCQQBridgePlugin getInstance() {
		return instance;
	}

	@Override
	public void onEnable() {
		MCQQBridge.LOGGER.info("[MCQQBridge] 正在初始化 Paper ↔ QQ 官方机器人互通插件 ...");

		// 注入服务器引用（供复用的 QQ 客户端代码使用）
		MCQQBridge.setServer(getServer());

		// 设置配置目录为插件数据文件夹
		ModConfig.setConfigDir(getDataFolder().toPath());

		// 0. 若配置不存在，从资源中复制默认模板
		initDefaultConfig();

		// 1. 加载配置
		config = ModConfig.load();
		if (!config.isValid()) {
			MCQQBridge.LOGGER.warn("[MCQQBridge] 配置不完整（缺少 appId / clientSecret），QQ 连接不会启动。");
			MCQQBridge.LOGGER.warn("[MCQQBridge] 请编辑 plugins/MCQQBridge/mcqqbridge.json 填写 QQ 开放平台机器人凭证，");
			MCQQBridge.LOGGER.warn("[MCQQBridge] 然后使用 /mcqq reload 重新加载。");
		}

		// 2. 创建 QQ 官方机器人客户端
		qqBot = new QQBot(config);

		// 3. 注册 Bukkit 事件监听器（聊天 / 进服 / 退服 / 死亡）
		chatListener = new ChatListener(qqBot);
		getServer().getPluginManager().registerEvents(chatListener, this);

		// 4. 注册命令
		PluginCommands commands = new PluginCommands(qqBot, this);
		getCommand("mcqq").setExecutor(commands);
		getCommand("mcqq").setTabCompleter(commands);

		// 5. 如果配置完整，启动 QQ 连接
		if (config.isEnabled()) {
			qqBot.start();
		}

		MCQQBridge.LOGGER.info("[MCQQBridge] 初始化完成。");
	}

	@Override
	public void onDisable() {
		if (qqBot != null) {
			qqBot.stop();
		}
		MCQQBridge.LOGGER.info("[MCQQBridge] 已停止。");
	}

	/** 重新加载配置。 */
	public synchronized void reloadConfigData() {
		qqBot.stop();
		config = ModConfig.load();
		qqBot.reload(config);
		MCQQBridge.LOGGER.info("[MCQQBridge] 配置已重新加载。");
	}

	/** 若配置不存在，从插件资源复制默认模板到数据文件夹。 */
	private void initDefaultConfig() {
		java.io.File cfgFile = new java.io.File(getDataFolder(), "mcqqbridge.json");
		if (cfgFile.exists()) {
			return;
		}
		try {
			if (!getDataFolder().exists()) {
				getDataFolder().mkdirs();
			}
			java.io.InputStream in = getResource("mcqqbridge.json");
			if (in == null) {
				MCQQBridge.LOGGER.warn("[MCQQBridge] 资源中缺少默认配置模板。");
				return;
			}
			java.nio.file.Files.copy(in, cfgFile.toPath(),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			MCQQBridge.LOGGER.info("[MCQQBridge] 已生成默认配置文件：{}", cfgFile.getAbsolutePath());
		} catch (Exception e) {
			MCQQBridge.LOGGER.error("[MCQQBridge] 生成默认配置失败：{}", e.getMessage());
		}
	}

	public ModConfig getConfigData() {
		return config;
	}
}
