package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.MerchantEntity;
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

/**
 * 광신도: 어두운 숲 · 늪의 밤. 거리를 두고 그림자 화살 (보라, 시듦) 을 쏨.
 * 체력이 반 아래로 떨어지면 한 번: 의식 — 피를 바쳐 회복 + 원령 (벡스) 둘을 부름.
 */
public class CultistEntity extends AWMonster implements TrophyMob {
    public static final int COLOR = 0x9A3CFF;
    int castCd = 40, pendingTarget = -1, pendingAt;
    boolean ritualDone;

    public CultistEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 9;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 28).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(7, new WanderAroundFarGoal(this, 0.9));
        goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12f));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        if (pendingTarget >= 0 && age >= pendingAt) {
            if (w.getEntityById(pendingTarget) instanceof LivingEntity le && le.isAlive()) {
                Combat.damage(le, getDamageSources().indirectMagic(this, this), 6, AWNet.DMG_SKILL);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), this);
            }
            pendingTarget = -1;
        }
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;
        getLookControl().lookAt(t, 30, 30);
        double d = distanceTo(t);
        // 거리 두기: 6 보다 가까우면 물러나고 12 보다 멀면 다가감
        if (age % 10 == 0) {
            if (d < 6) {
                Vec3d away = getPos().subtract(t.getPos()).normalize().multiply(6);
                getNavigation().startMovingTo(getX() + away.x, getY(), getZ() + away.z, 1.15);
            } else if (d > 12) getNavigation().startMovingTo(t, 1.0);
            else getNavigation().stop();
        }
        if (--castCd <= 0 && d < 18 && Combat.hasLineOfSight(this, t)) {
            castCd = 50 + random.nextInt(25);
            swingHand(net.minecraft.util.Hand.MAIN_HAND);
            MobFx.bolt(w, this, t, COLOR, 0.7f, 12);
            pendingTarget = t.getId();
            pendingAt = age + 12;
            playSound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean ok = super.damage(source, amount);
        if (ok && !ritualDone && isAlive() && getHealth() < getMaxHealth() * 0.5f && getWorld() instanceof ServerWorld w) {
            ritualDone = true;
            heal(getMaxHealth() * 0.3f);
            w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 40, 0.6, 0.8, 0.6, 0.04);
            playSound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
            for (int i = 0; i < 2; i++) {
                VexEntity vex = net.minecraft.entity.EntityType.VEX.create(w);
                if (vex == null) continue;
                BlockPos p = getBlockPos().add(-1 + random.nextInt(3), 1, -1 + random.nextInt(3));
                vex.refreshPositionAndAngles(p, 0, 0);
                vex.initialize(w, w.getLocalDifficulty(p), SpawnReason.MOB_SUMMONED, null, null);
                vex.setOwner(this);
                vex.setBounds(p);
                vex.setLifeTicks(20 * (20 + random.nextInt(10)));
                if (getTarget() != null) vex.setTarget(getTarget());
                w.spawnEntityAndPassengers(vex);
            }
        }
        return ok;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.REDSTONE, 0, 2);
        if (random.nextInt(6) == 0) drop(Items.BOOK, 1, 1);
    }

    @Override
    public Item trophy() {
        return RpgItems.CULT_SIGIL;
    }

    @Override
    public int trophyChance() {
        return 35;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_EVOKER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_EVOKER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_EVOKER_DEATH;
    }
}
