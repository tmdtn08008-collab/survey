package net.geforcemods.securitycraft.fabricmixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

/**
 * NeoForge's access transformer makes Screen#renderables accessible to subclasses.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {
	@Accessor("renderables")
	List<Renderable> securitycraft$getRenderables();
}
