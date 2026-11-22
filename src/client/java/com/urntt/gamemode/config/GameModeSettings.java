package com.urntt.gamemode.config;

import java.util.List;

/**
 * The values the mod reads. Implemented by the mod's own configuration, by the OneConfig integration and by the
 * constant {@link SettingValues} sets.
 */
public interface GameModeSettings {
	/** Whether client game modes may be used at all. Off in Disabled mode and while OneConfig's toggle is off. */
	boolean enabled();

	/** Whether a change of the server's game mode ends the client game mode. */
	boolean followServerChanges();

	/** On which multiplayer servers client game modes are allowed. */
	MultiplayerMode multiplayerMode();

	/** The server list used by the whitelist and blacklist modes. */
	List<String> servers();
}
