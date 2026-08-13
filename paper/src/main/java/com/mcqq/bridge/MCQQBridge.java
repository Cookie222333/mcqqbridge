package com.mcqq.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Paper 端门面类：为复用的 QQ 客户端代码提供统一的 LOGGER 与服务器引用。
 *
 * <p>Fabric 版的 QQ 类通过 {@code MCQQBridge.LOGGER} 打印日志，并通过
 * {@code MCQQBridge.getServer()} 获取服务器。这里在 Paper 端提供相同接口，
 * 使 QQ 客户端代码可无侵入复用。</p>
 */
public final class MCQQBridge {
	public static final String MOD_ID = "mcqqbridge";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 当前 Paper 服务器实例（由插件主类注入）。 */
	private static volatile org.bukkit.Server server;

	private MCQQBridge() {
	}

	/** 注入 Paper 服务器实例。 */
	public static void setServer(org.bukkit.Server s) {
		server = s;
	}

	/** 获取 Paper 服务器实例。 */
	public static org.bukkit.Server getServer() {
		return server;
	}
}
