package com.urntt.gamemode.config;

import org.jspecify.annotations.Nullable;

/**
 * The values the mod reads. Implemented by the mod's own configuration, by the OneConfig integration and by the
 * constant {@link SettingValues} sets.
 */
public interface GameModeSettings {
	/** Whether client game modes may be used at all. Off in Disabled mode and while OneConfig's toggle is off. */
	boolean enabled();

	/** Whether the switcher key opens the client game mode switcher. When off, the mod leaves the key to vanilla. */
	boolean switcherKey();

	/** Whether a change of the server's game mode ends the client game mode. */
	boolean followServerChanges();

	/**
	 * On which multiplayer servers client game modes are allowed, or {@code null} if every server is: OneConfig has
	 * no multiplayer rules, because its profiles take their place.
	 */
	@Nullable MultiplayerRules multiplayerRules();
}
