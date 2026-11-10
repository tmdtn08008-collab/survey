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

    /** 흑마법사 하수인은 주인 / 같은 편을 노리지 않는다. */
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void aw$minionTarget(LivingEntity target, CallbackInfo ci) {
        if (target != null && !this.getWorld().isClient && com.abilitywar.ability.warlock.Necromancy.isMinion(this)
                && com.abilitywar.ability.warlock.Necromancy.allied(this, target)) ci.cancel();
    }

    /** 하수인은 햇빛에 타지 않는다. */
    @Inject(method = "isAffectedByDaylight", at = @At("HEAD"), cancellable = true)
    private void aw$minionSun(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (com.abilitywar.ability.warlock.Necromancy.isMinion(this)) cir.setReturnValue(false);
    }

    /** 속박 상태의 몹은 AI 정지. */
    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void aw$stunAi(CallbackInfo ci) {
        if (this.hasStatusEffect(AWEffects.STUN)) ci.cancel();
    }
}
