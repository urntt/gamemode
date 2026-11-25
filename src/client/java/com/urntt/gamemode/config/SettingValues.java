package com.urntt.gamemode.config;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * An immutable set of settings.
 */
public record SettingValues(
		boolean enabled,
		boolean switcherKey,
		boolean followServerChanges,
		@Nullable MultiplayerRules multiplayerRules) implements GameModeSettings {
	/** The defaults of the mod's own settings: client game modes are allowed in singleplayer, but on no server. */
	public static final SettingValues DEFAULTS = new SettingValues(true, true, true, new MultiplayerRules(MultiplayerMode.DISABLED, List.of()));

	/**
	 * The defaults of the OneConfig settings: client game modes are turned off until the player turns them on in
	 * OneConfig, and then apply everywhere.
	 */
	public static final SettingValues ONECONFIG_DEFAULTS = new SettingValues(false, DEFAULTS.switcherKey(), DEFAULTS.followServerChanges(), null);

	/**
	 * The settings of Disabled mode, where client game modes are turned off everywhere. The switcher key stays on, so
	 * that it reports that they are turned off, as vanilla's switcher key does without permission.
	 */
	public static final SettingValues DISABLED = new SettingValues(false, DEFAULTS.switcherKey(), DEFAULTS.followServerChanges(), DEFAULTS.multiplayerRules());

	/** Copies the current values of {@code settings}. */
	public static SettingValues copyOf(final GameModeSettings settings) {
		return new SettingValues(settings.enabled(), settings.switcherKey(), settings.followServerChanges(), settings.multiplayerRules());
	}
}
