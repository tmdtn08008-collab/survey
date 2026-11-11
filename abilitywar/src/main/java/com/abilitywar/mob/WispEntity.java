package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** 도깨비불: 늪·어두운 숲의 밤에 떠다니는 빛. 거리를 두고 마력탄을 쏨. */
public class WispEntity extends AWMonster {
    public static final int COLOR = 0x7AF0FF;
    int shootIn = 40;
    int pendingTarget = -1, pendingAt;
    double phase;

    public WispEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        this.experiencePoints = 6;
        phase = random.nextDouble() * 6.28;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 10).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        phase += 0.05;
        LivingEntity t = getTarget();
        double ground = Combat.distanceToGround(this, 6);
        double vy = ground < 1.5 ? 0.05 : ground > 3.5 ? -0.04 : 0.01 * Math.sin(phase * 2);
        Vec3d v = Vec3d.ZERO;
        if (t != null && t.isAlive()) {
            Vec3d to = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ());
            double d = to.length();
            if (d > 1e-3) {
                Vec3d n = to.normalize();
                v = d > 10 ? n.multiply(0.08) : d < 6 ? n.multiply(-0.07) : new Vec3d(-n.z, 0, n.x).multiply(0.05 * Math.sin(phase));
            }
            getLookControl().lookAt(t);
            if (--shootIn <= 0 && d < 16 && Combat.hasLineOfSight(this, t)) {
                shootIn = 50 + random.nextInt(20);
                NbtCompound n = new NbtCompound();
                n.putInt("c", getId());
                n.putInt("e", t.getId());
                Vec3d tp = t.getPos().add(0, t.getHeight() * 0.5, 0);
                n.putDouble("tx", tp.x);
                n.putDouble("ty", tp.y);
                n.putDouble("tz", tp.z);
                n.putInt("col", COLOR);
                n.putFloat("s", 0.8f);
                n.putInt("t", 10);
                AWNet.fx((ServerWorld) getWorld(), getPos(), "basic_bolt", n);
                pendingTarget = t.getId();
                pendingAt = age + 10;
                playSound(SoundEvents.ENTITY_ALLAY_ITEM_THROWN, 1f, 0.6f);
            }
        } else {
            v = new Vec3d(Math.cos(phase) * 0.02, 0, Math.sin(phase) * 0.02);
        }
        setVelocity(getVelocity().multiply(0.85).add(v.x, vy, v.z));
        if (pendingTarget >= 0 && age >= pendingAt) {
            if (getWorld().getEntityById(pendingTarget) instanceof LivingEntity le && le.isAlive())
                Combat.damage(le, getDamageSources().indirectMagic(this, this), 4, AWNet.DMG_SKILL);
            pendingTarget = -1;
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public float getBrightnessAtEyes() {
        return 1f;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.GLOWSTONE_DUST, 1, 2);
        if (random.nextFloat() < 0.1f) drop(Items.LAPIS_LAZULI, 1, 3);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ALLAY_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.5f;
    }
}
