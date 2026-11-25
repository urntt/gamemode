package com.urntt.gamemode.config;

/**
 * Translation keys of the settings, shared by the configuration screens and the OneConfig integration. They are
 * compile-time constants so that OneConfig's option annotations can use them.
 */
public final class SettingKeys {
	private static final String PREFIX = "options.gamemode.";

	public static final String TITLE = PREFIX + "title";
	public static final String MODE = PREFIX + "mode";
	public static final String ONECONFIG_MISSING = PREFIX + "mode.oneconfig.missing";
	public static final String OPEN_ONECONFIG = PREFIX + "open_oneconfig";

	public static final String SHORTCUTS = PREFIX + "shortcuts";
	public static final String SWITCHER_KEY = PREFIX + "switcher_key";

	public static final String SERVER_CHANGES = PREFIX + "server_changes";
	public static final String FOLLOW_SERVER_CHANGES = PREFIX + "follow_server_changes";

	public static final String MULTIPLAYER = PREFIX + "multiplayer";
	public static final String MULTIPLAYER_MODE = PREFIX + "multiplayer_mode";
	public static final String SERVERS = PREFIX + "servers";
	public static final String EDIT_SERVERS = PREFIX + "edit_servers";
	public static final String SERVERS_HINT = SERVERS + ".hint";

	/** Suffix of the key that describes a setting, shown as its tooltip. */
	public static final String DESCRIPTION = ".description";

	private SettingKeys() {
	}
}
