package com.mcqq.bridge.mc;

import com.mcqq.bridge.MCQQBridge;
import com.mcqq.bridge.config.ModConfig;
import com.mcqq.bridge.qq.QQBot;
import com.mcqq.bridge.qq.QQMessage;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 聊天桥接器。
 *
 * <p>MC -> QQ：监听玩家聊天 / 进服退服 / 死亡 / 游戏系统消息 / 命令广播，转发到目标 QQ 群。</p>
 * <p>QQ -> MC：接收 QQ 群聊 / 单聊消息，在服务器主线程广播给所有在线玩家。</p>
 */
public class ChatBridge implements QQBot.McSink {
	private final QQBot qqBot;

	public ChatBridge(QQBot qqBot) {
		this.qqBot = qqBot;
		qqBot.setSink(this);
	}

	/** 注册 MC 侧事件监听。 */
	public void register() {
		// MC -> QQ：玩家聊天消息
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, boundChatType) -> {
			ModConfig config = MCQQBridge.getConfig();
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardChat) {
				return;
			}
			String player = sender.getGameProfile().name();
			String text = message.decoratedContent().getString();
			qqBot.sendToGroups(formatMc(config.mcToQq.format, player, text));
		});

		// MC -> QQ：玩家进服 / 退服通知（参考 nonebot_plugin_mcqq 的「加入了游戏/离开了游戏」）
		ServerPlayerEvents.JOIN.register(player -> {
			ModConfig config = MCQQBridge.getConfig();
			MCQQBridge.LOGGER.info("[MCQQBridge][JOIN] 玩家 {} 进服，forwardJoinLeave={}",
					player.getGameProfile().name(),
					config != null && config.mcToQq.enabled && config.mcToQq.forwardJoinLeave);
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardJoinLeave) {
				return;
			}
			String name = player.getGameProfile().name();
			qqBot.sendToGroups("[MC] " + name + " 加入了游戏");
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			ModConfig config = MCQQBridge.getConfig();
			MCQQBridge.LOGGER.info("[MCQQBridge][LEAVE] 玩家 {} 退服，forwardJoinLeave={}",
					player.getGameProfile().name(),
					config != null && config.mcToQq.enabled && config.mcToQq.forwardJoinLeave);
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardJoinLeave) {
				return;
			}
			String name = player.getGameProfile().name();
			qqBot.sendToGroups("[MC] " + name + " 离开了游戏");
		});

		// MC -> QQ：玩家死亡通知（仅转发玩家死亡，参考 nonebot 的死亡消息）
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return;
			}
			ModConfig config = MCQQBridge.getConfig();
			MCQQBridge.LOGGER.info("[MCQQBridge][DEATH] 玩家 {} 死亡，forwardDeath={}",
					player.getGameProfile().name(),
					config != null && config.mcToQq.enabled && config.mcToQq.forwardDeath);
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardDeath) {
				return;
			}
			String name = player.getGameProfile().name();
			Component deathMessage = player.getCombatTracker().getDeathMessage();
			String detail = deathMessage != null ? deathMessage.getString() : (name + " 死亡了");
			qqBot.sendToGroups("[MC] " + detail);
		});

		// MC -> QQ：游戏系统消息（进服 / 退服 / 死亡 / 成就）
		ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
			ModConfig config = MCQQBridge.getConfig();
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardGameMessage) {
				return;
			}
			qqBot.sendToGroups("[MC] " + message.getString());
		});

		// MC -> QQ：命令广播（/say、/me）
		ServerMessageEvents.COMMAND_MESSAGE.register((message, source, boundChatType) -> {
			ModConfig config = MCQQBridge.getConfig();
			if (config == null || !config.mcToQq.enabled || !config.mcToQq.forwardCommandMessage) {
				return;
			}
			String player = source.getTextName();
			String text = message.decoratedContent().getString();
			qqBot.sendToGroups(formatMc(config.mcToQq.format, player, text));
		});
	}

	/** QQ -> MC：在服务器主线程广播消息给所有在线玩家。 */
	@Override
	public void onQqMessage(QQMessage msg) {
		MinecraftServer server = MCQQBridge.getServer();
		ModConfig config = MCQQBridge.getConfig();
		if (server == null || config == null) {
			return;
		}
		server.execute(() -> {
			String prefix = config.mcBroadcastPrefix == null || config.mcBroadcastPrefix.isBlank()
					? "[QQ]" : config.mcBroadcastPrefix;
			String fmt = config.qqToMc.format;
			String text;
			if (fmt != null && !fmt.isBlank()) {
				text = prefix + " " + fmt.replace("{sender}", msg.senderName).replace("{message}", msg.content);
			} else {
				text = prefix + " " + msg.senderName + ": " + msg.content;
			}
			server.getPlayerList().broadcastSystemMessage(Component.literal(text), false);
		});
	}

	private static String formatMc(String format, String player, String message) {
		if (format == null || format.isBlank()) {
			return "[MC] " + player + ": " + message;
		}
		return format.replace("{player}", player).replace("{message}", message);
	}
}
