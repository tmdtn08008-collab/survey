package com.abilitywar.ability.blade;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWGameRules;
import com.abilitywar.registry.AWSounds;
import com.abilitywar.server.AbilityManager;
import com.abilitywar.server.ServerActions;
import com.abilitywar.server.TimedAction;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 마검사 — 마력이 깃든 검을 쓰는 연계형 검사.
 * <pre>
 * 1  마검 발도   : 발도 자세 → 마력 집중 → 순간 돌진 베기 (지나간 자리에 잔상) → 마지막 일격으로 살짝 띄움. 마력 표식.
 *                  표식이 있는 적을 다시 베면 추가 참격. 공중의 적은 따라가서 벰. T 직후면 강화 발도.
 * 2  파공참     : 아래에서 위로 올려베어 초승달 참격 발사 → 높이 띄움 → 공중에 마력 균열 추가 피해.
 *                  1번 직후면 빨라짐, 공중의 적은 한 번 더 띄움, 표식이 있으면 추가 피해.
 * 3  무형검무   : 순간이동하며 오른쪽 → 왼쪽 → 뒤 → 정면 참격 → 내려찍기 (5타). 공중 추격/강제 내리꽂기.
 *                  1→2 연속 적중 후면 마지막 참격 폭발, 표식이 있으면 추가 참격, 도중에 T 로 공격을 이어감.
 * 4  마검 반격   : 검을 세운 방어 자세. 평타/스킬(궁극기 제외) 무효 → 튕겨냄 → 뒤로 순간이동 → 등 베어 날려버림.
 *                  성공 시 3번 즉시 연계 + 마력 게이지 회복.
 * R  천지개벽   : 검을 들어 마력 흡수 → 거대한 검 → 도약 → 초대형 참격 → 지나간 자리 마력 폭발. 흡인, 슈퍼아머.
 * T  잔영보     : 잔상으로 변해 바라보는 쪽(또는 적의 뒤)으로 순간이동. 남은 검의 잔상이 잠시 후 폭발.
 *                  1번 직후면 그 적의 뒤로, 3번 도중이면 공격을 이어감, T 직후 1번은 강화 발도.
 * </pre>
 */
public class BladeAbility extends Ability {
    public static final int COLOR = 0xB44CFF;
    public static final int CD_S1 = 140, CD_S1_MISS = 60;
    public static final int CD_S2 = 160;
    public static final int CD_S3 = 240, CD_S3_MISS = 40;
    public static final int CD_S4 = 200, CD_S4_OK = 140;
    public static final int CD_T = 160;
    public static final int COMBO_WINDOW = 50;
    public static final int T_ENHANCE_WINDOW = 30;
    public static final int ULT_CINEMATIC = 72;
    public static final int ULT_FREEZE = 47;

    static final class St {
        long s1Hit = -999, s2Hit = -999, tAt = -999;
        int s1Target = -1;
        boolean s2AfterS1;
        @Nullable
        DanceAction dance;
    }

    private final Map<UUID, St> states = new HashMap<>();

    public BladeAbility() {
        super(AbilityWar.id("blade"), "blade", 0xFF000000 | COLOR);
    }

    St st(PlayerEntity p) {
        return states.computeIfAbsent(p.getUuid(), k -> new St());
    }

    // =================================================================== hooks

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 13 : 8;
    }

    @Override
    public boolean allowWhileBusy(ServerPlayerEntity player, AbilityData data, SkillSlot slot) {
        // 무형검무 도중의 잔영보 = 공격 이어가기
        if (slot != SkillSlot.PASSIVE) return false;
        DanceAction da = st(player).dance;
        return da != null && da.canExtend();
    }

    @Override
    public boolean clientAllowWhileBusy(PlayerEntity player, SkillSlot slot) {
        return slot == SkillSlot.PASSIVE;
    }

    @Override
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 2.5f);
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> draw(p, d);
            case SKILL2 -> breaker(p, d);
            case SKILL3 -> dance(p, d);
            case SKILL4 -> counterStance(p, d);
            case ULTIMATE -> ultimate(p, d);
            case PASSIVE -> phantomStep(p, d);
            case DASH -> false;
        };
    }

    // =================================================================== 도우미

    /** 검격 연출: 주인 기준 yaw 방향, tilt 기울기 (0 = 가로, 90 = 세로 올려베기, -90 = 내려베기). */
    static void slash(ServerWorld w, Entity e, float yaw, float tilt, float scale, int kind) {
        NbtCompound n = nbt();
        n.putInt("e", e.getId());
        n.putFloat("yaw", yaw);
        n.putFloat("tilt", tilt);
        n.putFloat("s", scale);
        n.putInt("k", kind);
        AWNet.fx(w, e.getPos(), "blade_slash", n);
    }

    static void afterimage(ServerWorld w, Entity e, Vec3d at) {
        NbtCompound n = nbt();
        n.putInt("e", e.getId());
        AWNet.fx(w, at, "blade_afterimage", n);
    }

    static void mark(ServerWorld w, LivingEntity t, Entity src) {
        t.addStatusEffect(new StatusEffectInstance(AWEffects.MANA_MARK, 200, 0, false, false, true), src);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putInt("d", 200);
        AWNet.fx(w, t.getPos(), "blade_mark", n);
        sound(t, AWSounds.BLADE_MARK, 0.8f, 1.2f);
    }

    static boolean marked(LivingEntity t) {
        return t.hasStatusEffect(AWEffects.MANA_MARK);
    }

    static boolean consumeMark(ServerWorld w, LivingEntity t) {
        if (!marked(t)) return false;
        t.removeStatusEffect(AWEffects.MANA_MARK);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        AWNet.fx(w, chest(t), "blade_mark_burst", n);
        sound(t, AWSounds.BLADE_EXPLODE, 0.8f, 1.6f);
        return true;
    }

    static boolean airborne(LivingEntity t, double h) {
        return Combat.distanceToGround(t, 6) >= h;
    }

    static void hit(ServerWorld w, ServerPlayerEntity p, LivingEntity t, float amount, byte kind, boolean unblockable) {
        Combat.damage(t, AWDamage.source(w, unblockable ? AWDamage.GRAB : AWDamage.SKILL, p), amount, kind);
    }

    static float yawTo(Entity from, Entity to) {
        return Combat.lookAt(from.getEyePos(), chest(to))[0];
    }

    static void face(ServerPlayerEntity c, Vec3d at, Entity look) {
        float[] rot = Combat.lookAt(at.add(0, c.getStandingEyeHeight(), 0), chest(look));
        Combat.teleport(c, at, rot[0], rot[1]);
    }

    /** a→b 선분 근처(반지름 r)의 적들. */
    static List<LivingEntity> alongPath(ServerPlayerEntity c, Vec3d a, Vec3d b, double r) {
        Box box = new Box(a, b).expand(r + 1);
        Vec3d ab = b.subtract(a);
        double len2 = Math.max(1e-6, ab.lengthSquared());
        return c.getWorld().getEntitiesByClass(LivingEntity.class, box, e -> {
            if (e == c || !e.isAlive() || !Combat.canTarget(c, e)) return false;
            Vec3d p = e.getBoundingBox().getCenter();
            double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / len2, 0, 1);
            return p.distanceTo(a.add(ab.multiply(t))) <= r + e.getWidth() * 0.5;
        });
    }

    /** 시작점에서 dir 쪽으로 최대 maxDist 까지, 막히지 않은 마지막 안전한 자리. */
    static Vec3d dashEnd(ServerPlayerEntity c, Vec3d from, Vec3d dir, double maxDist) {
        Vec3d eye = from.add(0, 1.0, 0);
        BlockHitResult hit = c.getWorld().raycast(new RaycastContext(eye, eye.add(dir.multiply(maxDist)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, c));
        double dist = hit.getType() == HitResult.Type.MISS ? maxDist : Math.max(0, hit.getPos().distanceTo(eye) - 0.6);
        for (double d = dist; d > 0.5; d -= 0.5) {
            Vec3d s = Combat.safeSpot(c, from.add(dir.multiply(d)));
            if (s != null) return s;
        }
        return from;
    }

    // =================================================================== 평타 (검 3연격)

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, AWSounds.BLADE_SLASH, 0.7f, 0.95f + combo * 0.08f);
        slash(w, p, p.getYaw(), combo == 0 ? 15 : combo == 1 ? -160 : -80, combo == 2 ? 1.25f : 1f, combo == 2 ? 1 : 0);
        if (target == null) return;
        boolean fin = combo == 2;
        float base = fin ? 5f : 3f;
        Combat.damage(target, AWDamage.source(w, AWDamage.PUNCH, p), base, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
        Vec3d dir = Combat.horizontal(target.getPos().subtract(p.getPos()));
        Combat.setVelocity(target, fin ? dir.multiply(0.65).add(0, 0.28, 0) : target.getVelocity().multiply(0.3).add(dir.multiply(0.2)).add(0, 0.05, 0));
        impact(w, chest(target).subtract(dir.multiply(target.getWidth() * 0.5)), fin ? "heavy" : "punch", fin ? 1.1f : 0.7f, COLOR);
        sound(target, fin ? AWSounds.BLADE_SLASH_HEAVY : AWSounds.VOID_PUNCH, 0.8f, 1.2f);
        Combat.hitFlinch(target);
    }

    // =================================================================== 1. 마검 발도

    private boolean draw(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        boolean enh = d.now() - s.tAt <= T_ENHANCE_WINDOW;
        LivingEntity target = Combat.findTarget(p, enh ? 15 : 12, 16);
        boolean air = target != null && airborne(target, 1.6);
        d.setBusy(18);
        d.startCooldown(SkillSlot.SKILL1, target != null ? CD_S1 : CD_S1_MISS);
        AWNet.anim(p, air ? "blade.skill1_air" : "blade.skill1");
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putBoolean("enh", enh);
        AWNet.fx(p.getServerWorld(), p.getPos(), "blade_charge", n);
        sound(p, AWSounds.BLADE_CHARGE, 0.9f, enh ? 1.4f : 1.1f);
        ServerActions.start(new DrawAction(p, d, target, air, enh));
        return true;
    }

    class DrawAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        @Nullable
        final LivingEntity t;
        final boolean air, enh;
        final Set<LivingEntity> hits = new HashSet<>();
        /** 이미 표식이 있던 적: 발도를 다시 맞으면 추가 참격. */
        final Set<LivingEntity> premarked = new HashSet<>();

        DrawAction(ServerPlayerEntity c, AbilityData d, @Nullable LivingEntity t, boolean air, boolean enh) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
            this.enh = enh;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt < 6) {
                // 발도 자세: 멈춰서 마력을 모음
                Combat.setVelocitySelf(c, c.getVelocity().multiply(0.3).add(0, air ? 0.02 : 0, 0));
                if (air) Combat.hold(c, true);
            }
            if (tt == 6) {
                Vec3d from = c.getPos();
                Vec3d dir;
                double dist;
                if (t != null && t.isAlive()) {
                    Vec3d to = (air ? t.getPos().subtract(0, 0.3, 0) : t.getPos()).subtract(from);
                    dir = air ? to.normalize() : Combat.horizontal(to);
                    dist = MathHelper.clamp((air ? to.length() : to.horizontalLength()) + 2.4, 3, enh ? 15 : 12);
                } else {
                    dir = Combat.horizontal(c.getRotationVec(1f));
                    dist = enh ? 10 : 7;
                }
                Vec3d end = air ? from.add(dir.multiply(dist)) : dashEnd(c, from, dir, dist);
                if (air) {
                    Vec3d safe = Combat.safeSpot(c, end);
                    end = safe != null ? safe : from;
                }
                float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 57.2957795) - 90f;
                Combat.teleport(c, end, yaw, c.getPitch());
                Combat.setVelocitySelf(c, Vec3d.ZERO);
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putDouble("fx", from.x);
                n.putDouble("fy", from.y);
                n.putDouble("fz", from.z);
                n.putBoolean("enh", enh);
                AWNet.fx(w, end, "blade_draw", n);
                sound(w, from, AWSounds.BLADE_DRAW, 1.3f, enh ? 0.8f : 1.0f);
                sound(c, AWSounds.BLADE_SLASH, 1.2f, 0.8f);
                for (LivingEntity e : alongPath(c, from.add(0, 0.9, 0), end.add(0, 0.9, 0), enh ? 1.9 : 1.5)) {
                    hits.add(e);
                    hit(w, c, e, enh ? 7.5f : 5f, enh ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, false);
                    Combat.stun(e, 8, c);
                    Combat.setVelocity(e, Vec3d.ZERO);
                    if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.flinch");
                    if (marked(e)) premarked.add(e);
                    mark(w, e, c);
                }
                if (enh) AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                AWNet.shake(c, 0.3f, 6);
                if (!hits.isEmpty()) {
                    St s = st(c);
                    s.s1Hit = d.now();
                    s.s2AfterS1 = false;
                    s.s1Target = (t != null && hits.contains(t) ? t : hits.iterator().next()).getId();
                    d.addUlt(4);
                }
            }
            if (tt == 9) {
                // 지나간 뒤 베인 자리가 갈라지며 마지막 일격: 살짝 띄움
                for (LivingEntity e : hits) {
                    if (!e.isAlive()) continue;
                    hit(w, c, e, enh ? 3f : 2f, AWNet.DMG_SKILL, true);
                    Combat.unstun(e);
                    Combat.setVelocity(e, new Vec3d(0, air ? 0.9 : 0.55, 0));
                    NbtCompound n = nbt();
                    n.putInt("e", e.getId());
                    n.putBoolean("enh", enh);
                    AWNet.fx(w, chest(e), "blade_cut", n);
                    sound(e, AWSounds.BLADE_SLASH_HEAVY, 1.1f, 1.1f);
                    if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched_up");
                }
                LivingEntity main = t != null && hits.contains(t) ? t : null;
                if (main != null) {
                    if (air) {
                        // 공중: 따라 올라가 한 번 더 벰
                        Vec3d dir = Combat.horizontal(c.getPos().subtract(main.getPos()));
                        Vec3d at = Combat.safeSpot(c, main.getPos().add(dir.multiply(1.4 + main.getWidth() * 0.5)));
                        if (at != null) {
                            afterimage(w, c, c.getPos());
                            face(c, at, main);
                            Combat.hold(c, true);
                        }
                    } else {
                        // 돌아서서 적을 바라봄 (2번 연계용)
                        float[] rot = Combat.lookAt(c.getEyePos(), chest(main));
                        Combat.teleport(c, c.getPos(), rot[0], rot[1]);
                    }
                }
            }
            if (tt == 12 && !premarked.isEmpty()) {
                // 표식이 있던 적에게 다시 발도: 추가 참격
                boolean any = false;
                for (LivingEntity e : premarked) {
                    if (!e.isAlive()) continue;
                    any = true;
                    hit(w, c, e, enh ? 3.5f : 2.5f, AWNet.DMG_CRIT, true);
                    NbtCompound n = nbt();
                    n.putInt("e", e.getId());
                    n.putBoolean("enh", true);
                    AWNet.fx(w, chest(e), "blade_cut", n);
                    sound(e, AWSounds.BLADE_SLASH, 1.2f, 1.4f);
                }
                if (any) AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
            }
            if (tt == 12 && air && t != null && hits.contains(t) && t.isAlive()) {
                hit(w, c, t, 2f, AWNet.DMG_CRIT, false);
                slash(w, c, yawTo(c, t), 70, 1.2f, 1);
                Combat.setVelocity(t, new Vec3d(0, 0.5, 0));
                sound(t, AWSounds.BLADE_SLASH, 1.2f, 1.3f);
            }
            return tt >= 15;
        }

        @Override
        protected void cleanup() {
            Combat.hold(c, false);
        }
    }

    // =================================================================== 2. 파공참

    private boolean breaker(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        boolean fast = d.now() - s.s1Hit <= COMBO_WINDOW;
        int windup = fast ? 2 : 6;
        d.setBusy(windup + 9);
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        AWNet.anim(p, fast ? "blade.skill2_fast" : "blade.skill2");
        sound(p, AWSounds.BLADE_CHARGE, 0.7f, 1.6f);
        ServerActions.start(new BreakerAction(p, d, windup, fast));
        return true;
    }

    class BreakerAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final int windup;
        final boolean fast;
        Vec3d pos, dir;
        int flown;
        final Set<LivingEntity> hits = new HashSet<>();
        final Map<LivingEntity, Integer> rifts = new HashMap<>();

        BreakerAction(ServerPlayerEntity c, AbilityData d, int windup, boolean fast) {
            super(c);
            this.c = c;
            this.d = d;
            this.windup = windup;
            this.fast = fast;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == windup) {
                dir = c.getRotationVec(1f);
                // 너무 위/아래를 보면 수평에 가깝게
                double py = MathHelper.clamp(dir.y, -0.35, 0.45);
                dir = new Vec3d(dir.x, py, dir.z).normalize();
                pos = c.getEyePos().subtract(0, 0.4, 0).add(dir.multiply(0.6));
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putDouble("dx", dir.x);
                n.putDouble("dy", dir.y);
                n.putDouble("dz", dir.z);
                n.putBoolean("fast", fast);
                AWNet.fx(w, pos, "blade_crescent", n);
                sound(c, AWSounds.BLADE_CRESCENT, 1.3f, 1.1f);
                sound(c, AWSounds.BLADE_SLASH, 1.0f, 0.8f);
                AWNet.shake(c, 0.25f, 5);
            }
            if (pos != null && flown < 10) {
                // 초승달 참격 비행 (틱당 1.6칸)
                for (int sub = 0; sub < 2; sub++) {
                    pos = pos.add(dir.multiply(0.8));
                    for (LivingEntity e : Combat.findInRadius(c, pos, 1.5)) {
                        if (!hits.add(e)) continue;
                        boolean wasAir = airborne(e, 1.2);
                        boolean mk = marked(e);
                        hit(w, c, e, 4f + (mk ? 2.5f : 0), mk ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, false);
                        Combat.setVelocity(e, dir.multiply(0.1).add(0, wasAir ? 1.0 : 1.25, 0));
                        impact(w, chest(e), "heavy", 1.2f, COLOR);
                        sound(e, AWSounds.BLADE_SLASH_HEAVY, 1.1f, 1.0f);
                        if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched_up");
                        rifts.put(e, tt + 8);
                        d.addUlt(3);
                        if (fast) AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                    }
                    if (!w.isSpaceEmpty(Box.of(pos, 0.4, 0.4, 0.4))) flown = 10;
                }
                flown++;
            }
            // 공중의 마력 균열
            for (Map.Entry<LivingEntity, Integer> en : rifts.entrySet()) {
                if (en.getValue() != tt) continue;
                LivingEntity e = en.getKey();
                if (!e.isAlive()) continue;
                Vec3d at = chest(e);
                NbtCompound n = nbt();
                n.putInt("e", e.getId());
                AWNet.fx(w, at, "blade_rift", n);
                sound(w, at, AWSounds.BLADE_RIFT, 1.3f, 1.0f);
                for (LivingEntity o : Combat.findInRadius(c, at, 2.4)) {
                    hit(w, c, o, 2.5f, AWNet.DMG_SKILL, true);
                    Combat.setVelocity(o, o.getVelocity().multiply(0.3).add(0, 0.25, 0));
                }
            }
            if (tt == windup + 1 && !hits.isEmpty()) {
                St s = st(c);
                s.s2Hit = d.now();
                s.s2AfterS1 = d.now() - s.s1Hit <= COMBO_WINDOW + windup + 2;
            }
            if (tt == windup + 10) {
                St s = st(c);
                if (!hits.isEmpty()) {
                    s.s2Hit = d.now();
                    s.s2AfterS1 = d.now() - s.s1Hit <= COMBO_WINDOW + 12;
                }
            }
            return tt >= windup + 20;
        }
    }

    // =================================================================== 3. 무형검무

    private boolean dance(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 14, 14);
        if (target == null) {
            d.startCooldown(SkillSlot.SKILL3, CD_S3_MISS);
            AbilityManager.deny(p);
            return true;
        }
        St s = st(p);
        boolean explode = s.s2AfterS1 && d.now() - s.s2Hit <= COMBO_WINDOW;
        s.s2AfterS1 = false;
        boolean air = airborne(target, 2.0);
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(40);
        AWNet.anim(p, "blade.skill3_dash");
        afterimage(p.getServerWorld(), p, p.getPos());
        sound(p, AWSounds.BLADE_TELEPORT, 1.0f, 1.0f);
        Combat.stun(target, 40, p);
        DanceAction a = new DanceAction(p, d, target, air, explode, marked(target));
        s.dance = a;
        ServerActions.start(a);
        return true;
    }

    class DanceAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;
        final boolean air, explode, mk;
        final Vec3d fwd, right;
        boolean released, extended;
        int finalAt = 18;
        int cur;
        int landAt = -1;

        DanceAction(ServerPlayerEntity c, AbilityData d, LivingEntity t, boolean air, boolean explode, boolean mk) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
            this.explode = explode;
            this.mk = mk;
            Vec3d f = Combat.horizontal(t.getPos().subtract(c.getPos()));
            if (f.lengthSquared() < 1e-4) f = Combat.horizontal(c.getRotationVec(1f));
            fwd = f;
            right = new Vec3d(-f.z, 0, f.x);
        }

        boolean canExtend() {
            return !extended && cur >= 1 && cur < finalAt - 4;
        }

        /** 잔영보로 이어가기: 마무리 전에 참격 두 번 추가. */
        void extend() {
            extended = true;
            finalAt += 8;
            NbtCompound n = nbt();
            n.putInt("e", c.getId());
            AWNet.fx(world(), c.getPos(), "blade_phantom_burst", n);
            AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
        }

        Vec3d around(Vec3d off) {
            double r = 1.6 + t.getWidth() * 0.5;
            Vec3d p = t.getPos().add(off.multiply(r));
            Vec3d s = Combat.safeSpot(c, p);
            return s != null ? s : t.getPos().subtract(fwd.multiply(r));
        }

        void blink(Vec3d off) {
            ServerWorld w = world();
            afterimage(w, c, c.getPos());
            Vec3d at = around(off);
            face(c, at, t);
            d.invulnUntil = Math.max(d.invulnUntil, d.now() + 3);
            sound(c, AWSounds.BLADE_TELEPORT, 0.8f, 1.2f + w.random.nextFloat() * 0.2f);
        }

        void cut(float dmg, float tilt, boolean heavy) {
            ServerWorld w = world();
            hit(w, c, t, dmg, heavy ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, true);
            slash(w, c, yawTo(c, t), tilt, heavy ? 1.4f : 1.15f, heavy ? 1 : 0);
            sound(t, heavy ? AWSounds.BLADE_SLASH_HEAVY : AWSounds.BLADE_SLASH, 1.1f, 1.0f + w.random.nextFloat() * 0.2f);
            AWNet.shake(t, 0.25f, 4);
            if (t instanceof ServerPlayerEntity) AWNet.anim(t, w.random.nextBoolean() ? "common.flinch" : "common.flinch2");
            d.addUlt(2);
        }

        @Override
        protected boolean step(int tt) {
            cur = tt;
            if (!t.isAlive() || t.isRemoved()) return true;
            ServerWorld w = world();
            if (tt == 1) {
                Combat.hold(t, true);
                if (air) Combat.hold(c, true);
                AWNet.anim(c, "blade.skill3");
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.held");
            }
            if (tt >= 1 && !released) {
                Combat.setVelocity(t, Vec3d.ZERO);
                Combat.setVelocitySelf(c, Vec3d.ZERO);
            }
            // 4방향 순간이동 참격: 오른쪽 → 왼쪽 → 뒤 → 정면
            Vec3d[] offs = {right, right.multiply(-1), fwd, fwd.multiply(-1)};
            float[] tilts = {10, 170, -60, 35};
            for (int i = 0; i < 4; i++) {
                if (tt == 1 + i * 4) blink(offs[i]);
                if (tt == 3 + i * 4) cut(2f, tilts[i], false);
            }
            // 잔영보로 이어간 추가 참격 2회
            if (extended) {
                if (tt == 17) {
                    blink(fwd.add(right).normalize());
                    AWNet.anim(c, "blade.skill3_cont");
                }
                if (tt == 19) cut(2.5f, -20, false);
                if (tt == 21) blink(fwd.subtract(right).normalize().multiply(-1));
                if (tt == 23) cut(2.5f, 150, false);
            }
            // 마무리: 위에서 내려찍기
            if (tt == finalAt - 3) {
                afterimage(w, c, c.getPos());
                Vec3d at = around(fwd.multiply(-1));
                Vec3d above = Combat.safeSpot(c, at.add(0, air ? 1.2 : 1.8, 0));
                face(c, above != null ? above : at, t);
                Combat.hold(c, true);
                AWNet.anim(c, "blade.skill3_final");
            }
            if (tt == finalAt) {
                release();
                cut(5f, -90, true);
                AWNet.shake(c, 0.4f, 6);
                if (air || airborne(t, 1.5)) {
                    Combat.setVelocity(t, new Vec3d(0, -2.8, 0));
                    Combat.setVelocitySelf(c, new Vec3d(0, -1.4, 0));
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.launched_down");
                } else {
                    Combat.setVelocity(t, new Vec3d(0, -0.6, 0));
                    crater(w, t.getPos(), 2.6f, COLOR);
                    sound(t, AWSounds.VOID_SLAM, 1.2f, 1.1f);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                }
                if (explode) {
                    // 1→2 연속 적중 후: 마지막 참격 폭발
                    NbtCompound n = nbt();
                    n.putInt("e", t.getId());
                    n.putFloat("s", 1.6f);
                    AWNet.fx(w, chest(t), "blade_burst", n);
                    sound(t, AWSounds.BLADE_EXPLODE, 1.5f, 0.9f);
                    for (LivingEntity o : Combat.findInRadius(c, chest(t), 3.2)) hit(w, c, o, 4f, AWNet.DMG_CRIT, true);
                    AWNet.shakeAround(w, t.getPos(), 0.7f, 10, 14);
                    AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                    d.addUlt(8);
                }
            }
            if (tt > finalAt && landAt < 0 && air) {
                if (t.isOnGround() || Combat.distanceToGround(t, 4) < 0.4 || tt >= finalAt + 14) {
                    landAt = tt;
                    hit(w, c, t, 2f, AWNet.DMG_CRIT, true);
                    crater(w, t.getPos(), 3.4f, COLOR);
                    sound(t, AWSounds.VOID_SLAM, 1.4f, 0.9f);
                    AWNet.shakeAround(w, t.getPos(), 0.9f, 12, 18);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                } else Combat.setVelocity(t, new Vec3d(0, -2.8, 0));
            }
            int extraAt = (air ? Math.max(finalAt + 4, landAt + 3) : finalAt + 4);
            if (mk && tt == extraAt) {
                // 표식이 있는 적: 추가 참격
                AWNet.anim(c, "blade.skill3_extra");
                consumeMark(w, t);
                hit(w, c, t, 3f, AWNet.DMG_CRIT, true);
                slash(w, c, yawTo(c, t), 45, 1.5f, 2);
                Combat.setVelocity(t, new Vec3d(0, 0.45, 0));
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
            }
            int end = mk ? extraAt + 6 : (air ? Math.max(finalAt + 6, landAt + 4) : finalAt + 7);
            return tt >= end && (!air || landAt > 0 || tt > finalAt + 16);
        }

        void release() {
            if (released) return;
            released = true;
            Combat.hold(t, false);
            Combat.hold(c, false);
            Combat.unstun(t);
        }

        @Override
        protected void cleanup() {
            release();
            St s = st(c);
            if (s.dance == this) s.dance = null;
            d.busyUntil = Math.min(d.busyUntil, d.now() + 2);
        }
    }

    // =================================================================== 4. 마검 반격

    private boolean counterStance(ServerPlayerEntity p, AbilityData d) {
        d.counterUntil = d.now() + 16;
        d.setBusy(16);
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, true);
        AWNet.anim(p, "blade.skill4");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 16);
        AWNet.fx(p.getServerWorld(), p.getPos(), "blade_guard", n);
        sound(p, AWSounds.BLADE_DRAW, 0.9f, 1.4f);
        return true;
    }

    @Override
    public boolean onIncomingDamage(ServerPlayerEntity p, AbilityData d, DamageSource source, float amount) {
        if (d.hasSuperArmor() && !d.isFrozen() && d.now() - d.lastArmorText > 8) {
            d.lastArmorText = d.now();
            AWNet.damageNumber(p, 0, AWNet.DMG_TEXT_ARMOR, p);
        }
        if (!d.isCountering()) return true;
        if (source.isIn(AWDamage.TAG_ULTIMATE)) return true;
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity la) || attacker == p || !Combat.canTarget(p, la)) return true;
        d.counterUntil = 0;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
        d.startCooldown(SkillSlot.SKILL4, CD_S4_OK);
        d.invulnUntil = d.now() + 14;
        d.busyUntil = d.now() + 14;
        ServerActions.start(new CounterAction(p, d, la));
        return false;
    }

    class CounterAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity a;
        boolean released;

        CounterAction(ServerPlayerEntity c, AbilityData d, LivingEntity a) {
            super(c);
            this.c = c;
            this.d = d;
            this.a = a;
        }

        @Override
        protected boolean step(int tt) {
            if (!a.isAlive() || a.isRemoved()) return true;
            ServerWorld w = world();
            if (tt == 0) {
                // 튕겨냄
                AWNet.anim(c, "blade.skill4_success");
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                AWNet.fx(w, c.getPos(), "blade_parry", n);
                sound(c, AWSounds.BLADE_PARRY, 1.4f, 1.0f);
                sound(c, AWSounds.VOID_COUNTER_SUCCESS, 1.0f, 1.1f);
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_COUNTER, a);
                Vec3d push = Combat.horizontal(a.getPos().subtract(c.getPos())).multiply(0.5);
                Combat.setVelocity(a, push.add(0, 0.1, 0));
                Combat.stun(a, 16, c);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.stagger");
                d.addUlt(25);
            }
            if (tt == 3) {
                // 적의 뒤로 순간이동
                Combat.hold(a, true);
                Combat.setVelocity(a, Vec3d.ZERO);
                afterimage(w, c, c.getPos());
                Vec3d dir = Combat.horizontal(a.getPos().subtract(c.getPos()));
                Vec3d dest = Combat.safeSpot(c, a.getPos().add(dir.multiply(1.4 + a.getWidth() * 0.5)));
                if (dest != null) face(c, dest, a);
                sound(c, AWSounds.BLADE_TELEPORT, 1.0f, 1.3f);
            }
            if (tt > 3 && !released) Combat.setVelocity(a, Vec3d.ZERO);
            if (tt == 6) {
                // 등을 베어 날려버림
                release();
                hit(w, c, a, 6f, AWNet.DMG_CRIT, true);
                slash(w, c, yawTo(c, a), 60, 1.5f, 1);
                Vec3d away = Combat.horizontal(a.getPos().subtract(c.getPos()));
                Combat.setVelocity(a, away.multiply(0.7).add(0, 1.15, 0));
                impact(w, chest(a), "crit", 1.4f, COLOR);
                sound(a, AWSounds.BLADE_SLASH_HEAVY, 1.4f, 0.9f);
                AWNet.shake(a, 0.6f, 8);
                AWNet.shake(c, 0.3f, 5);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.launched");
                // 반격 성공: 3번 즉시 연계 가능
                d.cdEnd[SkillSlot.SKILL3.ordinal()] = d.now();
                d.dirty = true;
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
            }
            return tt >= 12;
        }

        void release() {
            if (released) return;
            released = true;
            Combat.hold(a, false);
            Combat.unstun(a);
        }

        @Override
        protected void cleanup() {
            release();
        }
    }

    // =================================================================== R. 천지개벽

    private boolean ultimate(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        Vec3d fwd = Combat.horizontal(p.getRotationVec(1f));
        Vec3d ground = Combat.groundBelow(w, p.getPos().add(0, 1, 0), 6, p);
        Vec3d base = ground != null ? ground : p.getPos();
        Vec3d focus = base.add(fwd.multiply(13));
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.frozenUntil = now + ULT_FREEZE;
        d.invulnUntil = now + ULT_FREEZE;
        d.setBusy(ULT_FREEZE + 22);
        d.superArmorUntil = now + ULT_FREEZE + 22;
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Combat.stun(p, ULT_FREEZE, p);
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "blade_ult", ULT_CINEMATIC, focus);
            if (o != p) {
                AbilityData od = AbilityManager.data(o);
                od.setNow(now);
                od.frozenUntil = now + ULT_FREEZE;
                od.invulnUntil = now + ULT_FREEZE;
                Combat.stun(o, ULT_FREEZE, p);
                Combat.setVelocity(o, Vec3d.ZERO);
                AbilityManager.setBlocking(o, false);
            }
        }
        for (Entity e : w.getOtherEntities(p, new Box(p.getBlockPos()).expand(radius), e -> e instanceof MobEntity)) {
            Combat.stun((LivingEntity) e, ULT_FREEZE, p);
        }
        AWNet.anim(p, "blade.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putDouble("fx", fwd.x);
        n.putDouble("fz", fwd.z);
        n.putDouble("gy", base.y);
        n.putInt("d", ULT_CINEMATIC);
        AWNet.fx(w, p.getPos(), "blade_ult", n);
        ServerActions.start(new UltAction(p, d, base, fwd));
        return true;
    }

    class UltAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final Vec3d base, fwd, right;
        final Set<LivingEntity> hit = new HashSet<>();

        UltAction(ServerPlayerEntity c, AbilityData d, Vec3d base, Vec3d fwd) {
            super(c);
            this.c = c;
            this.d = d;
            this.base = base;
            this.fwd = fwd;
            this.right = new Vec3d(-fwd.z, 0, fwd.x);
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == 2) sound(w, c.getPos(), AWSounds.BLADE_CHARGE, 3f, 0.6f);
            if (tt == 22) {
                sound(w, c.getPos(), AWSounds.BLADE_GROW, 4f, 0.7f);
                AWNet.shakeAround(w, c.getPos(), 0.6f, 20, 30);
            }
            if (tt >= 6 && tt < 32) {
                // 주변 적을 참격 범위 안으로 끌어당김
                Vec3d into = base.add(fwd.multiply(7));
                for (LivingEntity e : Combat.findInRadius(c, c.getPos(), 15)) {
                    Vec3d to = into.subtract(e.getPos());
                    double dist = to.horizontalLength();
                    if (dist < 1.5) continue;
                    Vec3d v = Combat.horizontal(to).multiply(Math.min(0.5, 0.1 + dist * 0.035));
                    Combat.setVelocity(e, e.getVelocity().multiply(0.5).add(v));
                }
            }
            if (tt == 32) {
                // 하늘로 도약
                Combat.unstun(c);
                Combat.setVelocitySelf(c, new Vec3d(0, 1.45, 0).subtract(fwd.multiply(0.12)));
                sound(c, AWSounds.BLADE_TELEPORT, 1.5f, 0.7f);
            }
            if (tt >= 40 && tt < 46) Combat.setVelocitySelf(c, c.getVelocity().multiply(0.3));
            if (tt == 46) {
                sound(w, c.getPos(), AWSounds.BLADE_WAVE, 5f, 0.8f);
                sound(w, c.getPos(), AWSounds.BLADE_SLASH_HEAVY, 4f, 0.5f);
                AWNet.shakeAround(w, c.getPos(), 1.2f, 20, 40);
            }
            if (tt >= 48 && tt < 58) {
                // 초대형 참격이 앞으로 나아감 (틱당 2.8칸, 폭 ±3.8칸)
                double front = (tt - 47) * 2.8;
                for (LivingEntity e : Combat.findInRadius(c, base.add(fwd.multiply(front - 1.4)), 6)) {
                    if (hit.contains(e)) continue;
                    Vec3d rel = e.getPos().subtract(base);
                    double along = rel.dotProduct(fwd), side = Math.abs(rel.dotProduct(right));
                    if (along < front - 3.2 || along > front + 0.4 || side > 3.8 + e.getWidth() * 0.5) continue;
                    hit.add(e);
                    boolean mk = consumeMark(w, e);
                    Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), 14f + (mk ? 6f : 0f), AWNet.DMG_ULT);
                    Combat.setVelocity(e, fwd.multiply(0.4).add(0, 1.2, 0));
                    if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched");
                }
            }
            if (tt == 60) {
                // 지나간 자리에서 마력 폭발
                for (int i = 1; i <= 5; i++) {
                    Vec3d at = base.add(fwd.multiply(i * 5.0));
                    for (LivingEntity e : Combat.findInRadius(c, at.add(0, 1, 0), 3.5)) {
                        Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), 4f, AWNet.DMG_ULT);
                        Combat.addVelocity(e, new Vec3d(0, 0.5, 0));
                    }
                }
                sound(w, base.add(fwd.multiply(12)), AWSounds.BLADE_EXPLODE, 5f, 0.6f);
                AWNet.shakeAround(w, base.add(fwd.multiply(12)), 1.0f, 18, 40);
            }
            return tt >= 70;
        }
    }

    // =================================================================== T. 잔영보

    private boolean phantomStep(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        // 무형검무 도중: 공격을 이어감
        if (s.dance != null && s.dance.canExtend()) {
            s.dance.extend();
            d.startCooldown(SkillSlot.PASSIVE, CD_T);
            sound(p, AWSounds.BLADE_PHANTOM, 1.0f, 1.3f);
            return true;
        }
        LivingEntity target = null;
        if (d.now() - s.s1Hit <= 40 && w.getEntityById(s.s1Target) instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) < 18) {
            target = le;
        }
        if (target == null) target = Combat.findTarget(p, 16, 8);
        Vec3d from = p.getPos();
        Vec3d dest;
        if (target != null) {
            Vec3d dir = Combat.horizontal(target.getPos().subtract(from));
            Vec3d behind = target.getPos().add(dir.multiply(1.4 + target.getWidth() * 0.5));
            dest = Combat.safeSpot(p, behind);
            if (dest == null) dest = Combat.safeSpot(p, target.getPos().subtract(dir.multiply(1.6)));
        } else {
            Vec3d look = p.getRotationVec(1f);
            Vec3d dir = Combat.horizontal(look);
            dest = dashEnd(p, from, dir, 8);
        }
        if (dest == null || dest.distanceTo(from) < 0.8) {
            AbilityManager.deny(p);
            return false;
        }
        d.startCooldown(SkillSlot.PASSIVE, CD_T);
        d.setBusy(5);
        d.invulnUntil = d.now() + 4;
        if (target != null) face(p, dest, target);
        else Combat.teleport(p, dest, p.getYaw(), p.getPitch());
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        AWNet.anim(p, "blade.phantom_step");
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putFloat("yaw", p.getYaw());
        n.putDouble("tx", dest.x);
        n.putDouble("ty", dest.y);
        n.putDouble("tz", dest.z);
        AWNet.fx(w, from, "blade_phantom", n);
        sound(w, from, AWSounds.BLADE_PHANTOM, 1.0f, 1.0f);
        sound(p, AWSounds.BLADE_TELEPORT, 1.0f, 1.0f);
        s.tAt = d.now();
        ServerActions.start(new PhantomBlast(p, from));
        return true;
    }

    /** 남겨진 검의 잔상이 잠시 후 폭발. */
    static class PhantomBlast extends TimedAction {
        final ServerPlayerEntity c;
        final Vec3d at;

        PhantomBlast(ServerPlayerEntity c, Vec3d at) {
            super(c);
            this.c = c;
            this.at = at;
        }

        @Override
        protected boolean step(int tt) {
            if (tt == 20) {
                ServerWorld w = world();
                NbtCompound n = nbt();
                n.putInt("e", -1);
                n.putFloat("s", 1.2f);
                AWNet.fx(w, at.add(0, 1, 0), "blade_burst", n);
                sound(w, at, AWSounds.BLADE_EXPLODE, 1.3f, 1.1f);
                for (LivingEntity e : Combat.findInRadius(c, at.add(0, 1, 0), 3.2)) {
                    hit(w, c, e, 4f, AWNet.DMG_SKILL, false);
                    Vec3d out = Combat.horizontal(e.getPos().subtract(at)).multiply(0.5).add(0, 0.4, 0);
                    Combat.setVelocity(e, out);
                }
                return true;
            }
            return false;
        }
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        if (((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_COUNTER) && !d.isCountering()) {
            ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
            if (!ServerActions.hasAction(p, CounterAction.class)) {
                AWNet.anim(p, "blade.skill4_fail");
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                AWNet.fx(p.getServerWorld(), p.getPos(), "blade_guard_fade", n);
                sound(p, AWSounds.VOID_COUNTER_FAIL, 0.7f, 1.2f);
            }
        }
        if (d.hasSuperArmor() && !d.isFrozen()) {
            p.removeStatusEffect(AWEffects.CONCUSSION);
            p.removeStatusEffect(AWEffects.STUN);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        states.remove(p.getUuid());
        Combat.hold(p, false);
    }
}
