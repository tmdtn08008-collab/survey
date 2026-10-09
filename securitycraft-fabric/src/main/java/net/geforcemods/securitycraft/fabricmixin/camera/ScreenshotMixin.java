package net.geforcemods.securitycraft.fabricmixin.camera;

import java.io.File;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.geforcemods.securitycraft.entity.camera.CameraClientEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/**
 * Calls SecurityCraft's ScreenshotEvent handler (the camera shutter sound while a camera is mounted) where NeoForge's
 * Screenshot patch posts that event: after the image has been taken and its file name chosen, right before it is written on
 * the IO thread pool.
 */
@Mixin(Screenshot.class)
public class ScreenshotMixin {
	@Inject(method = "_grab", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;ioPool()Ljava/util/concurrent/ExecutorService;"))
	private static void securitycraft$onScreenshot(File gameDirectory, String screenshotName, RenderTarget renderTarget, Consumer<Component> messageConsumer, CallbackInfo ci) {
		CameraClientEvents.onScreenshot();
	}
}
