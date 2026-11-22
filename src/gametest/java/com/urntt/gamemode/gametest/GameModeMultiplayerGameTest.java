package com.urntt.gamemode.gametest;

import static com.urntt.gamemode.gametest.GameTestSupport.LOGGER;
import static com.urntt.gamemode.gametest.GameTestSupport.check;
import static com.urntt.gamemode.gametest.GameTestSupport.checkClientUses;
import static com.urntt.gamemode.gametest.GameTestSupport.clearMessages;
import static com.urntt.gamemode.gametest.GameTestSupport.clientGameMode;
import static com.urntt.gamemode.gametest.GameTestSupport.configure;
import static com.urntt.gamemode.gametest.GameTestSupport.listenForMessages;
import static com.urntt.gamemode.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.gamemode.gametest.GameTestSupport.requireNoMessageWith;
import static com.urntt.gamemode.gametest.GameTestSupport.serverGameMode;
import static com.urntt.gamemode.gametest.GameTestSupport.typeCommand;

import com.urntt.gamemode.ClientGameMode;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.Scene;
import com.urntt.gamemode.command.ClientGameModeCommands;
import com.urntt.gamemode.config.ConfigMode;
import com.urntt.gamemode.config.MultiplayerMode;
import com.urntt.gamemode.switcher.ClientGameModeSwitcherScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.level.GameType;

/**
 * Checks the mod on a local dedicated server, which the client reaches as {@code localhost} without operator
 * permissions: the disabled default, the command tree the mod adds without permission for {@code /gamemode}, and the
 * whitelist, including a client game mode that ends as soon as the server is ruled out.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GameModeMultiplayerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		configure(context, config -> {
			config.setMode(ConfigMode.CUSTOM);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set day");
			context.waitFor(client -> client.player.onGround() && client.gameMode.getPlayerMode() == GameType.SURVIVAL);
			Scene scene = context.computeOnClient(client -> GameModeClient.controller().scene());
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			checkCommandTreeWithoutPermission(context);
			checkDisabledByDefault(context, server);
			checkWhitelisted(context, server);
			checkWithPermission(context, server);
		}

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
	}

	/**
	 * Without permission the server sends no {@code /gamemode}, so the mod adds its own for suggestions.
	 */
	private static void checkCommandTreeWithoutPermission(final ClientGameTestContext context) {
		List<String> children = GameTestSupport.gamemodeCommandTree(context);
		LOGGER.info("Command tree without permission: {}", children);
		check(children.equals(List.of("@c,gamemode", "@c")), "the mod should add /gamemode @c and /gamemode <gamemode> @c, got " + children);
	}

	/**
	 * As an operator, the mod's nodes are merged into the server's {@code /gamemode}, whose own nodes stay, and
	 * vanilla {@code /gamemode} changes the real game mode, which ends the client game mode.
	 */
	private static void checkWithPermission(final ClientGameTestContext context, final TestDedicatedServerContext server) {
		configure(context, config -> config.setServers(List.of("localhost")));
		String name = context.computeOnClient(client -> client.player.getGameProfile().name());
		server.runCommand("op " + name);
		context.waitFor(client -> GameTestSupport.gamemodeCommandTree(client).equals(List.of("@c,gamemode", "@c,target")), 100);
		LOGGER.info("Command tree as an operator: {}", GameTestSupport.gamemodeCommandTree(context));

		typeCommand(context, "gamemode spectator @c");
		check(clientGameMode(context) == GameType.SPECTATOR, "client spectator should be on before the vanilla command");
		check(serverGameMode(server) == GameType.SURVIVAL, "the server should keep survival");
		clearMessages(context);
		typeCommand(context, "gamemode creative");
		context.waitFor(client -> client.gameMode.getPlayerMode() == GameType.CREATIVE, 100);
		check(serverGameMode(server) == GameType.CREATIVE, "vanilla /gamemode should change the server's game mode");
		check(clientGameMode(context) == null, "the server's change should end the client game mode");
		requireMessageWith(context, ClientGameMode.SERVER_CHANGED_KEY);
		checkClientUses(context, GameType.CREATIVE, "after vanilla /gamemode");

		typeCommand(context, "gamemode survival");
		context.waitFor(client -> client.gameMode.getPlayerMode() == GameType.SURVIVAL, 100);
		server.runCommand("deop " + name);
		context.waitFor(client -> GameTestSupport.gamemodeCommandTree(client).equals(List.of("@c,gamemode", "@c")), 100);
	}

	/**
	 * With the default multiplayer mode, nothing changes the client game mode and nothing reaches the server, while
	 * the status still works.
	 */
	private static void checkDisabledByDefault(final ClientGameTestContext context, final TestDedicatedServerContext server) {
		clearMessages(context);
		typeCommand(context, "gamemode creative @c");
		requireMessageWith(context, "message.gamemode.blocked");
		context.waitTicks(10);
		checkClientUses(context, GameType.SURVIVAL, "on a disabled server");
		check(clientGameMode(context) == null, "a disabled server should not allow a client game mode");
		requireNoMessageWith(context, "command.unknown.command");
		requireNoMessageWith(context, "argument.entity.selector.unknown");

		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().holdKey(options -> options.keyDebugModifier);
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		context.getInput().releaseKey(options -> options.keyDebugModifier);
		check(context.computeOnClient(client -> !(client.gui.screen() instanceof ClientGameModeSwitcherScreen)),
				"a disabled server should not open the switcher");
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.FIRST_PERSON),
				"F3 + F5 should not change the perspective on a disabled server");

		context.takeScreenshot("gamemode-blocked-on-server");

		clearMessages(context);
		typeCommand(context, "gamemode @c");
		requireMessageWith(context, ClientGameModeCommands.STATUS_SERVER_KEY);
		requireMessageWith(context, "message.gamemode.blocked");
		check(serverGameMode(server) == GameType.SURVIVAL, "the server's game mode should not change");
	}

	/**
	 * On a whitelisted server, the client game mode changes while the server keeps survival. Ruling the server out
	 * ends the client game mode at once.
	 */
	private static void checkWhitelisted(final ClientGameTestContext context, final TestDedicatedServerContext server) {
		configure(context, config -> {
			config.setServers(List.of("localhost"));
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		});

		clearMessages(context);
		typeCommand(context, "gamemode creative @c");
		requireMessageWith(context, ClientGameMode.SET_KEY);
		checkClientUses(context, GameType.CREATIVE, "on a whitelisted server");
		check(context.computeOnClient(client -> client.player.getAbilities().mayfly), "client creative should allow flight on the client");
		context.waitTicks(10);
		check(serverGameMode(server) == GameType.SURVIVAL, "the server should keep survival");
		check(!server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getAbilities().mayfly),
				"the server should not grant flight");
		requireNoMessageWith(context, "command.unknown.command");

		typeCommand(context, "gamemode survival @c");
		check(clientGameMode(context) == null, "the server's game mode should end the client game mode");

		context.getInput().holdKey(options -> options.keyDebugModifier);
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		GameType selected = context.computeOnClient(client -> client.gui.screen() instanceof ClientGameModeSwitcherScreen switcher
				? switcher.selected() : null);
		check(selected != null && selected != GameType.SURVIVAL, "a whitelisted server should open the switcher on another game mode, got " + selected);
		context.takeScreenshot("gamemode-switcher-on-server");
		context.getInput().releaseKey(options -> options.keyDebugModifier);
		context.waitTick();
		check(clientGameMode(context) == selected, "the switcher should set the client game mode");
		checkClientUses(context, selected, "after the switcher on a whitelisted server");
		check(serverGameMode(server) == GameType.SURVIVAL, "the server should keep survival");

		configure(context, config -> config.setServers(List.of("other.example.com")));
		context.waitTick();
		check(clientGameMode(context) == null, "ruling the server out should end the client game mode");
		checkClientUses(context, GameType.SURVIVAL, "after the server was ruled out");
		check(!context.computeOnClient(client -> client.player.getAbilities().mayfly), "the server's abilities should be restored");
	}
}
