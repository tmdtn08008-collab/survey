package com.abilitywar.client.mixin;

import com.abilitywar.data.AWPlayer;
import com.abilitywar.registry.AWEffects;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Shadow
    public Input input;

    /** 속박/연출 중에는 이동 입력 무시. */
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void aw$freeze(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self.hasStatusEffect(AWEffects.STUN) || ((AWPlayer) self).aw$hasFlag(AWPlayer.FLAG_CINEMATIC)) {
            input.movementForward = 0;
            input.movementSideways = 0;
            input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
            input.jumping = false;
            input.sneaking = false;
            self.setSprinting(false);
        }
    }
}
