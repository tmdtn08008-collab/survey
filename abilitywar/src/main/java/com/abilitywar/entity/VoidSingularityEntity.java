package com.abilitywar.entity;

import com.abilitywar.combat.Combat;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 공허 궁극기 "사건의 지평선" 블랙홀.
 * - 반경 안의 생명체를 끌어당긴다 (시전자 제외)
 * - 중심(사건의 지평선)에 붙잡히면 빠져나갈 수 없고, 최대 체력의 75% 만큼 피해를 입는다 (보스는 10%)
 * - 시간이 끝나면 붕괴하며 남은 피해를 한꺼번에 준다
 */
public class VoidSingularityEntity extends Entity {
    public static final float PULL_RADIUS = 14f;
    public static final float CAPTURE_RADIUS = 2.4f;
    public static final float DAMAGE_RATIO = 0.75f;
    /** 보스는 최대 체력의 10% 만. */
    public static final float BOSS_DAMAGE_RATIO = 0.10f;

    private static final TrackedData<Integer> DURATION = DataTracker.registerData(VoidSingularityEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER_ID = DataTracker.registerData(VoidSingularityEntity.class, TrackedDataHandlerRegistry.INTEGER);

    @Nullable
    private UUID ownerUuid;
    private final Map<UUID, Captured> captured = new HashMap<>();

    private static final class Captured {
        final LivingEntity entity;
        final float owed;
        float dealt;
        final float phase;
        /** 보스: 최대 체력의 10% 고정 피해 (장비·스탯 배율 없음). */
        final boolean boss;

        Captured(LivingEntity entity, float owed, float phase, boolean boss) {
            this.entity = entity;
            this.owed = owed;
            this.phase = phase;
            this.boss = boss;
        }

        void hit(ServerWorld w, @Nullable Entity owner, float amount) {
            var src = AWDamage.source(w, AWDamage.ULTIMATE, owner);
            if (boss && entity instanceof net.minecraft.entity.boss.dragon.EnderDragonEntity d) Combat.damageFixedDragon(d, src, amount, AWNet.DMG_ULT);
            else if (boss) Combat.damageFixed(entity, src, amount, AWNet.DMG_ULT);
            else Combat.damage(entity, src, amount, AWNet.DMG_ULT);
        }
    }

    public VoidSingularityEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
    }

    public void setup(Entity owner, int duration) {
        this.ownerUuid = owner.getUuid();
        this.dataTracker.set(OWNER_ID, owner.getId());
        this.dataTracker.set(DURATION, duration);
    }

    public int getDuration() {
        return this.dataTracker.get(DURATION);
    }

    public int getOwnerId() {
        return this.dataTracker.get(OWNER_ID);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(DURATION, 150);
        this.dataTracker.startTracking(OWNER_ID, -1);
    }

    @Nullable
    private Entity owner() {
        if (ownerUuid == null || !(getWorld() instanceof ServerWorld sw)) return null;
        return sw.getEntity(ownerUuid);
    }

    @Override
    public void tick() {
        super.tick();
        this.setVelocity(Vec3d.ZERO);
        if (getWorld().isClient) return;
        ServerWorld w = (ServerWorld) getWorld();
        int duration = getDuration();
        if (this.age >= duration) {
            collapse(w);
            this.discard();
            return;
        }
        Entity owner = owner();
        Vec3d center = getPos();
        float grow = MathHelper.clamp(this.age / 20f, 0f, 1f);
        float radius = PULL_RADIUS * (0.4f + 0.6f * grow);

        // 붙잡힌 대상 유지 + 지속 피해
        captured.values().removeIf(c -> !c.entity.isAlive() || c.entity.isRemoved() || c.entity.getWorld() != w);
        for (Captured c : captured.values()) {
            LivingEntity e = c.entity;
            double ang = (this.age * 0.18) + c.phase;
            Vec3d orbit = center.add(Math.cos(ang) * 0.55, -e.getHeight() * 0.5 + Math.sin(this.age * 0.11 + c.phase) * 0.15, Math.sin(ang) * 0.55);
            Vec3d v = orbit.subtract(e.getPos()).multiply(0.45);
            Combat.setVelocity(e, v);
            Combat.hold(e, true);
            Combat.stun(e, 12, owner);
            if (this.age % 8 == 0 && c.dealt < c.owed - 0.01f) {
                float chunk = Math.min(c.owed * 0.07f, c.owed - c.dealt);
                c.dealt += chunk;
                c.hit(w, owner, chunk);
            }
        }

        // 흡입
        Box box = new Box(center, center).expand(radius);
        for (Entity raw : w.getOtherEntities(this, box, en -> en instanceof LivingEntity)) {
            LivingEntity e = (LivingEntity) raw;
            if (captured.containsKey(e.getUuid())) continue;
            if (e == owner || !e.isAlive() || e.isSpectator()) continue;
            if (e instanceof PlayerEntity pe && pe.isCreative()) continue;
            if (owner != null && owner.isTeammate(e)) continue;
            Vec3d c = e.getBoundingBox().getCenter();
            Vec3d to = center.subtract(c);
            double d = to.length();
            if (d > radius) continue;
            if (d <= CAPTURE_RADIUS && grow > 0.3f) {
                capture(w, e, owner);
                continue;
            }
            Vec3d dir = to.normalize();
            double k = 1.0 - d / radius;
            double strength = 0.05 + 0.34 * Math.pow(k, 1.4);
            Vec3d swirl = dir.crossProduct(new Vec3d(0, 1, 0)).multiply(strength * 0.4);
            Vec3d v = e.getVelocity().multiply(0.86).add(dir.multiply(strength)).add(swirl);
            if (v.length() > 1.9) v = v.normalize().multiply(1.9);
            Combat.setVelocity(e, v);
        }
        if (this.age % 30 == 0) {
            w.playSound(null, getX(), getY(), getZ(), AWSounds.VOID_BLACKHOLE, SoundCategory.PLAYERS, 2.5f, 0.8f + random.nextFloat() * 0.2f);
        }
    }

    private void capture(ServerWorld w, LivingEntity e, @Nullable Entity owner) {
        boolean boss = com.abilitywar.rpg.RpgManager.isBoss(e);
        float owed = e.getMaxHealth() * (boss ? BOSS_DAMAGE_RATIO : DAMAGE_RATIO);
        captured.put(e.getUuid(), new Captured(e, owed, random.nextFloat() * 6.28f, boss));
        Combat.hold(e, true);
        Combat.stun(e, 20, owner);
        if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.captured");
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putInt("e", e.getId());
        AWNet.fx(w, getPos(), "capture", n);
        AWNet.shake(e, 0.9f, 16);
        w.playSound(null, e.getX(), e.getY(), e.getZ(), AWSounds.VOID_PULL, SoundCategory.PLAYERS, 1.5f, 0.6f);
    }

    private void collapse(ServerWorld w) {
        Entity owner = owner();
        Vec3d center = getPos();
        AWNet.fx(w, center, "collapse");
        w.playSound(null, center.x, center.y, center.z, AWSounds.VOID_COLLAPSE, SoundCategory.PLAYERS, 5.0f, 1.0f);
        AWNet.shakeAround(w, center, 1.3f, 24, 36);
        for (Captured c : captured.values()) {
            LivingEntity e = c.entity;
            if (!e.isAlive()) continue;
            Combat.hold(e, false);
            Combat.unstun(e);
            float rest = c.owed - c.dealt;
            if (rest > 0.01f) c.hit(w, owner, rest);
            if (c.boss) {
                // 보스: 튕겨 내지 않음 (떨어지는 피해까지 더해져 10% 를 넘지 않게)
                Combat.setVelocity(e, Vec3d.ZERO);
                e.fallDistance = -16;
            } else {
                Vec3d out = Combat.horizontal(e.getPos().subtract(center)).multiply(0.9).add(0, 0.6, 0);
                Combat.setVelocity(e, out);
            }
            Combat.concuss(e, 60, 30, owner);
            if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched");
        }
        captured.clear();
        // 붕괴 충격파: 근처 대상 밀쳐내기
        for (Entity raw : w.getOtherEntities(this, new Box(center, center).expand(6), en -> en instanceof LivingEntity && en != owner)) {
            if (raw.isSpectator()) continue;
            Vec3d out = Combat.horizontal(raw.getPos().subtract(center)).multiply(0.8).add(0, 0.45, 0);
            Combat.addVelocity(raw, out);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!getWorld().isClient) {
            for (Captured c : captured.values()) {
                Combat.hold(c.entity, false);
                Combat.unstun(c.entity);
            }
            captured.clear();
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 256 * 256;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
