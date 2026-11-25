package com.urntt.gamemode;

import com.urntt.gamemode.config.GameModeSettings;
import com.urntt.gamemode.config.MultiplayerRules;
import java.util.function.Supplier;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the player may use a client game mode right now and which game mode the client uses, from the
 * settings that apply in the configuration mode, the current {@link Scene} and the game modes the server sets. It is
 * the only place that combines the configuration mode, the multiplayer rules and the server-change rule. It holds no
 * Minecraft state; {@link ClientGameMode} applies its decisions to the client.
 */
public final class GameModeController {
	/** Whether the player may set a client game mode in the current scene. */
	public enum Availability {
		AVAILABLE,
		/** Client game modes are turned off: Disabled mode, or OneConfig's toggle in OneConfig mode. */
		DISABLED,
		/** The multiplayer rules rule out the current scene. */
		BLOCKED
	}

	/** Outcome of {@link #select(GameType)}. */
	public enum Selection {
		/** The game mode is now the client game mode. */
		CLIENT,
		/** The game mode is the server's, so the client game mode ended and the client uses the server's again. */
		SERVER,
		/** Nothing changed: client game modes are turned off. */
		DISABLED,
		/** Nothing changed: the multiplayer rules rule out the current scene. */
		BLOCKED
	}

	/** Outcome of {@link #onServerGameMode(GameType)}. */
	public enum ServerChange {
		/** No client game mode is active; the client uses the server's game mode. */
		NO_CLIENT_GAME_MODE,
		/** The client game mode stays active. */
		KEPT,
		/** The server changed the game mode and "follow server changes" is on, so the client game mode ended. */
		ENDED
	}

	private final Supplier<GameModeSettings> settings;
	private Scene scene = Scene.NONE;
	private @Nullable GameType clientGameMode;
	private @Nullable GameType serverGameMode;

	/**
	 * @param settings the settings that apply right now, which change with the configuration mode
	 */
	public GameModeController(final Supplier<GameModeSettings> settings) {
		this.settings = settings;
	}

	public Scene scene() {
		return this.scene;
	}

	/**
	 * The game mode the player chose for the client, or {@code null} if the client uses the server's game mode.
	 */
	public @Nullable GameType clientGameMode() {
		return this.clientGameMode;
	}

	/**
	 * The game mode the server last set for the player, or {@code null} before the player logged in.
	 */
	public @Nullable GameType serverGameMode() {
		return this.serverGameMode;
	}

	/**
	 * Called when the client logs in to a server or opens a world, with the game mode the server sets. Starts a new
	 * session without a client game mode.
	 */
	public void onLogin(final GameType serverGameMode) {
		this.clientGameMode = null;
		this.serverGameMode = serverGameMode;
	}

	/**
	 * Called when the player joins a world, after {@link #onLogin}.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		this.clientGameMode = null;
	}

	public void onDisconnect() {
		this.scene = Scene.NONE;
		this.clientGameMode = null;
		this.serverGameMode = null;
	}

	/**
	 * Called when the server sets the player's game mode after login, for example when it changes the game mode or
	 * respawns the player. A changed game mode ends the client game mode if "follow server changes" is on; respawns
	 * and dimension changes that keep the game mode never do.
	 */
	public ServerChange onServerGameMode(final GameType gameMode) {
		GameType previous = this.serverGameMode;
		this.serverGameMode = gameMode;
		if (this.clientGameMode == null) {
			return ServerChange.NO_CLIENT_GAME_MODE;
		}
		if (this.settings.get().followServerChanges() && gameMode != previous) {
			this.clientGameMode = null;
			return ServerChange.ENDED;
		}
		return ServerChange.KEPT;
	}

	/**
	 * Makes {@code gameMode} the client game mode, or ends the client game mode if {@code gameMode} is the server's.
	 * Changes nothing unless client game modes are {@link Availability#AVAILABLE available}.
	 */
	public Selection select(final GameType gameMode) {
		switch (this.availability()) {
			case DISABLED -> {
				return Selection.DISABLED;
			}
			case BLOCKED -> {
				return Selection.BLOCKED;
			}
			case AVAILABLE -> {
			}
		}
		if (gameMode == this.serverGameMode) {
			this.clientGameMode = null;
			return Selection.SERVER;
		}
		this.clientGameMode = gameMode;
		return Selection.CLIENT;
	}

	/**
	 * Ends the client game mode if client game modes are no longer available, for example after the configuration
	 * mode or the multiplayer rules changed. Returns whether it ended one.
	 */
	public boolean endIfUnavailable() {
		if (this.clientGameMode != null && this.availability() != Availability.AVAILABLE) {
			this.clientGameMode = null;
			return true;
		}
		return false;
	}

	/**
	 * Returns whether the player may set a client game mode now, and if not, why. Being turned off comes first,
	 * because it applies everywhere.
	 */
	public Availability availability() {
		GameModeSettings settings = this.settings.get();
		if (!settings.enabled()) {
			return Availability.DISABLED;
		}
		return this.isAllowed(settings) ? Availability.AVAILABLE : Availability.BLOCKED;
	}

	/**
	 * Returns whether the multiplayer rules that apply allow client game modes in the current scene.
	 */
	public boolean isAllowed() {
		return this.isAllowed(this.settings.get());
	}

	/**
	 * Settings without multiplayer rules, which is OneConfig's, allow every server.
	 */
	private boolean isAllowed(final GameModeSettings settings) {
		MultiplayerRules rules = settings.multiplayerRules();
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> rules == null || switch (rules.mode()) {
				case DISABLED -> false;
				case WHITELIST -> isListed(rules, multiplayer.address());
				case BLACKLIST -> !isListed(rules, multiplayer.address());
			};
		};
	}

	private static boolean isListed(final MultiplayerRules rules, final @Nullable String address) {
		return address != null && rules.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
