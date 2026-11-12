package com.abilitywar.combat;

import com.abilitywar.data.AWPlayer;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 전투 유틸리티. */
public final class Combat {
    /** 다음 피해를 치명타로 표시 (데미지 숫자 색). */
    public static final ThreadLocal<Byte> NEXT_KIND = new ThreadLocal<>();

    public static boolean canTarget(Entity attacker, Entity target) {
        if (!(target instanceof LivingEntity le)) return false;
        if (target == attacker || !le.isAlive() || target.isSpectator()) return false;
        if (target instanceof PlayerEntity p && (p.isCreative() && !(attacker instanceof PlayerEntity))) return false;
        if (target instanceof ArmorStandEntity as && as.isMarker()) return false;
        if (attacker != null && attacker.isTeammate(target)) return false;
        if (attacker != null && com.abilitywar.ability.warlock.Necromancy.allied(attacker, target)) return false;
        if (target instanceof AWPlayer awp && awp.aw$hasFlag(AWPlayer.FLAG_SHADOW)) return false;
        return true;
    }

    /** 시선 방향 원뿔 안의 가장 적절한 대상. */
    @Nullable
    public static LivingEntity findTarget(LivingEntity caster, double range, double coneDegrees) {
        Vec3d eye = caster.getEyePos();
        Vec3d look = caster.getRotationVec(1.0f);
        Box box = caster.getBoundingBox().stretch(look.multiply(range)).expand(3.0);
        double cosLimit = Math.cos(Math.toRadians(coneDegrees));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : caster.getWorld().getOtherEntities(caster, box, e -> canTarget(caster, e))) {
            LivingEntity le = (LivingEntity) e;
            Vec3d c = le.getBoundingBox().getCenter();
            Vec3d to = c.subtract(eye);
            double dist = to.length();
            if (dist > range + le.getWidth()) continue;
            double cos = to.normalize().dotProduct(look);
            // 가까운 대상은 판정을 넉넉하게 (몸통 폭 고려)
            double angleSlack = Math.atan2(le.getWidth() * 0.75, Math.max(dist, 0.1));
            double ang = Math.acos(MathHelper.clamp(cos, -1, 1));
            if (ang - angleSlack > Math.acos(cosLimit)) continue;
            if (!hasLineOfSight(caster, le)) continue;
            double score = Math.max(0, ang - angleSlack) * 6.0 + dist * 0.08;
            if (score < bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    /** 원뿔 범위 안 모든 대상. */
    public static List<LivingEntity> findInCone(LivingEntity caster, Vec3d origin, Vec3d dir, double range, double halfAngleDeg) {
        List<LivingEntity> list = new ArrayList<>();
        Box box = new Box(origin, origin).expand(range + 1);
        double lim = Math.toRadians(halfAngleDeg);
        for (Entity e : caster.getWorld().getOtherEntities(caster, box, e -> canTarget(caster, e))) {
            LivingEntity le = (LivingEntity) e;
            Vec3d c = le.getBoundingBox().getCenter();
            Vec3d to = c.subtract(origin);
            double dist = to.length();
            if (dist > range + le.getWidth() * 0.5) continue;
            double slack = Math.atan2(le.getWidth() * 0.8, Math.max(dist, 0.1));
            double ang = Math.acos(MathHelper.clamp(to.normalize().dotProduct(dir), -1, 1));
            if (dist > 1.2 && ang - slack > lim) continue;
            if (!hasLineOfSight(caster, le)) continue;
            list.add(le);
        }
        return list;
    }

    public static List<LivingEntity> findInRadius(Entity center, Vec3d pos, double radius) {
        List<LivingEntity> list = new ArrayList<>();
        for (Entity e : center.getWorld().getOtherEntities(center, new Box(pos, pos).expand(radius), e -> canTarget(center, e))) {
            if (e.getBoundingBox().getCenter().squaredDistanceTo(pos) <= radius * radius) list.add((LivingEntity) e);
        }
        return list;
    }

    public static boolean hasLineOfSight(Entity a, Entity b) {
        Vec3d from = a.getEyePos();
        Vec3d[] tos = {b.getEyePos(), b.getBoundingBox().getCenter(), b.getPos().add(0, 0.2, 0)};
        for (Vec3d to : tos) {
            BlockHitResult hit = a.getWorld().raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, a));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    /** 발밑부터 땅까지 거리 (최대 maxDist). */
    public static double distanceToGround(Entity e, double maxDist) {
        if (e.isOnGround()) return 0;
        Vec3d from = e.getPos();
        Vec3d to = from.add(0, -maxDist, 0);
        double best = maxDist;
        double hw = e.getWidth() * 0.4;
        double[][] offs = {{0, 0}, {hw, hw}, {-hw, hw}, {hw, -hw}, {-hw, -hw}};
        for (double[] o : offs) {
            BlockHitResult hit = e.getWorld().raycast(new RaycastContext(from.add(o[0], 0.01, o[1]), to.add(o[0], 0, o[1]),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, e));
            if (hit.getType() != HitResult.Type.MISS) {
                best = Math.min(best, from.y - hit.getPos().y);
            }
        }
        return Math.max(0, best);
    }

    @Nullable
    public static Vec3d groundBelow(ServerWorld world, Vec3d pos, double maxDist, Entity e) {
        BlockHitResult hit = world.raycast(new RaycastContext(pos.add(0, 0.5, 0), pos.add(0, -maxDist, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, e));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getPos();
    }

    private static final ThreadLocal<Boolean> FIXED = new ThreadLocal<>();

    /** 고정 피해: 장비·스탯·버프 배율을 안 받는다 (보스에게 주는 공허 궁극기 등). */
    public static boolean damageFixed(LivingEntity target, DamageSource source, float amount, byte kind) {
        FIXED.set(Boolean.TRUE);
        try {
            return damage(target, source, amount, kind);
        } finally {
            FIXED.remove();
        }
    }

    /** 엔더 드래곤: 몸통 대신 머리에 고정 피해 (몸통은 피해가 1/4 로 줄어서). */
    public static boolean damageFixedDragon(net.minecraft.entity.boss.dragon.EnderDragonEntity d, DamageSource source, float amount, byte kind) {
        FIXED.set(Boolean.TRUE);
        NEXT_KIND.set(kind);
        d.timeUntilRegen = 0;
        try {
            return d.damagePart(d.head, source, amount);
        } finally {
            FIXED.remove();
            NEXT_KIND.remove();
        }
    }

    public static boolean isFixedDamage() {
        return FIXED.get() != null;
    }

    public static boolean damage(LivingEntity target, DamageSource source, float amount, byte kind) {
        target.timeUntilRegen = 0;
        NEXT_KIND.set(kind);
        try {
            return target.damage(source, amount);
        } finally {
            NEXT_KIND.remove();
        }
    }

    public static void setVelocity(Entity e, Vec3d v) {
        if (e instanceof ServerPlayerEntity sp && com.abilitywar.server.AbilityManager.data(sp).hasSuperArmor()) return;
        setVelocitySelf(e, v);
    }

    /** 자기 자신의 이동 (슈퍼아머 무시). */
    public static void setVelocitySelf(Entity e, Vec3d v) {
        e.setVelocity(v);
        e.velocityModified = true;
        if (e instanceof LivingEntity le) le.fallDistance = 0;
    }

    public static void addVelocity(Entity e, Vec3d v) {
        setVelocity(e, e.getVelocity().add(v));
    }

    public static void effect(LivingEntity e, StatusEffect effect, int duration, int amp, @Nullable Entity source) {
        e.addStatusEffect(new StatusEffectInstance(effect, duration, amp, false, false, true), source);
    }

    /** 뇌진탕 (화면 흐림/흔들림) + 둔화. */
    public static void concuss(LivingEntity e, int concussionTicks, int slowTicks, @Nullable Entity source) {
        effect(e, AWEffects.CONCUSSION, concussionTicks, 0, source);
        if (slowTicks > 0) effect(e, StatusEffects.SLOWNESS, slowTicks, 1, source);
    }

    public static void stun(LivingEntity e, int ticks, @Nullable Entity source) {
        StatusEffectInstance cur = e.getStatusEffect(AWEffects.STUN);
        if (cur != null && cur.getDuration() >= ticks) return;
        e.addStatusEffect(new StatusEffectInstance(AWEffects.STUN, ticks, 0, false, false, false), source);
    }

    public static void unstun(LivingEntity e) {
        e.removeStatusEffect(AWEffects.STUN);
    }

    /** 대상을 마주보게 플레이어/엔티티 회전값. */
    public static float[] lookAt(Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (MathHelper.atan2(d.z, d.x) * 57.2957795) - 90f;
        float pitch = (float) -(MathHelper.atan2(d.y, h) * 57.2957795);
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90, 90)};
    }

    /** 순간이동 (플레이어는 패킷, 나머지는 위치 갱신). */
    public static void teleport(Entity e, Vec3d pos, float yaw, float pitch) {
        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, pitch);
            sp.setHeadYaw(yaw);
        } else {
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, pitch);
            e.setHeadYaw(yaw);
            e.setBodyYaw(yaw);
        }
        e.fallDistance = 0;
    }

    /** 순간이동 가능한 안전한 위치 찾기 (위로 최대 3칸 탐색). */
    @Nullable
    public static Vec3d safeSpot(Entity e, Vec3d desired) {
        for (int dy = 0; dy <= 3; dy++) {
            Vec3d p = desired.add(0, dy * 0.5, 0);
            Box b = e.getDimensions(e.getPose()).getBoxAt(p);
            if (e.getWorld().isSpaceEmpty(e, b)) return p;
        }
        return null;
    }

    public static boolean isShadowSpot(ServerWorld world, BlockPos standPos) {
        // 하늘이 안 보이거나(나무 밑, 실내, 동굴) 어두우면 그림자
        if (!world.isSkyVisible(standPos)) return true;
        return world.getLightLevel(standPos) <= 7;
    }

    public static Vec3d horizontal(Vec3d v) {
        Vec3d h = new Vec3d(v.x, 0, v.z);
        return h.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : h.normalize();
    }

    public static final String NOGRAV_TAG = "aw_forced_nograv";

    /** 공중 고정 (중력 끄기). 해제 시 반드시 hold(e, false). */
    public static void hold(Entity e, boolean on) {
        if (on) {
            if (!e.hasNoGravity()) {
                e.setNoGravity(true);
                e.addCommandTag(NOGRAV_TAG);
            }
        } else if (e.getCommandTags().contains(NOGRAV_TAG)) {
            e.setNoGravity(false);
            e.removeScoreboardTag(NOGRAV_TAG);
        }
    }

    public static void hitFlinch(LivingEntity target) {
        if (target instanceof PlayerEntity) AWNet.anim(target, "common.flinch");
    }

    private Combat() {
    }
}
