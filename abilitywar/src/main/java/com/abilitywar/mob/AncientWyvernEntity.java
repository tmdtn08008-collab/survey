package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 고대 비룡: 하늘을 맴돌다 내리꽂힘. 급강하 · 화염 숨결 (내려앉아서) · 불덩이 폭격 · 날개 돌풍.
 * 모델은 고룡 (용기사 궁극기) 을 작게 그림 (클라이언트 BossFx).
 */
public class AncientWyvernEntity extends BossEntity {
    public static final int SWOOP = 1, BREATH = 2, BARRAGE = 3, GUST = 4;
    public static final int FIRE = 0xFF8A30;

    /** 숨결 · 불덩이 색. */
    protected int fireColor() {
        return FIRE;
    }

    double orbit;
    Vec3d swoopFrom = Vec3d.ZERO, swoopTo = Vec3d.ZERO;
    final Set<Integer> hitOnce = new HashSet<>();
    final List<Vec3d> bombs = new ArrayList<>();
    final List<double[]> pendingBombs = new ArrayList<>();
    int count;

    public AncientWyvernEntity(EntityType<? extends HostileEntity> type, World world) {
        this(type, world, BossBar.Color.PURPLE);
    }

    protected AncientWyvernEntity(EntityType<? extends HostileEntity> type, World world, BossBar.Color color) {
        super(type, world, color);
        setNoGravity(true);
        orbit = random.nextDouble() * Math.PI * 2;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 600).add(EntityAttributes.GENERIC_ARMOR, 10)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64);
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.ancient_wyvern.sub";
    }

    @Override
    protected net.minecraft.sound.SoundEvent introSound() {
        return SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    /** 원하는 자리로 날아감 (최대 속도 sp). */
    void flyTo(Vec3d to, double sp) {
        Vec3d d = to.subtract(getPos());
        double len = d.length();
        Vec3d v = len < 0.05 ? Vec3d.ZERO : d.multiply(Math.min(sp, len * 0.25) / len);
        setVelocity(getVelocity().multiply(0.6).add(v.multiply(0.4)));
        velocityModified = true;
        if (v.horizontalLengthSquared() > 0.003) {
            float yaw = (float) (MathHelper.atan2(v.z, v.x) * 57.2957795) - 90f;
            setYaw(yaw);
            setBodyYaw(yaw);
            setHeadYaw(yaw);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        for (int i = pendingBombs.size() - 1; i >= 0; i--) {
            double[] b = pendingBombs.get(i);
            if (age < b[3]) continue;
            pendingBombs.remove(i);
            Vec3d at = new Vec3d(b[0], b[1], b[2]);
            NbtCompound s = new NbtCompound();
            s.putFloat("r", 2.6f);
            s.putInt("col", fireColor());
            AWNet.fx((ServerWorld) getWorld(), at, "boss_slam", s);
            for (LivingEntity v : victims(at.add(0, 0.6, 0), 2.6)) {
                hurt(v, 8, false);
                v.setOnFireFor(4);
                push(v, at, 0.4, 0.5);
            }
            getWorld().playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, getSoundCategory(), 2f, 0.9f);
        }
    }

    @Override
    protected void idleMove(LivingEntity target, double dist) {
        orbit += enraged() ? 0.035 : 0.025;
        Vec3d to = target.getPos().add(Math.cos(orbit) * orbitRadius(), orbitHeight() + Math.sin(age * 0.03) * 2, Math.sin(orbit) * orbitRadius());
        flyTo(to, enraged() ? 0.9 : 0.7);
    }

    protected double orbitRadius() {
        return 14;
    }

    protected double orbitHeight() {
        return 10;
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (dist < 8 && count % 2 == 0) return GUST;
        if (count % 3 == 0) return BREATH;
        return random.nextBoolean() ? SWOOP : BARRAGE;
    }

    @Override
    protected int restAfter(int id) {
        return enraged() ? 26 : 44;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case SWOOP -> {
                if (t == 1) {
                    if (target == null) return true;
                    hitOnce.clear();
                    Vec3d tp = target.getPos().add(0, 1.2, 0);
                    Vec3d dir = new Vec3d(tp.x - getX(), 0, tp.z - getZ());
                    if (dir.lengthSquared() < 1e-3) dir = new Vec3d(1, 0, 0);
                    dir = dir.normalize();
                    swoopFrom = tp.subtract(dir.multiply(16)).add(0, 9, 0);
                    swoopTo = tp.add(dir.multiply(14)).add(0, 1.5, 0);
                    for (int k = -2; k <= 3; k++) warn(tp.add(dir.multiply(k * 2.5)).add(0, -1.2, 0), 2.2f, 24, fireColor());
                    playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 3f, 1.2f);
                }
                if (t < 12) flyTo(swoopFrom, 1.2);
                else if (t <= 26) {
                    float k = (t - 12) / 14f;
                    Vec3d at = swoopFrom.lerp(swoopTo, k * k * (3 - 2 * k));
                    Vec3d v = at.subtract(getPos());
                    setVelocity(v);
                    velocityModified = true;
                    float yaw = (float) (MathHelper.atan2(swoopTo.z - swoopFrom.z, swoopTo.x - swoopFrom.x) * 57.2957795) - 90f;
                    setYaw(yaw);
                    setBodyYaw(yaw);
                    for (LivingEntity vic : victims(getPos().add(0, 1, 0), 4)) {
                        if (!hitOnce.add(vic.getId())) continue;
                        hurt(vic, 14, false);
                        push(vic, getPos(), 1.2, 0.8);
                    }
                    if (t % 3 == 0) {
                        NbtCompound n = new NbtCompound();
                        n.putFloat("r", 2.5f);
                        n.putInt("col", 0x8A6A50);
                        AWNet.fx(w, getPos().add(0, -1.5, 0), "boss_dust", n);
                    }
                } else flyTo(getPos().add(getVelocity().normalize().multiply(4)).add(0, 3, 0), 0.8);
                return t >= 36;
            }
            case BREATH -> {
                if (target == null) return true;
                Vec3d away = new Vec3d(getX() - target.getX(), 0, getZ() - target.getZ());
                if (away.lengthSquared() < 1e-3) away = new Vec3d(1, 0, 0);
                Vec3d hover = target.getPos().add(away.normalize().multiply(9)).add(0, 4.5, 0);
                if (t < 14) flyTo(hover, 1.0);
                else {
                    setVelocity(getVelocity().multiply(0.6));
                    velocityModified = true;
                    faceTarget(target);
                }
                if (t == 8) playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.8f);
                if (t == 14) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putInt("d", 36);
                    n.putInt("col", fireColor());
                    n.putFloat("len", 13);
                    AWNet.fx(w, getPos(), "boss_breath", n);
                }
                if (t >= 14 && t < 50 && t % 3 == 0) {
                    Vec3d mouth = getPos().add(forward().multiply(3)).add(0, 1.5, 0);
                    Vec3d aim = target.getPos().add(0, 0.8, 0).subtract(mouth).normalize();
                    for (LivingEntity v : victims(mouth.add(aim.multiply(6)), 7.5)) {
                        Vec3d to = v.getPos().add(0, 0.8, 0).subtract(mouth);
                        if (to.length() > 14 || to.normalize().dotProduct(aim) < 0.82) continue;
                        hurt(v, 3, false);
                        v.setOnFireFor(4);
                    }
                    if (t % 9 == 0) playSound(SoundEvents.ENTITY_BLAZE_BURN, 2f, 0.6f);
                }
                return t >= 54;
            }
            case BARRAGE -> {
                if (target != null) idleMove(target, 20);
                if (target != null && t % 4 == 0 && t <= 24) {
                    Vec3d at = target.getPos().add(random.nextGaussian() * 2.5, 0, random.nextGaussian() * 2.5);
                    warn(at, 2.6f, 16, fireColor());
                    NbtCompound m = new NbtCompound();
                    m.putInt("t", 16);
                    m.putInt("col", fireColor());
                    m.putInt("c", getId());
                    AWNet.fx(w, at, "boss_meteor", m);
                    pendingBombs.add(new double[]{at.x, at.y, at.z, age + 16});
                    playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 2f, 0.5f);
                }
                return t >= 30;
            }
            case GUST -> {
                setVelocity(getVelocity().multiply(0.5));
                if (t == 1) {
                    warn(getPos().add(0, -3, 0), 9, 10, 0xE8E8E8);
                    playSound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3f, 0.6f);
                }
                if (t == 11) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putFloat("r", 9);
                    n.putInt("col", 0xF0F0F0);
                    n.putInt("k", 0);
                    AWNet.fx(w, getPos(), "basic_ring", n);
                    for (LivingEntity v : victims(getPos(), 10)) {
                        hurt(v, 5, false);
                        push(v, getPos(), 1.8, 0.6);
                    }
                    playSound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 3f, 0.4f);
                    AWNet.shakeAround(w, getPos(), 0.5f, 10, 20);
                }
                return t >= 20;
            }
            default -> {
                return true;
            }
        }
    }
}
