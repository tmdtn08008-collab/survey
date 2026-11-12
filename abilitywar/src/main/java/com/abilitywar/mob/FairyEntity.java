package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * 요정: 꽃숲 · 초원 · 벚꽃 숲의 장난꾸러기. 작고 빠르게 날아다니며 반짝이 화살 (잠깐 공중 부양) 을 쏨.
 * 맞아서 약해지면 반짝이며 순간이동해 달아남.
 */
public class FairyEntity extends AWMonster implements TrophyMob {
    public static final int COLOR = 0xFF8AF0;
    double phase;
    int shootIn = 30, pendingTarget = -1, pendingAt, blinkCd;

    public FairyEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
        this.experiencePoints = 6;
        phase = random.nextDouble() * 6.28;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 12).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.35).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 20);
    }

    @Override
    protected void initGoals() {
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        phase += 0.09;
        blinkCd--;
        LivingEntity t = getTarget();
        double ground = Combat.distanceToGround(this, 6);
        double vy = ground < 1.2 ? 0.05 : ground > 3.2 ? -0.04 : 0.015 * Math.sin(phase * 2);
        Vec3d v = Vec3d.ZERO;
        if (t != null && t.isAlive()) {
            Vec3d to = new Vec3d(t.getX() - getX(), 0, t.getZ() - getZ());
            double d = to.length();
            if (d > 1e-3) {
                Vec3d n = to.normalize();
                v = d > 9 ? n.multiply(0.09) : d < 5 ? n.multiply(-0.08) : new Vec3d(-n.z, 0, n.x).multiply(0.08 * Math.sin(phase * 0.7));
            }
            getLookControl().lookAt(t);
            if (--shootIn <= 0 && d < 14 && Combat.hasLineOfSight(this, t)) {
                shootIn = 40 + random.nextInt(25);
                MobFx.bolt(w, this, t, COLOR, 0.5f, 8);
                pendingTarget = t.getId();
                pendingAt = age + 8;
                playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
            }
        } else v = new Vec3d(Math.cos(phase) * 0.025, 0, Math.sin(phase) * 0.025);
        setVelocity(getVelocity().multiply(0.84).add(v.x, vy, v.z));
        if (pendingTarget >= 0 && age >= pendingAt) {
            if (w.getEntityById(pendingTarget) instanceof LivingEntity le && le.isAlive()) {
                Combat.damage(le, getDamageSources().indirectMagic(this, this), 3, AWNet.DMG_SKILL);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 18, 0), this);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0), this);
            }
            pendingTarget = -1;
        }
        if (age % 3 == 0) w.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 0.2, getZ(), 1, 0.12, 0.1, 0.12, 0.005);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);
        if (ok && isAlive() && blinkCd <= 0 && getHealth() < getMaxHealth() * 0.5f && getWorld() instanceof ServerWorld w) {
            blinkCd = 100;
            w.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.3, 0.3, 0.05);
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3d to = getPos().add(Math.cos(a) * 8, 1 + random.nextDouble() * 2, Math.sin(a) * 8);
                if (w.isSpaceEmpty(this, getBoundingBox().offset(to.subtract(getPos())))) {
                    refreshPositionAfterTeleport(to);
                    break;
                }
            }
            playSound(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        }
        return ok;
    }

    /** 해 질 녘 · 낮에도 가끔 나옴. */
    @Override
    public float getPathfindingFavor(BlockPos pos, WorldView world) {
        return 0f;
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
        drop(Items.GLOWSTONE_DUST, 0, 2);
        if (random.nextInt(4) == 0) drop(Items.AMETHYST_SHARD, 1, 2);
    }

    @Override
    public Item trophy() {
        return RpgItems.FAIRY_DUST;
    }

    @Override
    public int trophyChance() {
        return 50;
    }

    @Override
    public int trophyCount(net.minecraft.util.math.random.Random r) {
        return 1 + r.nextInt(2);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM;
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
        return 1.4f;
    }
}
