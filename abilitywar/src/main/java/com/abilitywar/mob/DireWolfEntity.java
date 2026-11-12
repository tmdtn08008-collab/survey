package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/** 흉포한 늑대: 밤의 타이가 · 설원 · 숲에 무리 (3~5). 처음 사냥감을 찾으면 울부짖어 무리를 부름. 물면 잠깐 느려짐. */
public class DireWolfEntity extends AWMonster implements TrophyMob {
    boolean howled;

    public DireWolfEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 6;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 24).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        targetSelector.add(0, new RevengeGoal(this).setGroupRevenge());
    }

    @Override
    protected double attackSpeed() {
        return 1.3;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        LivingEntity t = getTarget();
        if (t != null && !howled) {
            howled = true;
            playSound(SoundEvents.ENTITY_WOLF_HOWL, 3f, 0.7f);
            for (DireWolfEntity o : getWorld().getEntitiesByClass(DireWolfEntity.class, getBoundingBox().expand(24), e -> e != this && e.getTarget() == null)) {
                o.howled = true;
                o.setTarget(t);
            }
        }
        if (t == null && age % 200 == 0) howled = false;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && target instanceof LivingEntity le) le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0), this);
        return ok;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        drop(Items.BONE, 0, 2);
        drop(Items.LEATHER, 0, 1);
    }

    @Override
    protected float gearChance() {
        return 0.02f;
    }

    @Override
    public Item trophy() {
        return RpgItems.DIRE_FANG;
    }

    @Override
    public int trophyChance() {
        return 40;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_WOLF_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.7f;
    }
}
