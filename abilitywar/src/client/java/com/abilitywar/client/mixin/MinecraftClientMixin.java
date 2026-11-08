package com.abilitywar.client.mixin;

import com.abilitywar.client.input.InputHandler;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.registry.AWEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void aw$attack(CallbackInfoReturnable<Boolean> cir) {
        if (InputHandler.onAttack()) cir.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void aw$use(CallbackInfo ci) {
        if (player != null && (player.hasStatusEffect(AWEffects.STUN) || ((AWPlayer) player).aw$hasFlag(AWPlayer.FLAG_SHADOW))) ci.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void aw$breaking(boolean breaking, CallbackInfo ci) {
        if (player != null && breaking && (player.hasStatusEffect(AWEffects.STUN) || ((AWPlayer) player).aw$hasFlag(AWPlayer.FLAG_SHADOW))) ci.cancel();
    }
}
