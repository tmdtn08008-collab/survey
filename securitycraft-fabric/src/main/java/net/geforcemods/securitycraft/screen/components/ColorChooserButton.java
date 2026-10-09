package net.geforcemods.securitycraft.screen.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.geforcemods.securitycraft.fabric.client.gui.GuiLayers;

public class ColorChooserButton extends Button {
	private final ColorChooser colorChooser;

	public ColorChooserButton(int x, int y, int width, int height, ColorChooser colorChooser) {
		super(x, y, width, height, Component.empty(), b -> {}, s -> Component.empty());

		this.colorChooser = colorChooser;
	}

	@Override
	public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		int color = colorChooser.getARGBColor();

		super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.fillGradient(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, color, color);
	}

	@Override
	public void onPress() {
		if (colorChooser.disabled)
			GuiLayers.push(colorChooser);
		else
			GuiLayers.pop();

		colorChooser.disabled = !colorChooser.disabled;
	}
}
