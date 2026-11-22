package com.urntt.gamemode.gametest;

import static com.urntt.gamemode.gametest.GameTestSupport.LOGGER;
import static com.urntt.gamemode.gametest.GameTestSupport.bindKey;
import static com.urntt.gamemode.gametest.GameTestSupport.check;
import static com.urntt.gamemode.gametest.GameTestSupport.checkClientUses;
import static com.urntt.gamemode.gametest.GameTestSupport.clearMessages;
import static com.urntt.gamemode.gametest.GameTestSupport.clickCommand;
import static com.urntt.gamemode.gametest.GameTestSupport.clickCommands;
import static com.urntt.gamemode.gametest.GameTestSupport.clientGameMode;
import static com.urntt.gamemode.gametest.GameTestSupport.configure;
import static com.urntt.gamemode.gametest.GameTestSupport.listenForMessages;
import static com.urntt.gamemode.gametest.GameTestSupport.requireMessageWith;
import static com.urntt.gamemode.gametest.GameTestSupport.requireNoMessageWith;
import static com.urntt.gamemode.gametest.GameTestSupport.serverGameMode;
import static com.urntt.gamemode.gametest.GameTestSupport.typeCommand;
import static com.urntt.gamemode.gametest.GameTestSupport.unbindKey;

import com.urntt.gamemode.ClientGameMode;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.command.ClientGameModeCommands;
import com.urntt.gamemode.config.ConfigMode;
import com.urntt.gamemode.config.GameModeConfigScreen;
import com.urntt.gamemode.switcher.ClientGameModeSwitcherScreen;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Checks client game modes in a singleplayer world: what each game mode changes on the client while the integrated
 * server keeps the real one, the commands and their feedback, the switcher, the server-change rule, the key bindings,
 * Disabled mode, leaving the world, and the settings key.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GameModeSingleplayerGameTest implements FabricClientGameTest {
	/** How long the jump key is held to fly up, in ticks. */
	private static final int FLIGHT_TICKS = 10;
	/** How long the forward key is held to fly into a wall, in ticks. */
	private static final int NOCLIP_TICKS = 30;
	/** Half the width of a player's bounding box. */
	private static final double PLAYER_HALF_WIDTH = 0.3;

	@Override
	public void runTest(final ClientGameTestContext context) {
		listenForMessages(context);
		KeyMapping openSettingsKey = bindKey(context, GameModeClient.OPEN_SETTINGS_KEY_NAME, "key.keyboard.k");
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("gamemode survival @a");
			server.runCommand("time set day");
			context.waitFor(client -> client.player != null && client.player.onGround() && client.gameMode.getPlayerMode() == GameType.SURVIVAL);
			check(clientGameMode(context) == null, "a new world should start without a client game mode");

			checkCreative(context, server);
			checkSpectator(context, server);
			checkBackToServerGameMode(context, server);
			checkDimensionChange(context, server, singleplayer);
			checkServerChanges(context, server);
			checkStatusAndClicks(context);
			checkCommandTree(context);
			checkVanillaCommandsReachServer(context, server);
			checkSwitcher(context);
			checkDisabledMode(context);
			checkSettingsKey(context, openSettingsKey);

			typeCommand(context, "gamemode creative @c");
			check(clientGameMode(context) == GameType.CREATIVE, "the world should be left in client creative");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.waitFor(client -> client.player != null);
			check(clientGameMode(context) == null, "leaving the world should end the client game mode");
			GameType serverGameMode = serverGameMode(singleplayer.getServer());
			checkClientUses(context, serverGameMode, "a new world");
		}

		unbindKey(context, openSettingsKey);
	}

	/**
	 * Client creative gives the client creative's abilities and lets the player fly, while the server keeps survival
	 * and never grants flight.
	 */
	private static void checkCreative(final ClientGameTestContext context, final TestServerContext server) {
		clearMessages(context);
		typeCommand(context, "gamemode creative @c");
		requireMessageWith(context, ClientGameMode.SET_KEY);
		checkClientUses(context, GameType.CREATIVE, "client creative");
		check(clientGameMode(context) == GameType.CREATIVE, "creative should be the client game mode");
		check(context.computeOnClient(client -> client.player.isCreative()), "the player should be creative on the client");
		Abilities abilities = clientAbilities(context);
		check(abilities.mayfly && abilities.instabuild && abilities.invulnerable, "client creative should grant creative's abilities");
		check(serverGameMode(server) == GameType.SURVIVAL, "the server should keep survival");
		requireNoMessageWith(context, "gameMode.changed");

		double startY = context.computeOnClient(client -> client.player.getY());
		setFlying(context, true);
		context.getInput().holdKeyFor(options -> options.keyJump, FLIGHT_TICKS);
		double flownY = context.computeOnClient(client -> client.player.getY());
		LOGGER.info("Client creative flight: y {} -> {} after holding jump for {} ticks", startY, flownY, FLIGHT_TICKS);
		check(flownY > startY + 0.5, "a client creative player should fly up, got y " + startY + " -> " + flownY);
		check(!serverAbilities(server).mayfly, "the server should never grant flight");
		setFlying(context, false);
		context.waitFor(client -> client.player.onGround(), 100);
	}

	/**
	 * Client spectator removes collisions on the client, so the player flies into a wall that a survival player could
	 * not enter, while the integrated server keeps the survival player out of it.
	 */
	private static void checkSpectator(final ClientGameTestContext context, final TestServerContext server) {
		BlockPos origin = context.computeOnClient(client -> client.player.blockPosition());
		BlockPos wall = origin.east(2);
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
				wall.getX(), wall.getY(), wall.getZ() - 1, wall.getX(), wall.getY() + 2, wall.getZ() + 1));
		context.waitFor(client -> client.level.getBlockState(wall).is(Blocks.STONE));

		typeCommand(context, "gamemode spectator @c");
		checkClientUses(context, GameType.SPECTATOR, "client spectator");
		context.waitTick();
		check(context.computeOnClient(client -> client.player.isSpectator() && client.player.noPhysics
				&& client.player.getAbilities().flying), "client spectator should fly without collisions");
		check(serverGameMode(server) == GameType.SURVIVAL, "the server should keep survival");

		context.getInput().lookAt(-90.0F, 0.0F);
		context.getInput().holdKey(options -> options.keyUp);
		double maxX = Double.NEGATIVE_INFINITY;
		for (int tick = 0; tick < NOCLIP_TICKS; tick++) {
			context.waitTick();
			maxX = Math.max(maxX, context.computeOnClient(client -> client.player.getX()));
		}
		context.getInput().releaseKey(options -> options.keyUp);
		double wallFace = wall.getX() - PLAYER_HALF_WIDTH;
		Vec3 onServer = server.computeOnServer(s -> player(s.getPlayerList().getPlayers()).position());
		LOGGER.info("Client spectator reached x {} (wall face for the player's center: {}); the server keeps the player at {}",
				maxX, wallFace, onServer);
		check(maxX > wallFace + 1.0E-3, "a client spectator should fly into the wall, got x " + maxX);
		check(onServer.x <= wallFace + 1.0E-3, "the server should keep the survival player out of the wall, got " + onServer);

		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air",
				wall.getX(), wall.getY(), wall.getZ() - 1, wall.getX(), wall.getY() + 2, wall.getZ() + 1));
		context.waitFor(client -> client.level.getBlockState(wall).isAir());
	}

	/**
	 * Choosing the server's game mode ends the client game mode and restores the abilities the server sent.
	 */
	private static void checkBackToServerGameMode(final ClientGameTestContext context, final TestServerContext server) {
		clearMessages(context);
		typeCommand(context, "gamemode survival @c");
		requireMessageWith(context, ClientGameMode.SERVER_KEY);
		check(clientGameMode(context) == null, "choosing the server's game mode should end the client game mode");
		checkClientUses(context, GameType.SURVIVAL, "back to the server's game mode");
		Abilities abilities = clientAbilities(context);
		check(!abilities.mayfly && !abilities.flying && !abilities.instabuild && !abilities.invulnerable,
				"the server's survival abilities should be restored");
		context.waitFor(client -> client.player.onGround(), 100);
	}

	/**
	 * A dimension change respawns the player in the same server game mode, which keeps the client game mode, and the
	 * abilities the server sends with it are adjusted to the client game mode.
	 */
	private static void checkDimensionChange(final ClientGameTestContext context, final TestServerContext server,
			final TestSingleplayerContext singleplayer) {
		Vec3 home = context.computeOnClient(client -> client.player.position());
		typeCommand(context, "gamemode creative @c");
		clearMessages(context);
		// The top of the nether's bedrock roof is a safe place to stand.
		server.runCommand("execute in minecraft:the_nether run tp @a 0.5 128 0.5");
		context.waitFor(client -> client.level != null && client.player != null && client.level.dimension() == Level.NETHER);
		context.waitTicks(10);
		check(clientGameMode(context) == GameType.CREATIVE, "a dimension change should keep the client game mode");
		checkClientUses(context, GameType.CREATIVE, "after a dimension change");
		Abilities abilities = clientAbilities(context);
		check(abilities.mayfly && abilities.instabuild, "the server's abilities should be adjusted to client creative");
		check(!serverAbilities(server).mayfly, "the server should still not grant flight");
		requireNoMessageWith(context, ClientGameMode.SERVER_CHANGED_KEY);

		server.runCommand(String.format(Locale.ROOT, "execute in minecraft:overworld run tp @a %.3f %.3f %.3f", home.x, home.y, home.z));
		context.waitFor(client -> client.level != null && client.player != null && client.level.dimension() == Level.OVERWORLD);
		singleplayer.getConnection().waitForChunksRender();
		check(clientGameMode(context) == GameType.CREATIVE, "returning to the overworld should keep the client game mode");
		typeCommand(context, "gamemode survival @c");
		context.waitFor(client -> client.player.onGround(), 100);
	}

	/**
	 * With "follow server changes" on, a game mode change by the server ends the client game mode; with it off, the
	 * client game mode stays.
	 */
	private static void checkServerChanges(final ClientGameTestContext context, final TestServerContext server) {
		typeCommand(context, "gamemode creative @c");
		clearMessages(context);
		server.runCommand("gamemode adventure @a");
		context.waitFor(client -> GameModeClient.controller().clientGameMode() == null);
		requireMessageWith(context, ClientGameMode.SERVER_CHANGED_KEY);
		checkClientUses(context, GameType.ADVENTURE, "after the server changed the game mode");

		configure(context, config -> config.setFollowServerChanges(false));
		typeCommand(context, "gamemode creative @c");
		clearMessages(context);
		server.runCommand("gamemode survival @a");
		context.waitFor(client -> GameModeClient.controller().serverGameMode() == GameType.SURVIVAL);
		context.waitTicks(5);
		check(clientGameMode(context) == GameType.CREATIVE, "without following, the client game mode should stay");
		checkClientUses(context, GameType.CREATIVE, "after an ignored server change");
		check(clientAbilities(context).mayfly, "the server's survival abilities should be adjusted to client creative");
		requireNoMessageWith(context, ClientGameMode.SERVER_CHANGED_KEY);
		configure(context, config -> config.setFollowServerChanges(true));

		typeCommand(context, "gamemode survival @c");
		checkClientUses(context, GameType.SURVIVAL, "back to the server's game mode");
	}

	/**
	 * {@code /gamemode @c} shows both game modes and links that run the client command, which works when clicked.
	 */
	private static void checkStatusAndClicks(final ClientGameTestContext context) {
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		clearMessages(context);
		typeCommand(context, "gamemode @c");
		requireMessageWith(context, ClientGameModeCommands.STATUS_SERVER_KEY);
		Component links = requireMessageWith(context, ClientGameModeCommands.STATUS_SWITCH_KEY);
		List<String> commands = clickCommands(links);
		check(commands.equals(List.of("/gamemode survival @c", "/gamemode creative @c", "/gamemode adventure @c", "/gamemode spectator @c")),
				"the status should link every game mode, got " + commands);

		clickCommand(context, "/gamemode adventure @c");
		check(clientGameMode(context) == GameType.ADVENTURE, "a clicked link should set the client game mode");
		typeCommand(context, "gamemode @c");
		Component status = requireMessageWith(context, ClientGameModeCommands.STATUS_CLIENT_KEY);
		LOGGER.info("Status: {}", status.getString());
		context.takeScreenshot("gamemode-status");

		clearMessages(context);
		typeCommand(context, "gamemode @c creative");
		requireMessageWith(context, "command.unknown.argument");
		check(clientGameMode(context) == GameType.ADVENTURE, "the other argument order should not be accepted");
		typeCommand(context, "gamemode survival @c");
	}

	/**
	 * The test world does not allow cheats, so the server sends no {@code /gamemode} and the mod adds its own for
	 * suggestions. The multiplayer test checks the merge with the server's node.
	 */
	private static void checkCommandTree(final ClientGameTestContext context) {
		List<String> children = GameTestSupport.gamemodeCommandTree(context);
		LOGGER.info("Command tree without permission: {}", children);
		check(children.equals(List.of("@c,gamemode", "@c")), "the mod should add /gamemode @c and /gamemode <gamemode> @c, got " + children);
	}

	/**
	 * Without {@code @c}, {@code /gamemode} goes to the server, which refuses it without permission.
	 */
	private static void checkVanillaCommandsReachServer(final ClientGameTestContext context, final TestServerContext server) {
		clearMessages(context);
		typeCommand(context, "gamemode creative");
		context.waitFor(client -> GameTestSupport.receivedOnClient("command.unknown.command"), 100);
		check(serverGameMode(server) == GameType.SURVIVAL, "vanilla /gamemode without permission should not change the game mode");
		check(clientGameMode(context) == null, "vanilla /gamemode should not set a client game mode");
		checkClientUses(context, GameType.SURVIVAL, "after vanilla /gamemode without permission");
	}

	/**
	 * F5 alone keeps changing the perspective. With F3 held, it opens the client switcher instead, which cycles like
	 * vanilla's and applies the selection when F3 is released, without toggling the debug overlay.
	 */
	private static void checkSwitcher(final ClientGameTestContext context) {
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.getInput().pressKey(options -> options.keyTogglePerspective);
		context.waitTick();
		check(context.computeOnClient(client -> client.options.getCameraType() != CameraType.FIRST_PERSON && client.gui.screen() == null),
				"F5 alone should still change the perspective");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// Leave creative as the previous game mode, which the switcher preselects like vanilla's.
		typeCommand(context, "gamemode creative @c");
		typeCommand(context, "gamemode survival @c");
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		clearMessages(context);

		context.getInput().holdKey(options -> options.keyDebugModifier);
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		check(context.computeOnClient(client -> client.gui.screen() instanceof ClientGameModeSwitcherScreen),
				"F3 + F5 should open the client game mode switcher");
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.FIRST_PERSON),
				"F3 + F5 should not change the perspective");
		check(selected(context) == GameType.CREATIVE, "the switcher should preselect the previous game mode");
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		check(selected(context) == GameType.SURVIVAL, "the switcher key should select the next game mode");
		context.takeScreenshot("gamemode-switcher-server");
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		check(selected(context) == GameType.ADVENTURE, "the switcher key should select the next game mode");
		context.takeScreenshot("gamemode-switcher-client");
		context.getInput().releaseKey(options -> options.keyDebugModifier);
		context.waitTick();

		check(context.computeOnClient(client -> client.gui.screen() == null), "releasing F3 should close the switcher");
		check(clientGameMode(context) == GameType.ADVENTURE, "releasing F3 should apply the selection");
		requireMessageWith(context, ClientGameMode.SET_KEY);
		check(!context.computeOnClient(client -> client.debugEntries.isOverlayVisible()), "releasing F3 should not open the debug overlay");
		typeCommand(context, "gamemode survival @c");
	}

	/**
	 * Disabled mode ends the client game mode at once; while it applies, the commands and the switcher only report it,
	 * and the status still works.
	 */
	private static void checkDisabledMode(final ClientGameTestContext context) {
		typeCommand(context, "gamemode creative @c");
		check(clientGameMode(context) == GameType.CREATIVE, "client creative should be on before Disabled mode");
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		configure(context, config -> config.setMode(ConfigMode.DISABLED));
		context.waitTick();
		check(clientGameMode(context) == null, "Disabled mode should end the client game mode");
		checkClientUses(context, GameType.SURVIVAL, "in Disabled mode");
		check(!clientAbilities(context).mayfly, "Disabled mode should restore the server's abilities");

		clearMessages(context);
		typeCommand(context, "gamemode creative @c");
		requireMessageWith(context, "message.gamemode.disabled");
		check(clientGameMode(context) == null, "Disabled mode should not set a client game mode");
		requireNoMessageWith(context, "command.unknown.command");

		context.getInput().holdKey(options -> options.keyDebugModifier);
		context.getInput().pressKey(GameModeClient.switcherKey());
		context.waitTick();
		context.getInput().releaseKey(options -> options.keyDebugModifier);
		check(context.computeOnClient(client -> client.gui.screen() == null), "Disabled mode should not open the switcher");
		check(context.computeOnClient(client -> client.options.getCameraType() == CameraType.FIRST_PERSON),
				"F3 + F5 should not change the perspective in Disabled mode");

		clearMessages(context);
		typeCommand(context, "gamemode @c");
		requireMessageWith(context, ClientGameModeCommands.STATUS_SERVER_KEY);
		requireMessageWith(context, "message.gamemode.disabled");
		context.takeScreenshot("gamemode-disabled");

		configure(context, config -> config.setMode(ConfigMode.CUSTOM));
		typeCommand(context, "gamemode creative @c");
		check(clientGameMode(context) == GameType.CREATIVE, "custom mode should allow client game modes again");
		typeCommand(context, "gamemode survival @c");
	}

	/**
	 * The settings key opens the configuration screen. {@code GameModeConfigScreenGameTest} checks the screen itself.
	 */
	private static void checkSettingsKey(final ClientGameTestContext context, final KeyMapping openSettingsKey) {
		context.getInput().pressKey(openSettingsKey);
		context.waitForScreen(GameModeConfigScreen.class);
		context.setScreen(() -> null);
	}

	private static GameType selected(final ClientGameTestContext context) {
		return context.computeOnClient(client -> ((ClientGameModeSwitcherScreen) client.gui.screen()).selected());
	}

	private static void setFlying(final ClientGameTestContext context, final boolean flying) {
		context.runOnClient(client -> {
			client.player.getAbilities().flying = flying;
			client.player.onUpdateAbilities();
		});
	}

	/** A copy of the local player's abilities. */
	private static Abilities clientAbilities(final ClientGameTestContext context) {
		return context.computeOnClient(client -> copy(client.player.getAbilities()));
	}

	/** A copy of the server player's abilities. */
	private static Abilities serverAbilities(final TestServerContext server) {
		return server.computeOnServer(s -> copy(player(s.getPlayerList().getPlayers()).getAbilities()));
	}

	private static ServerPlayer player(final List<ServerPlayer> players) {
		return players.getFirst();
	}

	private static Abilities copy(final Abilities abilities) {
		Abilities copy = new Abilities();
		copy.flying = abilities.flying;
		copy.mayfly = abilities.mayfly;
		copy.instabuild = abilities.instabuild;
		copy.invulnerable = abilities.invulnerable;
		copy.mayBuild = abilities.mayBuild;
		return copy;
	}
}
