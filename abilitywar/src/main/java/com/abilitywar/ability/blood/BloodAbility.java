package com.abilitywar.ability.blood;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 혈귀 — 피를 다루는 흡혈 귀신.
 * <pre>
 * 공통  출혈(최대 5단계, 1초마다 피해)을 쌓고 스킬로 터뜨린다. 스킬 피해의 일부를 흡혈해 회복. 일부 스킬은 자기 피를 대가로 쓴다.
 * 1  혈사포박 : 피의 실로 적을 낚아채 끌려가며 할퀴기 (공중의 적이면 공중으로 끌려감). 출혈 3단계 이상이면 터뜨림.
 *               박쥐 산개 직후면 적도 함께 끌어당김(강화).
 * 2  혈창분출 : (체력 2 소모) 땅을 내리쳐 앞으로 피의 창 3파 → 띄움. 1번 직후면 그 적 바로 밑에서 솟음.
 *               공중의 적은 다시 꿰뚫어 올림, 출혈 단계만큼 추가 피해.
 * 3  흡혈난무 : 돌진 → 할퀴기 4회(출혈을 하나씩 먹고 추가 피해) → 물어뜯어 흡혈 → 내리꽂기.
 *               공중 추격/강제 내리꽂기, 1→2 연속 적중 후면 흡혈 2배 + 마무리 피의 폭발.
 * 4  혈막반격 : 피의 막으로 몸을 감쌈. 평타/스킬(궁극기 제외) 무효 → 공격자 발밑에서 피의 창 → 흡혈.
 *               성공 시 3번 즉시 연계 + 혈기 회복. 실패하면 막이 흩어지는 동작만.
 * R  혈월만개 : 하늘이 붉어지고 핏빛 달 → 박쥐 날개로 떠오름 → 피의 사슬로 주변 적 결박·흡인 →
 *               피를 빨아들임(대량 흡혈) → 피의 폭발(출혈 단계만큼 추가 피해). 슈퍼아머, 게이지 전부 소모.
 * T  박쥐산개 : 박쥐 떼로 흩어져 날아감(무적, 지나가며 물어뜯기). 1번 직후면 그 적의 뒤로,
 *               박쥐 산개 직후의 1번은 강화.
 * </pre>
 */
public class BloodAbility extends Ability {
    public static final int COLOR = 0xD0102A;
    public static final int CD_S1 = 160, CD_S1_MISS = 60;
    public static final int CD_S2 = 180;
    public static final int CD_S3 = 240, CD_S3_MISS = 40;
    public static final int CD_S4 = 200, CD_S4_OK = 140;
    public static final int CD_T = 160;
    public static final int COMBO_WINDOW = 50;
    public static final int T_ENHANCE_WINDOW = 30;
    public static final int ULT_CINEMATIC = 78;
    public static final int ULT_FREEZE = 30;

    static final class St {
        long s1Hit = -999, s2Hit = -999, tAt = -999;
        int s1Target = -1;
        boolean s2AfterS1;
    }

    private final Map<UUID, St> states = new HashMap<>();

    public BloodAbility() {
        super(AbilityWar.id("blood"), "blood", 0xFF000000 | COLOR);
    }

    St st(PlayerEntity p) {
        return states.computeIfAbsent(p.getUuid(), k -> new St());
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 12 : 7;
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> thread(p, d);
            case SKILL2 -> spikes(p, d);
            case SKILL3 -> frenzy(p, d);
            case SKILL4 -> veil(p, d);
            case ULTIMATE -> ultimate(p, d);
            case PASSIVE -> swarm(p, d);
            case DASH -> false;
        };
    }

    // =================================================================== 출혈 / 흡혈

    public static int bleed(LivingEntity t) {
        StatusEffectInstance i = t.getStatusEffect(AWEffects.BLEED);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    static void setBleed(ServerWorld w, LivingEntity t, int stacks, @Nullable Entity src) {
        t.removeStatusEffect(AWEffects.BLEED);
        if (stacks > 0) t.addStatusEffect(new StatusEffectInstance(AWEffects.BLEED, 160, Math.min(5, stacks) - 1, false, false, true), src);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putInt("s", Math.min(5, stacks));
        AWNet.fx(w, t.getPos(), "blood_bleed", n);
    }

    static void addBleed(ServerWorld w, LivingEntity t, int n, Entity src) {
        setBleed(w, t, Math.min(5, bleed(t) + n), src);
    }

    /** 출혈을 모두 터뜨리고 단계 수를 돌려줌. */
    static int consumeBleed(ServerWorld w, LivingEntity t) {
        int s = bleed(t);
        if (s > 0) {
            setBleed(w, t, 0, null);
            NbtCompound n = nbt();
            n.putInt("e", t.getId());
            n.putFloat("s", 0.6f + s * 0.2f);
            AWNet.fx(w, chest(t), "blood_burst", n);
            sound(t, AWSounds.BLOOD_BURST, 0.9f, 1.2f);
        }
        return s;
    }

    /** 피해를 주고, 들어갔으면 그 비율만큼 회복. */
    static boolean hit(ServerWorld w, ServerPlayerEntity c, LivingEntity t, float amount, byte kind, boolean unblockable, float steal) {
        boolean ok = Combat.damage(t, AWDamage.source(w, unblockable ? AWDamage.GRAB : AWDamage.SKILL, c), amount, kind);
        if (ok && steal > 0) heal(w, c, amount * steal);
        return ok;
    }

    static void heal(ServerWorld w, ServerPlayerEntity c, float amount) {
        if (amount <= 0 || c.getHealth() >= c.getMaxHealth()) return;
        c.heal(amount);
        NbtCompound n = nbt();
        n.putInt("e", c.getId());
        n.putFloat("a", amount);
        AWNet.fx(w, c.getPos(), "blood_heal", n);
    }

    static void slash(ServerWorld w, Entity e, float yaw, float tilt, float scale) {
        NbtCompound n = nbt();
        n.putInt("e", e.getId());
        n.putFloat("yaw", yaw);
        n.putFloat("tilt", tilt);
        n.putFloat("s", scale);
        AWNet.fx(w, e.getPos(), "blood_slash", n);
    }

    static void spikesFx(ServerWorld w, Vec3d at, float radius, boolean big) {
        NbtCompound n = nbt();
        n.putFloat("r", radius);
        n.putBoolean("big", big);
        AWNet.fx(w, at, "blood_spikes", n);
    }

    static boolean airborne(LivingEntity t, double h) {
        return Combat.distanceToGround(t, 6) >= h;
    }

    static float yawTo(Entity from, Entity to) {
        return Combat.lookAt(from.getEyePos(), chest(to))[0];
    }

    static void face(ServerPlayerEntity c, Vec3d at, Entity look) {
        float[] rot = Combat.lookAt(at.add(0, c.getStandingEyeHeight(), 0), chest(look));
        Combat.teleport(c, at, rot[0], rot[1]);
    }

    static Vec3d groundAt(ServerWorld w, Vec3d p, Entity e) {
        Vec3d g = Combat.groundBelow(w, p.add(0, 2, 0), 6, e);
        return g != null ? g : p;
    }

    // =================================================================== 평타: 피의 발톱

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, AWSounds.BLOOD_SLASH, 0.7f, 1.0f + combo * 0.08f);
        slash(w, p, p.getYaw(), combo == 0 ? -35 : combo == 1 ? 35 : -85, combo == 2 ? 1.2f : 0.95f);
        if (target == null) return;
        boolean fin = combo == 2;
        float base = fin ? 4f : 2.5f;
        if (Combat.damage(target, AWDamage.source(w, AWDamage.PUNCH, p), base, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL)) heal(w, p, base * 0.15f);
        Vec3d dir = Combat.horizontal(target.getPos().subtract(p.getPos()));
        Combat.setVelocity(target, fin ? dir.multiply(0.6).add(0, 0.28, 0) : target.getVelocity().multiply(0.3).add(dir.multiply(0.2)).add(0, 0.05, 0));
        impact(w, chest(target).subtract(dir.multiply(target.getWidth() * 0.5)), fin ? "heavy" : "punch", fin ? 1.1f : 0.7f, COLOR);
        sound(target, AWSounds.VOID_PUNCH, 0.8f, 0.8f);
        if (fin) addBleed(w, target, 1, p);
        Combat.hitFlinch(target);
    }

    // =================================================================== 1. 혈사포박

    private boolean thread(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        boolean enh = d.now() - s.tAt <= T_ENHANCE_WINDOW;
        LivingEntity target = Combat.findTarget(p, 16, 10);
        ServerWorld w = p.getServerWorld();
        sound(p, AWSounds.BLOOD_THREAD, 1.1f, 1.0f);
        if (target == null) {
            d.startCooldown(SkillSlot.SKILL1, CD_S1_MISS);
            d.setBusy(8);
            AWNet.anim(p, "blood.skill1_miss");
            NbtCompound n = nbt();
            n.putInt("c", p.getId());
            n.putInt("e", -1);
            Vec3d end = p.getEyePos().add(p.getRotationVec(1f).multiply(12));
            n.putDouble("tx", end.x);
            n.putDouble("ty", end.y);
            n.putDouble("tz", end.z);
            n.putInt("d", 8);
            AWNet.fx(w, p.getPos(), "blood_thread", n);
            return true;
        }
        boolean air = airborne(target, 1.6);
        d.startCooldown(SkillSlot.SKILL1, CD_S1);
        d.setBusy(18);
        AWNet.anim(p, air ? "blood.skill1_air" : "blood.skill1");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", target.getId());
        n.putInt("d", 12);
        n.putBoolean("enh", enh);
        AWNet.fx(w, p.getPos(), "blood_thread", n);
        ServerActions.start(new ThreadAction(p, d, target, air, enh));
        return true;
    }

    class ThreadAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;
        final boolean air, enh;
        int ripAt = -1;

        ThreadAction(ServerPlayerEntity c, AbilityData d, LivingEntity t, boolean air, boolean enh) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
            this.enh = enh;
        }

        @Override
        protected boolean step(int tt) {
            if (!t.isAlive() || t.isRemoved()) return true;
            ServerWorld w = world();
            if (tt == 2) {
                // 피의 실이 박힘
                hit(w, c, t, 2f, AWNet.DMG_SKILL, false, 0.2f);
                addBleed(w, t, 1, c);
                Combat.stun(t, 14, c);
                sound(t, AWSounds.BLOOD_SLASH, 0.9f, 0.7f);
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.grabbed");
                if (air) Combat.hold(t, true);
            }
            if (tt >= 3 && ripAt < 0) {
                // 끌려감 (강화: 적도 끌어당김)
                Vec3d to = chest(t).subtract(c.getPos().add(0, air ? 1.0 : 0.6, 0));
                double dist = to.length();
                if (dist < 1.9 + t.getWidth() * 0.5 || tt >= 10) {
                    ripAt = tt;
                } else {
                    Vec3d dir = air ? to.normalize() : Combat.horizontal(to).add(0, 0.08, 0);
                    Combat.setVelocitySelf(c, dir.multiply(Math.min(1.7, 0.6 + dist * 0.25)));
                    if (air) Combat.hold(c, true);
                    if (enh) Combat.setVelocity(t, to.normalize().multiply(-0.55));
                    else Combat.setVelocity(t, Vec3d.ZERO);
                }
            }
            if (tt == ripAt) {
                Combat.setVelocitySelf(c, Vec3d.ZERO);
                face(c, c.getPos(), t);
                AWNet.anim(c, air ? "blood.skill1_rip_air" : "blood.skill1_rip");
                // 찢은 직후 바로 2번으로 이어갈 수 있게
                d.busyUntil = Math.min(d.busyUntil, d.now() + 4);
                int had = bleed(t);
                hit(w, c, t, enh ? 4.5f : 3f, AWNet.DMG_SKILL, true, 0.25f);
                slash(w, c, yawTo(c, t), -30, 1.15f);
                slash(w, c, yawTo(c, t), 30, 1.0f);
                sound(t, AWSounds.BLOOD_SLASH, 1.2f, 0.9f);
                impact(w, chest(t), "heavy", 1.2f, COLOR);
                if (had >= 3) {
                    // 출혈이 깊은 적: 터뜨림
                    int s = consumeBleed(w, t);
                    hit(w, c, t, 1.2f * s, AWNet.DMG_CRIT, true, 0.5f);
                    AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                } else {
                    addBleed(w, t, enh ? 2 : 1, c);
                }
                if (enh) AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                Combat.unstun(t);
                Combat.hold(t, false);
                Combat.setVelocity(t, new Vec3d(0, air ? 0.6 : 0.42, 0).add(Combat.horizontal(t.getPos().subtract(c.getPos())).multiply(0.15)));
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.launched_up");
                AWNet.shake(t, 0.4f, 6);
                d.addUlt(4);
                St s = st(c);
                s.s1Hit = d.now();
                s.s1Target = t.getId();
                s.s2AfterS1 = false;
            }
            return ripAt >= 0 && tt >= ripAt + 6;
        }

        @Override
        protected void cleanup() {
            Combat.hold(c, false);
            Combat.hold(t, false);
        }
    }

    // =================================================================== 2. 혈창분출

    private boolean spikes(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        LivingEntity follow = null;
        if (d.now() - s.s1Hit <= COMBO_WINDOW && w.getEntityById(s.s1Target) instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) < 14) follow = le;
        // 피의 대가: 체력 2
        if (p.getHealth() > 4) {
            p.setHealth(p.getHealth() - 2);
            NbtCompound n = nbt();
            n.putInt("e", p.getId());
            n.putFloat("s", 0.5f);
            AWNet.fx(w, chest(p), "blood_burst", n);
        }
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(follow != null ? 10 : 16);
        AWNet.anim(p, follow != null ? "blood.skill2_fast" : "blood.skill2");
        sound(p, AWSounds.BLOOD_HEART, 1.0f, 1.2f);
        ServerActions.start(new SpikeAction(p, d, follow));
        return true;
    }

    class SpikeAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        @Nullable
        final LivingEntity follow;
        final Vec3d fwd, base;
        final Set<LivingEntity> hits = new HashSet<>();

        SpikeAction(ServerPlayerEntity c, AbilityData d, @Nullable LivingEntity follow) {
            super(c);
            this.c = c;
            this.d = d;
            this.follow = follow;
            this.fwd = Combat.horizontal(c.getRotationVec(1f));
            this.base = c.getPos();
        }

        void erupt(Vec3d at, float r, boolean big) {
            ServerWorld w = world();
            Vec3d g = groundAt(w, at, c);
            spikesFx(w, g, r, big);
            sound(w, g, AWSounds.BLOOD_SPIKE, big ? 1.5f : 1.1f, big ? 0.8f : 1.0f);
            for (LivingEntity e : Combat.findInRadius(c, g.add(0, 1, 0), r + 0.6)) {
                if (!hits.add(e)) continue;
                boolean wasAir = airborne(e, 1.2);
                int st = bleed(e);
                float dmg = (big ? 4.5f : 3.5f) + st + (wasAir ? 1.5f : 0);
                hit(w, c, e, dmg, st > 0 || wasAir ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, false, 0.2f);
                addBleed(w, e, 1, c);
                Combat.setVelocity(e, new Vec3d(0, wasAir ? 1.05 : (big ? 1.35 : 1.2), 0));
                if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched_up");
                d.addUlt(3);
            }
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (follow != null) {
                if (tt == 3 && follow.isAlive()) {
                    // 1번 직후: 그 적 바로 밑에서 솟음
                    erupt(follow.getPos(), 1.8f, true);
                    erupt(follow.getPos().add(fwd.multiply(2.5)), 1.3f, false);
                    if (!hits.isEmpty()) AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                }
                if (tt == 4) done();
                return tt >= 10;
            }
            if (tt == 6) erupt(base.add(fwd.multiply(2.6)), 1.6f, false);
            if (tt == 8) erupt(base.add(fwd.multiply(5.1)), 1.7f, false);
            if (tt == 10) erupt(base.add(fwd.multiply(7.6)), 1.9f, true);
            if (tt == 11) done();
            return tt >= 16;
        }

        void done() {
            if (hits.isEmpty()) return;
            St s = st(c);
            s.s2Hit = d.now();
            s.s2AfterS1 = follow != null || d.now() - s.s1Hit <= COMBO_WINDOW + 12;
        }
    }

    // =================================================================== 3. 흡혈난무

    private boolean frenzy(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 10, 14);
        if (target == null) {
            d.startCooldown(SkillSlot.SKILL3, CD_S3_MISS);
            AbilityManager.deny(p);
            return true;
        }
        St s = st(p);
        boolean boost = s.s2AfterS1 && d.now() - s.s2Hit <= COMBO_WINDOW;
        s.s2AfterS1 = false;
        boolean air = airborne(target, 2.0);
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(46);
        AWNet.anim(p, "blood.skill3_dash");
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        AWNet.fx(p.getServerWorld(), p.getPos(), "blood_rush", n);
        sound(p, AWSounds.BLOOD_BATS, 0.8f, 1.3f);
        Combat.stun(target, 44, p);
        ServerActions.start(new FrenzyAction(p, d, target, air, boost));
        return true;
    }

    class FrenzyAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;
        final boolean air, boost;
        boolean released;
        int landAt = -1;

        FrenzyAction(ServerPlayerEntity c, AbilityData d, LivingEntity t, boolean air, boolean boost) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
            this.air = air;
            this.boost = boost;
        }

        @Override
        protected boolean step(int tt) {
            if (!t.isAlive() || t.isRemoved()) return true;
            ServerWorld w = world();
            if (tt == 1) {
                Vec3d dir = Combat.horizontal(c.getPos().subtract(t.getPos()));
                Vec3d at = Combat.safeSpot(c, t.getPos().add(dir.multiply(1.3 + t.getWidth() * 0.5)));
                if (at == null) at = t.getPos();
                face(c, at, t);
                Combat.hold(t, true);
                if (air) Combat.hold(c, true);
                AWNet.anim(c, "blood.skill3");
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.held");
            }
            if (tt >= 1 && !released) {
                Combat.setVelocity(t, Vec3d.ZERO);
                Combat.setVelocitySelf(c, Vec3d.ZERO);
            }
            float y = yawTo(c, t);
            if (tt == 3 || tt == 6 || tt == 9 || tt == 12) {
                int i = (tt - 3) / 3;
                int st = bleed(t);
                float dmg = 2f;
                if (st > 0) {
                    // 출혈을 하나씩 먹으며 추가 피해
                    setBleed(w, t, st - 1, c);
                    dmg += 1.5f;
                }
                hit(w, c, t, dmg, st > 0 ? AWNet.DMG_CRIT : AWNet.DMG_SKILL, true, 0.25f);
                slash(w, c, y, i % 2 == 0 ? -40 : 40, 1.1f);
                sound(t, AWSounds.BLOOD_SLASH, 1.1f, 0.95f + i * 0.08f);
                AWNet.shake(t, 0.25f, 4);
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, i % 2 == 0 ? "common.flinch" : "common.flinch2");
                d.addUlt(2);
            }
            if (tt == 14) {
                // 물어뜯어 흡혈
                AWNet.anim(c, "blood.skill3_drain");
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                n.putInt("e", t.getId());
                n.putInt("d", 17);
                n.putBoolean("big", boost);
                AWNet.fx(w, chest(t), "blood_drain", n);
                sound(t, AWSounds.BLOOD_DRAIN, 1.2f, 0.8f);
                sound(c, AWSounds.BLOOD_HEART, 1.2f, 1.0f);
                if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.headsnap");
            }
            if (tt >= 15 && tt <= 30 && (tt - 15) % 3 == 0) {
                float dr = boost ? 2.4f : 1.2f;
                hit(w, c, t, dr, AWNet.DMG_SKILL, true, 0.6f);
                d.addUlt(2);
            }
            if (tt == 31) AWNet.anim(c, "blood.skill3_throw");
            if (tt == 34) {
                release();
                hit(w, c, t, 4f, AWNet.DMG_CRIT, true, 0.25f);
                slash(w, c, y, -90, 1.4f);
                sound(t, AWSounds.VOID_SLAM, 1.2f, 1.0f);
                AWNet.shake(c, 0.35f, 6);
                if (air || airborne(t, 1.5)) {
                    Combat.setVelocity(t, new Vec3d(0, -2.8, 0));
                    Combat.setVelocitySelf(c, new Vec3d(0, -1.4, 0));
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.launched_down");
                } else {
                    Combat.setVelocity(t, new Vec3d(0, -0.6, 0));
                    crater(w, t.getPos(), 2.4f, COLOR);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                }
                if (boost) {
                    // 1→2 연속 적중 후: 피의 폭발
                    NbtCompound n = nbt();
                    n.putInt("e", t.getId());
                    n.putFloat("s", 1.8f);
                    AWNet.fx(w, chest(t), "blood_burst", n);
                    sound(t, AWSounds.BLOOD_BURST, 1.5f, 0.8f);
                    for (LivingEntity o : Combat.findInRadius(c, chest(t), 3.2)) {
                        hit(w, c, o, 4f, AWNet.DMG_CRIT, true, 0.2f);
                        addBleed(w, o, 2, c);
                    }
                    AWNet.shakeAround(w, t.getPos(), 0.7f, 10, 14);
                    AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                }
            }
            if (tt > 34 && landAt < 0 && air) {
                if (t.isOnGround() || Combat.distanceToGround(t, 4) < 0.4 || tt >= 48) {
                    landAt = tt;
                    hit(w, c, t, 2f, AWNet.DMG_CRIT, true, 0.2f);
                    crater(w, t.getPos(), 3.2f, COLOR);
                    sound(t, AWSounds.VOID_SLAM, 1.4f, 0.9f);
                    AWNet.shakeAround(w, t.getPos(), 0.9f, 12, 18);
                    if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.slammed");
                } else Combat.setVelocity(t, new Vec3d(0, -2.8, 0));
            }
            return tt >= 40 && (!air || landAt > 0 || tt > 50);
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
            d.busyUntil = Math.min(d.busyUntil, d.now() + 2);
        }
    }

    // =================================================================== 4. 혈막반격

    private boolean veil(ServerPlayerEntity p, AbilityData d) {
        d.counterUntil = d.now() + 16;
        d.setBusy(16);
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, true);
        AWNet.anim(p, "blood.skill4");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 16);
        AWNet.fx(p.getServerWorld(), p.getPos(), "blood_veil", n);
        sound(p, AWSounds.BLOOD_VEIL, 1.2f, 0.8f);
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
        ServerActions.start(new VeilCounter(p, d, la));
        return false;
    }

    class VeilCounter extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity a;
        boolean released;

        VeilCounter(ServerPlayerEntity c, AbilityData d, LivingEntity a) {
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
                AWNet.anim(c, "blood.skill4_success");
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                AWNet.fx(w, c.getPos(), "blood_veil_burst", n);
                sound(c, AWSounds.BLOOD_BURST, 1.3f, 1.1f);
                sound(c, AWSounds.VOID_COUNTER_SUCCESS, 1.0f, 0.9f);
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_COUNTER, a);
                Combat.stun(a, 18, c);
                Combat.setVelocity(a, Vec3d.ZERO);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.stagger");
                d.addUlt(25);
            }
            if (tt == 3) {
                // 공격자의 발밑에서 피의 창
                spikesFx(w, groundAt(w, a.getPos(), c), 1.4f, true);
                sound(a, AWSounds.BLOOD_SPIKE, 1.4f, 0.8f);
                hit(w, c, a, 5f, AWNet.DMG_CRIT, true, 0.3f);
                addBleed(w, a, 2, c);
                Combat.setVelocity(a, new Vec3d(0, 1.25, 0));
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.launched_up");
                // 반격 성공: 3번 즉시 연계 가능
                d.cdEnd[SkillSlot.SKILL3.ordinal()] = d.now();
                d.dirty = true;
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                n.putInt("e", a.getId());
                n.putInt("d", 10);
                n.putBoolean("big", false);
                AWNet.fx(w, chest(a), "blood_drain", n);
            }
            if (tt > 3 && tt <= 12 && tt % 3 == 0) heal(w, c, 1.3f);
            return tt >= 14;
        }
    }

    // =================================================================== R. 혈월만개

    private boolean ultimate(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.frozenUntil = now + ULT_FREEZE;
        d.invulnUntil = now + ULT_FREEZE;
        d.setBusy(ULT_CINEMATIC);
        d.superArmorUntil = now + ULT_CINEMATIC;
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Combat.stun(p, ULT_CINEMATIC, p);
        Vec3d focus = p.getPos().add(Combat.horizontal(p.getRotationVec(1f)).multiply(4));
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "blood_ult", ULT_CINEMATIC, focus);
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
        AWNet.anim(p, "blood.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", ULT_CINEMATIC);
        AWNet.fx(w, p.getPos(), "blood_ult", n);
        ServerActions.start(new UltAction(p, d));
        return true;
    }

    class UltAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final List<LivingEntity> bound = new ArrayList<>();
        final Vec3d origin;

        UltAction(ServerPlayerEntity c, AbilityData d) {
            super(c);
            this.c = c;
            this.d = d;
            this.origin = c.getPos();
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == 2) sound(w, c.getPos(), AWSounds.BLOOD_MOON, 4f, 0.7f);
            if (tt == 10) sound(w, c.getPos(), AWSounds.BLOOD_ROAR, 4f, 0.9f);
            if (tt >= 12 && tt < 28) {
                // 박쥐 날개로 떠오름
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, new Vec3d(0, 0.22, 0));
            }
            if (tt >= 28 && tt < 60) {
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, Vec3d.ZERO);
            }
            if (tt == 16) {
                // 피의 사슬로 결박
                for (LivingEntity e : Combat.findInRadius(c, origin, 15)) {
                    if (bound.size() >= 12) break;
                    bound.add(e);
                }
                int[] ids = bound.stream().mapToInt(Entity::getId).toArray();
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                n.putIntArray("ids", ids);
                n.putInt("d", 44);
                AWNet.fx(w, c.getPos(), "blood_chains", n);
                sound(w, c.getPos(), AWSounds.BLOOD_CHAIN, 3f, 0.8f);
                for (LivingEntity e : bound) Combat.stun(e, 50, c);
            }
            if (tt > 16 && tt < 30) {
                // 끌어당김: 시전자 주위 원으로
                for (LivingEntity e : bound) {
                    if (!e.isAlive()) continue;
                    Vec3d dir = Combat.horizontal(e.getPos().subtract(origin));
                    if (dir.lengthSquared() < 1e-4) dir = new Vec3d(1, 0, 0);
                    Vec3d into = origin.add(dir.multiply(4.2));
                    Vec3d to = into.subtract(e.getPos());
                    Combat.setVelocity(e, Combat.horizontal(to).multiply(Math.min(0.5, 0.1 + to.horizontalLength() * 0.06)).add(0, 0.04, 0));
                }
            }
            if (tt >= 30 && tt < 58) {
                for (LivingEntity e : bound) {
                    if (!e.isAlive()) continue;
                    Combat.hold(e, true);
                    Combat.setVelocity(e, new Vec3d(0, tt < 36 ? 0.18 : 0, 0));
                }
            }
            if (tt >= 34 && tt < 56 && (tt - 34) % 3 == 0) {
                // 피를 빨아들임
                if (tt == 34) sound(w, c.getPos(), AWSounds.BLOOD_HEART, 4f, 0.8f);
                for (LivingEntity e : bound) {
                    if (!e.isAlive()) continue;
                    if (Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), 0.8f, AWNet.DMG_ULT)) heal(w, c, 0.5f);
                }
                if (tt % 6 == 0) sound(w, c.getPos(), AWSounds.BLOOD_DRAIN, 2f, 0.7f);
            }
            if (tt == 58) {
                // 피의 폭발
                sound(w, c.getPos(), AWSounds.BLOOD_BURST, 5f, 0.6f);
                sound(w, c.getPos(), AWSounds.VOID_BLAST, 4f, 0.7f);
                AWNet.shakeAround(w, c.getPos(), 1.3f, 20, 40);
                for (LivingEntity e : bound) {
                    if (!e.isAlive()) continue;
                    Combat.hold(e, false);
                    Combat.unstun(e);
                    int s = bleed(e);
                    if (s > 0) setBleed(w, e, 0, null);
                    Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), 9f + 1.5f * s, AWNet.DMG_ULT);
                    heal(w, c, 1.5f);
                    Vec3d out = Combat.horizontal(e.getPos().subtract(origin)).multiply(1.1).add(0, 0.6, 0);
                    Combat.setVelocity(e, out);
                    if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched");
                }
            }
            if (tt == 60) {
                Combat.hold(c, false);
                Combat.unstun(c);
            }
            return tt >= ULT_CINEMATIC;
        }

        @Override
        protected void cleanup() {
            Combat.hold(c, false);
            for (LivingEntity e : bound) Combat.hold(e, false);
        }
    }

    // =================================================================== T. 박쥐산개

    private boolean swarm(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        ServerWorld w = p.getServerWorld();
        LivingEntity target = null;
        if (d.now() - s.s1Hit <= 40 && w.getEntityById(s.s1Target) instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) < 18) target = le;
        Vec3d from = p.getPos();
        Vec3d dest;
        if (target != null) {
            Vec3d dir = Combat.horizontal(target.getPos().subtract(from));
            dest = Combat.safeSpot(p, target.getPos().add(dir.multiply(1.4 + target.getWidth() * 0.5)));
        } else {
            Vec3d look = p.getRotationVec(1f);
            Vec3d dir = new Vec3d(look.x, MathHelper.clamp(look.y, -0.3, 0.5), look.z).normalize();
            Vec3d eye = from.add(0, 1, 0);
            BlockHitResult hit = w.raycast(new RaycastContext(eye, eye.add(dir.multiply(9)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            double dist = hit.getType() == HitResult.Type.MISS ? 9 : Math.max(0, hit.getPos().distanceTo(eye) - 0.7);
            dest = null;
            for (double k = dist; k > 0.8 && dest == null; k -= 0.5) dest = Combat.safeSpot(p, from.add(dir.multiply(k)));
        }
        if (dest == null || dest.distanceTo(from) < 1) {
            AbilityManager.deny(p);
            return false;
        }
        d.startCooldown(SkillSlot.PASSIVE, CD_T);
        d.setBusy(9);
        d.invulnUntil = d.now() + 10;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_SWARM, true);
        AWNet.anim(p, "blood.swarm");
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putDouble("tx", dest.x);
        n.putDouble("ty", dest.y);
        n.putDouble("tz", dest.z);
        n.putInt("d", 8);
        AWNet.fx(w, from, "blood_bats", n);
        sound(w, from, AWSounds.BLOOD_BATS, 1.4f, 1.0f);
        ServerActions.start(new SwarmAction(p, d, from, dest, target));
        return true;
    }

    class SwarmAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final Vec3d from, to;
        @Nullable
        final LivingEntity target;
        final Set<LivingEntity> bitten = new HashSet<>();

        SwarmAction(ServerPlayerEntity c, AbilityData d, Vec3d from, Vec3d to, @Nullable LivingEntity target) {
            super(c);
            this.c = c;
            this.d = d;
            this.from = from;
            this.to = to;
            this.target = target;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt < 8) {
                Vec3d a = from.lerp(to, tt / 8.0), b = from.lerp(to, (tt + 1) / 8.0);
                Vec3d dir = b.subtract(a);
                float yaw = (float) (MathHelper.atan2(dir.z, dir.x) * 57.2957795) - 90f;
                Combat.teleport(c, b, yaw, c.getPitch());
                Combat.setVelocitySelf(c, Vec3d.ZERO);
                for (LivingEntity e : Combat.findInRadius(c, b.add(0, 1, 0), 1.8)) {
                    if (!bitten.add(e)) continue;
                    hit(w, c, e, 2f, AWNet.DMG_SKILL, false, 0.3f);
                    addBleed(w, e, 1, c);
                    sound(e, AWSounds.BLOOD_SLASH, 0.8f, 1.5f);
                }
            }
            if (tt == 8) {
                ((AWPlayer) c).aw$setFlag(AWPlayer.FLAG_SWARM, false);
                if (target != null && target.isAlive()) face(c, c.getPos(), target);
                NbtCompound n = nbt();
                n.putInt("e", c.getId());
                n.putFloat("s", 0.8f);
                AWNet.fx(w, chest(c), "blood_burst", n);
                st(c).tAt = d.now();
                return true;
            }
            return false;
        }

        @Override
        protected void cleanup() {
            ((AWPlayer) c).aw$setFlag(AWPlayer.FLAG_SWARM, false);
        }
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        if (((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_COUNTER) && !d.isCountering()) {
            ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
            if (!ServerActions.hasAction(p, VeilCounter.class)) {
                AWNet.anim(p, "blood.skill4_fail");
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                AWNet.fx(p.getServerWorld(), p.getPos(), "blood_veil_fade", n);
                sound(p, AWSounds.VOID_COUNTER_FAIL, 0.7f, 0.7f);
            }
        }
        if (d.hasSuperArmor() && !d.isFrozen()) {
            p.removeStatusEffect(AWEffects.CONCUSSION);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        states.remove(p.getUuid());
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_SWARM, false);
        Combat.hold(p, false);
    }
}
