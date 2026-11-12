package com.abilitywar.mob;

import com.abilitywar.item.RpgItems;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * 강화 골렘: 흑요석 몸에 금빛 룬이 새겨진 정예 골렘 (고대 골렘의 강화형). 산 · 메사 · 사막의 밤.
 * 양 주먹 내려치기 (큰 충격파) · 룬 광선 3 연발 · 룬 방패 (잠깐 단단해지고 가까운 적을 밀어냄).
 */
public class EnhancedGolemEntity extends EliteEntity implements GolemLike {
    public static final int SLAM = 1, BEAM = 2, SHIELD = 3;
    public static final int RUNE = 0xFFB040;
    int attackTicks, count;

    public EnhancedGolemEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.YELLOW);
        this.experiencePoints = 60;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 320).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 16)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23).add(EntityAttributes.GENERIC_ARMOR, 16)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32);
    }

    @Override
    public int attackTicks() {
        return attackTicks;
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (attackTicks > 0) attackTicks--;
    }

    @Override
    public boolean tryAttack(Entity target) {
        attackTicks = 10;
        getWorld().sendEntityStatus(this, (byte) 4);
        boolean ok = super.tryAttack(target);
        if (ok) {
            target.setVelocity(target.getVelocity().add(0, 0.6, 0));
            target.velocityModified = true;
        }
        return ok;
    }

    @Override
    public void handleStatus(byte status) {
        if (status == 4) {
            attackTicks = 10;
            playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
        } else super.handleStatus(status);
    }

    @Override
    protected double reach() {
        return 3.4;
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (enraged() && count % 4 == 0) return SHIELD;
        if (dist < 5) return SLAM;
        if (dist < 20) return BEAM;
        return 0;
    }

    @Override
    protected int restAfter(int id) {
        return enraged() ? 34 : 50;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        getNavigation().stop();
        switch (id) {
            case SLAM -> {
                if (t == 1) {
                    warn(getPos(), 6f, 16, RUNE);
                    playSound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 2f, 0.5f);
                }
                if (t == 10) {
                    attackTicks = 10;
                    w.sendEntityStatus(this, (byte) 4);
                }
                if (t == 16) {
                    MobFx.slam(w, getPos(), 6f, RUNE);
                    playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                    AWNet.shakeAround(w, getPos(), 0.8f, 14, 18);
                    for (LivingEntity v : victims(getPos(), 6)) {
                        hurt(v, 16, false);
                        push(v, getPos(), 0.8, 0.9);
                    }
                }
                return t >= 26;
            }
            case BEAM -> {
                if (target == null) return true;
                faceTarget(target);
                if (t == 1) playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 2f, 1.4f);
                if (t == 10 || t == 18 || t == 26) {
                    MobFx.bolt(w, this, target, RUNE, 0.6f, 5);
                    playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
                    pending.add(new int[]{target.getId(), t + 5});
                }
                for (int i = pending.size() - 1; i >= 0; i--) {
                    int[] p = pending.get(i);
                    if (t < p[1]) continue;
                    pending.remove(i);
                    if (w.getEntityById(p[0]) instanceof LivingEntity le && le.isAlive() && hits(le)) {
                        hurt(le, 9, false);
                        le.setOnFireFor(2);
                    }
                }
                return t >= 34;
            }
            case SHIELD -> {
                if (t == 1) {
                    addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 2, false, false));
                    playSound(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.7f);
                    MobFx.ring(w, this, 5, RUNE);
                }
                if (t % 4 == 0) w.spawnParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 1.5, getZ(), 12, 1.2, 1.2, 1.2, 0.1);
                if (t == 20) {
                    for (LivingEntity v : victims(getPos(), 5)) {
                        hurt(v, 6, false);
                        push(v, getPos(), 1.4, 0.4);
                    }
                    MobFx.ring(w, this, 5, RUNE);
                }
                return t >= 30;
            }
            default -> {
                return true;
            }
        }
    }

    final java.util.List<int[]> pending = new java.util.ArrayList<>();

    @Override
    protected SoundEvent introSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_REPAIR;
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        dropStack(new ItemStack(Items.OBSIDIAN, 2 + random.nextInt(3)));
        dropStack(new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(3)));
    }

    @Override
    public Item trophy() {
        return RpgItems.GOLEM_CORE;
    }

    @Override
    public int trophyChance() {
        return 100;
    }

    @Override
    public int trophyCount(Random r) {
        return 2 + r.nextInt(2);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.5f);
    }

    @Override
    public float getSoundPitch() {
        return 0.5f;
    }
}
