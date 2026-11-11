package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 타락한 기사왕: 거대한 갑옷 기사. 대검 내려찍기 (충격파 줄) · 회전베기 · 돌진 · (2페이즈) 망령 기사 소환 · 어둠의 파동.
 */
public class FallenKingEntity extends BossEntity {
    public static final int SLAM = 1, SPIN = 2, CHARGE = 3, SUMMON = 4, WAVE = 5;
    public static final int RED = 0xFF3A3A, DARK = 0x8A2030;
    int count;
    final Set<Integer> hitOnce = new HashSet<>();
    Vec3d chargeDir = Vec3d.ZERO;

    public FallenKingEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.RED);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 400).add(EntityAttributes.GENERIC_ARMOR, 14)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.fallen_king.sub";
    }

    @Override
    protected void onEnrage() {
        addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 600, 0, false, false));
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (enraged() && count % 4 == 0) return SUMMON;
        if (dist > 8) return enraged() && random.nextBoolean() ? WAVE : CHARGE;
        if (dist <= 4.5) return random.nextFloat() < 0.5f ? SPIN : SLAM;
        if (dist <= 7) return enraged() && random.nextFloat() < 0.3f ? WAVE : SLAM;
        return 0;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        int speedUp = enraged() ? 3 : 0;
        switch (id) {
            case SLAM -> {
                if (t == 1 && target != null) {
                    faceTarget(target);
                    Vec3d f = forward();
                    for (int k = 0; k < 4; k++) warn(getPos().add(f.multiply(4 + k * 2.5)), k == 0 ? 3.6f : 2.2f, 16 - speedUp + k * 2, RED);
                    playSound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 2f, 0.5f);
                }
                int hit = 16 - speedUp;
                for (int k = 0; k < 4; k++) {
                    if (t == hit + k * 2) {
                        Vec3d at = getPos().add(forward().multiply(4 + k * 2.5));
                        float r = k == 0 ? 3.6f : 2.2f;
                        NbtCompound n = new NbtCompound();
                        n.putFloat("r", r);
                        n.putInt("col", RED);
                        AWNet.fx(w, at, "boss_slam", n);
                        if (k == 0) {
                            playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
                            AWNet.shakeAround(w, at, 0.6f, 10, 20);
                        }
                        for (LivingEntity v : victims(at.add(0, 0.6, 0), r)) {
                            hurt(v, k == 0 ? 14 : 8, false);
                            push(v, at, 0.3, 0.6);
                        }
                    }
                }
                return t >= hit + 14;
            }
            case SPIN -> {
                if (t == 1) {
                    hitOnce.clear();
                    playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.5f);
                }
                if (t >= 6 && t <= 18 && t % 3 == 0) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putInt("k", 0);
                    n.putInt("col", RED);
                    n.putBoolean("full", true);
                    n.putFloat("s", 2f);
                    AWNet.fx(w, getPos(), "boss_spin", n);
                    for (LivingEntity v : victims(getPos().add(0, 1.5, 0), 5.5)) {
                        hurt(v, 7, false);
                        push(v, getPos(), 0.9, 0.35);
                    }
                    playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
                }
                return t >= 26;
            }
            case CHARGE -> {
                if (t == 1 && target != null) {
                    faceTarget(target);
                    chargeDir = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ()).normalize();
                    hitOnce.clear();
                    for (int k = 1; k <= 5; k++) warn(getPos().add(chargeDir.multiply(k * 2.6)), 1.8f, 12, RED);
                    playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 2f, 0.8f);
                }
                if (t > 10 && t <= 22) {
                    setVelocity(chargeDir.multiply(1.15).add(0, getVelocity().y, 0));
                    velocityModified = true;
                    for (LivingEntity v : victims(getPos().add(0, 1.2, 0), 2.6)) {
                        if (!hitOnce.add(v.getId())) continue;
                        hurt(v, 12, false);
                        push(v, getPos(), 1.0, 0.7);
                    }
                    if (t % 2 == 0) {
                        NbtCompound n = new NbtCompound();
                        n.putFloat("r", 1.5f);
                        n.putInt("col", DARK);
                        AWNet.fx(w, getPos(), "boss_dust", n);
                    }
                }
                if (t == 23) setVelocity(Vec3d.ZERO);
                return t >= 32;
            }
            case SUMMON -> {
                if (t == 10) {
                    for (int k = 0; k < 3; k++) {
                        double a = k * Math.PI * 2 / 3 + random.nextDouble();
                        Vec3d at = getPos().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
                        SkeletonKnightEntity sk = AWMobs.SKELETON_KNIGHT.create(w);
                        if (sk == null) continue;
                        sk.refreshPositionAndAngles(at.x, at.y, at.z, random.nextFloat() * 360, 0);
                        sk.initialize(w, w.getLocalDifficulty(getBlockPos()), net.minecraft.entity.SpawnReason.MOB_SUMMONED, null, null);
                        if (target != null) sk.setTarget(target);
                        w.spawnEntity(sk);
                        NbtCompound n = new NbtCompound();
                        n.putBoolean("big", false);
                        AWNet.fx(w, at, "warlock_summon", n);
                    }
                    playSound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.6f);
                }
                return t >= 24;
            }
            case WAVE -> {
                if (t == 1) {
                    warn(getPos(), 9, 14, DARK);
                    playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 2f, 0.7f);
                }
                if (t == 15) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putFloat("r", 9);
                    n.putInt("col", DARK);
                    n.putInt("k", 4);
                    AWNet.fx(w, getPos(), "basic_ring", n);
                    NbtCompound s2 = new NbtCompound();
                    s2.putFloat("r", 4);
                    s2.putInt("col", RED);
                    AWNet.fx(w, getPos(), "boss_slam", s2);
                    for (LivingEntity v : victims(getPos().add(0, 1, 0), 9)) {
                        hurt(v, 10, false);
                        push(v, getPos(), 1.2, 0.5);
                        v.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), this);
                    }
                    playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.8f);
                    AWNet.shakeAround(w, getPos(), 0.7f, 12, 24);
                }
                return t >= 28;
            }
            default -> {
                return true;
            }
        }
    }
}
