package com.abilitywar.mob;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * 거미 여왕: 큰 거미 (거미 숲의 우두머리 · 거미 둥지 던전의 주인).
 * 물면 독 + 느려짐, 싸우는 동안 10초마다 새끼 동굴 거미 둘을 낳음 (둘레에 6마리까지).
 */
public class SpiderQueenEntity extends SpiderEntity {
    int broodCd = 120;

    public SpiderQueenEntity(EntityType<? extends SpiderEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 60;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 60).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32).add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof LivingEntity le) {
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 1), this);
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), this);
        }
        return hit;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld w) || getTarget() == null || !getTarget().isAlive()) return;
        if (--broodCd > 0) return;
        broodCd = 200;
        int near = w.getEntitiesByClass(CaveSpiderEntity.class, getBoundingBox().expand(16), e -> true).size();
        for (int i = 0; i < 2 && near + i < 6; i++) {
            CaveSpiderEntity c = EntityType.CAVE_SPIDER.create(w);
            if (c == null) continue;
            c.refreshPositionAndAngles(getX() + random.nextGaussian(), getY() + 0.2, getZ() + random.nextGaussian(), random.nextFloat() * 360, 0);
            c.initialize(w, w.getLocalDifficulty(getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
            c.setTarget(getTarget());
            for (String t : getCommandTags()) c.addCommandTag(t);
            w.spawnEntity(c);
        }
        w.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.6, getZ(), 30, 0.8, 0.4, 0.8, 0.0);
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 1.4f, 0.55f);
    }

    @Override
    public float getSoundPitch() {
        return 0.6f;
    }
}
