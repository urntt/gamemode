package com.urntt.gamemode.config;

import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.compat.OneConfigBridge;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The configuration screen, built from vanilla widgets. The mode button comes first. The settings below it can only
 * be edited in custom mode; otherwise they are greyed out and show the values that currently apply.
 */
public final class GameModeConfigScreen extends OptionsSubScreen {
	private final GameModeConfig config = GameModeClient.config();

	public GameModeConfigScreen(final @Nullable Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable(SettingKeys.TITLE));
	}

	@Override
	protected void addOptions() {
		if (this.list == null) {
			return;
		}
		ConfigMode mode = this.config.effectiveMode();
		boolean editable = mode == ConfigMode.CUSTOM;
		GameModeSettings shown = this.config.effective();

		this.list.addBig(this.createModeButton(mode));
		if (mode == ConfigMode.ONECONFIG) {
			this.list.addBig(Button.builder(Component.translatable(SettingKeys.OPEN_ONECONFIG), button -> OneConfigBridge.openOneConfig()).build());
		}

		this.list.addHeader(Component.translatable(SettingKeys.SHORTCUTS));
		this.list.addBig(this.toggle(SettingKeys.SWITCHER_KEY, shown.switcherKey(), editable, this.config::setSwitcherKey));

		this.list.addHeader(Component.translatable(SettingKeys.SERVER_CHANGES));
		this.list.addBig(this.toggle(SettingKeys.FOLLOW_SERVER_CHANGES, shown.followServerChanges(), editable, this.config::setFollowServerChanges));

		MultiplayerRules rules = shown.multiplayerRules();
		if (rules == null) {
			// OneConfig has no multiplayer rules; its profiles take their place.
			return;
		}
		this.list.addHeader(Component.translatable(SettingKeys.MULTIPLAYER));
		CycleButton<MultiplayerMode> multiplayerMode = CycleButton.builder(MultiplayerMode::label, rules.mode())
				.withValues(MultiplayerMode.values())
				.withTooltip(value -> Tooltip.create(value.description()))
				.create(Component.translatable(SettingKeys.MULTIPLAYER_MODE), (button, value) -> this.config.setMultiplayerMode(value));
		multiplayerMode.active = editable;
		this.list.addBig(multiplayerMode);
		Button servers = Button.builder(Component.translatable(SettingKeys.EDIT_SERVERS),
						button -> this.minecraft.gui.setScreen(new ServerListScreen(this, this.config)))
				.tooltip(Tooltip.create(Component.translatable(SettingKeys.EDIT_SERVERS + SettingKeys.DESCRIPTION)))
				.build();
		servers.active = editable;
		this.list.addBig(servers);
	}

	private CycleButton<ConfigMode> createModeButton(final ConfigMode mode) {
		List<ConfigMode> modes = OneConfigBridge.isAvailable()
				? List.of(ConfigMode.values())
				: List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM);
		boolean oneConfigMissing = this.config.storedMode() == ConfigMode.ONECONFIG && !OneConfigBridge.isAvailable();
		return CycleButton.builder(ConfigMode::label, mode)
				.withValues(modes)
				.withTooltip(value -> Tooltip.create(oneConfigMissing && value == mode
						? Component.translatable(SettingKeys.ONECONFIG_MISSING)
						: value.description()))
				.create(Component.translatable(SettingKeys.MODE), (button, value) -> {
					this.config.setMode(value);
					// The settings below change their values and whether they can be edited, so start over.
					this.minecraft.gui.setScreen(new GameModeConfigScreen(this.lastScreen));
				});
	}

	private CycleButton<Boolean> toggle(final String key, final boolean value, final boolean editable, final Consumer<Boolean> setter) {
		Tooltip tooltip = Tooltip.create(Component.translatable(key + SettingKeys.DESCRIPTION));
		CycleButton<Boolean> button = CycleButton.onOffBuilder(value)
				.withTooltip(ignored -> tooltip)
				.create(Component.translatable(key), (cycleButton, newValue) -> setter.accept(newValue));
		button.active = editable;
		return button;
	}

	/** Shows the screen on top of whatever screen is open. */
	public static void open(final Minecraft minecraft) {
		minecraft.gui.setScreen(new GameModeConfigScreen(minecraft.gui.screen()));
	}
}
