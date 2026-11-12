package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 유령 (원혼): 밤에 떠다니는 반투명한 망령. 날아서 쫓아와 할퀴고 생명력을 빨아들임 (어둠).
 * 화살 같은 투사체는 반만 들어감. 햇빛 아래에서는 흩어져 사라짐.
 */
public class WraithEntity extends AWMonster implements TrophyMob {
    double phase;
    int attackCd;

    public WraithEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        this.experiencePoints = 8;
        phase = random.nextDouble() * 6.28;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 24).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        phase += 0.06;
        // 햇빛: 흩어짐
        if (w.isDay() && age % 20 == 0 && w.isSkyVisible(getBlockPos()) && random.nextInt(4) == 0 && !hasCustomName()) {
            w.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 20, 0.4, 0.6, 0.4, 0.03);
            playSound(SoundEvents.ENTITY_VEX_DEATH, 1f, 0.6f);
            discard();
            return;
        }
        LivingEntity t = getTarget();
        Vec3d v;
        if (t != null && t.isAlive()) {
            Vec3d to = t.getPos().add(0, t.getHeight() * 0.6, 0).subtract(getPos().add(0, getHeight() * 0.5, 0));
            double d = to.length();
            v = d < 1e-3 ? Vec3d.ZERO : to.multiply(Math.min(0.09, d * 0.05) / d);
            getLookControl().lookAt(t);
            if (--attackCd <= 0 && d < 1.8) {
                attackCd = 24;
                swingHand(net.minecraft.util.Hand.MAIN_HAND);
                tryAttack(t);
            }
        } else v = new Vec3d(Math.cos(phase) * 0.015, Math.sin(phase * 1.7) * 0.01, Math.sin(phase) * 0.015);
        setVelocity(getVelocity().multiply(0.82).add(v));
        if (age % 4 == 0) w.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 0.4, getZ(), 1, 0.2, 0.3, 0.2, 0.005);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && target instanceof LivingEntity le) {
            heal(2.5f);
            if (random.nextInt(3) == 0) le.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0), this);
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0), this);
            playSound(SoundEvents.ENTITY_VEX_CHARGE, 1f, 0.6f);
        }
        return ok;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.IS_PROJECTILE)) amount *= 0.5f;
        if (source.isIn(DamageTypeTags.IS_DROWNING) || source.isIn(DamageTypeTags.IS_FALL)) return false;
        return super.damage(source, amount);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean canBreatheInWater() {
        return true;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        if (random.nextInt(3) == 0) drop(Items.PHANTOM_MEMBRANE, 1, 1);
    }

    @Override
    public Item trophy() {
        return RpgItems.WRAITH_ESSENCE;
    }

    @Override
    public int trophyChance() {
        return 40;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VEX_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.55f;
    }
}
