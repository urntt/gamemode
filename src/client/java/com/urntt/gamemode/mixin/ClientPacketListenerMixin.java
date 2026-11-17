package com.urntt.gamemode.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.CommandDispatcher;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.command.ClientGameModeCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the client's packet listener:
 * <ul>
 * <li>{@code /gamemode <mode> @c} and {@code /gamemode @c}, typed or clicked, run on the client instead of being sent
 * to the server. Every other command is sent as in vanilla.</li>
 * <li>The mod's command nodes join the command tree the server sends, for suggestions and syntax highlighting.</li>
 * <li>The game modes and abilities the server sets are recorded. While a client game mode is active, the client
 * keeps using it instead, unless the server changed the game mode and "follow server changes" is on.</li>
 * </ul>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final String SET_LOCAL_MODE =
			"Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;setLocalMode(Lnet/minecraft/world/level/GameType;)V";
	private static final String SET_LOCAL_MODE_WITH_PREVIOUS =
			"Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;setLocalMode(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V";

	@Shadow
	private CommandDispatcher<ClientSuggestionProvider> commands;

	@Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
	private void gamemode$runTypedCommand(final String command, final CallbackInfo ci) {
		if (ClientGameModeCommands.handles(command)) {
			ClientGameModeCommands.execute(command);
			ci.cancel();
		}
	}

	@Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
	private void gamemode$runClickedCommand(final String command, final @Nullable Screen screenAfterCommand, final CallbackInfo ci) {
		if (ClientGameModeCommands.handles(command)) {
			// Vanilla returns to this screen after sending a clicked command it has no concerns about.
			Minecraft.getInstance().gui.setScreen(screenAfterCommand);
			ClientGameModeCommands.execute(command);
			ci.cancel();
		}
	}

	@Inject(method = "handleCommands", at = @At("TAIL"))
	private void gamemode$addCommandNodes(final ClientboundCommandsPacket packet, final CallbackInfo ci) {
		ClientGameModeCommands.addTo(this.commands);
	}

	/**
	 * Logging in starts a new session: the client uses the server's game mode, as in vanilla.
	 */
	@WrapOperation(method = "handleLogin", at = @At(value = "INVOKE", target = SET_LOCAL_MODE_WITH_PREVIOUS))
	private void gamemode$startSession(final MultiPlayerGameMode gameMode, final GameType serverGameMode,
			final @Nullable GameType previous, final Operation<Void> original) {
		GameModeClient.clientGameMode().onLogin(serverGameMode);
		original.call(gameMode, serverGameMode, previous);
	}

	/**
	 * Respawns and dimension changes set the game mode again. A kept client game mode also keeps its previous game
	 * mode.
	 */
	@WrapOperation(method = "handleRespawn", at = @At(value = "INVOKE", target = SET_LOCAL_MODE_WITH_PREVIOUS))
	private void gamemode$keepClientGameModeOnRespawn(final MultiPlayerGameMode gameMode, final GameType serverGameMode,
			final @Nullable GameType previous, final Operation<Void> original) {
		GameType used = GameModeClient.clientGameMode().onServerGameMode(Minecraft.getInstance(), serverGameMode);
		if (used == serverGameMode) {
			original.call(gameMode, serverGameMode, previous);
		} else {
			original.call(gameMode, used, gameMode.getPreviousPlayerMode());
		}
	}

	/**
	 * The server changes the game mode, for example through {@code /gamemode}.
	 */
	@WrapOperation(method = "handleGameEvent", at = @At(value = "INVOKE", target = SET_LOCAL_MODE))
	private void gamemode$keepClientGameModeOnChange(final MultiPlayerGameMode gameMode, final GameType serverGameMode,
			final Operation<Void> original) {
		original.call(gameMode, GameModeClient.clientGameMode().onServerGameMode(Minecraft.getInstance(), serverGameMode));
	}

	@WrapMethod(method = "handlePlayerAbilities")
	private void gamemode$keepClientAbilities(final ClientboundPlayerAbilitiesPacket packet, final Operation<Void> original) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!minecraft.packetProcessor().isSameThread()) {
			// Vanilla hands the packet to the client thread, which calls this method again.
			original.call(packet);
			return;
		}
		boolean wasFlying = minecraft.player != null && minecraft.player.getAbilities().flying;
		original.call(packet);
		GameModeClient.clientGameMode().onServerAbilities(minecraft, packet, wasFlying);
	}
}
