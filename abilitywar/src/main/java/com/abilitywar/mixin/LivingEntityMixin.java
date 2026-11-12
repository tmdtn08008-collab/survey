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

    /** 흑마법사 하수인은 죽어도 아이템/경험치를 떨어뜨리지 않는다. */
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void aw$minionDrop(DamageSource source, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        // 심연의 탑에서 쓰러진 플레이어도 아이템을 떨어뜨리지 않음 (AbyssTower 가 되살아날 때 돌려줌)
        if (com.abilitywar.ability.warlock.Necromancy.isMinion(this) || com.abilitywar.rpg.AbyssTower.keepsInventory((LivingEntity) (Object) this)) ci.cancel();
    }

    @Inject(method = "dropXp", at = @At("HEAD"), cancellable = true)
    private void aw$minionXp(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.abilitywar.ability.warlock.Necromancy.isMinion(this) || com.abilitywar.rpg.AbyssTower.keepsInventory((LivingEntity) (Object) this)) ci.cancel();
    }

    /** 3차 전직: 내가 거는 (또는 나에게 걸리는) 좋은 효과의 지속 시간 +30%. */
    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true)
    private net.minecraft.entity.effect.StatusEffectInstance aw$grandBuff(net.minecraft.entity.effect.StatusEffectInstance effect, net.minecraft.entity.effect.StatusEffectInstance original, Entity source) {
        if (this.getWorld().isClient || effect == null || effect.isInfinite() || effect.getDuration() > 20 * 60 * 10) return effect;
        if (!effect.getEffectType().isBeneficial()) return effect;
        Entity giver = source != null ? source : (Object) this instanceof net.minecraft.entity.player.PlayerEntity ? this : null;
        if (!(giver instanceof net.minecraft.entity.player.PlayerEntity gp) || !com.abilitywar.rpg.RpgManager.isGrand(gp)) return effect;
        return new net.minecraft.entity.effect.StatusEffectInstance(effect.getEffectType(), Math.round(effect.getDuration() * com.abilitywar.rpg.RpgManager.GRAND_BUFF),
                effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
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
