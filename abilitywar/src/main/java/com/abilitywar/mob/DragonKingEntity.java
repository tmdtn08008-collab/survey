package com.abilitywar.mob;

import com.abilitywar.net.AWNet;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 용왕: 용의 둥지의 주인. 푸른 비늘 · 금빛 뿔의 거대한 고룡 (고대 비룡보다 훨씬 큼).
 * 고대 비룡의 기술 (급강하 · 숨결 · 불덩이 · 날개 돌풍) + 폭풍 (경고 원 위로 번개 8 줄기) · 해일의 숨결 (얼음 물결, 크게 느려짐) ·
 * 왕의 포효 (2페이즈: 비룡 셋을 부름).
 */
public class DragonKingEntity extends AncientWyvernEntity {
    public static final int STORM = 5, TIDE = 6, ROAR = 7;
    public static final int AZURE = 0x5AC8FF;
    final List<double[]> strikes = new ArrayList<>();
    int summons;

    public DragonKingEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world, BossBar.Color.BLUE);
        this.experiencePoints = 600;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 1400).add(EntityAttributes.GENERIC_ARMOR, 14)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 18).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80);
    }

    @Override
    protected String introSubtitleKey() {
        return "boss.abilitywar.dragon_king.sub";
    }

    @Override
    protected int fireColor() {
        return AZURE;
    }

    @Override
    protected double orbitRadius() {
        return 18;
    }

    @Override
    protected double orbitHeight() {
        return 13;
    }

    @Override
    protected void hurt(LivingEntity t, float amount, boolean ult) {
        super.hurt(t, amount * 1.35f, ult);
    }

    @Override
    protected void onEnrage() {
        attack = ROAR;
        attackTick = 0;
        startAnim(GUST);
    }

    @Override
    protected int pickAttack(LivingEntity t, double dist) {
        count++;
        if (enraged() && summons < 3 && count % 7 == 0) return ROAR;
        if (count % 4 == 0) return STORM;
        if (count % 5 == 2) return TIDE;
        return super.pickAttack(t, dist);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        for (int i = strikes.size() - 1; i >= 0; i--) {
            double[] s = strikes.get(i);
            if (age < s[3]) continue;
            strikes.remove(i);
            Vec3d at = new Vec3d(s[0], s[1], s[2]);
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(at);
                bolt.setCosmetic(true);
                w.spawnEntity(bolt);
            }
            for (LivingEntity v : victims(at.add(0, 0.8, 0), 3)) {
                hurt(v, 11, false);
                v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1), this);
            }
        }
    }

    @Override
    protected boolean runAttack(int id, int t, @Nullable LivingEntity target) {
        ServerWorld w = (ServerWorld) getWorld();
        switch (id) {
            case STORM -> {
                if (target != null) idleMove(target, 20);
                if (t == 1) {
                    playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.6f);
                    playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 4f, 0.5f);
                }
                if (target != null && t % 3 == 0 && t <= 24) {
                    for (LivingEntity v : victims(target.getPos(), 24)) {
                        BlockPos p = BlockPos.ofFloored(v.getX() + random.nextGaussian() * 2.2, v.getY(), v.getZ() + random.nextGaussian() * 2.2);
                        p = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p);
                        Vec3d at = Vec3d.ofBottomCenter(p);
                        warn(at, 3f, 20, AZURE);
                        strikes.add(new double[]{at.x, at.y, at.z, age + 20});
                        if (strikes.size() > 24) break;
                    }
                }
                return t >= 46;
            }
            case TIDE -> {
                if (target == null) return true;
                Vec3d away = new Vec3d(getX() - target.getX(), 0, getZ() - target.getZ());
                if (away.lengthSquared() < 1e-3) away = new Vec3d(1, 0, 0);
                Vec3d hover = target.getPos().add(away.normalize().multiply(11)).add(0, 5.5, 0);
                if (t < 14) flyTo(hover, 1.1);
                else {
                    setVelocity(getVelocity().multiply(0.6));
                    velocityModified = true;
                    faceTarget(target);
                }
                if (t == 8) playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 3f, 0.6f);
                if (t == 14) {
                    startAnim(BREATH);
                    NbtCompound n = new NbtCompound();
                    n.putInt("c", getId());
                    n.putInt("d", 40);
                    n.putInt("col", 0xA8F0FF);
                    n.putFloat("len", 16);
                    AWNet.fx(w, getPos(), "boss_breath", n);
                }
                if (t >= 14 && t < 54 && t % 3 == 0) {
                    Vec3d mouth = getPos().add(forward().multiply(4)).add(0, 2, 0);
                    Vec3d aim = target.getPos().add(0, 0.8, 0).subtract(mouth).normalize();
                    for (LivingEntity v : victims(mouth.add(aim.multiply(7)), 9)) {
                        Vec3d to = v.getPos().add(0, 0.8, 0).subtract(mouth);
                        if (to.length() > 17 || to.normalize().dotProduct(aim) < 0.8) continue;
                        hurt(v, 3.5f, false);
                        v.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2), this);
                        v.setFrozenTicks(Math.min(v.getMinFreezeDamageTicks() + 40, v.getFrozenTicks() + 12));
                        v.extinguish();
                    }
                    if (t % 9 == 0) playSound(SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 2f, 0.6f);
                }
                return t >= 58;
            }
            case ROAR -> {
                setVelocity(getVelocity().multiply(0.5));
                velocityModified = true;
                if (t == 1) playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 5f, 0.4f);
                if (t == 12) {
                    AWNet.shakeAround(w, getPos(), 1.0f, 20, 48);
                    MobFx.ring(w, this, 14, AZURE);
                    int n = 2 + (summons == 0 ? 1 : 0);
                    for (int i = 0; i < n; i++) {
                        DrakeEntity d = AWMobs.DRAKE.create(w);
                        if (d == null) continue;
                        double a = random.nextDouble() * Math.PI * 2;
                        d.refreshPositionAndAngles(getX() + Math.cos(a) * 6, getY() + 2, getZ() + Math.sin(a) * 6, random.nextFloat() * 360, 0);
                        d.initialize(w, w.getLocalDifficulty(d.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
                        if (target != null) d.setTarget(target);
                        w.spawnEntity(d);
                    }
                    summons++;
                }
                return t >= 30;
            }
            default -> {
                return super.runAttack(id, t, target);
            }
        }
    }

    @Override
    public float getSoundPitch() {
        return 0.6f;
    }

    @Override
    protected float getSoundVolume() {
        return 5f;
    }
}
