package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.geforcemods.securitycraft.fabric.client.gui.WidgetColors;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Stores the text color NeoForge's AbstractWidget#setFGColor would store, see {@link WidgetColors}.
 */
@Mixin(AbstractWidget.class)
public class AbstractWidgetMixin implements WidgetColors.Holder {
	@Unique
	private int securitycraft$packedFGColor = WidgetColors.UNSET_FG_COLOR;

	@Override
	public int securitycraft$getPackedFGColor() {
		return securitycraft$packedFGColor;
	}

	@Override
	public void securitycraft$setPackedFGColor(int color) {
		securitycraft$packedFGColor = color;
	}
}
