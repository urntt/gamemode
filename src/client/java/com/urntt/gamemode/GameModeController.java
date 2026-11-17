package com.urntt.gamemode;

import com.urntt.gamemode.config.GameModeConfig;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Decides whether the player may use a client game mode right now and which game mode the client uses, from the
 * configuration, the current {@link Scene} and the game modes the server sets. It is the only place that combines
 * the mod's state, the defaults, the reset rules, the multiplayer rules and the server-change rule. It holds no
 * Minecraft state; {@link ClientGameMode} applies its decisions to the client.
 */
public final class GameModeController {
	/** Whether the player may set a client game mode in the current scene. */
	public enum Availability {
		AVAILABLE,
		/** The multiplayer rules rule out the current scene. */
		BLOCKED,
		/** The player turned the mod off. */
		TURNED_OFF
	}

	/** Outcome of {@link #toggle()}. */
	public enum ToggleResult {
		ENABLED,
		DISABLED,
		/** The multiplayer rules rule out the current scene, so the state was left unchanged. */
		BLOCKED
	}

	/** Outcome of {@link #select(GameType)}. */
	public enum Selection {
		/** The game mode is now the client game mode. */
		CLIENT,
		/** The game mode is the server's, so the client game mode ended and the client uses the server's again. */
		SERVER,
		/** Nothing changed: the multiplayer rules rule out the current scene. */
		BLOCKED,
		/** Nothing changed: the mod is turned off. */
		TURNED_OFF
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

	private final GameModeConfig config;
	private Scene scene = Scene.NONE;
	private boolean allowedWorldJoinedSinceStart = false;
	private @Nullable GameType clientGameMode;
	private @Nullable GameType serverGameMode;

	public GameModeController(final GameModeConfig config) {
		this.config = config;
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
	 * Called when the player joins a world, after {@link #onLogin}. On an allowed scene, restores the scene's default
	 * state if a reset rule applies: always with "reset on world exit", and for the first allowed world since the game
	 * started with "reset on game exit". Applying the reset on join instead of on exit lets it pick the next scene's
	 * default and also works after a crash.
	 */
	public void onJoin(final Scene scene) {
		this.scene = scene;
		this.clientGameMode = null;
		if (!this.isAllowed()) {
			return;
		}

		boolean firstWorld = !this.allowedWorldJoinedSinceStart;
		this.allowedWorldJoinedSinceStart = true;
		if (this.config.resetOnWorldExit() || (this.config.resetOnGameExit() && firstWorld)) {
			this.config.setEnabled(this.defaultFor(scene));
		}
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
		if (this.config.followServerChanges() && gameMode != previous) {
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
			case BLOCKED -> {
				return Selection.BLOCKED;
			}
			case TURNED_OFF -> {
				return Selection.TURNED_OFF;
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
	 * Ends the client game mode if client game modes are no longer available, for example after the mod was turned
	 * off or the multiplayer rules changed. Returns whether it ended one.
	 */
	public boolean endIfUnavailable() {
		if (this.clientGameMode != null && this.availability() != Availability.AVAILABLE) {
			this.clientGameMode = null;
			return true;
		}
		return false;
	}

	/**
	 * Returns whether the player may set a client game mode now, and if not, why. The multiplayer rules come first,
	 * because they apply whatever the mod's state is.
	 */
	public Availability availability() {
		if (!this.isAllowed()) {
			return Availability.BLOCKED;
		}
		return this.config.isEnabled() ? Availability.AVAILABLE : Availability.TURNED_OFF;
	}

	/**
	 * Returns whether the multiplayer rules allow client game modes in the current scene.
	 */
	public boolean isAllowed() {
		return switch (this.scene) {
			case Scene.None none -> false;
			case Scene.Singleplayer singleplayer -> true;
			case Scene.Multiplayer multiplayer -> switch (this.config.multiplayerMode()) {
				case DISABLED -> false;
				case WHITELIST -> this.isListed(multiplayer.address());
				case BLACKLIST -> !this.isListed(multiplayer.address());
			};
		};
	}

	/**
	 * Flips and saves the state of the whole mod, unless the current server is not allowed. Turning it off does not
	 * end the client game mode by itself; {@link #endIfUnavailable()} does.
	 */
	public ToggleResult toggle() {
		if (this.scene instanceof Scene.Multiplayer && !this.isAllowed()) {
			return ToggleResult.BLOCKED;
		}
		boolean enabled = !this.config.isEnabled();
		this.config.setEnabled(enabled);
		return enabled ? ToggleResult.ENABLED : ToggleResult.DISABLED;
	}

	private boolean defaultFor(final Scene scene) {
		return scene instanceof Scene.Singleplayer ? this.config.singleplayerDefault() : this.config.multiplayerDefault();
	}

	private boolean isListed(final @Nullable String address) {
		return address != null && this.config.servers().stream().anyMatch(entry -> ServerAddresses.matches(entry, address));
	}
}
