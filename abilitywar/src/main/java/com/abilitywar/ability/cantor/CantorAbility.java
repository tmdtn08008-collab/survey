package com.abilitywar.ability.cantor;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 성가사 — 버퍼 직업. 찬가로 자신과 같은 팀을 강화하고, 빛과 종소리로 적을 묶는다.
 * <pre>
 * 버프   용맹(주는 피해↑) · 수호(받는 피해↓) · 신속(이동 속도↑, 쿨타임 회복↑) · 은총(회복)
 * 디버프 불협화음(주는 피해↓) · 침묵(스킬 사용 불가)
 * 선율   T 로 바꾸는 상시 오라 (용맹 → 수호 → 신속). 주변 아군에게 약한 버프.
 * 화음   스킬을 쓸 때마다 음표 +1 (평타 3타, 반격 성공도 +1). 음표 3개면 다음 1·2·3 스킬이 '화음'으로 강화.
 * 1  용맹의 찬가 : 소리의 파동 — 아군 용맹 + 가까운 적 피해·밀쳐내기·불협화음
 * 2  수호의 성역 : 빛의 돔 — 안의 아군 수호 + 회복, 적은 밖으로 튕겨냄
 * 3  천상의 종   : 하늘에서 거대한 종이 떨어짐 — 피해·속박, 종소리가 아군에게 신속
 * 4  응답송      : 반격 자세. 막으면 공격자에게 심판의 빛(피해 + 침묵), 아군 회복 + 수호, 1번 쿨타임 초기화
 * R  GLORIA     : 빛의 날개로 떠올라 천상의 합창 — 아군 전원 회복·정화·모든 버프, 적에게 심판의 빛 + 침묵
 * T  선율 전환   : 오라를 용맹 → 수호 → 신속으로 바꿈
 * </pre>
 */
public class CantorAbility extends Ability {
    public static final int COLOR = 0xFFD86A;
    public static final int CD_S1 = 240, CD_S2 = 320, CD_S3 = 280;
    public static final int CD_S4 = 200, CD_S4_OK = 140;
    public static final int CD_T = 30;
    public static final double AURA_R = 7, BUFF_R = 9;
    public static final int ULT_CINEMATIC = 76;
    public static final int ULT_FREEZE = 30;
    /** 선율 (오라) 종류. */
    public static final int VALOR = 0, AEGIS = 1, HASTE = 2, GRACE = 3;

    static final class St {
        int mode = VALOR;
        int notes;
    }

    private final Map<UUID, St> states = new HashMap<>();

    public CantorAbility() {
        super(AbilityWar.id("cantor"), "cantor", 0xFF000000 | COLOR);
    }

    St st(ServerPlayerEntity p) {
        return states.computeIfAbsent(p.getUuid(), k -> new St());
    }

    static StatusEffect effectOf(int kind) {
        return switch (kind) {
            case AEGIS -> AWEffects.AEGIS;
            case HASTE -> AWEffects.HASTE;
            case GRACE -> AWEffects.GRACE;
            default -> AWEffects.VALOR;
        };
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 14 : 8;
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> valor(p, d);
            case SKILL2 -> sanctuary(p, d);
            case SKILL3 -> bell(p, d);
            case SKILL4 -> ward(p, d);
            case ULTIMATE -> ultimate(p, d);
            case PASSIVE -> switchMelody(p, d);
            case DASH -> false;
        };
    }

    // =================================================================== 아군 / 버프 / 음표

    /** 자신 + 같은 팀 플레이어. */
    static List<ServerPlayerEntity> allies(ServerPlayerEntity c, Vec3d center, double r) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (ServerPlayerEntity o : c.getServerWorld().getPlayers()) {
            if (!o.isAlive() || o.isSpectator()) continue;
            if (o != c && !c.isTeammate(o)) continue;
            if (o.getPos().squaredDistanceTo(center) <= r * r) out.add(o);
        }
        return out;
    }

    /** 버프를 걸고 머리 위에 알림 이펙트. 다른 사람에게 걸면 게이지 +3. */
    static void buff(ServerWorld w, ServerPlayerEntity c, AbilityData d, LivingEntity t, int kind, int ticks, int amp) {
        t.addStatusEffect(new StatusEffectInstance(effectOf(kind), ticks, amp, false, false, true), c);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putInt("k", kind);
        n.putInt("d", ticks);
        AWNet.fx(w, t.getPos(), "cantor_buff", n);
        if (t != c) d.addUlt(3);
        com.abilitywar.rpg.RpgManager.onBuff(c, 1);
    }

    static void heal(ServerWorld w, LivingEntity t, float amount) {
        if (amount <= 0 || t.getHealth() >= t.getMaxHealth()) return;
        t.heal(amount);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putFloat("a", amount);
        AWNet.fx(w, t.getPos(), "cantor_heal", n);
    }

    /** 정화: 해로운 능력 효과 제거. */
    static void cleanse(LivingEntity t) {
        for (StatusEffect e : new StatusEffect[]{AWEffects.STUN, AWEffects.CONCUSSION, AWEffects.BLEED, AWEffects.SILENCE, AWEffects.DISSONANCE,
                AWEffects.DRAGON_MARK, AWEffects.MANA_MARK, StatusEffects.SLOWNESS, StatusEffects.WEAKNESS, StatusEffects.POISON, StatusEffects.WITHER,
                StatusEffects.BLINDNESS, StatusEffects.MINING_FATIGUE}) {
            t.removeStatusEffect(e);
        }
    }

    static void silence(LivingEntity t, int ticks, @Nullable Entity src) {
        t.addStatusEffect(new StatusEffectInstance(AWEffects.SILENCE, ticks, 0, false, false, true), src);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putInt("d", ticks);
        AWNet.fx((ServerWorld) t.getWorld(), t.getPos(), "cantor_silence", n);
    }

    static void dissonance(LivingEntity t, int ticks, @Nullable Entity src) {
        t.addStatusEffect(new StatusEffectInstance(AWEffects.DISSONANCE, ticks, 0, false, false, true), src);
    }

    void sync(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        int ex = s.mode + s.notes * 4;
        if (d.extra != ex) {
            d.extra = ex;
            d.dirty = true;
        }
        // 다른 사람 화면의 광륜 색 / 음표
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("m", s.mode);
        n.putInt("n", s.notes);
        AWNet.fx(p.getServerWorld(), p.getPos(), "cantor_state", n);
    }

    /** 스킬을 쓸 때: 음표가 3개면 화음(강화)으로 소모, 아니면 음표 +1. */
    boolean harmony(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        boolean h = s.notes >= 3;
        if (h) {
            s.notes = 0;
            AWNet.damageNumber(p, 0, AWNet.DMG_TEXT_CHAIN, p);
            sound(p, AWSounds.CANTOR_CHIME, 1.2f, 1.4f);
        } else {
            s.notes++;
        }
        sync(p, d);
        return h;
    }

    void addNote(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        if (s.notes < 3) {
            s.notes++;
            sync(p, d);
        }
    }

    static Vec3d groundAt(ServerWorld w, Vec3d p, Entity e) {
        Vec3d g = Combat.groundBelow(w, p.add(0, 2, 0), 8, e);
        return g != null ? g : p;
    }

    static boolean hit(ServerWorld w, ServerPlayerEntity c, LivingEntity t, float amount, byte kind, boolean unblockable) {
        return Combat.damage(t, AWDamage.source(w, unblockable ? AWDamage.GRAB : AWDamage.SKILL, c), amount, kind);
    }

    // =================================================================== 평타: 음표탄 (지휘봉에서 음표를 날림)

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        LivingEntity tgt = target != null ? target : Combat.findTarget(p, 16, 8);
        sound(p, AWSounds.CANTOR_NOTE, 0.8f, 1.0f + combo * 0.2f);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", tgt != null ? tgt.getId() : -1);
        Vec3d end = p.getEyePos().add(p.getRotationVec(1f).multiply(14));
        n.putDouble("tx", end.x);
        n.putDouble("ty", end.y);
        n.putDouble("tz", end.z);
        n.putInt("n", combo == 2 ? 3 : 1);
        n.putInt("m", st(p).mode);
        AWNet.fx(w, p.getPos(), "cantor_note", n);
        if (combo == 2) addNote(p, d);
        if (tgt == null) return;
        int travel = Math.max(2, Math.min(5, (int) (tgt.distanceTo(p) / 3.5)));
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                if (!tgt.isAlive()) return true;
                boolean fin = combo == 2;
                Combat.damage(tgt, AWDamage.source(w, AWDamage.PUNCH, p), fin ? 3.5f : 2f, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
                Vec3d dir = Combat.horizontal(tgt.getPos().subtract(p.getPos()));
                if (fin) Combat.setVelocity(tgt, dir.multiply(0.45).add(0, 0.2, 0));
                impact(w, chest(tgt), fin ? "heavy" : "punch", fin ? 0.9f : 0.55f, COLOR);
                Combat.hitFlinch(tgt);
                return true;
            }
        });
    }

    // =================================================================== 1. 용맹의 찬가

    private boolean valor(ServerPlayerEntity p, AbilityData d) {
        boolean harm = harmony(p, d);
        d.startCooldown(SkillSlot.SKILL1, CD_S1);
        d.setBusy(12);
        AWNet.anim(p, "cantor.skill1");
        sound(p, AWSounds.CANTOR_HYMN, 1.2f, 1.0f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt != 6) return tt >= 12;
                ServerWorld w = world();
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                n.putFloat("r", (float) BUFF_R);
                n.putBoolean("h", harm);
                n.putInt("k", VALOR);
                AWNet.fx(w, p.getPos(), "cantor_wave", n);
                sound(p, AWSounds.CANTOR_BUFF, 1.2f, 1.1f);
                for (ServerPlayerEntity a : allies(p, p.getPos(), BUFF_R)) {
                    buff(w, p, d, a, VALOR, harm ? 300 : 200, 1);
                    if (harm) buff(w, p, d, a, GRACE, 100, 0);
                }
                for (LivingEntity e : Combat.findInRadius(p, p.getPos().add(0, 1, 0), 4.5)) {
                    hit(w, p, e, harm ? 5f : 3f, AWNet.DMG_SKILL, false);
                    Vec3d dir = Combat.horizontal(e.getPos().subtract(p.getPos()));
                    if (dir.lengthSquared() < 1e-4) dir = Combat.horizontal(p.getRotationVec(1f));
                    Combat.setVelocity(e, dir.normalize().multiply(0.85).add(0, 0.3, 0));
                    dissonance(e, 80, p);
                    if (harm) Combat.stun(e, 12, p);
                    d.addUlt(3);
                }
                return false;
            }
        });
        return true;
    }

    // =================================================================== 2. 수호의 성역

    private boolean sanctuary(ServerPlayerEntity p, AbilityData d) {
        boolean harm = harmony(p, d);
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(12);
        AWNet.anim(p, "cantor.skill2");
        sound(p, AWSounds.CANTOR_CHIME, 1.0f, 0.8f);
        Vec3d at = groundAt(p.getServerWorld(), p.getPos(), p);
        float r = harm ? 7f : 5f;
        int dur = harm ? 160 : 120;
        ServerActions.start(new TimedAction(p) {
            final Set<UUID> told = new HashSet<>();

            @Override
            protected boolean step(int tt) {
                ServerWorld w = world();
                if (tt < 6) return false;
                int k = tt - 6;
                if (k == 0) {
                    NbtCompound n = nbt();
                    n.putFloat("r", r);
                    n.putInt("d", dur);
                    n.putBoolean("h", harm);
                    AWNet.fx(w, at, "cantor_sanctuary", n);
                    sound(w, at, AWSounds.CANTOR_SANCTUARY, 1.3f, 1.0f);
                }
                if (k % 10 == 0) {
                    for (ServerPlayerEntity a : allies(p, at, r)) {
                        if (told.add(a.getUuid())) buff(w, p, d, a, AEGIS, 40, 1);
                        else a.addStatusEffect(new StatusEffectInstance(AWEffects.AEGIS, 40, 1, false, false, true), p);
                        heal(w, a, harm ? 1f : 0.5f);
                    }
                    for (LivingEntity e : Combat.findInRadius(p, at.add(0, 1, 0), r)) {
                        // 성역 밖으로 튕겨냄
                        Vec3d dir = Combat.horizontal(e.getPos().subtract(at));
                        if (dir.lengthSquared() < 1e-4) dir = new Vec3d(1, 0, 0);
                        Combat.setVelocity(e, dir.normalize().multiply(0.75).add(0, 0.25, 0));
                        if (k % 20 == 0) {
                            hit(w, p, e, 1f, AWNet.DMG_SKILL, true);
                            dissonance(e, 60, p);
                        }
                        NbtCompound n = nbt();
                        n.putInt("e", e.getId());
                        AWNet.fx(w, e.getPos(), "cantor_repel", n);
                    }
                }
                return k >= dur;
            }
        });
        return true;
    }

    // =================================================================== 3. 천상의 종

    private boolean bell(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w0 = p.getServerWorld();
        LivingEntity target = Combat.findTarget(p, 20, 12);
        Vec3d at;
        if (target != null) at = groundAt(w0, target.getPos(), p);
        else at = groundAt(w0, p.getPos().add(Combat.horizontal(p.getRotationVec(1f)).multiply(10)), p);
        boolean harm = harmony(p, d);
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(10);
        AWNet.anim(p, "cantor.skill3");
        sound(p, AWSounds.CANTOR_CHIME, 1.1f, 1.2f);
        int rings = harm ? 3 : 1;
        NbtCompound n = nbt();
        n.putInt("rings", rings);
        n.putBoolean("h", harm);
        AWNet.fx(w0, at, "cantor_bell", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                ServerWorld w = world();
                if (tt == 8) {
                    sound(w, at, AWSounds.CANTOR_BELL, 2.0f, 0.8f);
                    AWNet.shakeAround(w, at, 0.6f, 8, 14);
                    for (LivingEntity e : Combat.findInRadius(p, at.add(0, 1, 0), 3.5)) {
                        hit(w, p, e, harm ? 7f : 6f, AWNet.DMG_CRIT, false);
                        Combat.stun(e, harm ? 30 : 20, p);
                        dissonance(e, 100, p);
                        Combat.setVelocity(e, new Vec3d(0, 0.45, 0));
                        if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched_up");
                        d.addUlt(4);
                    }
                }
                for (int k = 0; k < rings; k++) {
                    if (tt != 11 + k * 7) continue;
                    sound(w, at, AWSounds.CANTOR_BELL_RING, 2.2f, 0.9f + k * 0.1f);
                    if (k == 0) for (ServerPlayerEntity a : allies(p, at, 8)) buff(w, p, d, a, HASTE, 160, 1);
                    if (k > 0) {
                        for (LivingEntity e : Combat.findInRadius(p, at.add(0, 1, 0), 4.5)) {
                            hit(w, p, e, 2f, AWNet.DMG_SKILL, true);
                            Combat.stun(e, 12, p);
                            Combat.setVelocity(e, new Vec3d(0, 0.3, 0));
                        }
                    }
                }
                return tt >= 11 + (rings - 1) * 7 + 2;
            }
        });
        return true;
    }

    // =================================================================== 4. 응답송 (반격)

    private boolean ward(ServerPlayerEntity p, AbilityData d) {
        d.counterUntil = d.now() + 16;
        d.setBusy(16);
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, true);
        AWNet.anim(p, "cantor.skill4");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 16);
        AWNet.fx(p.getServerWorld(), p.getPos(), "cantor_ward", n);
        sound(p, AWSounds.CANTOR_CHIME, 1.0f, 1.0f);
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
        d.invulnUntil = d.now() + 12;
        d.busyUntil = d.now() + 12;
        ServerActions.start(new Answer(p, d, la));
        return false;
    }

    class Answer extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity a;

        Answer(ServerPlayerEntity c, AbilityData d, LivingEntity a) {
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
                AWNet.anim(c, "cantor.skill4_success");
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                AWNet.fx(w, c.getPos(), "cantor_ward_burst", n);
                sound(c, AWSounds.CANTOR_BELL_RING, 1.2f, 1.4f);
                sound(c, AWSounds.VOID_COUNTER_SUCCESS, 0.9f, 1.2f);
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_COUNTER, a);
                Combat.stun(a, 16, c);
                Combat.setVelocity(a, Vec3d.ZERO);
                if (a instanceof ServerPlayerEntity) AWNet.anim(a, "common.stagger");
                d.addUlt(25);
                addNote(c, d);
            }
            if (tt == 3) {
                // 심판의 빛
                NbtCompound n = nbt();
                n.putBoolean("big", false);
                AWNet.fx(w, groundAt(w, a.getPos(), c), "cantor_pillar", n);
                sound(a, AWSounds.CANTOR_PILLAR, 1.3f, 1.1f);
                hit(w, c, a, 5f, AWNet.DMG_CRIT, true);
                silence(a, 50, c);
                Vec3d dir = Combat.horizontal(a.getPos().subtract(c.getPos()));
                Combat.setVelocity(a, dir.multiply(0.6).add(0, 0.35, 0));
                for (ServerPlayerEntity al : allies(c, c.getPos(), 7)) {
                    heal(w, al, 3f);
                    buff(w, c, d, al, AEGIS, 100, 1);
                }
                // 반격 성공: 용맹의 찬가 바로 사용 가능
                d.cdEnd[SkillSlot.SKILL1.ordinal()] = d.now();
                d.dirty = true;
                AWNet.damageNumber(c, 0, AWNet.DMG_TEXT_CHAIN, c);
            }
            return tt >= 12;
        }
    }

    // =================================================================== R. GLORIA (천상의 합창)

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
            AWNet.cinematic(o, p, "cantor_ult", ULT_CINEMATIC, focus);
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
        AWNet.anim(p, "cantor.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", ULT_CINEMATIC);
        AWNet.fx(w, p.getPos(), "cantor_ult", n);
        ServerActions.start(new UltAction(p, d));
        return true;
    }

    class UltAction extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
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
            if (tt == 2) sound(w, c.getPos(), AWSounds.CANTOR_CHOIR, 4f, 0.8f);
            if (tt == 14) sound(w, c.getPos(), AWSounds.CANTOR_HYMN, 4f, 0.7f);
            if (tt == 30) sound(w, c.getPos(), AWSounds.CANTOR_CHOIR, 4f, 1.0f);
            if (tt >= 12 && tt < 28) {
                // 빛의 날개로 떠오름
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, new Vec3d(0, 0.17, 0));
            }
            if (tt >= 28 && tt < 60) {
                Combat.hold(c, true);
                Combat.setVelocitySelf(c, Vec3d.ZERO);
            }
            if (tt == 48) {
                sound(w, c.getPos(), AWSounds.CANTOR_GLORIA, 5f, 1.0f);
                sound(w, c.getPos(), AWSounds.CANTOR_PILLAR, 4f, 0.8f);
                AWNet.shakeAround(w, c.getPos(), 0.9f, 16, 30);
                // 아군: 회복 + 정화 + 모든 버프
                for (ServerPlayerEntity a : allies(c, origin, 16)) {
                    cleanse(a);
                    heal(w, a, a.getMaxHealth() * 0.5f);
                    buff(w, c, d, a, VALOR, 300, 1);
                    a.addStatusEffect(new StatusEffectInstance(AWEffects.AEGIS, 300, 1, false, false, true), c);
                    a.addStatusEffect(new StatusEffectInstance(AWEffects.HASTE, 300, 1, false, false, true), c);
                    a.addStatusEffect(new StatusEffectInstance(AWEffects.GRACE, 200, 0, false, false, true), c);
                    NbtCompound n = nbt();
                    n.putInt("e", a.getId());
                    n.putInt("k", 9); // 모두
                    n.putInt("d", 300);
                    AWNet.fx(w, a.getPos(), "cantor_buff", n);
                }
                // 적: 심판의 빛 + 침묵
                int count = 0;
                for (LivingEntity e : Combat.findInRadius(c, origin, 16)) {
                    if (count++ >= 12) break;
                    NbtCompound n = nbt();
                    n.putBoolean("big", true);
                    AWNet.fx(w, groundAt(w, e.getPos(), c), "cantor_pillar", n);
                    Combat.damage(e, AWDamage.source(w, AWDamage.ULTIMATE, c), 10f, AWNet.DMG_ULT);
                    silence(e, 60, c);
                    dissonance(e, 160, c);
                    Vec3d out = Combat.horizontal(e.getPos().subtract(origin)).multiply(0.9).add(0, 0.5, 0);
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
        }
    }

    // =================================================================== T. 선율 전환

    private boolean switchMelody(ServerPlayerEntity p, AbilityData d) {
        St s = st(p);
        s.mode = (s.mode + 1) % 3;
        d.startCooldown(SkillSlot.PASSIVE, CD_T);
        AWNet.anim(p, "cantor.switch");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("m", s.mode);
        AWNet.fx(p.getServerWorld(), p.getPos(), "cantor_switch", n);
        sound(p, AWSounds.CANTOR_SWITCH, 1.0f, 0.9f + s.mode * 0.2f);
        sync(p, d);
        aura(p);
        return true;
    }

    /** 선율 오라: 주변 아군에게 약한 버프 (1단계). */
    void aura(ServerPlayerEntity p) {
        St s = st(p);
        for (ServerPlayerEntity a : allies(p, p.getPos(), AURA_R)) {
            StatusEffect e = effectOf(s.mode);
            StatusEffectInstance cur = a.getStatusEffect(e);
            if (cur != null && cur.getAmplifier() > 0 && cur.getDuration() > 40) continue;
            a.addStatusEffect(new StatusEffectInstance(e, 50, 0, true, false, true), p);
        }
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        long now = d.now();
        if (now % 20 == 0) aura(p);
        if (now % 40 == 0) sync(p, d);
        if (((AWPlayer) p).aw$hasFlag(AWPlayer.FLAG_COUNTER) && !d.isCountering()) {
            ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
            if (!ServerActions.hasAction(p, Answer.class)) {
                AWNet.anim(p, "cantor.skill4_fail");
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                AWNet.fx(p.getServerWorld(), p.getPos(), "cantor_ward_fade", n);
                sound(p, AWSounds.VOID_COUNTER_FAIL, 0.7f, 1.2f);
            }
        }
        if (d.hasSuperArmor() && !d.isFrozen()) {
            p.removeStatusEffect(AWEffects.CONCUSSION);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        St s = states.get(p.getUuid());
        int mode = s != null ? s.mode : VALOR;
        states.remove(p.getUuid());
        st(p).mode = mode;
        d.extra = mode;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
    }
}
