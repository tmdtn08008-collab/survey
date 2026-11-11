package com.abilitywar.mob;

import com.abilitywar.combat.Combat;
import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 서리 마녀: 떠다니며 거리를 둠. 얼음 가시 (발밑에서 솟음) · 서리 폭발 (얼림 + 순간이동) · 얼음 화살 연사 ·
 * (2페이즈) 눈보라 · 얼음 망령 소환.
 */
public class FrostWitchEntity extends BossEntity {
    public static final int SPIKES = 1, NOVA = 2, VOLLEY = 3, SUMMON = 4, BLIZZARD = 5;
    public static final int ICE = 0x9FE8FF;
    int count;
    final List<Vec3d> spikeAt = new ArrayList<>();
    /** 날아가는 얼음 화살: [대상 id, 맞는 나이]. */
    final List<int[]> pending = new ArrayList<>();

    public FrostWitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.BLUE);
        setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 320).add(EntityAttributes.GENERIC_ARMOR, 6)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.frost_witch.sub";
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        // 땅에서 1.5칸 위에 떠 있음
        double ground = Combat.distanceToGround(this, 6);
        double vy = ground < 1.3 ? 0.08 : ground > 1.8 ? -0.06 : 0;
        setVelocity(getVelocity().x * 0.9, vy, getVelocity().z * 0.9);
        // 얼음 화살 맞추기
        for (int i = pending.size() - 1; i >= 0; i--) {
            int[] pnd = pending.get(i);
            if (age < pnd[1]) continue;
            pending.remove(i);
            if (getWorld().getEntityById(pnd[0]) instanceof LivingEntity v && hits(v)) {
                hurt(v, 5, false);
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), this);
                v.setFrozenTicks(Math.min(v.getMinFreezeDamageTicks() + 40, v.getFrozenTicks() + 50));
            }
        }
    }

    @Override
    protected void idleMove(LivingEntity target, double dist) {
        // 8~12칸 거리 유지
        Vec3d to = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
        if (to.lengthSquared() < 1e-3) return;
        Vec3d d = to.normalize();
        Vec3d side = new Vec3d(-d.z, 0, d.x).multiply(Math.sin(age * 0.05) * 0.12);
        Vec3d v = dist < 7 ? d.multiply(-0.22) : dist > 12 ? d.multiply(0.2) : Vec3d.ZERO;
        setVelocity(getVelocity().add(v.add(side).multiply(0.5)));
        faceTarget(target);
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (enraged() && count % 5 == 0) return SUMMON;
        if (enraged() && count % 3 == 0) return BLIZZARD;
        if (dist < 5) return NOVA;
        return random.nextFloat() < 0.55f ? SPIKES : VOLLEY;
    }

    void spike(ServerWorld w, Vec3d at, float r, float dmg) {
        NbtCompound n = new NbtCompound();
        n.putFloat("s", r);
        AWNet.fx(w, at, "boss_spike", n);
        for (LivingEntity v : victims(at.add(0, 0.8, 0), r)) {
            hurt(v, dmg, false);
            v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), this);
            Combat.setVelocity(v, new Vec3d(0, 0.7, 0));
        }
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case SPIKES -> {
                if (t == 1) {
                    spikeAt.clear();
                    for (LivingEntity v : victims(getPos(), 22)) {
                        if (spikeAt.size() >= 5) break;
                        spikeAt.add(v.getPos());
                    }
                    for (Vec3d at : spikeAt) warn(at, 1.8f, 16, ICE);
                    playSound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 2f, 1.4f);
                }
                if (t == 17) {
                    for (Vec3d at : spikeAt) spike(w, at, 1.8f, 9);
                    playSound(SoundEvents.BLOCK_GLASS_BREAK, 2f, 0.6f);
                }
                return t >= 26;
            }
            case NOVA -> {
                if (t == 1) {
                    warn(getPos(), 7, 14, ICE);
                    playSound(SoundEvents.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 2f, 1.2f);
                }
                if (t == 15) {
                    NbtCompound n = new NbtCompound();
                    n.putFloat("r", 7);
                    AWNet.fx(w, getPos(), "boss_nova", n);
                    for (LivingEntity v : victims(getPos().add(0, 1, 0), 7)) {
                        hurt(v, 6, false);
                        v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3), this);
                        v.setFrozenTicks(v.getMinFreezeDamageTicks() + 100);
                        push(v, getPos(), 0.9, 0.3);
                    }
                    playSound(SoundEvents.BLOCK_GLASS_BREAK, 3f, 0.5f);
                }
                if (t == 20 && target != null) {
                    // 멀리 순간이동
                    Vec3d away = getPos().subtract(target.getPos());
                    away = new Vec3d(away.x, 0, away.z);
                    if (away.lengthSquared() < 1e-3) away = new Vec3d(1, 0, 0);
                    Vec3d to = Combat.safeSpot(this, getPos().add(away.normalize().multiply(9)));
                    NbtCompound n = new NbtCompound();
                    n.putDouble("fx", getX());
                    n.putDouble("fy", getY());
                    n.putDouble("fz", getZ());
                    n.putInt("col", ICE);
                    AWNet.fx(w, to, "basic_blink", n);
                    refreshPositionAndAngles(to.x, to.y, to.z, getYaw(), getPitch());
                    playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 2f, 1.3f);
                }
                return t >= 24;
            }
            case VOLLEY -> {
                if (target != null && t % 4 == 0 && t <= 20) {
                    faceTarget(target);
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putInt("e", target.getId());
                    Vec3d tp = target.getPos().add(0, target.getHeight() * 0.5, 0);
                    n.putDouble("tx", tp.x);
                    n.putDouble("ty", tp.y);
                    n.putDouble("tz", tp.z);
                    n.putInt("col", ICE);
                    n.putFloat("s", 1.1f);
                    n.putInt("t", 8);
                    AWNet.fx(w, getPos(), "basic_bolt", n);
                    pending.add(new int[]{target.getId(), age + 8});
                    playSound(SoundEvents.ENTITY_SNOW_GOLEM_SHOOT, 2f, 0.8f);
                }
                return t >= 26;
            }
            case SUMMON -> {
                if (t == 10) {
                    for (int k = 0; k < 2; k++) {
                        StrayEntity s = EntityType.STRAY.create(w);
                        if (s == null) continue;
                        Vec3d at = getPos().add(random.nextGaussian() * 3, 0, random.nextGaussian() * 3);
                        s.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                        s.initialize(w, w.getLocalDifficulty(getBlockPos()), net.minecraft.entity.SpawnReason.MOB_SUMMONED, null, null);
                        if (target != null) s.setTarget(target);
                        w.spawnEntity(s);
                        NbtCompound n = new NbtCompound();
                        n.putFloat("s", 1.2f);
                        AWNet.fx(w, at, "boss_spike", n);
                    }
                    playSound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 1.2f);
                }
                return t >= 22;
            }
            case BLIZZARD -> {
                if (t == 1) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("e", getId());
                    n.putInt("d", 64);
                    AWNet.fx(w, getPos(), "boss_blizzard", n);
                    playSound(SoundEvents.WEATHER_RAIN_ABOVE, 3f, 0.5f);
                }
                // 12틱마다: 경고 (10틱) → 가시
                if (t % 12 == 2 && t < 54) {
                    spikeAt.clear();
                    for (LivingEntity v : victims(getPos(), 20)) spikeAt.add(v.getPos().add(random.nextGaussian(), 0, random.nextGaussian()));
                    for (Vec3d at : spikeAt) warn(at, 1.6f, 10, ICE);
                }
                if (t % 12 == 0 && t > 2 && t <= 60) {
                    for (Vec3d at : spikeAt) spike(w, at, 1.6f, 6);
                }
                return t >= 66;
            }
            default -> {
                return true;
            }
        }
    }
}
