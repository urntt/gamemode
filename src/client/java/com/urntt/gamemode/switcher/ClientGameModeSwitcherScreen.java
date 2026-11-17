package com.urntt.gamemode.switcher;

import com.urntt.gamemode.ClientGameMode;
import com.urntt.gamemode.GameModeClient;
import com.urntt.gamemode.GameModeController;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The client game mode switcher. It looks and works like vanilla's game mode switcher (F3 + F4): the switcher key
 * cycles the game modes, the mouse can pick one, and releasing the debug modifier applies the selection. Instead of
 * asking the server, it sets the client game mode through {@link ClientGameMode}. The selected game mode's name says
 * whether choosing it sets a client game mode or returns to the server's.
 */
public final class ClientGameModeSwitcherScreen extends Screen {
	private static final Identifier SLOT_SPRITE = Identifier.withDefaultNamespace("gamemode_switcher/slot");
	private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("gamemode_switcher/selection");
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/gamemode_switcher.png");
	private static final String CLIENT_NAME_KEY = "gamemode.switcher.client";
	private static final String SERVER_NAME_KEY = "gamemode.switcher.server";
	/** The game modes in vanilla's switcher order, which is also the order the switcher key cycles through. */
	private static final List<Icon> ICONS = List.of(
			new Icon(GameType.CREATIVE, new ItemStack(Blocks.GRASS_BLOCK)),
			new Icon(GameType.SURVIVAL, new ItemStack(Items.IRON_SWORD)),
			new Icon(GameType.ADVENTURE, new ItemStack(Items.BURIED_TREASURE_MAP)),
			new Icon(GameType.SPECTATOR, new ItemStack(Items.ENDER_EYE)));

	// Vanilla's switcher layout.
	private static final int TEXTURE_SIZE = 128;
	private static final int BACKGROUND_WIDTH = 125;
	private static final int BACKGROUND_HEIGHT = 75;
	private static final int SLOT_SIZE = 26;
	private static final int SLOT_SPACING = 31;
	private static final int ICON_OFFSET = 5;
	private static final int NAME_OFFSET_Y = 20;
	private static final int HELP_OFFSET_Y = 5;
	private static final int BACKGROUND_OFFSET_Y = 27;
	private static final int ALL_SLOTS_WIDTH = ICONS.size() * SLOT_SPACING - (SLOT_SPACING - SLOT_SIZE);

	private final GameType defaultSelected;
	private final List<Slot> slots = new ArrayList<>();
	private GameType selected;
	private int firstMouseX;
	private int firstMouseY;
	private boolean setFirstMousePos;

	/**
	 * @param gameMode the client's current game mode handler, which knows the current and previous game modes
	 */
	public ClientGameModeSwitcherScreen(final MultiPlayerGameMode gameMode) {
		super(GameNarrator.NO_TITLE);
		this.defaultSelected = defaultSelected(gameMode);
		this.selected = this.defaultSelected;
	}

	/**
	 * Preselects the previous game mode, as vanilla does, or otherwise toggles between creative and survival.
	 */
	private static GameType defaultSelected(final MultiPlayerGameMode gameMode) {
		GameType previous = gameMode.getPreviousPlayerMode();
		if (previous != null) {
			return previous;
		}
		return gameMode.getPlayerMode() == GameType.CREATIVE ? GameType.SURVIVAL : GameType.CREATIVE;
	}

	/** The game mode that releasing the debug modifier would apply. */
	public GameType selected() {
		return this.selected;
	}

	@Override
	protected void init() {
		super.init();
		this.slots.clear();
		this.selected = this.defaultSelected;
		for (int i = 0; i < ICONS.size(); i++) {
			this.slots.add(new Slot(ICONS.get(i), this.width / 2 - ALL_SLOTS_WIDTH / 2 + i * SLOT_SPACING, this.height / 2 - SLOT_SPACING));
		}
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		graphics.centeredText(this.font, this.selectedName(), this.width / 2, this.height / 2 - SLOT_SPACING - NAME_OFFSET_Y, -1);
		KeyMapping switcherKey = GameModeClient.switcherKey();
		Component selectNext = Component.translatable("debug.gamemodes.select_next",
				switcherKey.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA));
		graphics.centeredText(this.font, selectNext, this.width / 2, this.height / 2 + HELP_OFFSET_Y, -1);
		if (!this.setFirstMousePos) {
			this.firstMouseX = mouseX;
			this.firstMouseY = mouseY;
			this.setFirstMousePos = true;
		}

		boolean mouseMoved = this.firstMouseX != mouseX || this.firstMouseY != mouseY;
		for (Slot slot : this.slots) {
			slot.extractRenderState(graphics, mouseX, mouseY, a);
			slot.setSelected(this.selected == slot.icon.gameMode());
			if (mouseMoved && slot.isHoveredOrFocused()) {
				this.selected = slot.icon.gameMode();
			}
		}
	}

	/**
	 * The selected game mode's name, marked as the server's game mode if choosing it ends the client game mode.
	 */
	private Component selectedName() {
		boolean server = this.selected == GameModeClient.controller().serverGameMode();
		return Component.translatable(server ? SERVER_NAME_KEY : CLIENT_NAME_KEY, this.selected.getLongDisplayName());
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		int x = this.width / 2 - BACKGROUND_WIDTH / 2;
		int y = this.height / 2 - SLOT_SPACING - BACKGROUND_OFFSET_Y;
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (GameModeClient.switcherKey().matches(event)) {
			this.setFirstMousePos = false;
			this.selected = next(this.selected);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (this.minecraft.options.keyDebugModifier.matches(event)) {
			this.applySelection();
			this.minecraft.gui.setScreen(null);
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent event) {
		if (this.minecraft.options.keyDebugModifier.matchesMouse(event)) {
			this.applySelection();
			this.minecraft.gui.setScreen(null);
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * Applies the selection unless it is the game mode the client already uses, and reports the outcome in the chat.
	 */
	private void applySelection() {
		if (this.minecraft.player == null || this.minecraft.gameMode == null || this.selected == this.minecraft.gameMode.getPlayerMode()) {
			return;
		}
		GameModeController.Selection selection = GameModeClient.clientGameMode().select(this.minecraft, this.selected);
		this.minecraft.player.sendSystemMessage(ClientGameMode.feedback(selection, this.selected));
	}

	private static GameType next(final GameType gameMode) {
		for (int i = 0; i < ICONS.size(); i++) {
			if (ICONS.get(i).gameMode() == gameMode) {
				return ICONS.get((i + 1) % ICONS.size()).gameMode();
			}
		}
		return ICONS.getFirst().gameMode();
	}

	private record Icon(GameType gameMode, ItemStack stack) {
	}

	/**
	 * One game mode slot, drawn like vanilla's.
	 */
	private static final class Slot extends AbstractWidget {
		private final Icon icon;
		private boolean isSelected;

		private Slot(final Icon icon, final int x, final int y) {
			super(x, y, SLOT_SIZE, SLOT_SIZE, icon.gameMode().getLongDisplayName());
			this.icon = icon;
		}

		@Override
		public void extractWidgetRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE, this.getX(), this.getY(), SLOT_SIZE, SLOT_SIZE);
			if (this.isSelected) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE, this.getX(), this.getY(), SLOT_SIZE, SLOT_SIZE);
			}
			graphics.item(this.icon.stack(), this.getX() + ICON_OFFSET, this.getY() + ICON_OFFSET);
		}

		@Override
		protected void updateWidgetNarration(final NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}

		@Override
		public boolean isHoveredOrFocused() {
			return super.isHoveredOrFocused() || this.isSelected;
		}

		private void setSelected(final boolean isSelected) {
			this.isSelected = isSelected;
		}
	}
}
