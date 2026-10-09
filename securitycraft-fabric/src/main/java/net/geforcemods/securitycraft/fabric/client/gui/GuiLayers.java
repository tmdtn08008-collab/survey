package net.geforcemods.securitycraft.fabric.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * Layered screens, replacing Minecraft#pushGuiLayer and Minecraft#popGuiLayer that NeoForge adds. Pushing a screen keeps
 * the current screen open (for example a container screen, whose menu then stays open) and shows the new screen on top of
 * it. Only the top screen ({@link Minecraft#screen}) receives input and ticks; the others are drawn underneath it. Opening
 * a screen normally (Minecraft#setScreen) closes all layers, and closing a layered screen (Screen#onClose) returns to the
 * screen below it. The mixins in fabricmixin.client wire this into Minecraft, Screen and GameRenderer.
 */
public final class GuiLayers {
	/**
	 * The screens below the current screen, the most recently covered one first
	 */
	private static final Deque<Screen> LAYERS = new ArrayDeque<>();

	private GuiLayers() {}

	/**
	 * Shows the given screen on top of the current one, without closing the current one.
	 */
	public static void push(Screen screen) {
		Minecraft mc = Minecraft.getInstance();

		Objects.requireNonNull(screen);

		if (mc.screen != null)
			LAYERS.push(mc.screen);

		mc.screen = screen;
		screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
		mc.getNarrator().sayNow(screen.getNarrationMessage());
	}

	/**
	 * Closes the current screen and shows the one below it. If there is none, all screens are closed.
	 */
	public static void pop() {
		Minecraft mc = Minecraft.getInstance();

		if (LAYERS.isEmpty()) {
			mc.setScreen(null);
			return;
		}

		removeTopLayer(mc);

		if (mc.screen != null)
			mc.getNarrator().sayNow(mc.screen.getNarrationMessage());
	}

	/**
	 * Closes every screen above the bottom one, leaving the bottom one as the current screen. Called when a screen is opened
	 * normally, which then closes the bottom screen itself.
	 */
	public static void clear(Minecraft mc) {
		while (!LAYERS.isEmpty()) {
			removeTopLayer(mc);
		}
	}

	public static void resize(Minecraft mc, int width, int height) {
		for (Screen layer : LAYERS) {
			layer.resize(mc, width, height);
		}
	}

	/**
	 * Draws the covered screens from the bottom up and then the current screen on top of them. The covered screens do not
	 * get the real mouse position, so that none of their widgets show up as hovered.
	 *
	 * @param drawTopScreen Draws the current screen (the vanilla call, so that other mods' wrappers around it still run)
	 */
	public static void drawLayers(GuiGraphics guiGraphics, float partialTick, Runnable drawTopScreen) {
		if (!LAYERS.isEmpty()) {
			Iterator<Screen> bottomUp = LAYERS.descendingIterator();

			while (bottomUp.hasNext()) {
				bottomUp.next().renderWithTooltip(guiGraphics, Integer.MAX_VALUE, Integer.MAX_VALUE, partialTick);
				//make sure everything of the covered screen is drawn and can not end up in front of the screen above it
				guiGraphics.flush();
				RenderSystem.clear(256, Minecraft.ON_OSX);
			}
		}

		drawTopScreen.run();
	}

	private static void removeTopLayer(Minecraft mc) {
		if (mc.screen != null)
			mc.screen.removed();

		mc.screen = LAYERS.pop();
	}
}
