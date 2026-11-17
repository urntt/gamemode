package com.urntt.gamemode.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.tree.CommandNode;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.config.GameModeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("gamemode-gametest");

	/** Game messages the client received since the last {@link #clearMessages}, including the mod's own feedback. */
	private static final List<Component> MESSAGES = new ArrayList<>();
	private static boolean listening;

	private GameTestSupport() {
	}

	/**
	 * Starts collecting game messages. Call it once per test class before relying on {@link #messages}.
	 */
	static void listenForMessages(final ClientGameTestContext context) {
		context.runOnClient(client -> {
			if (!listening) {
				listening = true;
				ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add(message));
			}
			MESSAGES.clear();
		});
	}

	static void clearMessages(final ClientGameTestContext context) {
		context.runOnClient(client -> MESSAGES.clear());
	}

	static List<Component> messages(final ClientGameTestContext context) {
		return context.computeOnClient(client -> List.copyOf(MESSAGES));
	}

	/**
	 * Returns the newest received message that uses the translation key {@code key} anywhere inside it.
	 */
	static Optional<Component> lastMessageWith(final ClientGameTestContext context, final String key) {
		List<Component> messages = messages(context);
		for (int i = messages.size() - 1; i >= 0; i--) {
			if (usesKey(messages.get(i), key)) {
				return Optional.of(messages.get(i));
			}
		}
		return Optional.empty();
	}

	static Component requireMessageWith(final ClientGameTestContext context, final String key) {
		return lastMessageWith(context, key).orElseThrow(() -> new AssertionError(
				"expected a message with " + key + ", got " + messageTexts(context)));
	}

	static void requireNoMessageWith(final ClientGameTestContext context, final String key) {
		check(lastMessageWith(context, key).isEmpty(), "expected no message with " + key + ", got " + messageTexts(context));
	}

	/**
	 * Returns whether a message with {@code key} arrived; call it on the client thread, for example in
	 * {@code context.waitFor}.
	 */
	static boolean receivedOnClient(final String key) {
		return MESSAGES.stream().anyMatch(message -> usesKey(message, key));
	}

	static List<String> messageTexts(final ClientGameTestContext context) {
		return messages(context).stream().map(Component::getString).toList();
	}

	static boolean usesKey(final Component component, final String key) {
		if (component.getContents() instanceof TranslatableContents translatable) {
			if (translatable.getKey().equals(key)) {
				return true;
			}
			for (Object arg : translatable.getArgs()) {
				if (arg instanceof Component argument && usesKey(argument, key)) {
					return true;
				}
			}
		}
		for (Component sibling : component.getSiblings()) {
			if (usesKey(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the commands that clicking parts of {@code component} would run.
	 */
	static List<String> clickCommands(final Component component) {
		List<String> commands = new ArrayList<>();
		component.visit((Style style, String text) -> {
			if (style.getClickEvent() instanceof ClickEvent.RunCommand run && !commands.contains(run.command())) {
				commands.add(run.command());
			}
			return Optional.empty();
		}, Style.EMPTY);
		return commands;
	}

	/**
	 * Sends {@code command} the way the chat screen does when the player types it.
	 */
	static void typeCommand(final ClientGameTestContext context, final String command) {
		LOGGER.info("Typed command: /{}", command);
		context.runOnClient(client -> client.player.connection.sendCommand(command));
	}

	/**
	 * Sends {@code command} the way a click on a chat component that runs it does.
	 */
	static void clickCommand(final ClientGameTestContext context, final String command) {
		LOGGER.info("Clicked command: {}", command);
		context.runOnClient(client -> client.player.connection.sendUnattendedCommand(Commands.trimOptionalPrefix(command), null));
	}

	/**
	 * Returns the names of the children of {@code /gamemode} and of {@code /gamemode <gamemode>} in the client's
	 * command tree, sorted and joined with commas, or an empty list without {@code /gamemode}.
	 */
	static List<String> gamemodeCommandTree(final ClientGameTestContext context) {
		return context.computeOnClient(GameTestSupport::gamemodeCommandTree);
	}

	/** {@link #gamemodeCommandTree(ClientGameTestContext)} on the client thread. */
	static List<String> gamemodeCommandTree(final Minecraft client) {
		CommandNode<ClientSuggestionProvider> gamemode = client.getConnection().getCommands().getRoot().getChild("gamemode");
		if (gamemode == null) {
			return List.of();
		}
		CommandNode<ClientSuggestionProvider> argument = gamemode.getChild("gamemode");
		return List.of(childNames(gamemode), argument == null ? "" : childNames(argument));
	}

	private static String childNames(final CommandNode<ClientSuggestionProvider> node) {
		return String.join(",", node.getChildren().stream().map(CommandNode::getName).sorted().toList());
	}

	/** The game mode the client uses: the local game mode of {@code MultiPlayerGameMode}. */
	static GameType localGameMode(final ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gameMode.getPlayerMode());
	}

	/** The local player's own game mode, which {@code isSpectator()} and {@code isCreative()} read. */
	static @Nullable GameType playerGameMode(final ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.gameMode());
	}

	static @Nullable GameType clientGameMode(final ClientGameTestContext context) {
		return context.computeOnClient(client -> GameModeClient.controller().clientGameMode());
	}

	/** The game mode the server uses for the (only) player. */
	static GameType serverGameMode(final TestServerContext server) {
		return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().gameMode());
	}

	/**
	 * Checks that the client uses {@code gameMode} everywhere it reads its own game mode.
	 */
	static void checkClientUses(final ClientGameTestContext context, final GameType gameMode, final String what) {
		check(localGameMode(context) == gameMode, what + ": the local game mode should be " + gameMode + ", got " + localGameMode(context));
		check(playerGameMode(context) == gameMode, what + ": the player's game mode should be " + gameMode + ", got " + playerGameMode(context));
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<GameModeConfig> change) {
		context.runOnClient(client -> change.accept(GameModeClient.config()));
	}

	static GameModeConfig loadSavedConfig() {
		return GameModeConfig.load(GameModeConfig.defaultPath());
	}

	static void switchLanguage(final ClientGameTestContext context, final String language) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}

	static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
