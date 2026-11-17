package com.urntt.gamemode.gametest;

import static com.urntt.gamemode.gametest.GameTestSupport.check;

import com.urntt.gamemode.GameModeController;
import com.urntt.gamemode.GameModeController.Availability;
import com.urntt.gamemode.GameModeController.Selection;
import com.urntt.gamemode.GameModeController.ServerChange;
import com.urntt.gamemode.Scene;
import com.urntt.gamemode.ServerAddresses;
import com.urntt.gamemode.command.ClientGameModeCommands;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.MultiplayerMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.GameType;

/**
 * Checks the address matching, the configuration, the command detection and the controller's rules without a world.
 * Each check uses its own configuration file, so the game's configuration is left alone. Restarting the game is not
 * possible in a game test, so this is also where "reset on game exit" is covered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GameModeLogicGameTest implements FabricClientGameTest {
	private static final Scene SERVER_A = Scene.multiplayer("a.example.com");
	private static final Scene SERVER_B = Scene.multiplayer("b.example.com:25570");
	private static final Scene UNKNOWN_SERVER = Scene.multiplayer(null);

	@Override
	public void runTest(final ClientGameTestContext context) {
		Path directory = createTempDirectory();
		checkGameConfigDefaults();
		checkAddressMatching();
		checkConfigFile(directory);
		checkCommandDetection();
		checkSelection(directory);
		checkServerChanges(directory);
		checkTurnedOff(directory);
		checkMultiplayerRules(directory);
		checkResetRules(directory);
		GameTestSupport.LOGGER.info("Logic checks passed");
	}

	/**
	 * Loom deletes the run directory before each run, so the game's own configuration file holds the defaults.
	 */
	private static void checkGameConfigDefaults() {
		GameModeConfig config = GameTestSupport.loadSavedConfig();
		checkDefaults(config, "the game's configuration");
	}

	private static void checkDefaults(final GameModeConfig config, final String what) {
		check(config.isEnabled(), what + ": the mod should be on by default");
		check(config.followServerChanges(), what + ": following server changes should be on by default");
		check(config.singleplayerDefault(), what + ": the singleplayer default should be on");
		check(config.multiplayerDefault(), what + ": the server default should be on");
		check(!config.resetOnWorldExit(), what + ": reset on world exit should be off by default");
		check(!config.resetOnGameExit(), what + ": reset on game exit should be off by default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, what + ": multiplayer should be disabled by default");
		check(config.servers().isEmpty(), what + ": the server list should be empty by default");
	}

	private static void checkAddressMatching() {
		checkMatch("mc.example.com", "mc.example.com", true);
		checkMatch("MC.Example.com", "mc.example.COM:25565", true);
		checkMatch("mc.example.com", "mc.example.com:25566", true);
		checkMatch("mc.example.com:25565", "mc.example.com", true);
		checkMatch("mc.example.com:25566", "mc.example.com", false);
		checkMatch("mc.example.com", "play.example.com", false);
		checkMatch("  mc.example.com  ", "mc.example.com", true);
		checkMatch("192.168.1.5", "192.168.1.5:51234", true);
		checkMatch("[::1]:25565", "[::1]", true);
		checkMatch("::1", "[::1]:25570", true);
		checkMatch("bücher.example", "xn--bcher-kva.example", true);
		checkMatch("localhost", "localhost:41234", true);

		for (String valid : List.of("mc.example.com:25566", "localhost", "192.168.1.5", "[::1]:25565", "bücher.example")) {
			check(ServerAddresses.isValid(valid), "'" + valid + "' should be valid");
		}
		for (String invalid : List.of("", "   ", "mc.example.com:abc", "mc.example.com:99999", "[::1", "mc example.com",
				"not a host:port:x")) {
			check(!ServerAddresses.isValid(invalid), "'" + invalid + "' should be invalid");
		}
	}

	private static void checkMatch(final String entry, final String address, final boolean expected) {
		check(ServerAddresses.matches(entry, address) == expected,
				"'" + entry + "' should " + (expected ? "" : "not ") + "match '" + address + "'");
	}

	private static void checkConfigFile(final Path directory) {
		Path defaults = directory.resolve("defaults.json");
		checkDefaults(GameModeConfig.load(defaults), "a new configuration");
		check(Files.exists(defaults), "a missing config file should be created");

		Path partial = directory.resolve("partial.json");
		write(partial, "{\"enabled\": false, \"multiplayerMode\": \"bogus\", \"servers\": [\" a.example.com \", \"\"]}");
		GameModeConfig config = GameModeConfig.load(partial);
		check(!config.isEnabled(), "existing settings should be kept");
		check(config.followServerChanges(), "a missing setting should get its default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example.com")), "entries should be trimmed and blanks dropped");
		check(read(partial).contains("\"followServerChanges\""), "missing settings should be written to the file");

		Path broken = directory.resolve("broken.json");
		write(broken, "{ not json");
		checkDefaults(GameModeConfig.load(broken), "an unreadable configuration");
		check(read(broken).equals("{ not json"), "an unreadable config file should be left untouched");

		config.setFollowServerChanges(false);
		check(!GameModeConfig.load(partial).followServerChanges(), "changes should be saved immediately");
	}

	/**
	 * Only vanilla's argument order with {@code @c} as the whole target belongs to the mod; everything else goes to
	 * the server.
	 */
	private static void checkCommandDetection() {
		for (String command : List.of("gamemode creative @c", "gamemode spectator @c", "gamemode @c", "gamemode bogus @c",
				"gamemode @c creative", "gamemode creative @c extra")) {
			check(ClientGameModeCommands.handles(command), "the mod should run '" + command + "'");
		}
		for (String command : List.of("gamemode creative", "gamemode creative @s", "gamemode creative @a", "gamemode creative @c[x=1]",
				"gamemode creative @cx", "gamemodes creative @c", "tp @c", "gamemode", "say gamemode creative @c")) {
			check(!ClientGameModeCommands.handles(command), "'" + command + "' should be sent to the server");
		}
	}

	private static void checkSelection(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("selection.json"));
		GameModeController controller = new GameModeController(config);
		check(controller.availability() == Availability.BLOCKED, "nothing should be available outside a world");

		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.availability() == Availability.AVAILABLE, "singleplayer should always be allowed");
		check(controller.clientGameMode() == null, "a new session should have no client game mode");
		check(controller.serverGameMode() == GameType.SURVIVAL, "the login game mode should be the server's");

		check(controller.select(GameType.CREATIVE) == Selection.CLIENT, "another game mode should become the client game mode");
		check(controller.clientGameMode() == GameType.CREATIVE, "the client game mode should be creative");
		check(controller.select(GameType.SPECTATOR) == Selection.CLIENT, "the client game mode should change");
		check(controller.clientGameMode() == GameType.SPECTATOR, "the client game mode should be spectator");
		check(controller.select(GameType.SURVIVAL) == Selection.SERVER, "the server's game mode should end the client game mode");
		check(controller.clientGameMode() == null, "no client game mode should be left");
		check(controller.select(GameType.SURVIVAL) == Selection.SERVER, "the server's game mode should stay the server's");

		controller.select(GameType.CREATIVE);
		controller.onLogin(GameType.ADVENTURE);
		check(controller.clientGameMode() == null, "logging in again should end the client game mode");
		check(controller.serverGameMode() == GameType.ADVENTURE, "logging in should record the server's game mode");

		controller.select(GameType.CREATIVE);
		controller.onDisconnect();
		check(controller.clientGameMode() == null && controller.serverGameMode() == null, "disconnecting should end the session");
	}

	private static void checkServerChanges(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("server-changes.json"));
		GameModeController controller = new GameModeController(config);
		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(Scene.SINGLEPLAYER);

		check(controller.onServerGameMode(GameType.ADVENTURE) == ServerChange.NO_CLIENT_GAME_MODE,
				"without a client game mode the server's game mode applies");
		check(controller.serverGameMode() == GameType.ADVENTURE, "the server's new game mode should be recorded");

		controller.select(GameType.CREATIVE);
		check(controller.onServerGameMode(GameType.ADVENTURE) == ServerChange.KEPT, "a respawn in the same game mode should keep it");
		check(controller.clientGameMode() == GameType.CREATIVE, "the client game mode should stay creative");
		check(controller.onServerGameMode(GameType.SURVIVAL) == ServerChange.ENDED, "a server change should end it");
		check(controller.clientGameMode() == null, "no client game mode should be left after a server change");
		check(controller.serverGameMode() == GameType.SURVIVAL, "the server's new game mode should be recorded");

		config.setFollowServerChanges(false);
		controller.select(GameType.CREATIVE);
		check(controller.onServerGameMode(GameType.ADVENTURE) == ServerChange.KEPT, "without following, a server change should keep it");
		check(controller.clientGameMode() == GameType.CREATIVE, "the client game mode should stay creative");
		check(controller.serverGameMode() == GameType.ADVENTURE, "the server's new game mode should still be recorded");
		check(controller.select(GameType.ADVENTURE) == Selection.SERVER, "choosing the server's new game mode should end it");
	}

	private static void checkTurnedOff(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("turned-off.json"));
		GameModeController controller = new GameModeController(config);
		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(Scene.SINGLEPLAYER);
		controller.select(GameType.CREATIVE);

		check(controller.toggle() == GameModeController.ToggleResult.DISABLED, "toggling should turn the mod off");
		check(!config.isEnabled(), "the off state should be saved");
		check(controller.availability() == Availability.TURNED_OFF, "the mod should report that it is off");
		check(controller.endIfUnavailable(), "turning the mod off should end the client game mode");
		check(controller.clientGameMode() == null, "no client game mode should be left");
		check(!controller.endIfUnavailable(), "there should be nothing left to end");
		check(controller.select(GameType.CREATIVE) == Selection.TURNED_OFF, "selecting should report that the mod is off");
		check(controller.clientGameMode() == null, "a turned-off mod should not set a client game mode");
		check(controller.toggle() == GameModeController.ToggleResult.ENABLED, "toggling should turn the mod on again");
	}

	private static void checkMultiplayerRules(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("rules.json"));
		GameModeController controller = new GameModeController(config);

		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(SERVER_A);
		check(controller.availability() == Availability.BLOCKED, "the disabled mode should rule out every server");
		check(controller.select(GameType.CREATIVE) == Selection.BLOCKED, "selecting should be blocked");
		check(controller.toggle() == GameModeController.ToggleResult.BLOCKED, "toggling should be blocked");
		check(config.isEnabled(), "a blocked toggle should leave the state unchanged");
		config.setEnabled(false);
		check(controller.availability() == Availability.BLOCKED, "the multiplayer rules should come before the state");
		config.setEnabled(true);

		config.setServers(List.of("a.example.com"));
		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		check(controller.availability() == Availability.AVAILABLE, "a whitelisted server should be allowed");
		check(controller.select(GameType.CREATIVE) == Selection.CLIENT, "a whitelisted server should allow a client game mode");
		config.setServers(List.of("other.example.com"));
		check(controller.endIfUnavailable(), "removing the server from the whitelist should end the client game mode");
		check(controller.clientGameMode() == null, "no client game mode should be left on a ruled-out server");

		config.setServers(List.of("a.example.com"));
		controller.onJoin(SERVER_B);
		check(!controller.isAllowed(), "a server missing from the whitelist should be ruled out");
		config.setServers(List.of("b.example.com:25571"));
		check(!controller.isAllowed(), "a server whose port differs from the entry's should be ruled out");
		config.setServers(List.of("b.example.com:25570"));
		check(controller.isAllowed(), "a server with the entry's port should be allowed");
		controller.onJoin(UNKNOWN_SERVER);
		check(!controller.isAllowed(), "an unknown address should not count as whitelisted");

		config.setServers(List.of("b.example.com"));
		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		check(controller.isAllowed(), "an unknown address should not count as blacklisted");
		controller.onJoin(SERVER_A);
		check(controller.isAllowed(), "a server missing from the blacklist should be allowed");
		controller.onJoin(SERVER_B);
		check(!controller.isAllowed(), "a blacklisted server should be ruled out");

		controller.onDisconnect();
		check(controller.availability() == Availability.BLOCKED, "nothing should be available after disconnecting");
	}

	private static void checkResetRules(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("reset.json"));
		config.setResetOnGameExit(true);
		config.setEnabled(false);

		// A fresh controller stands for a game that has just started.
		GameModeController controller = new GameModeController(config);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(), "a ruled-out server should not apply the game exit reset");
		controller.onDisconnect();
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(), "the first allowed world after starting should restore the default");
		controller.onDisconnect();
		config.setEnabled(false);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(!config.isEnabled(), "later worlds should keep the state without reset on world exit");
		controller.onDisconnect();

		config.setResetOnWorldExit(true);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(), "reset on world exit should restore the singleplayer default");
		controller.onDisconnect();

		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		config.setServers(List.of("a.example.com"));
		config.setMultiplayerDefault(false);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(), "an allowed server should restore the server default");
		controller.onDisconnect();
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("gamemode-gametest");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(final Path path, final String content) {
		try {
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(final Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
