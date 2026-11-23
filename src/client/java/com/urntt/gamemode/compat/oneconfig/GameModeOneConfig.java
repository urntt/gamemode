package com.urntt.gamemode.compat.oneconfig;

import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.GameModeSettings;
import com.urntt.gamemode.config.MultiplayerMode;
import com.urntt.gamemode.config.SettingKeys;
import com.urntt.gamemode.config.SettingValues;
import java.util.ArrayList;
import java.util.List;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.MasterToggle;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.TextList;

/**
 * The settings OneConfig manages in OneConfig mode, with the same defaults as the mod's own settings. Titles use the
 * mod's translation keys; the keys double as fallback titles.
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
	public boolean enabled = SettingValues.DEFAULTS.enabled();

	@Switch(
			title = SettingKeys.FOLLOW_SERVER_CHANGES,
			titleTranslation = true,
			description = SettingKeys.FOLLOW_SERVER_CHANGES + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.SERVER_CHANGES,
			subcategoryTranslation = true)
	public boolean followServerChanges = SettingValues.DEFAULTS.followServerChanges();

	/** The index of the multiplayer mode in {@link MultiplayerMode#values()}, as OneConfig's dropdown stores it. */
	@Dropdown(
			title = SettingKeys.MULTIPLAYER_MODE,
			titleTranslation = true,
			description = SettingKeys.MULTIPLAYER_MODE + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.MULTIPLAYER,
			subcategoryTranslation = true,
			options = {SettingKeys.MULTIPLAYER_MODE_DISABLED, SettingKeys.MULTIPLAYER_MODE_WHITELIST, SettingKeys.MULTIPLAYER_MODE_BLACKLIST},
			optionsTranslation = true)
	public int multiplayerMode = SettingValues.DEFAULTS.multiplayerMode().ordinal();

	@TextList(
			title = SettingKeys.SERVERS,
			titleTranslation = true,
			description = SettingKeys.SERVERS + SettingKeys.DESCRIPTION,
			descriptionTranslation = true,
			subcategory = SettingKeys.MULTIPLAYER,
			subcategoryTranslation = true,
			placeholder = SettingKeys.SERVERS_HINT,
			placeholderTranslation = true)
	public List<String> servers = new ArrayList<>(SettingValues.DEFAULTS.servers());

	public GameModeOneConfig() {
		super(FILE_NAME, ICON_PATH, GameModeClient.MOD_ID, Category.UTILITY);
	}

	/** The settings that apply in OneConfig mode, which turn client game modes off while the card's toggle is off. */
	GameModeSettings applied() {
		return this.enabled ? this : SettingValues.DISABLED;
	}

	@Override
	public boolean enabled() {
		return this.enabled;
	}

	@Override
	public boolean followServerChanges() {
		return this.followServerChanges;
	}

	/** An index OneConfig's file holds out of range falls back to the default. */
	@Override
	public MultiplayerMode multiplayerMode() {
		MultiplayerMode[] modes = MultiplayerMode.values();
		return this.multiplayerMode >= 0 && this.multiplayerMode < modes.length
				? modes[this.multiplayerMode]
				: SettingValues.DEFAULTS.multiplayerMode();
	}

	@Override
	public List<String> servers() {
		return GameModeConfig.normalizedServers(this.servers);
	}
}
