package com.urntt.gamemode;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Applies the {@link GameModeController}'s decisions to the client. A client game mode is the local game mode of
 * {@link MultiPlayerGameMode} together with the abilities that game mode grants; the local player's own game mode
 * follows it through {@code AbstractClientPlayerMixin}. The server's game mode and the abilities it last sent are
 * remembered, so that ending the client game mode restores them. Nothing here sends anything to the server.
 */
public final class ClientGameMode {
	public static final String SET_KEY = "gamemode.command.set";
	public static final String SERVER_KEY = "gamemode.command.server";
	public static final String SERVER_CHANGED_KEY = "gamemode.message.server_changed";

	private final GameModeController controller;
	/** The abilities the server last sent in this session, or {@code null} before it sent any. */
	private @Nullable ServerAbilities serverAbilities;

	public ClientGameMode(final GameModeController controller) {
		this.controller = controller;
	}

	/**
	 * Called when the client logs in with the game mode the server sets, which it applies as in vanilla.
	 */
	public void onLogin(final GameType serverGameMode) {
		this.controller.onLogin(serverGameMode);
		this.serverAbilities = null;
	}

	/**
	 * Called when the server sets the player's game mode after login. Returns the game mode the client should use
	 * instead: the client game mode while it stays active, otherwise {@code serverGameMode}.
	 */
	public GameType onServerGameMode(final Minecraft minecraft, final GameType serverGameMode) {
		return switch (this.controller.onServerGameMode(serverGameMode)) {
			case NO_CLIENT_GAME_MODE -> serverGameMode;
			case KEPT -> Objects.requireNonNull(this.controller.clientGameMode());
			case ENDED -> {
				send(minecraft, Component.translatable(SERVER_CHANGED_KEY, serverGameMode.getLongDisplayName()));
				yield serverGameMode;
			}
		};
	}

	/**
	 * Called after the client applied abilities the server sent. While a client game mode is active, gives the player
	 * that game mode's abilities again; in creative, the player keeps flying or not as before the packet.
	 */
	public void onServerAbilities(final Minecraft minecraft, final ClientboundPlayerAbilitiesPacket packet, final boolean wasFlying) {
		this.serverAbilities = ServerAbilities.of(packet);
		GameType clientGameMode = this.controller.clientGameMode();
		LocalPlayer player = minecraft.player;
		if (clientGameMode == null || player == null) {
			return;
		}
		Abilities abilities = player.getAbilities();
		clientGameMode.updatePlayerAbilities(abilities);
		if (clientGameMode == GameType.CREATIVE) {
			abilities.flying = wasFlying;
		}
	}

	/**
	 * Makes {@code gameMode} the client game mode, or returns to the server's game mode if it is the server's.
	 * Changes nothing unless client game modes are available; the result says which happened.
	 */
	public GameModeController.Selection select(final Minecraft minecraft, final GameType gameMode) {
		GameModeController.Selection selection = this.controller.select(gameMode);
		switch (selection) {
			case CLIENT -> this.apply(minecraft, gameMode);
			case SERVER -> this.restore(minecraft);
			case DISABLED, BLOCKED -> {
			}
		}
		return selection;
	}

	/**
	 * Returns the message that reports {@code selection}, the outcome of selecting {@code gameMode}.
	 */
	public static Component feedback(final GameModeController.Selection selection, final GameType gameMode) {
		return switch (selection) {
			case CLIENT -> Component.translatable(SET_KEY, gameMode.getLongDisplayName());
			case SERVER -> Component.translatable(SERVER_KEY, gameMode.getLongDisplayName());
			case DISABLED -> GameModeClient.DISABLED_MESSAGE;
			case BLOCKED -> GameModeClient.BLOCKED_MESSAGE;
		};
	}

	/**
	 * Ends the client game mode as soon as client game modes are no longer available. Called every client tick and
	 * right after the state changes.
	 */
	public void sync(final Minecraft minecraft) {
		if (minecraft.player != null && minecraft.gameMode != null && this.controller.endIfUnavailable()) {
			this.restore(minecraft);
		}
	}

	private void apply(final Minecraft minecraft, final GameType gameMode) {
		MultiPlayerGameMode localGameMode = Objects.requireNonNull(minecraft.gameMode);
		GameType before = localGameMode.getPlayerMode();
		localGameMode.setLocalMode(gameMode);
		if (gameMode != before) {
			Objects.requireNonNull(minecraft.player).onGameModeChanged(gameMode);
		}
	}

	/**
	 * Returns the client to the server's game mode and to the abilities the server last sent.
	 */
	private void restore(final Minecraft minecraft) {
		GameType serverGameMode = this.controller.serverGameMode();
		LocalPlayer player = minecraft.player;
		if (serverGameMode == null || player == null || minecraft.gameMode == null) {
			return;
		}
		GameType before = minecraft.gameMode.getPlayerMode();
		minecraft.gameMode.setLocalMode(serverGameMode);
		if (this.serverAbilities != null) {
			this.serverAbilities.applyTo(player.getAbilities());
		}
		if (serverGameMode != before) {
			player.onGameModeChanged(serverGameMode);
		}
	}

	private static void send(final Minecraft minecraft, final Component message) {
		if (minecraft.player != null) {
			minecraft.player.sendSystemMessage(message);
		}
	}

	/**
	 * The abilities a {@link ClientboundPlayerAbilitiesPacket} sets, apart from the speeds, which a client game mode
	 * does not change.
	 */
	private record ServerAbilities(boolean flying, boolean instabuild, boolean invulnerable, boolean mayfly) {
		static ServerAbilities of(final ClientboundPlayerAbilitiesPacket packet) {
			return new ServerAbilities(packet.isFlying(), packet.canInstabuild(), packet.isInvulnerable(), packet.canFly());
		}

		void applyTo(final Abilities abilities) {
			abilities.flying = this.flying;
			abilities.instabuild = this.instabuild;
			abilities.invulnerable = this.invulnerable;
			abilities.mayfly = this.mayfly;
		}
	}
}
