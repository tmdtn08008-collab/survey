package com.abilitywar.ability.paladin;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.ability.angel.AngelAbility;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWGameRules;
import com.abilitywar.rpg.RpgManager;
import com.abilitywar.server.AbilityManager;
import com.abilitywar.server.ServerActions;
import com.abilitywar.server.TimedAction;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityGroup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 성기사 — 왕의 퀘스트로 얻는 특별 직업 (전사·탱커·힐러). 성검으로 싸우며 동료를 지킨다. 언데드에게 50% 더 강함.
 * 궁극기 게이지는 주는 피해와 회복량으로 찬다.
 * <pre>
 * 평타 성검 3연격 (마지막: 작은 빛기둥)
 * 1    심판의 일격 : 앞으로 뛰어올라 성검을 내리꽂음 (반지름 3)
 * 2    신성한 방패 : 6칸 안 파티에 보호막 + 나쁜 효과 정화 + 수호
 * 3    정화의 땅   : 6초 동안 반지름 5 성역 (적은 계속 피해, 아군은 회복)
 * 4    빛의 사슬   : 적 하나를 빛의 사슬로 묶어 끌어당김 (2.5초 속박)
 * R    성검강림    : 하늘에서 거대한 성검이 내려와 박힘 (반지름 7, 아군은 크게 회복)
 * T    성스러운 오라 (켜기/끄기): 8칸 안 아군 받는 피해 -12%, 내 공격이 6칸 안 아군을 조금씩 회복
 * </pre>
 */
public class PaladinAbility extends Ability {
    public static final int COLOR = 0xFFE7A0;
    public static final int SKY = 0x9FD8FF;
    public static final int ULT_CINEMATIC = 60;
    public static final int ULT_FREEZE = 28;

    public PaladinAbility() {
        super(AbilityWar.id("paladin"), "paladin", 0xFF000000 | COLOR);
    }

    // ================================================================ 오라 / 성역 (서버 상태)
    static final Set<UUID> AURA = new HashSet<>();

    record Sanctum(UUID owner, ServerWorld world, Vec3d pos, double r, long until, float dmg, float heal) {
    }

    static final List<Sanctum> SANCTA = new ArrayList<>();

    public static boolean auraOn(Entity e) {
        return AURA.contains(e.getUuid());
    }

    /** 맞는 쪽: 오라 안 아군은 피해 -12%. */
    public static float incoming(LivingEntity victim, float amount) {
        if (!(victim instanceof ServerPlayerEntity vp) || AURA.isEmpty()) return amount;
        for (UUID id : AURA) {
            ServerPlayerEntity pal = vp.getServer().getPlayerManager().getPlayer(id);
            if (pal == null || !pal.isAlive() || pal.getServerWorld() != vp.getServerWorld()) continue;
            if ((pal == vp || pal.isTeammate(vp)) && pal.squaredDistanceTo(vp) <= 64) return amount * 0.88f;
        }
        return amount;
    }

    public static void tickWorld(MinecraftServer server) {
        long now = server.getTicks();
        for (Iterator<Sanctum> it = SANCTA.iterator(); it.hasNext(); ) {
            Sanctum s = it.next();
            if (s.until < now) {
                it.remove();
                continue;
            }
            if (now % 10 != 0) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(s.owner);
            if (owner == null) continue;
            for (LivingEntity e : s.world.getEntitiesByClass(LivingEntity.class, new Box(s.pos, s.pos).expand(s.r, 3, s.r), e -> e.isAlive())) {
                if (e.getPos().squaredDistanceTo(s.pos) > s.r * s.r) continue;
                if (AngelAbility.ally(owner, e)) {
                    if (s.heal > 0) heal(owner, e, s.heal);
                } else if (Combat.canTarget(owner, e)) {
                    Combat.damage(e, AWDamage.source(s.world, AWDamage.SKILL, owner), holy(e, s.dmg), AWNet.DMG_SKILL);
                }
            }
        }
    }

    // ================================================================ 공용
    /** 언데드는 50% 더. */
    static float holy(LivingEntity t, float dmg) {
        return t.getGroup() == EntityGroup.UNDEAD ? dmg * 1.5f : dmg;
    }

    static void heal(ServerPlayerEntity c, LivingEntity t, float amount) {
        float a = amount * RpgManager.healPower(c);
        float before = t.getHealth();
        if (before >= t.getMaxHealth()) return;
        t.heal(a);
        float got = t.getHealth() - before;
        if (got <= 0.01f) return;
        AWNet.damageNumber(t, got, AWNet.DMG_HEAL, c);
        RpgManager.onHeal(c, got);
        AbilityManager.data(c).addUlt(got * 3f);
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putFloat("a", got);
        AWNet.fx(c.getServerWorld(), t.getPos(), "angel_heal", n);
    }

    static void pillar(ServerWorld w, Vec3d at, float scale) {
        NbtCompound n = nbt();
        n.putFloat("s", scale);
        AWNet.fx(w, at, "paladin_pillar", n);
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 12 : 8;
    }

    @Override
    public void onDealtDamage(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 1.5f);
    }

    @Override
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 0.8f);
    }

    // ================================================================ 평타: 성검 3연격
    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        boolean fin = combo == 2;
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.1f + combo * 0.1f);
        NbtCompound sl = nbt();
        sl.putInt("c", p.getId());
        sl.putInt("k", combo);
        sl.putInt("col", COLOR);
        sl.putBoolean("full", false);
        AWNet.fx(w, p.getPos(), "basic_slash", sl);
        Vec3d look = Combat.horizontal(p.getRotationVec(1f));
        boolean hit = false;
        for (LivingEntity t : Combat.findInCone(p, p.getEyePos(), p.getRotationVec(1f), 3.6, fin ? 55 : 45)) {
            Combat.damage(t, AWDamage.source(w, AWDamage.PUNCH, p), holy(t, fin ? 4.5f : 2.5f), fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
            Combat.setVelocity(t, t.getVelocity().multiply(0.3).add(look.multiply(fin ? 0.6 : 0.25)).add(0, fin ? 0.25 : 0.05, 0));
            impact(w, chest(t), fin ? "heavy" : "slash", fin ? 1f : 0.6f, COLOR);
            Combat.hitFlinch(t);
            if (fin) {
                pillar(w, t.getPos(), 0.8f);
                Combat.damage(t, AWDamage.source(w, AWDamage.SKILL, p), holy(t, 1.5f), AWNet.DMG_SKILL);
            }
            hit = true;
        }
        // 오라가 켜져 있으면 맞힐 때마다 주변 아군이 조금씩 회복
        if (hit && auraOn(p)) for (ServerPlayerEntity a : AngelAbility.party(p, p.getPos(), 6)) heal(p, a, 0.6f);
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> judgment(p, d);
            case SKILL2 -> sacredShield(p, d);
            case SKILL3 -> consecrate(p, d);
            case SKILL4 -> chains(p, d);
            case ULTIMATE -> holySword(p, d);
            case PASSIVE -> aura(p, d);
            case DASH -> false;
        };
    }

    // ================================================================ 1. 심판의 일격
    boolean judgment(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, 160);
        d.setBusy(16);
        d.superArmorUntil = d.now() + 16;
        AWNet.anim(p, "paladin.skill1");
        sound(p, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.8f);
        Vec3d dir = Combat.horizontal(p.getRotationVec(1f)).normalize();
        ServerActions.start(new TimedAction(p) {
            boolean landed;

            @Override
            protected boolean step(int tt) {
                if (tt == 2) Combat.setVelocitySelf(p, dir.multiply(0.75).add(0, 0.6, 0));
                if (tt > 5 && !landed && (p.isOnGround() || tt >= 14)) {
                    landed = true;
                    ServerWorld w = world();
                    Vec3d at = p.getPos().add(dir.multiply(1.2));
                    sound(w, at, SoundEvents.ITEM_TRIDENT_THUNDER, 0.8f, 1.4f);
                    sound(w, at, SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", 3.2f);
                    AWNet.fx(w, at, "paladin_judgment", n);
                    pillar(w, at, 1.4f);
                    crater(w, at, 2.6f, COLOR);
                    for (LivingEntity t : Combat.findInRadius(p, at.add(0, 0.6, 0), 3.2)) {
                        if (!Combat.canTarget(p, t)) continue;
                        Combat.damage(t, AWDamage.source(w, AWDamage.SKILL, p), holy(t, 6f), AWNet.DMG_SKILL);
                        Combat.setVelocity(t, Combat.horizontal(t.getPos().subtract(at)).normalize().multiply(0.4).add(0, 0.35, 0));
                        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1), p);
                    }
                    AWNet.shakeAround(w, at, 0.4f, 8, 12);
                }
                if (tt >= 7 && !landed) Combat.setVelocitySelf(p, new Vec3d(p.getVelocity().x * 0.6, -1.2, p.getVelocity().z * 0.6));
                return landed && tt >= 16 || tt >= 26;
            }
        });
        return true;
    }

    // ================================================================ 2. 신성한 방패
    boolean sacredShield(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 320);
        d.setBusy(10);
        AWNet.anim(p, "paladin.skill2");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 5) {
                    ServerWorld w = world();
                    sound(p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
                    sound(p, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, 1f, 1f);
                    int n = 0;
                    for (ServerPlayerEntity a : AngelAbility.party(p, p.getPos(), 6)) {
                        AngelAbility.cleanse(a);
                        a.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 160, 1, false, false, true), p);
                        a.addStatusEffect(new StatusEffectInstance(AWEffects.AEGIS, 160, 0, false, false, true), p);
                        NbtCompound b = nbt();
                        b.putInt("e", a.getId());
                        b.putInt("d", 160);
                        b.putInt("col", COLOR);
                        AWNet.fx(w, a.getPos(), "basic_bubble", b);
                        NbtCompound bf = nbt();
                        bf.putInt("e", a.getId());
                        bf.putBoolean("big", false);
                        AWNet.fx(w, a.getPos(), "angel_buffed", bf);
                        n++;
                    }
                    RpgManager.onBuff(p, n);
                    NbtCompound r = nbt();
                    r.putInt("c", p.getId());
                    r.putFloat("r", 6);
                    r.putInt("col", COLOR);
                    r.putInt("k", 3);
                    AWNet.fx(w, p.getPos(), "basic_ring", r);
                }
                return tt >= 8;
            }
        });
        return true;
    }

    // ================================================================ 3. 정화의 땅
    boolean consecrate(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, 360);
        d.setBusy(12);
        AWNet.anim(p, "paladin.skill3");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 6) {
                    ServerWorld w = world();
                    Vec3d at = p.getPos();
                    sound(w, at, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1f, 1.3f);
                    sound(w, at, SoundEvents.BLOCK_BELL_USE, 1f, 1.6f);
                    SANCTA.add(new Sanctum(p.getUuid(), w, at, 5, w.getServer().getTicks() + 120, 1.5f, 1f));
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", 5);
                    n.putInt("d", 120);
                    AWNet.fx(w, at, "paladin_consecrate", n);
                    pillar(w, at, 1.2f);
                }
                return tt >= 9;
            }
        });
        return true;
    }

    // ================================================================ 4. 빛의 사슬
    boolean chains(ServerPlayerEntity p, AbilityData d) {
        LivingEntity tg = Combat.findTarget(p, 14, 12);
        if (tg == null) return false;
        d.startCooldown(SkillSlot.SKILL4, 240);
        d.setBusy(10);
        AWNet.anim(p, "paladin.skill4");
        sound(p, SoundEvents.BLOCK_CHAIN_PLACE, 1.4f, 0.7f);
        sound(p, SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1f, 1.5f);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", tg.getId());
        n.putInt("d", 50);
        AWNet.fx(p.getServerWorld(), tg.getPos(), "paladin_chains", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (!tg.isAlive()) return true;
                ServerWorld w = world();
                if (tt == 3) {
                    Combat.damage(tg, AWDamage.source(w, AWDamage.SKILL, p), holy(tg, 4f), AWNet.DMG_SKILL);
                    Combat.stun(tg, 50, p);
                    tg.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 9, false, false), p);
                    sound(tg, SoundEvents.BLOCK_CHAIN_HIT, 1.4f, 0.6f);
                }
                // 3칸 끌어당김 (3~9틱)
                if (tt >= 3 && tt <= 9) {
                    Vec3d to = p.getPos().subtract(tg.getPos());
                    if (to.length() > 2.5) Combat.setVelocity(tg, Combat.horizontal(to).normalize().multiply(0.32).add(0, 0.05, 0));
                }
                return tt >= 10;
            }
        });
        return true;
    }

    // ================================================================ R. 성검강림
    boolean holySword(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.frozenUntil = now + ULT_FREEZE;
        d.invulnUntil = now + ULT_FREEZE;
        d.setBusy(ULT_FREEZE + 4);
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Vec3d fwd = Combat.horizontal(p.getRotationVec(1f)).normalize();
        Vec3d target = Combat.groundBelow(w, p.getPos().add(fwd.multiply(6)).add(0, 3, 0), 8, p);
        if (target == null) target = p.getPos().add(fwd.multiply(6));
        final Vec3d at = target;
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "paladin_ult", ULT_CINEMATIC, at);
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
        for (Entity e : w.getOtherEntities(p, new Box(p.getBlockPos()).expand(radius), e -> e instanceof MobEntity)) Combat.stun((LivingEntity) e, ULT_FREEZE, p);
        AWNet.anim(p, "paladin.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putDouble("tx", at.x);
        n.putDouble("ty", at.y);
        n.putDouble("tz", at.z);
        n.putInt("d", 200);
        AWNet.fx(w, p.getPos(), "paladin_ult", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                ServerWorld ww = world();
                if (tt == 3) sound(ww, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 3f, 0.6f);
                if (tt == 14) sound(ww, at, SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 3f, 0.5f);
                if (tt == ULT_FREEZE - 4) {
                    sound(ww, at, SoundEvents.ITEM_TRIDENT_THUNDER, 3f, 0.7f);
                    sound(ww, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
                    crater(ww, at, 6f, COLOR);
                    for (LivingEntity t : ww.getEntitiesByClass(LivingEntity.class, new Box(at, at).expand(7), e -> e != p && e.isAlive())) {
                        if (t.getPos().squaredDistanceTo(at) > 49) continue;
                        if (AngelAbility.ally(p, t)) continue;
                        if (!Combat.canTarget(p, t)) continue;
                        Combat.damage(t, AWDamage.source(ww, AWDamage.ULTIMATE, p), holy(t, 18f), AWNet.DMG_ULT);
                        Combat.stun(t, 40, p);
                        Combat.setVelocity(t, Combat.horizontal(t.getPos().subtract(at)).normalize().multiply(0.7).add(0, 0.6, 0));
                    }
                    for (ServerPlayerEntity a : AngelAbility.party(p, at, 10)) {
                        heal(p, a, a.getMaxHealth() * 0.4f);
                        a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 160, 1, false, false, true), p);
                    }
                    // 박힌 성검 = 8초 성역 (아군 회복, 적 피해)
                    SANCTA.add(new Sanctum(p.getUuid(), ww, at, 6, ww.getServer().getTicks() + 160, 2f, 1.5f));
                    AWNet.shakeAround(ww, at, 0.7f, 14, 18);
                }
                return tt >= ULT_FREEZE;
            }
        });
        return true;
    }

    // ================================================================ T. 성스러운 오라
    boolean aura(ServerPlayerEntity p, AbilityData d) {
        boolean on = !auraOn(p);
        setAura(p, on);
        d.startCooldown(SkillSlot.PASSIVE, 20);
        AWNet.anim(p, "paladin.passive");
        sound(p, on ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        return true;
    }

    static void setAura(ServerPlayerEntity p, boolean on) {
        if (on) AURA.add(p.getUuid());
        else AURA.remove(p.getUuid());
        AbilityData d = AbilityManager.data(p);
        d.extra = on ? 1 : 0;
        d.dirty = true;
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putBoolean("on", on);
        AWNet.fx(p.getServerWorld(), p.getPos(), "paladin_aura", n);
    }

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        if (auraOn(p) && d.extra != 1) setAura(p, true);
        if (auraOn(p) && p.age % 40 == 0) {
            NbtCompound n = nbt();
            n.putInt("c", p.getId());
            n.putBoolean("on", true);
            n.putBoolean("q", true);
            AWNet.fx(p.getServerWorld(), p.getPos(), "paladin_aura", n);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        if (auraOn(p)) setAura(p, false);
        d.extra = 0;
    }
}
