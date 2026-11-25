package com.urntt.gamemode.config;

import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Decides on which multiplayer servers the player may set a client game mode. On servers it rules out, the client
 * always uses the server's game mode, whatever the mod's state is.
 */
public enum MultiplayerMode implements StringRepresentable {
	/** Client game modes are never allowed in multiplayer. */
	@SerializedName("disabled")
	DISABLED("disabled"),
	/** Client game modes are allowed only on servers in the server list. */
	@SerializedName("whitelist")
	WHITELIST("whitelist"),
	/** Client game modes are allowed on every server except those in the server list. */
	@SerializedName("blacklist")
	BLACKLIST("blacklist");

	public static final Codec<MultiplayerMode> CODEC = StringRepresentable.fromEnum(MultiplayerMode::values);

	private final String name;

	MultiplayerMode(final String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return this.name;
	}

	public Component label() {
		return Component.translatable(SettingKeys.MULTIPLAYER_MODE + "." + this.name);
	}

	public Component description() {
		return Component.translatable(SettingKeys.MULTIPLAYER_MODE + "." + this.name + SettingKeys.DESCRIPTION);
	}
}
