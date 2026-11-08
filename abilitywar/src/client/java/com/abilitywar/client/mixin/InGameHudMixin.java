package com.abilitywar.client.mixin;

import com.abilitywar.client.camera.CameraController;
import com.abilitywar.client.hud.CinematicOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void aw$cinematic(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (CameraController.isCinematic()) {
            CinematicOverlay.render(context, tickDelta);
            ci.cancel();
        }
    }
}
