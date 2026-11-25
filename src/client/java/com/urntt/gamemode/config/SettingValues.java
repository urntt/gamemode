package com.urntt.gamemode.config;

import java.util.List;

/**
 * An immutable set of settings.
 */
public record SettingValues(
		boolean enabled,
		boolean switcherKey,
		boolean followServerChanges,
		MultiplayerMode multiplayerMode,
		List<String> servers) implements GameModeSettings {
	/** The defaults of the mod's own settings and of its OneConfig settings. */
	public static final SettingValues DEFAULTS = new SettingValues(true, true, true, MultiplayerMode.DISABLED, List.of());

	/**
	 * The settings of Disabled mode, where client game modes are turned off everywhere. The switcher key stays on, so
	 * that it reports that they are turned off, as vanilla's switcher key does without permission.
	 */
	public static final SettingValues DISABLED = new SettingValues(
			false, DEFAULTS.switcherKey(), DEFAULTS.followServerChanges(), MultiplayerMode.DISABLED, List.of());

	public SettingValues {
		servers = List.copyOf(servers);
	}

	/** Copies the current values of {@code settings}. */
	public static SettingValues copyOf(final GameModeSettings settings) {
		return new SettingValues(
				settings.enabled(), settings.switcherKey(), settings.followServerChanges(), settings.multiplayerMode(), settings.servers());
	}
}
