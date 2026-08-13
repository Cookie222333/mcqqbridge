package com.mcqq.bridge.paper;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.config.ModConfig;
import com.mcqq.bridge.qq.QQBot;
import com.mcqq.bridge.qq.QQMessage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Bukkit 事件监听器：将游戏内聊天、进出游戏、死亡等事件转发到 QQ。
 */
public class ChatListener implements Listener, QQBot.McSink {
	private final QQBot qqBot;

	public ChatListener(QQBot qqBot) {
		this.qqBot = qqBot;
		qqBot.setSink(this);
	}

	/** MC -> QQ：玩家聊天消息。 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onChat(AsyncPlayerChatEvent event) {
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardChat) {
			return;
		}
		String player = event.getPlayer().getName();
		String text = event.getMessage();
		qqBot.sendToGroups(formatMc(config.mcToQq.format, player, text));
	}

	/** MC -> QQ：玩家进服通知。 */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardJoinLeave) {
			return;
		}
		qqBot.sendToGroups("[MC] " + event.getPlayer().getName() + " 加入了游戏");
	}

	/** MC -> QQ：玩家退服通知。 */
	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardJoinLeave) {
			return;
		}
		qqBot.sendToGroups("[MC] " + event.getPlayer().getName() + " 离开了游戏");
	}

	/** MC -> QQ：玩家死亡通知。 */
	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardDeath) {
			return;
		}
		String detail = event.getDeathMessage();
		if (detail == null || detail.isBlank()) {
			detail = event.getEntity().getName() + " 死亡了";
		}
		// 移除可能存在的颜色码
		detail = detail.replaceAll("§[0-9a-fk-or]", "");
		qqBot.sendToGroups("[MC] " + detail);
	}

	/** MC -> QQ：命令广播（/say、/me）。 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onCommand(PlayerCommandPreprocessEvent event) {
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardCommandMessage) {
			return;
		}
		String msg = event.getMessage();
		if (msg == null || (!msg.startsWith("/say") && !msg.startsWith("/me"))) {
			return;
		}
		String text = msg.substring(msg.indexOf(' ') + 1);
		qqBot.sendToGroups(formatMc(config.mcToQq.format, event.getPlayer().getName(), text));
	}

	/** QQ -> MC：在服务器主线程广播消息给所有在线玩家。 */
	@Override
	public void onQqMessage(QQMessage msg) {
		org.bukkit.Server server = MCQQBridge.getServer();
		ModConfig config = MCQQBridgePlugin.getInstance().getConfigData();
		if (server == null || config == null) {
			return;
		}
		server.getScheduler().scheduleSyncDelayedTask(MCQQBridgePlugin.getInstance(), () -> {
			String prefix = config.mcBroadcastPrefix == null || config.mcBroadcastPrefix.isBlank()
					? "[QQ]" : config.mcBroadcastPrefix;
			String fmt = config.qqToMc.format;
			String text;
			if (fmt != null && !fmt.isBlank()) {
				text = prefix + " " + fmt.replace("{sender}", msg.senderName).replace("{message}", msg.content);
			} else {
				text = prefix + " " + msg.senderName + ": " + msg.content;
			}
			server.broadcastMessage(text);
		});
	}

	private static String formatMc(String format, String player, String message) {
		if (format == null || format.isBlank()) {
			return "[MC] " + player + ": " + message;
		}
		return format.replace("{player}", player).replace("{message}", message);
	}
}
