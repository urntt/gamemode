package com.urntt.gamemode.gametest;

import static com.urntt.gamemode.gametest.GameTestSupport.LOGGER;
import static com.urntt.gamemode.gametest.GameTestSupport.check;
import static com.urntt.gamemode.gametest.GameTestSupport.configure;
import static com.urntt.gamemode.gametest.GameTestSupport.switchLanguage;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.compat.OneConfigBridge;
import com.urntt.gamemode.config.ConfigMode;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.GameModeConfigScreen;
import com.urntt.gamemode.config.MultiplayerMode;
import com.urntt.gamemode.config.MultiplayerRules;
import com.urntt.gamemode.config.ServerListScreen;
import com.urntt.gamemode.config.SettingKeys;
import com.urntt.gamemode.config.SettingValues;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Checks the configuration modes and the configuration screen: the mode button cycles through the offered modes and
 * saves each, the settings are editable only in custom mode and otherwise show the values that apply, and OneConfig
 * mode links to OneConfig. The test client runs without OneConfig by default and with it when Gradle is given
 * {@code -PoneConfigRuntime=true}; the checks follow whichever modes are offered. Screenshots show every settings
 * screen and the key bindings in both languages.
 */
@SuppressWarnings("UnstableApiUsage")
public final class GameModeConfigScreenGameTest implements FabricClientGameTest {
	/** A custom multiplayer mode that differs from the default, to show which settings apply in each mode. */
	private static final MultiplayerMode CUSTOM_MULTIPLAYER_MODE = MultiplayerMode.WHITELIST;
	private static final MultiplayerRules DEFAULT_RULES = Objects.requireNonNull(SettingValues.DEFAULTS.multiplayerRules());

	@Override
	public void runTest(final ClientGameTestContext context) {
		GameModeConfig config = GameModeClient.config();
		boolean oneConfigInstalled = FabricLoader.getInstance().isModLoaded(OneConfigBridge.MOD_ID);
		ConfigMode defaultMode = oneConfigInstalled ? ConfigMode.ONECONFIG : ConfigMode.CUSTOM;
		check(config.storedMode() == null, "no mode should be chosen by default, was " + config.storedMode());
		check(config.effectiveMode() == defaultMode, "the default mode should be " + defaultMode + ", was " + config.effectiveMode());

		checkModeCycle(context, config, defaultMode, oneConfigInstalled);

		configure(context, c -> {
			c.setMode(ConfigMode.CUSTOM);
			c.setMultiplayerMode(MultiplayerMode.WHITELIST);
			c.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		for (String language : List.of("en_us", "zh_cn")) {
			switchLanguage(context, language);
			takeScreenshots(context, language);
		}
		switchLanguage(context, "en_us");
		configure(context, c -> {
			c.setMultiplayerMode(DEFAULT_RULES.mode());
			c.setServers(DEFAULT_RULES.servers());
		});
		context.setScreen(() -> null);
	}

	/**
	 * Clicks the mode button through every offered mode, starting from and ending at the default mode. OneConfig mode
	 * is only offered while OneConfig is installed.
	 */
	private static void checkModeCycle(final ClientGameTestContext context, final GameModeConfig config, final ConfigMode defaultMode,
			final boolean oneConfigInstalled) {
		List<ConfigMode> modes = oneConfigInstalled
				? List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM, ConfigMode.ONECONFIG)
				: List.of(ConfigMode.DISABLED, ConfigMode.CUSTOM);
		configure(context, c -> c.setMultiplayerMode(CUSTOM_MULTIPLAYER_MODE));
		context.setScreen(() -> new GameModeConfigScreen(null));
		// The option list positions its widgets while rendering, so let it render before clicking.
		context.waitTicks(2);
		checkScreenFor(context, config, defaultMode);

		int start = modes.indexOf(defaultMode);
		for (int i = 1; i <= modes.size(); i++) {
			ConfigMode expected = modes.get((start + i) % modes.size());
			clickWidget(context, widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof ConfigMode);
			context.waitForScreen(GameModeConfigScreen.class);
			check(config.storedMode() == expected, "the mode button should switch to " + expected + ", was " + config.storedMode());
			check(GameModeConfig.load(GameModeConfig.defaultPath()).storedMode() == expected, "the " + expected + " mode should be saved");
			parkCursor(context);
			context.takeScreenshot("gamemode-config-" + expected.name().toLowerCase(Locale.ROOT));
			checkScreenFor(context, config, expected);
		}
		configure(context, c -> c.setMultiplayerMode(DEFAULT_RULES.mode()));
	}

	/**
	 * Checks that the open screen shows the settings of {@code mode}: editable in custom mode, greyed out with the
	 * values that apply otherwise, and without the multiplayer section in OneConfig mode.
	 */
	private static void checkScreenFor(final ClientGameTestContext context, final GameModeConfig config, final ConfigMode mode) {
		switch (mode) {
			case CUSTOM -> {
				checkSettings(context, true, true);
				check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.copyOf(config)), "custom mode should apply the custom settings");
				check(shownMultiplayerMode(context) == CUSTOM_MULTIPLAYER_MODE, "custom mode should show the custom multiplayer mode");
			}
			case DISABLED -> {
				checkSettings(context, false, true);
				check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.DISABLED), "Disabled mode should apply the disabled settings");
				check(shownMultiplayerMode(context) == DEFAULT_RULES.mode(), "Disabled mode should show the values that apply");
			}
			case ONECONFIG -> checkOneConfigMode(context);
		}
	}

	/**
	 * In OneConfig mode, OneConfig's settings apply: off by default, without multiplayer rules. The screen shows its
	 * feature settings greyed out, no multiplayer section, and links to the mod's OneConfig page.
	 */
	private static void checkOneConfigMode(final ClientGameTestContext context) {
		check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.ONECONFIG_DEFAULTS),
				"OneConfig mode should apply OneConfig's settings, which start at their defaults, instead of the custom ones");
		checkSettings(context, false, false);

		clickWidget(context, widget -> widget instanceof Button && hasLabel(widget, SettingKeys.OPEN_ONECONFIG));
		context.waitFor(client -> client.gui.screen() != null && !(client.gui.screen() instanceof GameModeConfigScreen));
		context.waitTicks(20);
		context.takeScreenshot("gamemode-oneconfig-page");
		String screen = context.computeOnClient(client -> client.gui.screen().getClass().getName());
		LOGGER.info("Open in OneConfig shows {}", screen);
		context.setScreen(() -> new GameModeConfigScreen(null));
		// The option list positions its widgets while rendering, so let it render before clicking.
		context.waitTicks(2);
	}

	private static void takeScreenshots(final ClientGameTestContext context, final String language) {
		context.setScreen(() -> new GameModeConfigScreen(null));
		parkCursor(context);
		context.takeScreenshot("gamemode-config-screen-" + language);

		context.setScreen(() -> new ServerListScreen(new GameModeConfigScreen(null), GameModeClient.config()));
		parkCursor(context);
		context.takeScreenshot("gamemode-server-list-" + language);

		context.setScreen(() -> new KeyBindsScreen(null, Minecraft.getInstance().options));
		context.getInput().scroll(-1000);
		parkCursor(context);
		context.takeScreenshot("gamemode-key-binds-" + language);
	}

	/** Clicks the first widget of the open screen that matches, with the real mouse. */
	private static void clickWidget(final ClientGameTestContext context, final Predicate<AbstractWidget> matches) {
		double[] position = context.computeOnClient(client -> {
			AbstractWidget widget = widgets(client).stream()
					.filter(matches)
					.findFirst()
					.orElseThrow(() -> new AssertionError("widget to click not found"));
			Window window = client.getWindow();
			double scale = window.getScreenWidth() / (double) window.getGuiScaledWidth();
			return new double[] {(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(position[0], position[1]);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	/** Moves the cursor into a corner, so that no tooltip covers the screenshot. */
	private static void parkCursor(final ClientGameTestContext context) {
		context.getInput().setCursorPos(0, 0);
		context.waitTicks(2);
	}

	private static MultiplayerMode shownMultiplayerMode(final ClientGameTestContext context) {
		return context.computeOnClient(client -> widgets(client).stream()
				.filter(widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof MultiplayerMode)
				.map(widget -> (MultiplayerMode) ((CycleButton<?>) widget).getValue())
				.findFirst()
				.orElseThrow(() -> new AssertionError("multiplayer mode button not found")));
	}

	/**
	 * Checks that the settings are all editable or all greyed out, and that the multiplayer section is there or not.
	 */
	private static void checkSettings(final ClientGameTestContext context, final boolean active, final boolean multiplayer) {
		List<Boolean> states = context.computeOnClient(client -> settingWidgets(client).stream().map(widget -> widget.active).toList());
		int expected = multiplayer ? 4 : 2;
		check(states.size() == expected, "expected " + expected + " setting widgets, found " + states.size());
		check(states.stream().allMatch(state -> state == active), "the settings should all be " + (active ? "editable" : "greyed out") + ": " + states);
	}

	/** Every widget that edits a setting: the two toggles and, with multiplayer rules, the multiplayer mode and the server list button. */
	private static List<AbstractWidget> settingWidgets(final Minecraft client) {
		return widgets(client).stream()
				.filter(widget -> widget instanceof CycleButton<?> cycle
						&& (cycle.getValue() instanceof Boolean || cycle.getValue() instanceof MultiplayerMode)
						|| widget instanceof Button && hasLabel(widget, SettingKeys.EDIT_SERVERS))
				.toList();
	}

	/**
	 * Returns whether {@code widget}'s label is the translation of {@code key}. Inactive buttons show a greyed copy of
	 * their label, so the label is compared by its key rather than by equality.
	 */
	private static boolean hasLabel(final AbstractWidget widget, final String key) {
		return widget.getMessage().getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key);
	}

	private static List<AbstractWidget> widgets(final Minecraft client) {
		Screen screen = Objects.requireNonNull(client.gui.screen(), "no screen is open");
		List<AbstractWidget> widgets = new ArrayList<>();
		collect(screen.children(), widgets);
		return widgets;
	}

	private static void collect(final List<? extends GuiEventListener> listeners, final List<AbstractWidget> widgets) {
		for (GuiEventListener listener : listeners) {
			if (listener instanceof AbstractWidget widget) {
				widgets.add(widget);
			}
			if (listener instanceof ContainerEventHandler container) {
				collect(container.children(), widgets);
			}
		}
	}
}
