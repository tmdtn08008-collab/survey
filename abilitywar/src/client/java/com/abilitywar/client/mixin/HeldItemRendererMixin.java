package com.abilitywar.client.mixin;

import com.abilitywar.client.anim.FirstPersonRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"), cancellable = true)
    private void aw$fp(float tickDelta, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (FirstPersonRenderer.shouldTakeOver(player)) {
            FirstPersonRenderer.render(tickDelta, matrices, consumers, player, light);
            ci.cancel();
        }
    }
}
