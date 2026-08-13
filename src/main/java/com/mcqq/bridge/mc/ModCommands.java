package com.mcqq.bridge.mc;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.config.ModConfig;
import com.mcqq.bridge.qq.QQBot;

import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * 游戏内命令：/mcqq status、/mcqq reload、/mcqq help。
 */
public final class ModCommands {
	private ModCommands() {
	}

	public static void register(QQBot qqBot) {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(literal("mcqq")
						.then(literal("status").executes(ctx -> status(ctx)))
						.then(literal("reload").executes(ctx -> reload(ctx, qqBot)))
						.then(literal("help").executes(ctx -> help(ctx)))));
	}

	private static int status(CommandContext<CommandSourceStack> ctx) {
		ModConfig config = MCQQBridge.getConfig();
		QQBot qqBot = MCQQBridge.getQQBot();
		StringBuilder sb = new StringBuilder("§a[MCQQ] 互通状态§r\n");
		sb.append("  配置：")
				.append(config != null && config.isValid() ? "§a凭证已填写" : "§c凭证不完整")
				.append("§r\n");
		sb.append("  QQ 网关：")
				.append(qqBot != null && qqBot.isGatewayRunning() ? "§a运行中" : "§c未运行")
				.append("§r\n");
		sb.append("  QQ→MC 转发：")
				.append(config != null && config.qqToMc.enabled ? "§a开" : "§c关")
				.append("§r\n");
		sb.append("  MC→QQ 转发：")
				.append(config != null && config.mcToQq.enabled ? "§a开" : "§c关")
				.append("§r");
		ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx, QQBot qqBot) {
		ModConfig cfg = ModConfig.load();
		MCQQBridge.reloadConfig(cfg);
		qqBot.reload(cfg);
		boolean valid = cfg.isValid();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[MCQQ] 配置已重新加载。" + (valid ? "" : " §c（AppID/AppSecret 不完整，QQ 连接未启动）")), false);
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> ctx) {
		String help = "§a[MCQQ] 指令列表§r\n"
				+ "  /mcqq status —— 查看互通状态\n"
				+ "  /mcqq reload —— 重新加载配置文件\n"
				+ "  /mcqq help —— 查看本帮助\n"
				+ "§7QQ 群里发送 /mc help 可查看 QQ 端指令§r";
		ctx.getSource().sendSuccess(() -> Component.literal(help), false);
		return 1;
	}
}
