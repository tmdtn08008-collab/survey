package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.Window;

import net.geforcemods.securitycraft.fabric.client.gui.GuiLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps SecurityCraft's layered screens ({@link GuiLayers}) consistent the way NeoForge's Minecraft patch does: opening a
 * screen normally closes all layers, and resizing the window resizes the covered screens as well.
 */
@Mixin(Minecraft.class)
public class MinecraftGuiLayersMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void securitycraft$clearGuiLayers(Screen screen, CallbackInfo ci) {
		GuiLayers.clear((Minecraft) (Object) this);
	}

	@Inject(method = "resizeDisplay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;resize(Lnet/minecraft/client/Minecraft;II)V", shift = At.Shift.AFTER))
	private void securitycraft$resizeGuiLayers(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		Window window = mc.getWindow();

		GuiLayers.resize(mc, window.getGuiScaledWidth(), window.getGuiScaledHeight());
	}
}
