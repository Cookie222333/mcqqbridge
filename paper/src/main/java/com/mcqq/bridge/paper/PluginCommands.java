package com.mcqq.bridge.paper;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.qq.QQBot;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /mcqq 命令处理器。
 */
public class PluginCommands implements CommandExecutor, TabCompleter {
	private final QQBot qqBot;
	private final MCQQBridgePlugin plugin;

	public PluginCommands(QQBot qqBot, MCQQBridgePlugin plugin) {
		this.qqBot = qqBot;
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) {
			sendHelp(sender);
			return true;
		}
		switch (args[0].toLowerCase()) {
			case "status" -> sender.sendMessage(qqBot.isGatewayRunning()
					? "§a[MCQQBridge] QQ 连接已启动。" : "§c[MCQQBridge] QQ 连接未启动。");
			case "reload" -> {
				if (!sender.hasPermission("mcqqbridge.admin")) {
					sender.sendMessage("§c你没有权限执行此命令。");
					return true;
				}
				plugin.reloadConfigData();
				sender.sendMessage("§a[MCQQBridge] 配置已重新加载。");
			}
			case "help" -> sendHelp(sender);
			default -> sender.sendMessage("§c未知指令。使用 /mcqq help 查看帮助。");
		}
		return true;
	}

	private void sendHelp(CommandSender sender) {
		sender.sendMessage("§e=== MCQQBridge 帮助 ===");
		sender.sendMessage("§7/mcqq status §f- 查看 QQ 连接状态");
		sender.sendMessage("§7/mcqq reload §f- 重新加载配置");
		sender.sendMessage("§7/mcqq help §f- 查看帮助");
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> list = new ArrayList<>();
		if (args.length == 1) {
			String prefix = args[0].toLowerCase();
			for (String s : new String[]{"status", "reload", "help"}) {
				if (s.startsWith(prefix)) {
					list.add(s);
				}
			}
		}
		return list;
	}
}
