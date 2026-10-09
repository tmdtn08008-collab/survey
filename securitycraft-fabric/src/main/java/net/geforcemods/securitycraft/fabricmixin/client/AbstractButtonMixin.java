package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.geforcemods.securitycraft.fabric.client.gui.WidgetColors;
import net.minecraft.client.gui.components.AbstractButton;

/**
 * Draws a button's message in the color set through {@link WidgetColors#setFGColor}, like NeoForge's AbstractButton patch.
 * Buttons without a color set are unaffected.
 */
@Mixin(AbstractButton.class)
public class AbstractButtonMixin {
	@ModifyArg(method = "renderWidget", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/AbstractButton;renderString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"), index = 2)
	private int securitycraft$applyFGColor(int originalColor) {
		int color = ((WidgetColors.Holder) (Object) this).securitycraft$getPackedFGColor();

		if (color == WidgetColors.UNSET_FG_COLOR)
			return originalColor;

		//keep the alpha that vanilla computed from the widget's alpha
		return color | originalColor & 0xFF000000;
	}
}
