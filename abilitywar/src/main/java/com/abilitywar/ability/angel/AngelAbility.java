package com.abilitywar.ability.angel;

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
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 천사 — 힐러 (RPG 직업). 궁극기 게이지는 회복한 양으로만 찬다.
 * <pre>
 * 평타  빛의 구슬: 조준한 아군을 회복 (아군이 없으면 적에게 약한 빛 공격)
 * 1    치유의 빛     : 나와 주변 5칸의 파티 회복
 * 2    천사의 가호   : 나와 주변 5칸의 파티에게 공격 속도 + 최대 체력 + 피해 + 저항 버프
 * 3    축복          : 아군 한 명(없으면 나)에게 2번보다 강한 버프 + 지속 회복
 * 4    대치유        : 아군 한 명(없으면 나)을 크게 회복 + 해로운 효과 정화
 * R    천상의 심판   : 하늘에 마법진 여러 개 → 천상의 빛줄기가 쏟아짐 (적은 피해, 아군은 회복 + 버프)
 * T    날개          : 비행 모드 ↔ 걷기 모드
 * </pre>
 */
public class AngelAbility extends Ability {
    public static final int COLOR = 0xCFEFFF;
    public static final int CD_S1 = 160, CD_S2 = 400, CD_S3 = 500, CD_S4 = 240, CD_T = 20;
    public static final double PARTY_R = 5;
    public static final int ULT_CINEMATIC = 70;
    public static final int ULT_FREEZE = 30;
    /** 회복량 1당 궁극기 게이지. */
    public static final float GAUGE_PER_HEAL = 3f;

    public AngelAbility() {
        super(AbilityWar.id("angel"), "angel", 0xFF000000 | COLOR);
    }

    // =================================================================== 게이지: 회복으로만 참

    @Override
    public boolean passiveGauge() {
        return false;
    }

    @Override
    public void onDealtDamage(ServerPlayerEntity player, AbilityData data, float lost) {
    }

    @Override
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 12 : 8;
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> healWave(p, d);
            case SKILL2 -> grace(p, d);
            case SKILL3 -> bless(p, d);
            case SKILL4 -> greatHeal(p, d);
            case ULTIMATE -> judgement(p, d);
            case PASSIVE -> wings(p, d);
            case DASH -> false;
        };
    }

    // =================================================================== 아군 / 회복 / 버프

    public static boolean ally(ServerPlayerEntity c, Entity e) {
        return e == c || (e instanceof ServerPlayerEntity o && o.isAlive() && !o.isSpectator() && c.isTeammate(o));
    }

    /** 자신 + 같은 팀 플레이어. */
    public static List<ServerPlayerEntity> party(ServerPlayerEntity c, Vec3d center, double r) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity o : c.getServerWorld().getPlayers()) {
            if (ally(c, o) && o.getPos().squaredDistanceTo(center) <= r * r) out.add(o);
        }
        return out;
    }

    /** 조준한 아군 (자신 제외). */
    @Nullable
    public static ServerPlayerEntity aimedAlly(ServerPlayerEntity c, double range, double cone) {
        Vec3d eye = c.getEyePos(), look = c.getRotationVec(1f);
        ServerPlayerEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double lim = Math.toRadians(cone);
        for (ServerPlayerEntity o : c.getServerWorld().getPlayers()) {
            if (o == c || !ally(c, o)) continue;
            Vec3d to = o.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range) continue;
            double ang = Math.acos(Math.max(-1, Math.min(1, to.normalize().dotProduct(look))));
            double slack = Math.atan2(o.getWidth() * 0.75, Math.max(dist, 0.1));
            if (ang - slack > lim || !Combat.hasLineOfSight(c, o)) continue;
            double score = Math.max(0, ang - slack) * 6 + dist * 0.05;
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    /** 회복 (+초록 숫자). 실제로 찬 체력만큼 궁극기 게이지. */
    static float heal(ServerWorld w, ServerPlayerEntity c, AbilityData d, LivingEntity t, float amount) {
        amount *= com.abilitywar.rpg.RpgManager.healPower(c);
        float before = t.getHealth();
        if (before < t.getMaxHealth()) t.heal(amount);
        float got = t.getHealth() - before;
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putFloat("a", amount);
        AWNet.fx(w, t.getPos(), "angel_heal", n);
        if (got > 0.01f) {
            com.abilitywar.rpg.RpgManager.onHeal(c, got);
            AWNet.damageNumber(t, got, AWNet.DMG_HEAL, c);
            d.addUlt(got * GAUGE_PER_HEAL);
        }
        return got;
    }

    static void effect(LivingEntity t, StatusEffect e, int ticks, int amp, Entity src) {
        StatusEffectInstance cur = t.getStatusEffect(e);
        if (cur != null && (cur.getAmplifier() > amp || (cur.getAmplifier() == amp && cur.getDuration() > ticks))) return;
        t.addStatusEffect(new StatusEffectInstance(e, ticks, amp, false, false, true), src);
    }

    /** 공격 속도 + 최대 체력 + 피해 + 저항. */
    static void blessings(ServerWorld w, ServerPlayerEntity c, AbilityData d, LivingEntity t, int ticks, int amp) {
        effect(t, AWEffects.FERVOR, ticks, amp, c);
        effect(t, StatusEffects.HEALTH_BOOST, ticks, amp, c);
        effect(t, AWEffects.VALOR, ticks, amp, c);
        effect(t, AWEffects.AEGIS, ticks, amp, c);
        if (t != c) d.addUlt(4);
    }

    public static void cleanse(LivingEntity t) {
        for (StatusEffect e : new StatusEffect[]{AWEffects.CONCUSSION, AWEffects.BLEED, AWEffects.SILENCE, AWEffects.DISSONANCE, AWEffects.DRAGON_MARK,
                AWEffects.MANA_MARK, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, StatusEffects.POISON, StatusEffects.WITHER, StatusEffects.BLINDNESS}) {
            t.removeStatusEffect(e);
        }
    }

    // =================================================================== 평타: 빛의 구슬 (아군 회복)

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        LivingEntity t = aimedAlly(p, 18, 9);
        boolean healing = t != null;
        if (!healing) t = target != null && !ally(p, target) ? target : Combat.findTarget(p, 15, 8);
        sound(p, AWSounds.ANGEL_ORB, 0.8f, 1.0f + combo * 0.15f);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", t != null ? t.getId() : -1);
        Vec3d end = p.getEyePos().add(p.getRotationVec(1f).multiply(14));
        n.putDouble("tx", end.x);
        n.putDouble("ty", end.y);
        n.putDouble("tz", end.z);
        n.putBoolean("h", healing);
        n.putInt("n", combo == 2 ? 3 : 1);
        AWNet.fx(w, p.getPos(), "angel_orb", n);
        if (t == null) return;
        final LivingEntity tgt = t;
        int travel = Math.max(2, Math.min(5, (int) (tgt.distanceTo(p) / 3.5)));
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                if (!tgt.isAlive()) return true;
                boolean fin = combo == 2;
                if (healing) {
                    heal(w, p, d, tgt, fin ? 3f : 2f);
                } else {
                    Combat.damage(tgt, AWDamage.source(w, AWDamage.PUNCH, p), fin ? 2.5f : 1.5f, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
                    impact(w, chest(tgt), fin ? "heavy" : "punch", fin ? 0.8f : 0.5f, COLOR);
                }
                return true;
            }
        });
    }

    // =================================================================== 1. 치유의 빛 (범위 회복)

    private boolean healWave(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, CD_S1);
        d.setBusy(10);
        AWNet.anim(p, "angel.skill1");
        sound(p, AWSounds.ANGEL_HEAL, 1.0f, 1.0f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 5) {
                    ServerWorld w = world();
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", (float) PARTY_R);
                    n.putInt("k", 0);
                    AWNet.fx(w, p.getPos(), "angel_wave", n);
                    for (ServerPlayerEntity a : party(p, p.getPos(), PARTY_R)) heal(w, p, d, a, 6f);
                }
                return tt >= 8;
            }
        });
        return true;
    }

    // =================================================================== 2. 천사의 가호 (범위 버프)

    private boolean grace(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(12);
        AWNet.anim(p, "angel.skill2");
        sound(p, AWSounds.ANGEL_BLESS, 1.0f, 1.2f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 6) {
                    ServerWorld w = world();
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", (float) PARTY_R);
                    n.putInt("k", 1);
                    AWNet.fx(w, p.getPos(), "angel_wave", n);
                    for (ServerPlayerEntity a : party(p, p.getPos(), PARTY_R)) {
                        blessings(w, p, d, a, 300, 0);
                        heal(w, p, d, a, 4f);
                        NbtCompound m = nbt();
                        m.putInt("e", a.getId());
                        m.putBoolean("big", false);
                        AWNet.fx(w, a.getPos(), "angel_buffed", m);
                    }
                }
                return tt >= 10;
            }
        });
        return true;
    }

    // =================================================================== 3. 축복 (한 명에게 강한 버프)

    private boolean bless(ServerPlayerEntity p, AbilityData d) {
        ServerPlayerEntity t = aimedAlly(p, 24, 12);
        if (t == null) t = p;
        final ServerPlayerEntity tgt = t;
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(12);
        AWNet.anim(p, "angel.skill3");
        sound(p, AWSounds.ANGEL_BLESS, 1.2f, 0.9f);
        ServerWorld w = p.getServerWorld();
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", tgt.getId());
        AWNet.fx(w, tgt.getPos(), "angel_bless", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 7 && tgt.isAlive()) {
                    blessings(w, p, d, tgt, 400, 1);
                    effect(tgt, AWEffects.GRACE, 400, 0, p);
                    heal(w, p, d, tgt, 4f);
                    sound(tgt, AWSounds.ANGEL_CHOIR, 1.2f, 1.3f);
                    NbtCompound m = nbt();
                    m.putInt("e", tgt.getId());
                    m.putBoolean("big", true);
                    AWNet.fx(w, tgt.getPos(), "angel_buffed", m);
                }
                return tt >= 10;
            }
        });
        return true;
    }

    // =================================================================== 4. 대치유 (한 명 크게 회복)

    private boolean greatHeal(ServerPlayerEntity p, AbilityData d) {
        ServerPlayerEntity t = aimedAlly(p, 24, 12);
        if (t == null) t = p;
        final ServerPlayerEntity tgt = t;
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        d.setBusy(14);
        AWNet.anim(p, "angel.skill4");
        sound(p, AWSounds.ANGEL_HEAL, 1.3f, 0.8f);
        ServerWorld w = p.getServerWorld();
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", tgt.getId());
        n.putInt("d", 12);
        AWNet.fx(w, tgt.getPos(), "angel_stream", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 9 && tgt.isAlive()) {
                    cleanse(tgt);
                    heal(w, p, d, tgt, 14f);
                    effect(tgt, AWEffects.GRACE, 100, 1, p);
                    sound(tgt, AWSounds.ANGEL_CHOIR, 1.2f, 1.1f);
                    NbtCompound m = nbt();
                    m.putInt("e", tgt.getId());
                    AWNet.fx(w, tgt.getPos(), "angel_bloom", m);
                }
                return tt >= 12;
            }
        });
        return true;
    }

    // =================================================================== R. 천상의 심판

    private boolean judgement(ServerPlayerEntity p, AbilityData d) {
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
        Combat.stun(p, ULT_CINEMATIC - 6, p);
        Vec3d focus = p.getPos().add(Combat.horizontal(p.getRotationVec(1f)).multiply(6));
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "angel_ult", ULT_CINEMATIC, focus);
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
        AWNet.anim(p, "angel.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", ULT_CINEMATIC);
        AWNet.fx(w, p.getPos(), "angel_ult", n);
        ServerActions.start(new Judgement(p, d));
        return true;
    }

    class Judgement extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final Vec3d origin;
        final Vec3d[] circles = new Vec3d[7];
        /** 대상별 마지막으로 맞은 시각: 한 대상에 빛줄기가 몰리지 않게 (적 하나면 전부 그 적에게 가던 문제). */
        final java.util.Map<LivingEntity, Integer> lastHit = new java.util.HashMap<>();
        int next;

        Judgement(ServerPlayerEntity c, AbilityData d) {
            super(c);
            this.c = c;
            this.d = d;
            this.origin = c.getPos();
            // 클라이언트 이펙트와 같은 배치 (AngelFx.Ult.circle)
            for (int i = 0; i < circles.length; i++) circles[i] = circle(origin, i);
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == 2) sound(w, c.getPos(), AWSounds.ANGEL_CHOIR, 4f, 0.8f);
            if (tt >= 8 && tt < 22) {
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, new Vec3d(0, 0.12, 0));
            }
            if (tt >= 22 && tt < 64) {
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, Vec3d.ZERO);
            }
            if (tt >= 30 && tt <= 62 && tt % 3 == 0) {
                // 마법진마다 돌아가며 빛줄기
                List<LivingEntity> targets = new ArrayList<>(), all = new ArrayList<>();
                for (Entity e : w.getOtherEntities(null, new Box(origin, origin).expand(16, 8, 16), e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator())) {
                    if (!ally(c, e) && !Combat.canTarget(c, e)) continue;
                    all.add((LivingEntity) e);
                    Integer last = lastHit.get(e);
                    if (last == null || tt - last >= HIT_GAP) targets.add((LivingEntity) e);
                }
                for (int k = 0; k < 2; k++) {
                    Vec3d from = circles[next++ % circles.length];
                    LivingEntity t = targets.isEmpty() ? null : targets.remove(c.getRandom().nextInt(targets.size()));
                    Vec3d at;
                    if (t != null) {
                        at = t.getPos();
                        lastHit.put(t, tt);
                    } else if (!all.isEmpty()) {
                        // 맞힐 수 있는 대상이 없으면 누군가의 주변 땅에 꽂힘 (연출)
                        Vec3d near = all.get(c.getRandom().nextInt(all.size())).getPos();
                        double a = c.getRandom().nextDouble() * Math.PI * 2, r = 2 + c.getRandom().nextDouble() * 2.5;
                        at = new Vec3d(near.x + Math.cos(a) * r, near.y, near.z + Math.sin(a) * r);
                    } else {
                        at = origin.add(c.getRandom().nextGaussian() * 6, 0, c.getRandom().nextGaussian() * 6);
                    }
                    boolean isAlly = t != null && ally(c, t);
                    NbtCompound n = nbt();
                    n.putDouble("fx", from.x);
                    n.putDouble("fy", from.y);
                    n.putDouble("fz", from.z);
                    n.putBoolean("ally", isAlly);
                    AWNet.fx(w, at, "angel_beam", n);
                    sound(w, at, AWSounds.ANGEL_BEAM, 1.4f, isAlly ? 1.3f : 0.9f);
                    if (t == null) continue;
                    if (isAlly) {
                        heal(w, c, d, t, 4f);
                        effect(t, AWEffects.VALOR, 200, 0, c);
                        effect(t, AWEffects.AEGIS, 200, 0, c);
                    } else {
                        Combat.damage(t, AWDamage.source(w, AWDamage.ULTIMATE, c), BEAM_DAMAGE, AWNet.DMG_ULT);
                        Combat.stun(t, 8, c);
                    }
                }
            }
            if (tt == 64) {
                Combat.hold(c, false);
                Combat.unstun(c);
            }
            return tt >= ULT_CINEMATIC;
        }

        @Override
        protected void cleanup() {
            Combat.hold(c, false);
        }
    }

    /** 빛줄기 하나의 피해 / 같은 대상이 다시 맞을 수 있는 간격 (틱): 적 한 명은 최대 5번 = 20 피해. */
    static final float BEAM_DAMAGE = 4f;
    static final int HIT_GAP = 7;

    /** 하늘의 마법진 위치 (서버/클라이언트 공통). */
    public static Vec3d circle(Vec3d origin, int i) {
        if (i == 0) return origin.add(0, 15, 0);
        double a = (i - 1) * Math.PI * 2 / 6;
        return origin.add(Math.cos(a) * 9, 13 + (i % 2) * 2, Math.sin(a) * 9);
    }

    // =================================================================== T. 날개 (비행 ↔ 걷기)

    private boolean wings(ServerPlayerEntity p, AbilityData d) {
        boolean fly = !((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_FLYING);
        setFlying(p, fly);
        d.startCooldown(SkillSlot.PASSIVE, CD_T);
        AWNet.anim(p, "angel.mode");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putBoolean("fly", fly);
        AWNet.fx(p.getServerWorld(), p.getPos(), "angel_mode", n);
        sound(p, AWSounds.ANGEL_WINGS, 1.0f, fly ? 1.0f : 0.8f);
        if (fly) Combat.setVelocitySelf(p, p.getVelocity().add(0, 0.5, 0));
        return true;
    }

    static void setFlying(ServerPlayerEntity p, boolean fly) {
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_FLYING, fly);
        boolean creative = p.isCreative() || p.isSpectator();
        if (fly) {
            p.getAbilities().allowFlying = true;
            p.getAbilities().flying = true;
        } else {
            if (!creative) {
                p.getAbilities().allowFlying = false;
                p.getAbilities().flying = false;
            }
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false, false));
        }
        p.sendAbilitiesUpdate();
        AbilityData d = AbilityManager.data(p);
        d.extra = fly ? 1 : 0;
        d.dirty = true;
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        boolean flag = ((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_FLYING);
        // 다른 이유로 비행이 풀렸으면 (예: 게임모드 변경) 다시 켬
        if (flag && !p.getAbilities().allowFlying) {
            p.getAbilities().allowFlying = true;
            p.sendAbilitiesUpdate();
        }
        if (d.hasSuperArmor() && !d.isFrozen()) p.removeStatusEffect(AWEffects.CONCUSSION);
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        if (((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_FLYING)) setFlying(p, false);
        d.extra = 0;
    }
}
