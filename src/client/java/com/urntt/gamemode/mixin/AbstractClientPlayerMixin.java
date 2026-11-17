package com.urntt.gamemode.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.urntt.gamemode.GameModeClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the local player the client game mode while one is active. Vanilla reads a client player's game mode from the
 * player list, which keeps the server's game mode; {@code Player.isSpectator()} and {@code isCreative()} build on it,
 * so the local player gets, for example, spectator noclip. Other players are not affected.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@ModifyReturnValue(method = "gameMode", at = @At("RETURN"))
	private @Nullable GameType gamemode$useClientGameMode(final @Nullable GameType gameMode) {
		if ((Object) this instanceof LocalPlayer) {
			GameType clientGameMode = GameModeClient.controller().clientGameMode();
			if (clientGameMode != null) {
				return clientGameMode;
			}
		}
		return gameMode;
	}
}
