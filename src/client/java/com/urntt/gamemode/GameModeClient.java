package com.urntt.gamemode;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.GameModeConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class GameModeClient implements ClientModInitializer {
	public static final String MOD_ID = "gamemode";
	public static final String SWITCHER_KEY_NAME = "key.gamemode.switcher";
	public static final String TOGGLE_KEY_NAME = "key.gamemode.toggle";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.gamemode.open_settings";
	/**
	 * Default key of the client game mode switcher, pressed while vanilla's debug modifier (F3) is held. Vanilla binds
	 * no debug key to it, and alone it keeps changing the camera perspective.
	 */
	public static final String SWITCHER_DEFAULT_KEY = "key.keyboard.f5";

	/** Translation key of the mod's state, shared by the toggle message and the configuration screen. */
	public static final String FEATURE_NAME_KEY = "options.gamemode.enabled";
	public static final Component FEATURE_NAME = Component.translatable(FEATURE_NAME_KEY);
	/** Client game modes are ruled out by the multiplayer mode. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.gamemode.blocked");
	/** The player turned the whole mod off. */
	public static final Component TURNED_OFF_MESSAGE = Component.translatable("message.gamemode.turned_off");

	private static GameModeConfig config;
	private static GameModeController controller;
	private static ClientGameMode clientGameMode;
	private static KeyMapping switcherKey;

	@Override
	public void onInitializeClient() {
		config = GameModeConfig.load(GameModeConfig.defaultPath());
		controller = new GameModeController(config);
		clientGameMode = new ClientGameMode(controller);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		// Handled together with vanilla's debug keys in KeyboardHandlerMixin, never through consumeClick().
		switcherKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(SWITCHER_KEY_NAME, InputConstants.getKey(SWITCHER_DEFAULT_KEY).getValue(), category));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(TOGGLE_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				toggle(client);
			}
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new GameModeConfigScreen(client.gui.screen()));
			}
			clientGameMode.sync(client);
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> controller.onJoin(Scene.of(client)));
		// The disconnect event may arrive on the network thread; the state is only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(controller::onDisconnect));
	}

	public static GameModeConfig config() {
		return config;
	}

	public static GameModeController controller() {
		return controller;
	}

	public static ClientGameMode clientGameMode() {
		return clientGameMode;
	}

	/** The key that opens the client game mode switcher while the debug modifier is held. */
	public static KeyMapping switcherKey() {
		return switcherKey;
	}

	/**
	 * Returns the message that explains why client game modes are not available, or {@code null} if they are.
	 */
	public static @Nullable Component unavailableMessage(final GameModeController.Availability availability) {
		return switch (availability) {
			case AVAILABLE -> null;
			case BLOCKED -> BLOCKED_MESSAGE;
			case TURNED_OFF -> TURNED_OFF_MESSAGE;
		};
	}

	/**
	 * Flips the mod's state and shows the new state, or why it did not change, on the action bar. Turning the mod off
	 * ends the client game mode at once.
	 */
	private static void toggle(final Minecraft client) {
		GameModeController.ToggleResult result = controller.toggle();
		clientGameMode.sync(client);
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED -> CommonComponents.optionStatus(FEATURE_NAME, true);
			case DISABLED -> CommonComponents.optionStatus(FEATURE_NAME, false);
			case BLOCKED -> BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
