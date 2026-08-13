package com.mcqq.bridge.mc;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * 捕获 MC 服务端命令输出的 {@link CommandSource}。
 *
 * <p>配合 {@link CommandSourceStack#withSource(CommandSource)} 使用，
 * 让服务端执行命令时把 {@code sendSystemMessage} 输出收集起来，供 QQ 端回复。</p>
 */
public class CommandCapture implements CommandSource {
	private final List<String> lines = new ArrayList<>();

	@Override
	public void sendSystemMessage(Component message) {
		if (message != null) {
			lines.add(message.getString());
		}
	}

	@Override
	public boolean acceptsSuccess() {
		return true;
	}

	@Override
	public boolean acceptsFailure() {
		return true;
	}

	@Override
	public boolean shouldInformAdmins() {
		return false;
	}

	/** 在服务器主线程执行命令并捕获输出。 */
	public static String execute(MinecraftServer server, String command) {
		CommandCapture capture = new CommandCapture();
		try {
			CommandSourceStack source = server.createCommandSourceStack()
					.withSource(capture)
					.withLevel(server.overworld());
			server.getCommands().performPrefixedCommand(source, command);
		} catch (Exception e) {
			capture.lines.add("命令执行失败：" + e.getMessage());
		}
		return String.join("\n", capture.lines);
	}
}

