package com.urntt.gamemode;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.gamemode.config.GameModeConfig;
import com.urntt.gamemode.config.GameModeConfigScreen;
import com.urntt.gamemode.config.GameModeSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public final class GameModeClient implements ClientModInitializer {
	public static final String MOD_ID = "gamemode";
	public static final String SWITCHER_KEY_NAME = "key.gamemode.switcher";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.gamemode.open_settings";
	/**
	 * Default key of the client game mode switcher, pressed while vanilla's debug modifier (F3) is held. Vanilla binds
	 * no debug key to it, and alone it keeps changing the camera perspective.
	 */
	public static final String SWITCHER_DEFAULT_KEY = "key.keyboard.f5";

	/** Client game modes are turned off by the configuration mode or OneConfig's toggle. */
	public static final Component DISABLED_MESSAGE = Component.translatable("message.gamemode.disabled");
	/** Client game modes are ruled out by the multiplayer mode. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.gamemode.blocked");

	private static GameModeConfig config;
	private static GameModeController controller;
	private static ClientGameMode clientGameMode;
	private static KeyMapping switcherKey;

	@Override
	public void onInitializeClient() {
		config = GameModeConfig.load(GameModeConfig.defaultPath());
		controller = new GameModeController(config::effective);
		clientGameMode = new ClientGameMode(controller);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		// Handled together with vanilla's debug keys in KeyboardHandlerMixin, never through consumeClick().
		switcherKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(SWITCHER_KEY_NAME, InputConstants.getKey(SWITCHER_DEFAULT_KEY).getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openSettingsKey.consumeClick()) {
				GameModeConfigScreen.open(client);
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

	/** The settings that currently apply, according to the configuration mode. */
	public static GameModeSettings settings() {
		return config.effective();
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
			case DISABLED -> DISABLED_MESSAGE;
			case BLOCKED -> BLOCKED_MESSAGE;
		};
	}
}
