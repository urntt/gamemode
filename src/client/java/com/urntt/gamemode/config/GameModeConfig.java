package com.urntt.gamemode.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.compat.OneConfigBridge;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's own settings and the configuration mode, persisted as JSON. Every change is written to disk immediately.
 */
public final class GameModeConfig implements GameModeSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger(GameModeClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Settings settings;

	private GameModeConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(GameModeClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks and without those it no longer uses. An unreadable file falls back to the
	 * defaults and is left untouched until the next change is saved.
	 */
	public static GameModeConfig load(final Path path) {
		if (Files.notExists(path)) {
			GameModeConfig config = new GameModeConfig(path, new Settings().normalized());
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			return new GameModeConfig(path, new Settings().normalized());
		}

		GameModeConfig config = new GameModeConfig(path, (settings != null ? settings : new Settings()).normalized());
		config.save();
		return config;
	}

	/** The mode stored in the file, which may name OneConfig even while OneConfig is not installed. */
	public ConfigMode storedMode() {
		return this.settings.mode;
	}

	/** The mode that applies: OneConfig mode falls back to custom settings while OneConfig is not available. */
	public ConfigMode effectiveMode() {
		ConfigMode mode = this.settings.mode;
		return mode == ConfigMode.ONECONFIG && !OneConfigBridge.isAvailable() ? ConfigMode.CUSTOM : mode;
	}

	/** The settings that apply in the {@linkplain #effectiveMode() effective mode}. */
	public GameModeSettings effective() {
		return switch (this.effectiveMode()) {
			case DISABLED -> SettingValues.DISABLED;
			case CUSTOM -> this;
			case ONECONFIG -> OneConfigBridge.settings().orElse(this);
		};
	}

	public void setMode(final ConfigMode mode) {
		this.settings.mode = mode;
		this.save();
	}

	/** The mod's own settings are always enabled; Disabled mode turns client game modes off. */
	@Override
	public boolean enabled() {
		return true;
	}

	@Override
	public boolean switcherKey() {
		return this.settings.switcherKey;
	}

	public void setSwitcherKey(final boolean switcherKey) {
		this.settings.switcherKey = switcherKey;
		this.save();
	}

	@Override
	public boolean followServerChanges() {
		return this.settings.followServerChanges;
	}

	public void setFollowServerChanges(final boolean followServerChanges) {
		this.settings.followServerChanges = followServerChanges;
		this.save();
	}

	@Override
	public MultiplayerMode multiplayerMode() {
		return this.settings.multiplayerMode;
	}

	public void setMultiplayerMode(final MultiplayerMode multiplayerMode) {
		this.settings.multiplayerMode = multiplayerMode;
		this.save();
	}

	@Override
	public List<String> servers() {
		return List.copyOf(this.settings.servers);
	}

	public void setServers(final List<String> servers) {
		this.settings.servers = new ArrayList<>(servers);
		this.settings.normalized();
		this.save();
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * The serialized form. Field initializers are the defaults, which also apply to keys missing from the file.
	 */
	private static final class Settings {
		/** {@code null} only while loading a file without a mode, which {@link #normalized()} replaces. */
		private @Nullable ConfigMode mode;
		/**
		 * The on/off state of version 1.0.0, which had no modes. Only read, to start such a file in Disabled mode if
		 * the mod was off; Gson leaves it out of the file once it is {@code null}.
		 */
		private @Nullable Boolean enabled;
		private boolean switcherKey = SettingValues.DEFAULTS.switcherKey();
		private boolean followServerChanges = SettingValues.DEFAULTS.followServerChanges();
		private MultiplayerMode multiplayerMode = SettingValues.DEFAULTS.multiplayerMode();
		private List<String> servers = new ArrayList<>(SettingValues.DEFAULTS.servers());

		/**
		 * Picks the mode of a file without one, replaces values Gson could not map (unknown mode names, a missing
		 * list) and tidies the server list.
		 */
		private Settings normalized() {
			if (this.mode == null) {
				this.mode = Boolean.FALSE.equals(this.enabled) ? ConfigMode.DISABLED : ConfigMode.CUSTOM;
			}
			this.enabled = null;
			if (this.multiplayerMode == null) {
				this.multiplayerMode = SettingValues.DEFAULTS.multiplayerMode();
			}
			this.servers = normalizedServers(this.servers);
			return this;
		}
	}

	/**
	 * Trims the entries of a server list and drops blank ones.
	 */
	public static List<String> normalizedServers(final @Nullable List<String> servers) {
		List<String> entries = new ArrayList<>();
		if (servers != null) {
			for (String entry : servers) {
				if (entry != null && !entry.isBlank()) {
					entries.add(entry.trim());
				}
			}
		}
		return entries;
	}
}
