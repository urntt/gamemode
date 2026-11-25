package com.urntt.gamemode.gametest;

import static com.urntt.gamemode.gametest.GameTestSupport.check;

import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.GameModeController;
import com.urntt.gamemode.GameModeController.Availability;
import com.urntt.gamemode.GameModeController.Selection;
import com.urntt.gamemode.GameModeController.ServerChange;
import com.urntt.gamemode.Scene;
import com.urntt.gamemode.ServerAddresses;
import com.urntt.gamemode.command.ClientGameModeCommands;
import com.urntt.gamemode.compat.OneConfigBridge;
import com.urntt.gamemode.config.ConfigMode;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.MultiplayerMode;
import com.urntt.gamemode.config.SettingValues;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.world.level.GameType;

/**
 * Checks the address matching, the configuration and its modes, the command detection and the controller's rules
 * without a world. Each check uses its own configuration file, so the game's configuration is left alone. The test
 * client runs without OneConfig by default and with it when Gradle is given {@code -PoneConfigRuntime=true}.
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
		checkIcon();
		checkAddressMatching();
		checkConfigFile(directory);
		checkUpgradeFrom100(directory);
		checkOneConfigFallback(directory);
		checkCommandDetection();
		checkSelection(directory);
		checkServerChanges(directory);
		checkDisabledMode(directory);
		checkMultiplayerRules(directory);
		GameTestSupport.LOGGER.info("Logic checks passed");
	}

	/**
	 * Loom deletes the run directory before each run, so the game's own configuration file holds the defaults.
	 */
	private static void checkGameConfigDefaults() {
		GameModeConfig config = GameTestSupport.loadSavedConfig();
		checkDefaults(config, "the game's configuration");
		boolean oneConfigInstalled = FabricLoader.getInstance().isModLoaded(OneConfigBridge.MOD_ID);
		check(OneConfigBridge.isAvailable() == oneConfigInstalled,
				"OneConfig mode should be available exactly when OneConfig is installed (installed: " + oneConfigInstalled + ")");
	}

	/**
	 * The icon that {@code fabric.mod.json} names, which Mod Menu and OneConfig show, is part of the mod.
	 */
	private static void checkIcon() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(GameModeClient.MOD_ID).orElseThrow();
		String icon = mod.getMetadata().getIconPath(512).orElseThrow(() -> new AssertionError("fabric.mod.json should name an icon"));
		check(mod.findPath(icon).filter(Files::isRegularFile).isPresent(), "the icon " + icon + " should be part of the mod");
	}

	private static void checkDefaults(final GameModeConfig config, final String what) {
		check(config.storedMode() == ConfigMode.CUSTOM, what + ": the default mode should be custom, was " + config.storedMode());
		check(SettingValues.copyOf(config).equals(SettingValues.DEFAULTS), what + ": the custom settings should start at the defaults, were "
				+ SettingValues.copyOf(config));
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, what + ": multiplayer should be disabled by default");
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
		check(read(defaults).contains("\"mode\": \"custom\""), "a missing config file should be created with the mode");

		Path partial = directory.resolve("partial.json");
		write(partial, "{\"mode\": \"bogus\", \"multiplayerMode\": \"bogus\", \"servers\": [\" a.example.com \", \"\"]}");
		GameModeConfig config = GameModeConfig.load(partial);
		check(config.storedMode() == ConfigMode.CUSTOM, "an unknown mode should fall back to custom");
		check(config.followServerChanges() && config.switcherKey(), "missing settings should get their defaults");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown multiplayer mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example.com")), "entries should be trimmed and blanks dropped");
		check(read(partial).contains("\"followServerChanges\""), "missing settings should be written to the file");

		Path broken = directory.resolve("broken.json");
		write(broken, "{ not json");
		checkDefaults(GameModeConfig.load(broken), "an unreadable configuration");
		check(read(broken).equals("{ not json"), "an unreadable config file should be left untouched");

		config.setFollowServerChanges(false);
		config.setSwitcherKey(false);
		config.setMode(ConfigMode.DISABLED);
		GameModeConfig reloaded = GameModeConfig.load(partial);
		check(!reloaded.followServerChanges() && !reloaded.switcherKey() && reloaded.storedMode() == ConfigMode.DISABLED,
				"changes should be saved immediately");
	}

	/**
	 * Version 1.0.0 had an on/off state instead of modes, defaults and reset rules. A file from it keeps its other
	 * settings, starts in Disabled mode if the mod was off, and loses the keys this version no longer uses.
	 */
	private static void checkUpgradeFrom100(final Path directory) {
		Path off = directory.resolve("upgrade-off.json");
		write(off, "{\"enabled\": false, \"followServerChanges\": false, \"singleplayerDefault\": true, \"multiplayerDefault\": true,"
				+ " \"resetOnWorldExit\": true, \"resetOnGameExit\": false, \"multiplayerMode\": \"whitelist\", \"servers\": [\"a.example.com\"]}");
		GameModeConfig config = GameModeConfig.load(off);
		check(config.storedMode() == ConfigMode.DISABLED, "a 1.0.0 file with the mod off should start in Disabled mode");
		check(!config.followServerChanges() && config.multiplayerMode() == MultiplayerMode.WHITELIST
				&& config.servers().equals(List.of("a.example.com")), "a 1.0.0 file should keep its other settings");
		String saved = read(off);
		for (String key : List.of("enabled", "singleplayerDefault", "multiplayerDefault", "resetOnWorldExit", "resetOnGameExit")) {
			check(!saved.contains("\"" + key + "\""), "the upgraded file should not keep " + key + ": " + saved);
		}
		check(GameModeConfig.load(off).storedMode() == ConfigMode.DISABLED, "the upgraded mode should be saved");

		Path on = directory.resolve("upgrade-on.json");
		write(on, "{\"enabled\": true, \"multiplayerMode\": \"disabled\"}");
		check(GameModeConfig.load(on).storedMode() == ConfigMode.CUSTOM, "a 1.0.0 file with the mod on should start in custom mode");
	}

	/**
	 * OneConfig mode applies only while OneConfig is installed; otherwise the custom settings apply and the stored
	 * mode is kept until the player picks another.
	 */
	private static void checkOneConfigFallback(final Path directory) {
		Path path = directory.resolve("oneconfig.json");
		write(path, "{\"mode\": \"oneconfig\", \"multiplayerMode\": \"blacklist\"}");
		GameModeConfig config = GameModeConfig.load(path);
		check(config.storedMode() == ConfigMode.ONECONFIG, "the stored mode should stay OneConfig");
		if (OneConfigBridge.isAvailable()) {
			check(config.effectiveMode() == ConfigMode.ONECONFIG, "OneConfig mode should apply while OneConfig is installed");
			check(config.effective() != config, "OneConfig mode should apply OneConfig's settings");
		} else {
			check(config.effectiveMode() == ConfigMode.CUSTOM, "without OneConfig, OneConfig mode should fall back to custom");
			check(config.effective() == config, "without OneConfig, the custom settings should apply");
		}
		check(GameModeConfig.load(path).storedMode() == ConfigMode.ONECONFIG, "loading should not change the stored mode");

		config.setMode(ConfigMode.DISABLED);
		check(SettingValues.copyOf(config.effective()).equals(SettingValues.DISABLED), "Disabled mode should apply the disabled settings");
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

	private static void checkDisabledMode(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("disabled.json"));
		GameModeController controller = new GameModeController(config);
		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(Scene.SINGLEPLAYER);
		controller.select(GameType.CREATIVE);

		config.setMode(ConfigMode.DISABLED);
		check(controller.availability() == Availability.DISABLED, "Disabled mode should turn client game modes off");
		check(controller.endIfUnavailable(), "Disabled mode should end the client game mode");
		check(controller.clientGameMode() == null, "no client game mode should be left");
		check(!controller.endIfUnavailable(), "there should be nothing left to end");
		check(controller.select(GameType.CREATIVE) == Selection.DISABLED, "selecting should report Disabled mode");
		check(controller.clientGameMode() == null, "Disabled mode should not set a client game mode");

		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		controller.onJoin(SERVER_A);
		check(controller.availability() == Availability.DISABLED, "Disabled mode should come before the multiplayer rules");
		config.setMode(ConfigMode.CUSTOM);
		check(controller.availability() == Availability.AVAILABLE, "custom mode should allow client game modes again");
	}

	private static void checkMultiplayerRules(final Path directory) {
		GameModeConfig config = GameModeConfig.load(directory.resolve("rules.json"));
		GameModeController controller = new GameModeController(config);

		controller.onLogin(GameType.SURVIVAL);
		controller.onJoin(SERVER_A);
		check(controller.availability() == Availability.BLOCKED, "the disabled mode should rule out every server");
		check(controller.select(GameType.CREATIVE) == Selection.BLOCKED, "selecting should be blocked");
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
