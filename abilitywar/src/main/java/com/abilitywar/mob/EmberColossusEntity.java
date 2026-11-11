package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 화염 거인: 용암 바위 거인. 땅 내려치기 (불 폭발) · 운석 비 · 용암 던지기 · (2페이즈) 화염 숨결.
 */
public class EmberColossusEntity extends BossEntity {
    public static final int POUND = 1, METEOR = 2, THROW = 3, BREATH = 4;
    public static final int FIRE = 0xFF7A20;
    int count;
    final List<Vec3d> meteorAt = new ArrayList<>();

    public EmberColossusEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.YELLOW);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 500).add(EntityAttributes.GENERIC_ARMOR, 12)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.ember_colossus.sub";
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (enraged() && count % 3 == 0 && dist < 10) return BREATH;
        if (count % 4 == 0) return METEOR;
        if (dist <= 6) return POUND;
        if (dist <= 18) return random.nextBoolean() ? THROW : METEOR;
        return 0;
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case POUND -> {
                if (t == 1) {
                    warn(getPos(), 6.5f, 16, FIRE);
                    playSound(SoundEvents.ENTITY_IRON_GOLEM_HURT, 2f, 0.4f);
                }
                if (t == 17) {
                    NbtCompound n = new NbtCompound();
                    AWNet.fx(w, getPos(), "dragon_explosion", n);
                    NbtCompound s = new NbtCompound();
                    s.putFloat("r", 6.5f);
                    s.putInt("col", FIRE);
                    AWNet.fx(w, getPos(), "boss_slam", s);
                    playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
                    AWNet.shakeAround(w, getPos(), 0.8f, 14, 24);
                    for (LivingEntity v : victims(getPos().add(0, 0.8, 0), 6.5)) {
                        hurt(v, 13, false);
                        v.setOnFireFor(5);
                        push(v, getPos(), 0.6, 0.8);
                    }
                }
                return t >= 30;
            }
            case METEOR -> {
                int waves = enraged() ? 2 : 1;
                for (int wv = 0; wv < waves; wv++) {
                    int start = 4 + wv * 14, land = start + 18;
                    if (t == start) {
                        meteorAt.clear();
                        playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 3f, 0.4f);
                        int n = 0;
                        for (ServerPlayerEntity p : w.getEntitiesByClass(ServerPlayerEntity.class, getBoundingBox().expand(24), this::hits)) {
                            if (n++ >= 5) break;
                            Vec3d at = p.getPos().add(random.nextGaussian() * 0.8, 0, random.nextGaussian() * 0.8);
                            meteorAt.add(at);
                        }
                        for (int k = 0; k < 3; k++) meteorAt.add(getPos().add(random.nextGaussian() * 9, 0, random.nextGaussian() * 9));
                        for (Vec3d at : meteorAt) {
                            warn(at, 2.6f, 18, FIRE);
                            NbtCompound m = new NbtCompound();
                            m.putInt("t", 18);
                            m.putInt("col", FIRE);
                            AWNet.fx(w, at, "boss_meteor", m);
                        }
                    }
                    if (t == land) {
                        for (Vec3d at : meteorAt) {
                            NbtCompound s = new NbtCompound();
                            s.putFloat("r", 2.6f);
                            s.putInt("col", FIRE);
                            AWNet.fx(w, at, "boss_slam", s);
                            for (LivingEntity v : victims(at.add(0, 0.6, 0), 2.6)) {
                                hurt(v, 10, false);
                                v.setOnFireFor(4);
                                push(v, at, 0.4, 0.5);
                            }
                        }
                        playSound(SoundEvents.ENTITY_GENERIC_EXPLODE, 3f, 0.8f);
                    }
                }
                return t >= (enraged() ? 40 : 26);
            }
            case THROW -> {
                if (target != null && (t == 6 || t == 11 || t == 16)) {
                    faceTarget(target);
                    Vec3d from = getPos().add(0, getHeight() * 0.75, 0).add(forward().multiply(1.5));
                    Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from);
                    SmallFireballEntity fb = new SmallFireballEntity(w, this, to.x, to.y, to.z);
                    fb.setPosition(from);
                    w.spawnEntity(fb);
                    playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 2f, 0.6f);
                }
                return t >= 22;
            }
            case BREATH -> {
                if (target != null && t < 12) faceTarget(target);
                if (t == 1) playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2f, 1.4f);
                if (t == 10) {
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putInt("d", 30);
                    n.putInt("col", FIRE);
                    n.putFloat("len", 9);
                    AWNet.fx(w, getPos(), "boss_breath", n);
                }
                if (t >= 10 && t < 40 && t % 4 == 0) {
                    Vec3d o = getPos().add(0, getHeight() * 0.7, 0);
                    Vec3d f = forward();
                    for (LivingEntity v : victims(o.add(f.multiply(4.5)), 5.5)) {
                        Vec3d to = v.getPos().subtract(getPos());
                        if (new Vec3d(to.x, 0, to.z).normalize().dotProduct(f) < 0.6) continue;
                        hurt(v, 3, false);
                        v.setOnFireFor(4);
                    }
                    if (t % 8 == 0) playSound(SoundEvents.ENTITY_BLAZE_BURN, 2f, 0.5f);
                }
                return t >= 44;
            }
            default -> {
                return true;
            }
        }
    }
}
