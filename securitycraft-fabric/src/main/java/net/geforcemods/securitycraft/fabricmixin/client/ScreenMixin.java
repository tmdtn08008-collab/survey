package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.client.gui.GuiLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Like NeoForge, closing a screen returns to the screen it was layered on ({@link GuiLayers}). Without layers this is the
 * same as vanilla's setScreen(null).
 */
@Mixin(Screen.class)
public class ScreenMixin {
	@WrapOperation(method = "onClose", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void securitycraft$popGuiLayer(Minecraft mc, Screen newScreen, Operation<Void> original) {
		GuiLayers.pop();
	}
}
