package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;

/**
 * NeoForge's access transformer makes AbstractButton#SPRITES accessible to subclasses.
 */
@Mixin(AbstractButton.class)
public interface AbstractButtonAccessor {
	@Accessor("SPRITES")
	static WidgetSprites securitycraft$getSprites() {
		throw new AssertionError();
	}
}
