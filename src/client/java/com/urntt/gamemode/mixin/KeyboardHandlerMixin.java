package com.urntt.gamemode.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.switcher.ClientGameModeSwitcherScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Handles the client game mode switcher key together with vanilla's debug keys, which only run while the debug
 * modifier (F3) is held. Like vanilla's game mode switcher key (F4), it counts as a debug action, so the key's own
 * binding, the camera perspective for the default F5, does not also fire, and releasing the modifier does not toggle
 * the debug overlay. Where client game modes are not available, it shows why in the chat, as vanilla does without
 * permission. While the "switcher key" setting is off, the mod leaves the key to vanilla.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	private void debugFeedbackComponent(final Component component) {
		throw new AssertionError("shadowed");
	}

	@ModifyReturnValue(method = "handleDebugKeys", at = @At("RETURN"))
	private boolean gamemode$openClientGameModeSwitcher(final boolean handled, @Local(argsOnly = true) final KeyEvent event) {
		if (!GameModeClient.settings().switcherKey() || !GameModeClient.switcherKey().matches(event)
				|| this.minecraft.level == null || this.minecraft.gui.screen() != null) {
			return handled;
		}
		Component unavailable = GameModeClient.unavailableMessage(GameModeClient.controller().availability());
		if (unavailable != null) {
			this.debugFeedbackComponent(unavailable);
		} else if (this.minecraft.canSwitchGameMode()) {
			this.minecraft.gui.setScreen(new ClientGameModeSwitcherScreen(this.minecraft.gameMode));
		}
		return true;
	}
}
