package com.abilitywar.ability.dragon;

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
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 용기사 — 고룡의 계승자 (근접 콤보형, 고난도).
 * <pre>
 * 1  용조       : 돌진 → 발톱 2회 → 마지막 타격으로 끌어당김 + 용혈 표식 (공중이면 올려베기, 표식이 있으면 강화)
 * 2  용익타     : 날개로 주변 밀어내기 → 정면 후려치기로 높이 띄움 (1번 직후면 2회, 공중의 적은 끌어옴)
 * 3  용왕난무   : 접근 → 오른발톱 → 왼발톱 → 꼬리 → 교차 내려치기 (공중 추격/내리꽂기, 1→2 연속 적중 후면 추가타)
 * 4  역린 반격  : 반격 자세. 평타/스킬(궁극기 제외)을 무효 → 얼굴 강타 → 꼬리로 내리꽂기. 성공 시 3번 즉시 연계
 * R  고룡 강림  : 고룡의 환영이 포효(흡인)하고 브레스 폭발. 용혈 게이지 전부 소모, 시전 중 슈퍼아머
 * T  용화       : 용혈 게이지가 가득일 때 T 길게 → 변신 (발톱/날개/비늘, 불꽃 잔상, 발톱 평타, 스킬 강화)
 * 용혈 게이지 = 이 능력의 궁극기 게이지. 공격을 맞힐 때만 찬다. 용화 중에는 줄어들고, 용화 중 R 은 남은 게이지로 사용 가능.
 * </pre>
 */
public class DragonAbility extends Ability {
    public static final int CD_S1 = 140, CD_S1_MISS = 60;
    public static final int CD_S2 = 180;
    public static final int CD_S3 = 240, CD_S3_MISS = 40;
    public static final int CD_S4 = 200, CD_S4_OK = 140;
    public static final int COMBO_WINDOW = 50;
    public static final int TRANSFORM_TICKS = 400;
    public static final int ULT_CINEMATIC = 74;
    /** 실제 정지/무적 시간 (폭발 피해가 들어가도록 폭발 직전에 끝남). */
    public static final int ULT_FREEZE = 63;
    public static final int COLOR = 0xFF7A2E;

    private static final UUID TRANSFORM_SPEED = UUID.fromString("2f8c5d61-4a0e-4b7a-8d3c-91e6a0b2c7d4");
    private static final UUID ARMOR_KB = UUID.fromString("7a1b3c5d-9e2f-4c6a-8b0d-1e3f5a7c9b2d");

    static final class St {
        long s1Hit = -999, s2Hit = -999;
        boolean s2AfterS1;
        long armorUntil;
    }

    private final Map<UUID, St> states = new HashMap<>();

    public DragonAbility() {
        super(AbilityWar.id("dragon"), "dragon", 0xFF000000 | COLOR);
    }

    St st(PlayerEntity p) {
        return states.computeIfAbsent(p.getUuid(), k -> new St());
    }

    public static boolean transformed(PlayerEntity p) {
        return ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_TRANSFORMED);
    }

    static float dmg(PlayerEntity p, float base) {
        return transformed(p) ? base * 1.25f : base;
    }

    static void gauge(AbilityData d, float amount) {
        d.addUlt(amount);
    }

    // =================================================================== hooks

    @Override
    public String attackAnim(PlayerEntity player, int combo) {
        return (transformed(player) ? "dragon.claw" : "dragon.attack") + (combo + 1);
    }

    @Override
    public int passiveHoldTicks(PlayerEntity player) {
        return transformed(player) ? 0 : 16;
    }

    @Override
    public boolean canUseUltimate(ServerPlayerEntity player, AbilityData data) {
        return data.ultReady() || (transformed(player) && data.ult > 0.5f);
    }

    @Override
    public boolean passiveGauge() {
        return false;
    }

    @Override
    public void onDealtDamage(ServerPlayerEntity player, AbilityData data, float lost) {
        // 용혈 게이지는 스킬 적중 이벤트로만 찬다
    }

    @Override
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 1.0f);
    }

    @Override
    public boolean clientUltReady(PlayerEntity player, float ult) {
        return ult >= 99.99f || (transformed(player) && ult > 0.5f);
    }

    @Override
    public boolean clientPassiveReady(PlayerEntity player, float ult, boolean cooldownReady) {
        return !transformed(player) && ult >= 99.99f;
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 12 : 7;
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> claw(p, d);
            case SKILL2 -> wing(p, d);
            case SKILL3 -> fury(p, d);
            case SKILL4 -> counterStance(p, d);
            case ULTIMATE -> ultimate(p, d);
            case PASSIVE -> transform(p, d);
            case DASH -> false;
        };
    }

    // =================================================================== 공통 도우미

    static void slash(ServerWorld w, Entity e, float yaw, float tilt, float scale, boolean fire) {
        NbtCompound n = nbt();
        n.putInt("e", e.getId());
        n.putFloat("yaw", yaw);
        n.putFloat("tilt", tilt);
        n.putFloat("s", scale);
        n.putBoolean("fire", fire);
        AWNet.fx(w, e.getPos(), "claw_slash", n);
    }

    static void mark(ServerWorld w, LivingEntity t, Entity src) {
        t.addStatusEffect(new StatusEffectInstance(AWEffects.DRAGON_MARK, 200, 0, false, false, true), src);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putInt("d", 200);
        AWNet.fx(w, t.getPos(), "dragon_mark", n);
        sound(t, AWSounds.DRAGON_MARK, 0.8f, 1.0f);
    }

    /** 표식이 있으면 터뜨리고 true. */
    static boolean consumeMark(ServerWorld w, LivingEntity t) {
        if (!t.hasStatusEffect(AWEffects.DRAGON_MARK)) return false;
        t.removeStatusEffect(AWEffects.DRAGON_MARK);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        AWNet.fx(w, chest(t), "mark_burst", n);
        sound(t, AWSounds.DRAGON_FIRE, 1.2f, 1.2f);
        return true;
    }

    static boolean airborne(LivingEntity t, double h) {
        return Combat.distanceToGround(t, 6) >= h;
    }

    static void hit(ServerWorld w, ServerPlayerEntity p, LivingEntity t, float base, byte kind, boolean unblockable) {
        float amount = dmg(p, base);
        Combat.damage(t, AWDamage.source(w, unblockable ? AWDamage.GRAB : AWDamage.SKILL, p), amount, kind);
        if (transformed(p)) t.setOnFireFor(2);
    }

    static float yawTo(Entity from, Entity to) {
        return Combat.lookAt(from.getEyePos(), chest(to))[0];
    }

    // =================================================================== 평타

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        boolean tf = transformed(p);
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, tf ? AWSounds.DRAGON_CLAW : AWSounds.VOID_SWING, 0.7f, 0.9f + combo * 0.1f);
        if (tf) slash(w, p, p.getYaw(), combo == 0 ? -35 : combo == 1 ? 35 : 0, combo == 2 ? 1.3f : 1f, true);
        if (target == null) return;
        boolean fin = combo == 2;
        float base = tf ? (fin ? 5.5f : 3.5f) : (fin ? 4f : 2.5f);
        Combat.damage(target, AWDamage.source(w, AWDamage.PUNCH, p), base, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
        Vec3d dir = Combat.horizontal(target.getPos().subtract(p.getPos()));
        Combat.setVelocity(target, fin ? dir.multiply(0.7).add(0, 0.3, 0) : target.getVelocity().multiply(0.3).add(dir.multiply(0.22)).add(0, 0.05, 0));
        if (tf && fin) target.setOnFireFor(2);
        impact(w, chest(target).subtract(dir.multiply(target.getWidth() * 0.5)), fin ? "heavy" : "punch", fin ? 1.1f : 0.7f, COLOR);
        sound(target, fin ? AWSounds.DRAGON_CLAW_HEAVY : AWSounds.VOID_PUNCH, 0.9f, 1.0f);
        Combat.hitFlinch(target);
        gauge(d, tf ? 3 : 2);
    }

    // =================================================================== 1. 용조

    private boolean claw(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 10, 12);
        d.setBusy(target != null ? 14 : 8);
        d.startCooldown(SkillSlot.SKILL1, target != null ? CD_S1 : CD_S1_MISS);
        boolean air = target != null && airborne(target, 1.6);
        AWNet.anim(p, air ? "dragon.skill1_air" : "dragon.skill1");
        sound(p, AWSounds.DRAGON_DASH, 1.0f, 1.0f);
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putInt("d", 5);
        AWNet.fx(p.getServerWorld(), p.getPos(), "dragon_rush", n);
        ServerActions.start(new ClawAction(p, d, target, air));
        return true;
    }

    class ClawAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;
        final boolean air;
        boolean arrived;

        ClawAction(ServerPlayerEntity c, AbilityData d, @Nullable LivingEntity t, boolean air) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            Vec3d look = Combat.horizontal(c.getRotationVec(1f));
            // 돌진
            if (tt < 4 && !arrived) {
                Vec3d dir = look;
                if (t != null) {
                    Vec3d to = t.getPos().subtract(c.getPos());
                    double dist = to.horizontalLength();
                    if (dist < 1.7 + t.getWidth() * 0.5) arrived = true;
                    dir = Combat.horizontal(to);
                    if (air) dir = to.normalize();
                }
                if (!arrived) Combat.setVelocity(c, dir.multiply(1.5).add(0, air ? 0 : 0.05, 0));
            } else if (tt == 4 && !arrived) {
                Combat.setVelocity(c, c.getVelocity().multiply(0.3));
            }
            if (t == null) return tt >= 8;
            if (!t.isAlive()) return true;
            if (air && tt < 12) {
                Combat.hold(c, true);
                Combat.setVelocity(c, c.getVelocity().multiply(tt < 4 ? 1 : 0.2));
            }
            if (tt == 5 || tt == 8) {
                hit(w, c, t, 2f, AWNet.DMG_SKILL, false);
                slash(w, c, yawTo(c, t), tt == 5 ? -40 : 40, 1.0f, transformed(c));
                sound(t, AWSounds.DRAGON_CLAW, 1.0f, tt == 5 ? 1.0f : 1.15f);
                Combat.stun(t, 8, c);
                Combat.setVelocity(t, t.getVelocity().multiply(0.2).add(0, air ? 0.08 : 0, 0));
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, tt == 5 ? "common.flinch" : "common.flinch2");
                gauge(d, 4);
            }
            if (tt == 11) {
                boolean marked = consumeMark(w, t);
                float base = marked ? 5.5f : 2.5f;
                hit(w, c, t, base, marked ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, false);
                if (air) {
                    // 공중: 아래에서 위로 올려베어 더 띄움
                    Combat.setVelocity(t, new Vec3d(0, 1.05, 0).add(look.multiply(0.15)));
                    slash(w, c, yawTo(c, t), 90, 1.3f, true);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.launched_up");
                } else {
                    // 마지막 타격으로 내 앞으로 끌어당김
                    Vec3d dest = c.getPos().add(look.multiply(1.3 + t.getWidth() * 0.5));
                    Vec3d v = dest.subtract(t.getPos());
                    Combat.setVelocity(t, v.multiply(0.55).add(0, 0.18, 0));
                    slash(w, c, yawTo(c, t), 0, 1.2f, marked || transformed(c));
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.grabbed");
                }
                sound(t, AWSounds.DRAGON_CLAW_HEAVY, 1.1f, 1.0f);
                impact(w, chest(t), marked ? "crit" : "heavy", marked ? 1.5f : 1.1f, COLOR);
                AWNet.shake(t, 0.5f, 8);
                AWNet.shake(c, 0.25f, 5);
                Combat.unstun(t);
                mark(w, t, c);
                gauge(d, 6);
                St s = st(c);
                s.s1Hit = d.now();
                s.s2AfterS1 = false;
            }
            return tt >= 14;
        }

        @Override
        protected void cleanup() {
            Combat.hold(c, false);
        }
    }

    // =================================================================== 2. 용익타

    private boolean wing(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        boolean combo = d.now() - s.s1Hit <= COMBO_WINDOW;
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(combo ? 22 : 18);
        AWNet.anim(p, combo ? "dragon.skill2_double" : "dragon.skill2");
        sound(p, AWSounds.DRAGON_WING, 1.2f, 0.8f);
        ServerActions.start(new WingAction(p, d, combo));
        return true;
    }

    class WingAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final boolean combo;
        LivingEntity target;
        boolean pulled;

        WingAction(ServerPlayerEntity c, AbilityData d, boolean combo) {
            super(c);
            this.c = c;
            this.d = d;
            this.combo = combo;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            Vec3d look = Combat.horizontal(c.getRotationVec(1f));
            if (tt == 5) {
                // 날개 펼침: 주변 밀어내기
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                AWNet.fx(w, c.getPos(), "wing_gust", n);
                sound(c, AWSounds.DRAGON_WING, 1.5f, 1.0f);
                AWNet.shakeAround(w, c.getPos(), 0.3f, 6, 10);
                for (LivingEntity e : Combat.findInRadius(c, c.getPos().add(0, 1, 0), 4.5)) {
                    Vec3d out = Combat.horizontal(e.getPos().subtract(c.getPos()));
                    hit(w, c, e, 1.5f, AWNet.DMG_SKILL, false);
                    Combat.setVelocity(e, out.multiply(0.9).add(0, 0.25, 0));
                    gauge(d, 2);
                }
            }
            if (tt == 9 || (combo && tt == 14)) {
                boolean second = tt == 14;
                if (!second) {
                    List<LivingEntity> front = Combat.findInCone(c, c.getEyePos(), c.getRotationVec(1f), 5.5, 55);
                    double best = 1e9;
                    for (LivingEntity e : front) {
                        double dd = e.squaredDistanceTo(c);
                        if (dd < best) {
                            best = dd;
                            target = e;
                        }
                    }
                }
                sound(c, AWSounds.DRAGON_WING, 1.4f, second ? 1.3f : 1.1f);
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putBoolean("second", second);
                AWNet.fx(w, c.getPos(), "wing_slash", n);
                if (target == null || !target.isAlive()) return !combo || second;
                boolean air = airborne(target, 1.8) && !second;
                if (air) {
                    // 공중의 적: 내 앞으로 끌어온다
                    Vec3d dest = c.getPos().add(look.multiply(1.5 + target.getWidth() * 0.5)).add(0, 0.6, 0);
                    Combat.setVelocity(target, dest.subtract(target.getPos()).multiply(0.5));
                    hit(w, c, target, 3f, AWNet.DMG_SKILL, false);
                    Combat.stun(target, 10, c);
                    pulled = true;
                    if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.grabbed");
                } else {
                    hit(w, c, target, second ? 3f : 5f, AWNet.DMG_SKILL, false);
                    Combat.setVelocity(target, look.multiply(0.15).add(0, second ? 0.95 : 1.25, 0));
                    if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.launched_up");
                }
                impact(w, chest(target), "heavy", second ? 1.2f : 1.5f, COLOR);
                AWNet.shake(target, 0.6f, 10);
                AWNet.shake(c, 0.3f, 6);
                gauge(d, second ? 4 : 6);
                if (!second) {
                    if (combo) {
                        gauge(d, 10);
                        AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                    }
                    St s = st(c);
                    s.s2Hit = d.now();
                    s.s2AfterS1 = combo;
                }
            }
            return tt >= (combo ? 22 : 16);
        }
    }

    // =================================================================== 3. 용왕난무

    private boolean fury(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 12, 10);
        if (target == null) {
            d.startCooldown(SkillSlot.SKILL3, CD_S3_MISS);
            AbilityManager.deny(p);
            return true;
        }
        St s = st(p);
        boolean extra = s.s2AfterS1 && d.now() - s.s2Hit <= COMBO_WINDOW;
        s.s2AfterS1 = false;
        boolean air = airborne(target, 2.0);
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(extra ? 32 : 26);
        d.invulnUntil = d.now() + 3;
        AWNet.anim(p, "dragon.skill3_dash");
        sound(p, AWSounds.DRAGON_DASH, 1.0f, 1.2f);
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putInt("d", 4);
        AWNet.fx(p.getServerWorld(), p.getPos(), "dragon_rush", n);
        Combat.stun(target, 30, p);
        ServerActions.start(new FuryAction(p, d, target, air, extra));
        return true;
    }

    class FuryAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;
        final boolean air, extra;
        boolean released;
        int landAt = -1;

        FuryAction(ServerPlayerEntity c, AbilityData d, LivingEntity t, boolean air, boolean extra) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
            this.extra = extra;
        }

        @Override
        protected boolean step(int tt) {
            if (!t.isAlive() || t.isRemoved()) return true;
            ServerWorld w = world();
            if (tt == 2) {
                Vec3d dir = Combat.horizontal(c.getPos().subtract(t.getPos()));
                Vec3d dest = t.getPos().add(dir.multiply(1.3 + t.getWidth() * 0.5));
                Vec3d safe = Combat.safeSpot(c, dest);
                if (safe == null) safe = Combat.safeSpot(c, t.getPos().add(dir.multiply(0.6)));
                if (safe == null) safe = t.getPos();
                float[] rot = Combat.lookAt(safe.add(0, c.getStandingEyeHeight(), 0), chest(t));
                Combat.teleport(c, safe, rot[0], rot[1]);
                Combat.hold(t, true);
                if (air) Combat.hold(c, true);
                Combat.setVelocity(t, Vec3d.ZERO);
                Combat.setVelocity(c, Vec3d.ZERO);
                AWNet.anim(c, air ? "dragon.skill3_air" : "dragon.skill3");
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.held");
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putInt("d", 4);
                AWNet.fx(w, safe, "dragon_rush", n);
            }
            if (tt > 2 && !released) {
                Combat.setVelocity(t, Vec3d.ZERO);
                Combat.setVelocity(c, Vec3d.ZERO);
            }
            float y = yawTo(c, t);
            if (tt == 5 || tt == 8) {
                hit(w, c, t, 2f, AWNet.DMG_SKILL, true);
                slash(w, c, y, tt == 5 ? -45 : 45, 1.1f, transformed(c));
                sound(t, AWSounds.DRAGON_CLAW, 1.1f, tt == 5 ? 1.0f : 1.15f);
                AWNet.shake(t, 0.25f, 4);
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, tt == 5 ? "common.flinch" : "common.flinch2");
                gauge(d, 3);
            }
            if (tt == 12) {
                // 꼬리
                hit(w, c, t, 2.5f, AWNet.DMG_SKILL, true);
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                AWNet.fx(w, c.getPos(), "tail_whip", n);
                sound(t, AWSounds.DRAGON_TAIL, 1.3f, 0.9f);
                impact(w, chest(t), "heavy", 1.2f, COLOR);
                AWNet.shake(t, 0.4f, 6);
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.stagger");
                gauge(d, 3);
            }
            if (tt == 17) {
                // 양 발톱 교차 내려치기
                release();
                hit(w, c, t, 4f, AWNet.DMG_CRIT, true);
                slash(w, c, y, -90, 1.5f, true);
                sound(t, AWSounds.DRAGON_CLAW_HEAVY, 1.4f, 0.8f);
                AWNet.shake(c, 0.35f, 6);
                gauge(d, 3);
                if (air || airborne(t, 1.5)) {
                    Combat.setVelocity(t, new Vec3d(0, -2.6, 0));
                    Combat.setVelocity(c, new Vec3d(0, -1.6, 0));
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.launched_down");
                } else {
                    Combat.setVelocity(t, new Vec3d(0, -0.6, 0));
                    AbilityFxCompat.crater(w, t.getPos(), 2.4f);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                }
            }
            if (tt > 17 && landAt < 0 && (air)) {
                if (t.isOnGround() || Combat.distanceToGround(t, 4) < 0.4 || tt >= 30) {
                    landAt = tt;
                    hit(w, c, t, 2f, AWNet.DMG_CRIT, true);
                    AbilityFxCompat.crater(w, t.getPos(), 3.5f);
                    sound(t, AWSounds.VOID_SLAM, 1.4f, 0.9f);
                    AWNet.shakeAround(w, t.getPos(), 0.9f, 12, 18);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                } else {
                    Combat.setVelocity(t, new Vec3d(0, -2.6, 0));
                }
            }
            int extraAt = air ? Math.max(22, landAt + 3) : 22;
            if (extra && tt == extraAt) {
                // 1→2 연속 적중 후: 추가 타격 (용의 불꽃 올려치기)
                AWNet.anim(c, "dragon.skill3_extra");
                hit(w, c, t, 3f, AWNet.DMG_CRIT, true);
                t.setOnFireFor(3);
                Combat.setVelocity(t, new Vec3d(0, 0.75, 0));
                NbtCompound n = nbt();
                n.putInt("e", t.getId());
                AWNet.fx(w, chest(t), "mark_burst", n);
                slash(w, c, y, 90, 1.6f, true);
                sound(t, AWSounds.DRAGON_FIRE, 1.4f, 1.0f);
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                gauge(d, 15);
            }
            int end = extra ? extraAt + 8 : (air ? Math.max(26, landAt + 4) : 26);
            return tt >= end && (!air || landAt > 0 || tt > 34);
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
        }
    }

    // =================================================================== 4. 역린 반격

    private boolean counterStance(ServerPlayerEntity p, AbilityData d) {
        d.counterUntil = d.now() + 16;
        d.setBusy(16);
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, true);
        AWNet.anim(p, "dragon.skill4");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 16);
        AWNet.fx(p.getServerWorld(), p.getPos(), "dragon_scales", n);
        sound(p, AWSounds.DRAGON_SCALES, 1.2f, 0.9f);
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
        d.invulnUntil = d.now() + 18;
        d.busyUntil = d.now() + 16;
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
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                AWNet.fx(w, c.getPos(), "scales_break", n);
                sound(c, AWSounds.DRAGON_SCALES, 1.5f, 1.3f);
                sound(c, AWSounds.VOID_COUNTER_SUCCESS, 1.0f, 0.8f);
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_COUNTER, a);
                Combat.stun(a, 20, c);
                Combat.hold(a, true);
                Combat.setVelocity(a, Vec3d.ZERO);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.held");
                double dist = c.distanceTo(a);
                if (dist > 3.2 && dist < 16) {
                    Vec3d dir = Combat.horizontal(c.getPos().subtract(a.getPos()));
                    Vec3d dest = Combat.safeSpot(c, a.getPos().add(dir.multiply(1.25 + a.getWidth() * 0.5)));
                    if (dest != null) {
                        float[] rot = Combat.lookAt(dest.add(0, c.getStandingEyeHeight(), 0), a.getEyePos());
                        Combat.teleport(c, dest, rot[0], rot[1]);
                    }
                }
                AWNet.anim(c, "dragon.skill4_success");
                gauge(d, 30);
            }
            if (tt > 0 && !released) Combat.setVelocity(a, Vec3d.ZERO);
            if (tt == 4) {
                // 발톱으로 얼굴 강타
                hit(w, c, a, 4f, AWNet.DMG_CRIT, true);
                slash(w, c, yawTo(c, a), -20, 1.1f, true);
                impact(w, a.getEyePos(), "crit", 1.3f, COLOR);
                sound(a, AWSounds.DRAGON_CLAW_HEAVY, 1.3f, 1.0f);
                AWNet.shake(a, 0.6f, 8);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.headsnap");
            }
            if (tt == 11) {
                // 꼬리로 바닥에 내리꽂기
                release();
                hit(w, c, a, 4f, AWNet.DMG_SKILL, true);
                Combat.setVelocity(a, new Vec3d(0, -1.6, 0));
                Combat.concuss(a, 30, 20, c);
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putBoolean("down", true);
                AWNet.fx(w, c.getPos(), "tail_whip", n);
                AbilityFxCompat.crater(w, a.getPos(), 2.6f);
                sound(a, AWSounds.DRAGON_TAIL, 1.4f, 0.7f);
                sound(a, AWSounds.VOID_SLAM, 1.2f, 1.1f);
                AWNet.shakeAround(w, a.getPos(), 0.7f, 10, 14);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.slammed");
                // 반격 성공: 3번 즉시 연계 가능
                d.cdEnd[SkillSlot.SKILL3.ordinal()] = d.now();
                d.dirty = true;
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
            }
            return tt >= 18;
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

    // =================================================================== R. 고룡 강림

    private boolean ultimate(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        boolean wasTf = transformed(p);
        if (wasTf) endTransform(p, d, false);
        d.ult = 0;
        d.dirty = true;
        Vec3d impact = ultTarget(p);
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.frozenUntil = now + ULT_FREEZE;
        d.invulnUntil = now + ULT_FREEZE;
        d.setBusy(ULT_FREEZE + 20);
        st(p).armorUntil = now + ULT_FREEZE + 25;
        d.superArmorUntil = now + ULT_FREEZE + 25;
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Combat.stun(p, ULT_FREEZE, p);
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "dragon_ult", ULT_CINEMATIC, impact);
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
        AWNet.anim(p, "dragon.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putDouble("tx", impact.x);
        n.putDouble("ty", impact.y);
        n.putDouble("tz", impact.z);
        n.putInt("d", ULT_CINEMATIC);
        n.putFloat("yaw", p.getYaw());
        AWNet.fx(w, p.getPos(), "dragon_ult", n);
        ServerActions.start(new UltAction(p, d, impact, wasTf));
        return true;
    }

    static Vec3d ultTarget(ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = Combat.horizontal(look);
        LivingEntity aimed = Combat.findTarget(p, 16, 12);
        Vec3d pos;
        if (aimed != null) pos = aimed.getPos();
        else {
            Vec3d end = eye.add(look.multiply(14));
            BlockHitResult hit = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            pos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        }
        if (pos.subtract(p.getPos()).horizontalLength() < 6) pos = p.getPos().add(flat.multiply(8));
        Vec3d ground = Combat.groundBelow(p.getServerWorld(), pos.add(0, 2, 0), 24, p);
        return ground != null ? ground : new Vec3d(pos.x, p.getY(), pos.z);
    }

    class UltAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final Vec3d impact;
        final boolean tf;

        UltAction(ServerPlayerEntity c, AbilityData d, Vec3d impact, boolean tf) {
            super(c);
            this.c = c;
            this.d = d;
            this.impact = impact;
            this.tf = tf;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == 10) sound(w, c.getPos(), AWSounds.DRAGON_WING, 3f, 0.6f);
            if (tt == 26) {
                sound(w, c.getPos(), AWSounds.DRAGON_ROAR, 4f, 0.8f);
                AWNet.shakeAround(w, c.getPos(), 0.8f, 20, 30);
            }
            if (tt >= 26 && tt < 42) {
                // 포효: 주변 적을 용기사 앞으로 끌어당김
                Vec3d front = c.getPos().add(Combat.horizontal(impact.subtract(c.getPos())).multiply(4));
                for (LivingEntity e : Combat.findInRadius(c, c.getPos(), 13)) {
                    Vec3d to = front.subtract(e.getPos());
                    double dist = to.length();
                    if (dist < 1.5) continue;
                    Combat.setVelocity(e, e.getVelocity().multiply(0.6).add(to.normalize().multiply(Math.min(0.55, 0.12 + dist * 0.04))));
                }
            }
            if (tt == 42) {
                // 하늘로 도약
                Combat.unstun(c);
                Combat.setVelocitySelf(c, new Vec3d(0, 1.35, 0).add(Combat.horizontal(c.getPos().subtract(impact)).multiply(0.15)));
                sound(c, AWSounds.DRAGON_WING, 2f, 1.2f);
            }
            if (tt == 56) sound(w, c.getPos(), AWSounds.DRAGON_BREATH, 4f, 0.6f);
            if (tt == 60) sound(w, c.getPos(), AWSounds.DRAGON_FIRE, 3f, 0.6f);
            if (tt == 66) explode(w);
            if (tt > 66 && tt <= 126 && tt % 10 == 0) {
                // 불타는 지면
                for (LivingEntity e : Combat.findInRadius(c, impact.add(0, 0.5, 0), 5)) {
                    Combat.damage(e, AWDamage.source(w, AWDamage.SKILL, c), 1f, AWNet.DMG_SKILL);
                    e.setOnFireFor(3);
                }
            }
            return tt >= 126;
        }

        void explode(ServerWorld w) {
            NbtCompound n = nbt();
            AWNet.fx(w, impact, "dragon_explosion", n);
            sound(w, impact, AWSounds.DRAGON_EXPLOSION, 5f, 0.7f);
            AWNet.shakeAround(w, impact, 1.4f, 24, 36);
            for (LivingEntity e : Combat.findInRadius(c, impact.add(0, 1, 0), 6.5)) {
                boolean marked = consumeMark(w, e);
                float amount = (12f + (marked ? 6f : 0f)) * (tf ? 1.25f : 1f);
                Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), amount, AWNet.DMG_ULT);
                Vec3d out = Combat.horizontal(e.getPos().subtract(impact)).multiply(1.3).add(0, 0.75, 0);
                Combat.setVelocity(e, out);
                e.setOnFireFor(6);
                if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched");
            }
        }
    }

    // =================================================================== T. 용화

    private boolean transform(ServerPlayerEntity p, AbilityData d) {
        if (transformed(p)) return false;
        if (!d.ultReady()) {
            AbilityManager.deny(p);
            return false;
        }
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_TRANSFORMED, true);
        d.invulnUntil = d.now() + 12;
        d.setBusy(14);
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(TRANSFORM_SPEED);
            speed.addTemporaryModifier(new EntityAttributeModifier(TRANSFORM_SPEED, "abilitywar dragon form", 0.12, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        AWNet.anim(p, "dragon.transform");
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        AWNet.fx(p.getServerWorld(), p.getPos(), "dragon_transform", n);
        sound(p, AWSounds.DRAGON_TRANSFORM, 2.5f, 1.0f);
        sound(p, AWSounds.DRAGON_WING, 1.5f, 0.9f);
        AWNet.shakeAround(p.getServerWorld(), p.getPos(), 0.6f, 12, 16);
        d.dirty = true;
        return true;
    }

    void endTransform(ServerPlayerEntity p, AbilityData d, boolean fx) {
        if (!transformed(p)) return;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_TRANSFORMED, false);
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(TRANSFORM_SPEED);
        if (fx) {
            NbtCompound n = nbt();
            n.putInt("e", p.getId());
            AWNet.fx(p.getServerWorld(), p.getPos(), "dragon_transform_end", n);
            sound(p, AWSounds.DRAGON_FIRE, 1.0f, 0.7f);
        }
        d.dirty = true;
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        AWPlayer awp = (AWPlayer) p;
        if (awp.aw$hasFlag(AWPlayer.FLAG_COUNTER) && !d.isCountering()) {
            awp.aw$setFlag(AWPlayer.FLAG_COUNTER, false);
            if (!ServerActions.hasAction(p, CounterAction.class)) {
                // 반격 실패: 방어 자세만 나오고 끝 (다른 모션)
                AWNet.anim(p, "dragon.skill4_fail");
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                AWNet.fx(p.getServerWorld(), p.getPos(), "scales_fade", n);
                sound(p, AWSounds.VOID_COUNTER_FAIL, 0.7f, 0.8f);
            }
        }
        if (transformed(p)) {
            d.ult = Math.max(0, d.ult - 100f / TRANSFORM_TICKS);
            d.dirty = true;
            if (d.ult <= 0) endTransform(p, d, true);
        }
        // 슈퍼아머: 넉백/속박/뇌진탕 무시
        St s = st(p);
        EntityAttributeInstance kb = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        boolean armor = s.armorUntil > d.now();
        if (kb != null) {
            boolean has = kb.getModifier(ARMOR_KB) != null;
            if (armor && !has) kb.addTemporaryModifier(new EntityAttributeModifier(ARMOR_KB, "abilitywar super armor", 1.0, EntityAttributeModifier.Operation.ADDITION));
            if (!armor && has) kb.removeModifier(ARMOR_KB);
        }
        if (armor && d.frozenUntil <= d.now()) {
            p.removeStatusEffect(AWEffects.CONCUSSION);
            if (!d.isFrozen()) p.removeStatusEffect(AWEffects.STUN);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        endTransform(p, d, false);
        EntityAttributeInstance kb = p.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(ARMOR_KB);
        states.remove(p.getUuid());
        Combat.hold(p, false);
    }
}
