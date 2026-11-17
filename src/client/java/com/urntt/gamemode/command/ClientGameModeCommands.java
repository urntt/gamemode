package com.urntt.gamemode.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.urntt.gamemode.ClientGameMode;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.GameModeController;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code /gamemode <mode> @c} and {@code /gamemode @c} commands. They run on the client: {@link #handles} tells
 * the packet listener which commands to keep from the server, {@link #execute} runs them, and {@link #addTo} adds
 * their nodes to the command tree the server sends, so the chat suggests and highlights them like server commands.
 */
public final class ClientGameModeCommands {
	/** The pseudo selector that makes a vanilla game mode command change only the client game mode. */
	public static final String CLIENT_TARGET = "@c";
	public static final String STATUS_CLIENT_KEY = "gamemode.command.status.client";
	public static final String STATUS_SERVER_KEY = "gamemode.command.status.server";
	public static final String STATUS_SWITCH_KEY = "gamemode.command.status.switch";

	private static final SimpleCommandExceptionType BLOCKED = new SimpleCommandExceptionType(GameModeClient.BLOCKED_MESSAGE);
	private static final SimpleCommandExceptionType TURNED_OFF = new SimpleCommandExceptionType(GameModeClient.TURNED_OFF_MESSAGE);
	private static final SimpleCommandExceptionType NOT_IN_WORLD =
			new SimpleCommandExceptionType(Component.translatable("gamemode.command.not_in_world"));

	private static final Logger LOGGER = LoggerFactory.getLogger(GameModeClient.MOD_ID);
	private static final String GAMEMODE = "gamemode";
	/** Name of vanilla's game mode argument, so the mod's nodes merge into the server's. */
	private static final String GAMEMODE_ARGUMENT = "gamemode";
	private static final Pattern COMMAND =
			Pattern.compile("^" + GAMEMODE + " (?:\\S+ )?" + Pattern.quote(CLIENT_TARGET) + "(?:\\s|$)");
	private static final CommandDispatcher<ClientSuggestionProvider> DISPATCHER = createDispatcher();

	private ClientGameModeCommands() {
	}

	/**
	 * Returns whether {@code command} (without the leading slash) is one of this mod's commands.
	 */
	public static boolean handles(final String command) {
		return COMMAND.matcher(command).find();
	}

	/**
	 * Runs {@code command} on the client and reports errors in the chat like vanilla does.
	 */
	public static void execute(final String command) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientPacketListener connection = minecraft.getConnection();
		LocalPlayer player = minecraft.player;
		if (connection == null || player == null) {
			return;
		}
		try {
			DISPATCHER.execute(command, connection.getSuggestionsProvider());
		} catch (CommandSyntaxException e) {
			CommandFeedback.sendError(player, e, command);
		} catch (RuntimeException e) {
			LOGGER.error("Failed to run client command '{}'", command, e);
			player.sendSystemMessage(Component.translatable("command.failed").withStyle(ChatFormatting.RED));
		}
	}

	/**
	 * Adds the mod's nodes to {@code commands}, the command tree the server sent. Brigadier merges nodes with the
	 * same name, so the server's own {@code /gamemode} keeps working. Without permission for {@code /gamemode}, the
	 * server sends no {@code gamemode} node, and this adds one.
	 */
	public static void addTo(final CommandDispatcher<ClientSuggestionProvider> commands) {
		commands.getRoot().addChild(command().build());
	}

	private static CommandDispatcher<ClientSuggestionProvider> createDispatcher() {
		CommandDispatcher<ClientSuggestionProvider> dispatcher = new CommandDispatcher<>();
		dispatcher.register(command());
		return dispatcher;
	}

	/**
	 * Builds {@code gamemode @c} and {@code gamemode <gamemode> @c}. Literals take precedence over arguments, so
	 * {@code @c} is never read as a game mode or a target selector.
	 */
	private static LiteralArgumentBuilder<ClientSuggestionProvider> command() {
		return LiteralArgumentBuilder.<ClientSuggestionProvider>literal(GAMEMODE)
				.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal(CLIENT_TARGET)
						.executes(c -> showStatus(player())))
				.then(RequiredArgumentBuilder.<ClientSuggestionProvider, GameType>argument(GAMEMODE_ARGUMENT, GameModeArgument.gameMode())
						.then(LiteralArgumentBuilder.<ClientSuggestionProvider>literal(CLIENT_TARGET)
								.executes(c -> select(player(), c.getArgument(GAMEMODE_ARGUMENT, GameType.class)))));
	}

	private static int select(final LocalPlayer player, final GameType gameMode) throws CommandSyntaxException {
		GameModeController.Selection selection = GameModeClient.clientGameMode().select(Minecraft.getInstance(), gameMode);
		switch (selection) {
			case BLOCKED -> throw BLOCKED.create();
			case TURNED_OFF -> throw TURNED_OFF.create();
			case CLIENT, SERVER -> player.sendSystemMessage(ClientGameMode.feedback(selection, gameMode));
		}
		return 1;
	}

	/**
	 * Shows the client and server game modes, and either clickable game modes or why the client game mode cannot be
	 * changed here. Changes nothing, so it works everywhere.
	 */
	private static int showStatus(final LocalPlayer player) {
		GameModeController controller = GameModeClient.controller();
		GameType server = controller.serverGameMode();
		GameType client = controller.clientGameMode();
		Component serverName = server != null ? server.getLongDisplayName() : CommonComponents.EMPTY;
		player.sendSystemMessage(client != null
				? Component.translatable(STATUS_CLIENT_KEY, client.getLongDisplayName(), serverName)
				: Component.translatable(STATUS_SERVER_KEY, serverName));

		Component unavailable = GameModeClient.unavailableMessage(controller.availability());
		if (unavailable != null) {
			player.sendSystemMessage(unavailable.copy().withStyle(ChatFormatting.GRAY));
			return 1;
		}
		MutableComponent modes = Component.empty();
		for (GameType gameMode : GameType.values()) {
			if (!modes.getSiblings().isEmpty()) {
				modes.append(CommonComponents.SPACE);
			}
			modes.append(modeLink(gameMode));
		}
		player.sendSystemMessage(Component.translatable(STATUS_SWITCH_KEY, modes));
		return 1;
	}

	private static Component modeLink(final GameType gameMode) {
		String command = "/" + GAMEMODE + " " + gameMode.getName() + " " + CLIENT_TARGET;
		return ComponentUtils.wrapInSquareBrackets(gameMode.getShortDisplayName())
				.withStyle(style -> style
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand(command))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private static LocalPlayer player() throws CommandSyntaxException {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			throw NOT_IN_WORLD.create();
		}
		return player;
	}
}
