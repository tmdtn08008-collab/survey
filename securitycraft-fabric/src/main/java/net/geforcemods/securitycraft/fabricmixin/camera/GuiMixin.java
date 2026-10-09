package net.geforcemods.securitycraft.fabricmixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.entity.camera.VanillaGuiLayers;
import net.geforcemods.securitycraft.misc.LayerToggleHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;

/**
 * Replaces the cancellation of NeoForge's RenderGuiLayerEvent.Pre in LayerToggleHandler for the vanilla HUD layers that
 * SecurityCraft hides while a camera is mounted. NeoForge splits the vanilla HUD into named layers; vanilla has no such
 * names, so each of these layers is skipped at the start of the vanilla method that draws it, which is what canceling the
 * NeoForge layer does.
 */
@Mixin(Gui.class)
public class GuiMixin {
	@Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
	private void securitycraft$hideJumpMeter(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
		if (LayerToggleHandler.isDisabled(VanillaGuiLayers.JUMP_METER))
			ci.cancel();
	}

	@Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
	private void securitycraft$hideExperienceBar(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
		if (LayerToggleHandler.isDisabled(VanillaGuiLayers.EXPERIENCE_BAR))
			ci.cancel();
	}

	@Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
	private void securitycraft$hideExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (LayerToggleHandler.isDisabled(VanillaGuiLayers.EXPERIENCE_LEVEL))
			ci.cancel();
	}

	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void securitycraft$hideEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (LayerToggleHandler.isDisabled(VanillaGuiLayers.EFFECTS))
			ci.cancel();
	}
}
