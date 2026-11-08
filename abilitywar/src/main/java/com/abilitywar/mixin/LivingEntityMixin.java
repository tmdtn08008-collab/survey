package com.abilitywar.mixin;

import com.abilitywar.combat.Combat;
import com.abilitywar.server.AbilityManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow
    public abstract float getHealth();

    @Shadow
    public abstract float getAbsorptionAmount();

    @Unique
    private float aw$hpBefore = -1;
    @Unique
    private int aw$depth;

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float aw$modifyDamage(float amount, DamageSource source, float originalAmount) {
        if (this.getWorld().isClient) return amount;
        return AbilityManager.modifyDamage((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void aw$beforeDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (aw$depth++ == 0) aw$hpBefore = this.getHealth() + this.getAbsorptionAmount();
    }

    @Inject(method = "damage", at = @At("RETURN"))
    private void aw$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (--aw$depth > 0) return;
        aw$depth = 0;
        if (this.getWorld().isClient || aw$hpBefore < 0) return;
        float after = Math.max(0, this.getHealth()) + this.getAbsorptionAmount();
        float lost = aw$hpBefore - after;
        aw$hpBefore = -1;
        if (lost > 0.001f) {
            Byte kind = Combat.NEXT_KIND.get();
            AbilityManager.afterDamage((LivingEntity) (Object) this, source, lost, kind == null ? -1 : kind);
        }
    }
}
