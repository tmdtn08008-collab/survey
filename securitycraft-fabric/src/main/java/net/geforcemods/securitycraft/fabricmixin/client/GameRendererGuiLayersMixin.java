package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.client.gui.GuiLayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Draws the screens covered by a layered screen ({@link GuiLayers}) below it, where NeoForge's GameRenderer patch does.
 */
@Mixin(GameRenderer.class)
public class GameRendererGuiLayersMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void securitycraft$drawGuiLayers(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
		GuiLayers.drawLayers(guiGraphics, partialTick, () -> original.call(screen, guiGraphics, mouseX, mouseY, partialTick));
	}
}
