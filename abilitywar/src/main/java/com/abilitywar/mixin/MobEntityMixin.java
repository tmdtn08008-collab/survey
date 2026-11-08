package com.abilitywar.mixin;

import com.abilitywar.registry.AWEffects;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntity {
    protected MobEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    /** 속박 상태의 몹은 AI 정지. */
    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void aw$stunAi(CallbackInfo ci) {
        if (this.hasStatusEffect(AWEffects.STUN)) ci.cancel();
    }
}
