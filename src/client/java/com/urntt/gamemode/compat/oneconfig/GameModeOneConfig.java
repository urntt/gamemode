package com.urntt.gamemode.compat.oneconfig;

import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.config.GameModeSettings;
import com.urntt.gamemode.config.MultiplayerRules;
import com.urntt.gamemode.config.SettingKeys;
import com.urntt.gamemode.config.SettingValues;
import org.jspecify.annotations.Nullable;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.MasterToggle;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

/**
 * The settings OneConfig manages in OneConfig mode: the mod's toggle on its card, off by default, and the feature
 * settings, with the same defaults as the mod's own. There are no multiplayer rules; OneConfig's profiles take their
 * place, so once the toggle is on, client game modes apply in singleplayer and on every server. Titles use the mod's
 * translation keys; the keys double as fallback titles.
 */
public final class GameModeOneConfig extends Config implements GameModeSettings {
	/**
	 * OneConfig keeps its files in the same config directory as the mod's own {@code gamemode.json} and watches them
	 * for changes, so its file needs a name of its own.
	 */
	private static final String FILE_NAME = GameModeClient.MOD_ID + "-oneconfig.json";

	/** The mod icon, as named in {@code fabric.mod.json}, as a resource path for OneConfig's mod card. */
	private static final String ICON_PATH = "/assets/" + GameModeClient.MOD_ID + "/icon.png";

	/** The toggle on the mod's OneConfig card. While it is off, client game modes are turned off. */
	@MasterToggle
	public boolean enabled = SettingValues.ONECONFIG_DEFAULTS.enabled();

	@Switch(
			title = SettingKeys.SWITCHER_KEY,
			titleTranslation = true,
			description = SettingKeys.SWITCHER_KEY + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SHORTCUTS,
			subcategoryTranslation = true)
	public boolean switcherKey = SettingValues.ONECONFIG_DEFAULTS.switcherKey();

	@Switch(
			title = SettingKeys.FOLLOW_SERVER_CHANGES,
			titleTranslation = true,
			description = SettingKeys.FOLLOW_SERVER_CHANGES + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SERVER_CHANGES,
			subcategoryTranslation = true)
	public boolean followServerChanges = SettingValues.ONECONFIG_DEFAULTS.followServerChanges();

	public GameModeOneConfig() {
		super(FILE_NAME, ICON_PATH, GameModeClient.MOD_ID, Category.UTILITY);
	}

	@Override
	public boolean enabled() {
		return this.enabled;
	}

	@Override
	public boolean switcherKey() {
		return this.switcherKey;
	}

	@Override
	public boolean followServerChanges() {
		return this.followServerChanges;
	}

	/** OneConfig has no multiplayer rules; its profiles take their place. */
	@Override
	public @Nullable MultiplayerRules multiplayerRules() {
		return SettingValues.ONECONFIG_DEFAULTS.multiplayerRules();
	}
}
