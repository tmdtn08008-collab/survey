package net.geforcemods.securitycraft.fabric.client.gui;

import java.util.List;

import com.mojang.blaze3d.vertex.Tesselator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * A rectangular, vertically scrollable area with a scroll bar on its right edge. Fabric replacement for the scroll panel
 * widget NeoForge ships, with the same protected API, so SecurityCraft's lists keep working unchanged. Subclasses provide
 * the height of their content and draw it; this class takes care of clipping, scrolling (mouse wheel and dragging the bar)
 * and translating clicks into content coordinates.
 */
public abstract class ScrollPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
	private static final int DEFAULT_BORDER = 4;
	private static final int DEFAULT_BAR_WIDTH = 6;
	private static final int MIN_BAR_HEIGHT = 32;
	private final Minecraft client;
	protected final int width;
	protected final int height;
	protected final int top;
	protected final int bottom;
	protected final int right;
	protected final int left;
	protected final int border;
	protected float scrollDistance;
	protected boolean captureMouse = true;
	private final int barWidth;
	private final int barLeft;
	private final int barBackgroundColor;
	private final int barColor;
	private final int barHighlightColor;
	private boolean draggingBar;

	public ScrollPanel(Minecraft client, int width, int height, int top, int left) {
		this(client, width, height, top, left, DEFAULT_BORDER);
	}

	public ScrollPanel(Minecraft client, int width, int height, int top, int left, int border) {
		this(client, width, height, top, left, border, DEFAULT_BAR_WIDTH);
	}

	public ScrollPanel(Minecraft client, int width, int height, int top, int left, int border, int barWidth) {
		this(client, width, height, top, left, border, barWidth, 0xFF000000, 0xFF808080, 0xFFC0C0C0);
	}

	public ScrollPanel(Minecraft client, int width, int height, int top, int left, int border, int barWidth, int barBackgroundColor, int barColor, int barHighlightColor) {
		this.client = client;
		this.width = width;
		this.height = height;
		this.top = top;
		this.left = left;
		this.bottom = top + height;
		this.right = left + width;
		this.border = border;
		this.barWidth = barWidth;
		this.barLeft = right - barWidth;
		this.barBackgroundColor = barBackgroundColor;
		this.barColor = barColor;
		this.barHighlightColor = barHighlightColor;
	}

	/**
	 * @return The total height of the content of this panel, in GUI pixels
	 */
	protected abstract int getContentHeight();

	/**
	 * Draws the content of this panel. Drawing is clipped to the panel's bounds.
	 *
	 * @param entryRight The right edge of the panel
	 * @param relativeY The y coordinate at which the content starts, already offset by the current scroll distance
	 */
	protected abstract void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY, Tesselator tess, int mouseX, int mouseY);

	/**
	 * Draws the background of the panel. Drawing is clipped to the panel's bounds.
	 */
	protected void drawBackground(GuiGraphics guiGraphics, Tesselator tess, float partialTick) {
		Screen.renderMenuBackgroundTexture(guiGraphics, Screen.MENU_BACKGROUND, left, top, 0.0F, 0.0F, width, height);
	}

	/**
	 * Called when the content area has been clicked.
	 *
	 * @param mouseX The x position of the click, relative to the left edge of the panel
	 * @param mouseY The y position of the click, relative to the start of the content (scroll distance included)
	 * @return true if the click was handled
	 */
	protected boolean clickPanel(double mouseX, double mouseY, int button) {
		return false;
	}

	/**
	 * @return How far one notch of the mouse wheel scrolls
	 */
	protected int getScrollAmount() {
		return 20;
	}

	protected void drawGradientRect(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int colorTop, int colorBottom) {
		guiGraphics.fillGradient(left, top, right, bottom, colorTop, colorBottom);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		Tesselator tesselator = Tesselator.getInstance();
		int contentStart = top + border - (int) scrollDistance;
		int overflow = getContentHeight() + border - height;

		guiGraphics.enableScissor(left, top, right, bottom);
		drawBackground(guiGraphics, tesselator, partialTick);
		drawPanel(guiGraphics, right, contentStart, tesselator, mouseX, mouseY);

		if (overflow > 0) {
			int barHeight = getBarHeight();
			int barTop = Math.max(top, (int) scrollDistance * (height - barHeight) / overflow + top);

			guiGraphics.fill(barLeft, top, barLeft + barWidth, bottom, barBackgroundColor);
			guiGraphics.fill(barLeft, barTop, barLeft + barWidth, barTop + barHeight, barColor);
			guiGraphics.fill(barLeft, barTop, barLeft + barWidth - 1, barTop + barHeight - 1, barHighlightColor);
		}

		guiGraphics.disableScissor();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button))
			return true;

		draggingBar = button == 0 && mouseX >= barLeft && mouseX < right && mouseY >= top && mouseY < bottom;

		if (draggingBar)
			return true;

		double contentY = mouseY - top + (int) scrollDistance - border;

		//only clicks that hit the content (not the empty space below it) are passed on
		if (mouseX >= left && mouseX < right && contentY < getContentHeight())
			return clickPanel(mouseX - left, contentY, button);

		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (super.mouseReleased(mouseX, mouseY, button))
			return true;

		boolean wasDragging = draggingBar;

		draggingBar = false;
		return wasDragging;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (!draggingBar)
			return false;

		scrollDistance += getMaxScroll() * (dragY / (height - getBarHeight()));
		clampScrollDistance();
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY == 0.0D)
			return false;

		scrollDistance -= (float) (scrollY * getScrollAmount());
		clampScrollDistance();
		return true;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
	}

	@Override
	public List<? extends GuiEventListener> children() {
		return List.of();
	}

	private int getMaxScroll() {
		return getContentHeight() - (height - border);
	}

	private void clampScrollDistance() {
		int maxScroll = getMaxScroll();

		//content that is shorter than the panel ends up centered, matching the behavior of NeoForge's panel
		if (maxScroll < 0)
			maxScroll /= 2;

		if (scrollDistance < 0.0F)
			scrollDistance = 0.0F;

		if (scrollDistance > maxScroll)
			scrollDistance = maxScroll;
	}

	private int getBarHeight() {
		int barHeight = height * height / Math.max(1, getContentHeight());

		return Math.min(Math.max(barHeight, MIN_BAR_HEIGHT), height - border * 2);
	}
}
