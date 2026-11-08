package com.abilitywar.client.mixin;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.fx.PostEffects;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void aw$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        CameraController.applyViewTransform(matrices, tickDelta);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void aw$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        double fov = cir.getReturnValueD();
        double mod = CameraController.modifyFov(fov, tickDelta, changingFov);
        if (mod != fov) cir.setReturnValue(mod);
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void aw$hideHand(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (CameraController.isCinematic()) ci.cancel();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;drawEntityOutlinesFramebuffer()V", shift = At.Shift.AFTER))
    private void aw$post(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        PostEffects.render(tickDelta);
    }
}
