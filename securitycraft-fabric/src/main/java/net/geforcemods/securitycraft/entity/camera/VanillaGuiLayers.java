package net.geforcemods.securitycraft.entity.camera;

import net.minecraft.resources.ResourceLocation;

/**
 * Fabric stand-in for the ids NeoForge gives the vanilla HUD layers that SecurityCraft hides while a camera is mounted. The ids
 * are the same as NeoForge's, so {@link net.geforcemods.securitycraft.misc.LayerToggleHandler} keeps working unchanged.
 * Vanilla has no layer ids, so hiding these layers is done by
 * {@link net.geforcemods.securitycraft.fabricmixin.camera.GuiMixin}, which checks LayerToggleHandler for exactly these ids.
 */
public final class VanillaGuiLayers {
	public static final ResourceLocation JUMP_METER = ResourceLocation.withDefaultNamespace("jump_meter");
	public static final ResourceLocation EXPERIENCE_BAR = ResourceLocation.withDefaultNamespace("experience_bar");
	public static final ResourceLocation EXPERIENCE_LEVEL = ResourceLocation.withDefaultNamespace("experience_level");
	public static final ResourceLocation EFFECTS = ResourceLocation.withDefaultNamespace("effects");

	private VanillaGuiLayers() {}
}
