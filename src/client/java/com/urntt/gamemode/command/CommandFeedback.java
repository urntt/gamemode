package com.urntt.gamemode.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;

/**
 * Reports command errors in the chat the way vanilla reports a failed command.
 */
final class CommandFeedback {
	/** How many characters before the error vanilla shows. */
	private static final int CONTEXT_LENGTH = 10;

	private CommandFeedback() {
	}

	/**
	 * Shows the message in red, followed by the part of the input that caused it.
	 */
	static void sendError(final LocalPlayer player, final CommandSyntaxException e, final String command) {
		player.sendSystemMessage(ComponentUtils.fromMessage(e.getRawMessage()).copy().withStyle(ChatFormatting.RED));
		if (e.getInput() != null && e.getCursor() >= 0) {
			int cursor = Math.min(e.getInput().length(), e.getCursor());
			MutableComponent context = Component.empty()
					.withStyle(ChatFormatting.GRAY)
					.withStyle(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/" + command)));
			if (cursor > CONTEXT_LENGTH) {
				context.append(CommonComponents.ELLIPSIS);
			}
			context.append(e.getInput().substring(Math.max(0, cursor - CONTEXT_LENGTH), cursor));
			if (cursor < e.getInput().length()) {
				context.append(Component.literal(e.getInput().substring(cursor)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE));
			}
			context.append(Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
			player.sendSystemMessage(context);
		}
	}
}
