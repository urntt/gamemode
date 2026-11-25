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

	@Override
	public void runTest(final ClientGameTestContext context) {
		GameModeConfig config = GameModeClient.config();
		check(config.storedMode() == ConfigMode.CUSTOM, "the default mode should be custom, was " + config.storedMode());
		boolean oneConfigInstalled = FabricLoader.getInstance().isModLoaded(OneConfigBridge.MOD_ID);

		context.setScreen(() -> new GameModeConfigScreen(null));
		parkCursor(context);
		check(settingsActive(context), "settings should be editable in custom mode");
		checkModeCycle(context, config, oneConfigInstalled);

		configure(context, c -> {
			c.setMultiplayerMode(MultiplayerMode.WHITELIST);
			c.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		for (String language : List.of("en_us", "zh_cn")) {
			switchLanguage(context, language);
			takeScreenshots(context, language);
		}
		switchLanguage(context, "en_us");
		configure(context, c -> {
			c.setMultiplayerMode(SettingValues.DEFAULTS.multiplayerMode());
			c.setServers(SettingValues.DEFAULTS.servers());
		});
		context.setScreen(() -> null);
	}

	/**
	 * Clicks the mode button through every offered mode and back to custom. OneConfig mode is only offered while
	 * OneConfig is installed.
	 */
	private static void checkModeCycle(final ClientGameTestContext context, final GameModeConfig config, final boolean oneConfigInstalled) {
		List<ConfigMode> modes = oneConfigInstalled
				? List.of(ConfigMode.CUSTOM, ConfigMode.ONECONFIG, ConfigMode.DISABLED)
				: List.of(ConfigMode.CUSTOM, ConfigMode.DISABLED);
		configure(context, c -> c.setMultiplayerMode(CUSTOM_MULTIPLAYER_MODE));
		context.setScreen(() -> new GameModeConfigScreen(null));
		// The option list positions its widgets while rendering, so let it render before clicking.
		context.waitTicks(2);
		check(shownMultiplayerMode(context) == CUSTOM_MULTIPLAYER_MODE, "custom mode should show the custom multiplayer mode");

		for (int i = 1; i <= modes.size(); i++) {
			ConfigMode expected = modes.get(i % modes.size());
			clickWidget(context, widget -> widget instanceof CycleButton<?> cycle && cycle.getValue() instanceof ConfigMode);
			context.waitForScreen(GameModeConfigScreen.class);
			check(config.storedMode() == expected, "the mode button should switch to " + expected + ", was " + config.storedMode());
			check(GameModeConfig.load(GameModeConfig.defaultPath()).storedMode() == expected, "the " + expected + " mode should be saved");
			if (expected == ConfigMode.CUSTOM) {
				check(settingsActive(context), "settings should be editable again in custom mode");
				check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.copyOf(config)), "custom mode should apply the custom settings");
			} else {
				check(settingsInactive(context), "settings should be greyed out in " + expected + " mode");
			}
			parkCursor(context);
			context.takeScreenshot("gamemode-config-" + expected.name().toLowerCase(Locale.ROOT));

			if (expected == ConfigMode.DISABLED) {
				check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.DISABLED), "Disabled mode should apply the disabled settings");
				check(shownMultiplayerMode(context) == SettingValues.DISABLED.multiplayerMode(), "Disabled mode should show the values that apply");
			} else if (expected == ConfigMode.ONECONFIG) {
				checkOneConfigMode(context);
			}
		}
		configure(context, c -> c.setMultiplayerMode(SettingValues.DEFAULTS.multiplayerMode()));
	}

	/** In OneConfig mode, OneConfig's settings apply and the screen shows them and links to the mod's OneConfig page. */
	private static void checkOneConfigMode(final ClientGameTestContext context) {
		check(SettingValues.copyOf(GameModeClient.settings()).equals(SettingValues.DEFAULTS),
				"OneConfig mode should apply OneConfig's settings, which start at the defaults, instead of the custom ones");
		check(shownMultiplayerMode(context) == SettingValues.DEFAULTS.multiplayerMode(), "OneConfig mode should show OneConfig's values");

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

	private static boolean settingsActive(final ClientGameTestContext context) {
		return context.computeOnClient(client -> settingWidgets(client).stream().allMatch(widget -> widget.active));
	}

	private static boolean settingsInactive(final ClientGameTestContext context) {
		return context.computeOnClient(client -> settingWidgets(client).stream().noneMatch(widget -> widget.active));
	}

	/** Every widget that edits a setting: the two toggles, the multiplayer mode and the server list button. */
	private static List<AbstractWidget> settingWidgets(final Minecraft client) {
		List<AbstractWidget> settings = widgets(client).stream()
				.filter(widget -> widget instanceof CycleButton<?> cycle
						&& (cycle.getValue() instanceof Boolean || cycle.getValue() instanceof MultiplayerMode)
						|| widget instanceof Button && hasLabel(widget, SettingKeys.EDIT_SERVERS))
				.toList();
		check(settings.size() == 4, "expected the two toggles, the multiplayer mode and the server list button, found " + settings.size());
		return settings;
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
