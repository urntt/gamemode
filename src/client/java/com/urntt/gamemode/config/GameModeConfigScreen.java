package com.urntt.gamemode.config;

import com.urntt.gamemode.GameModeClient;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Configuration screen built from vanilla widgets. Every change is saved immediately.
 */
public final class GameModeConfigScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.translatable("options.gamemode.title");

	public GameModeConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, TITLE);
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		GameModeConfig config = GameModeClient.config();

		this.list.addHeader(Component.translatable("options.gamemode.section.current"));
		this.list.addBig(toggle(GameModeClient.FEATURE_NAME_KEY, config.isEnabled(), config::setEnabled));

		this.list.addHeader(Component.translatable("options.gamemode.section.server_changes"));
		this.list.addBig(toggle("options.gamemode.follow_server_changes", config.followServerChanges(),
				config::setFollowServerChanges));

		this.list.addHeader(Component.translatable("options.gamemode.section.defaults"));
		this.list.addBig(toggle("options.gamemode.singleplayer_default", config.singleplayerDefault(),
				config::setSingleplayerDefault));
		this.list.addBig(toggle("options.gamemode.multiplayer_default", config.multiplayerDefault(),
				config::setMultiplayerDefault));

		this.list.addHeader(Component.translatable("options.gamemode.section.reset"));
		this.list.addBig(toggle("options.gamemode.reset_on_world_exit", config.resetOnWorldExit(),
				config::setResetOnWorldExit));
		this.list.addBig(toggle("options.gamemode.reset_on_game_exit", config.resetOnGameExit(),
				config::setResetOnGameExit));

		this.list.addHeader(Component.translatable("options.gamemode.section.multiplayer"));
		this.list.addBig(new OptionInstance<>(
				"options.gamemode.multiplayer_mode",
				mode -> Tooltip.create(mode.description()),
				// The button itself prepends the caption, so this only names the value.
				(caption, mode) -> mode.label(),
				new OptionInstance.Enum<>(List.of(MultiplayerMode.values()), MultiplayerMode.CODEC),
				config.multiplayerMode(),
				config::setMultiplayerMode));
		this.list.addBig(Button.builder(Component.translatable("options.gamemode.edit_servers"),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, config)))
				.tooltip(Tooltip.create(Component.translatable("options.gamemode.edit_servers.tooltip")))
				.build());
	}

	/**
	 * Creates an on/off option whose tooltip is the translation of {@code captionKey + ".tooltip"}.
	 */
	private static OptionInstance<Boolean> toggle(final String captionKey, final boolean value,
			final Consumer<Boolean> onChange) {
		return OptionInstance.createBoolean(
				captionKey,
				OptionInstance.cachedConstantTooltip(Component.translatable(captionKey + ".tooltip")),
				value,
				onChange::accept);
	}
}
