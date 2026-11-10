package com.abilitywar.ability.warlock;

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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 흑마법사 — 하수인을 부리는 강령술사 (RPG 직업).
 * <pre>
 * 1  해골 소환   : 나만의 해골 전사. 처음엔 맨몸이지만 G(흑마법서)에서 갑옷·무기를 입히면 계속 유지됨 (쓰면 내구도가 닳음).
 *                  내가 때린 적을 공격. 이미 나와 있으면 내 옆으로 불러옴. 죽으면 15초 뒤 다시 소환 가능.
 * 2  사령 소환   : 흑마법서의 영혼으로 몹을 되살림 (체력·피해 절반, 그 몹의 능력은 그대로).
 *                  죽여서 얻은 영혼 → 되살린 몹이 죽으면 영혼도 사라짐 / 궁극기로 포획한 영혼 → 죽으면 30분 쿨타임.
 * 3  흑마법 광선 : 정면으로 광선 (6번 다 맞으면 11 피해 + 2초 쓰러짐)
 * 4  속박        : 조준한 적을 어둠의 사슬로 5초 묶음
 * R  영혼 강탈   : 체력 50% 이하(보스 20%)인 적을 즉시 포획 (플레이어는 즉사)
 * T  영혼 수집   : 주변에 떨어진 영혼을 모음 (흑마법사 또는 같은 팀이 몹을 죽이면 10초 동안 떨어짐, 플레이어는 없음)
 * G  흑마법서    : 해골 장비, 영혼 목록 (소환할 영혼 선택 / 하수인 해제 / 영혼 버리기)
 * </pre>
 */
public class WarlockAbility extends Ability {
    public static final int COLOR = 0x48E8A0;
    public static final int CD_S1 = 40, CD_S1_DEAD = 300;
    public static final int CD_S2 = 60;
    public static final int CD_S3 = 200;
    public static final int CD_S4 = 320, CD_S4_MISS = 60;
    public static final int CD_T = 20;
    public static final int ULT_CINEMATIC = 50;
    public static final int ULT_FREEZE = 20;
    public static final int BEAM_HITS = 6;
    public static final float BEAM_TOTAL = 11f;

    public WarlockAbility() {
        super(AbilityWar.id("warlock"), "warlock", 0xFF000000 | COLOR);
    }

    static Grimoire g(ServerPlayerEntity p) {
        return AbilityManager.data(p).grimoire;
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 14 : 8;
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> skeleton(p, d);
            case SKILL2 -> raise(p, d);
            case SKILL3 -> beam(p, d);
            case SKILL4 -> bind(p, d);
            case ULTIMATE -> harvest(p, d);
            case PASSIVE -> collect(p, d);
            case DASH -> false;
        };
    }

    static Vec3d spotBeside(ServerPlayerEntity p, double side) {
        Vec3d f = Combat.horizontal(p.getRotationVec(1f));
        Vec3d r = new Vec3d(-f.z, 0, f.x);
        Vec3d want = p.getPos().add(f.multiply(1.6)).add(r.multiply(side));
        Vec3d safe = Combat.safeSpot(p, want);
        return safe != null ? safe : p.getPos();
    }

    static void deny(ServerPlayerEntity p, String key, Object... args) {
        AbilityManager.deny(p);
        p.sendMessage(Text.translatable(key, args), true);
    }

    // =================================================================== 평타: 흑마법 구체

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        LivingEntity tgt = target != null ? target : Combat.findTarget(p, 15, 8);
        sound(p, AWSounds.WARLOCK_BOLT, 0.8f, 1.0f + combo * 0.15f);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", tgt != null ? tgt.getId() : -1);
        Vec3d end = p.getEyePos().add(p.getRotationVec(1f).multiply(14));
        n.putDouble("tx", end.x);
        n.putDouble("ty", end.y);
        n.putDouble("tz", end.z);
        n.putInt("n", combo == 2 ? 3 : 1);
        AWNet.fx(w, p.getPos(), "warlock_bolt", n);
        if (tgt == null) return;
        Necromancy.onHit(p, tgt);
        int travel = Math.max(2, Math.min(5, (int) (tgt.distanceTo(p) / 3.5)));
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                if (!tgt.isAlive()) return true;
                boolean fin = combo == 2;
                Combat.damage(tgt, AWDamage.source(w, AWDamage.PUNCH, p), fin ? 3.5f : 2f, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
                impact(w, chest(tgt), fin ? "heavy" : "punch", fin ? 0.9f : 0.55f, COLOR);
                Combat.hitFlinch(tgt);
                return true;
            }
        });
    }

    // =================================================================== 1. 해골 소환

    private boolean skeleton(ServerPlayerEntity p, AbilityData d) {
        Grimoire g = d.grimoire;
        MobEntity sk = Necromancy.skeleton(p, g);
        ServerWorld w = p.getServerWorld();
        if (sk != null) {
            // 이미 나와 있음: 내 옆으로 불러옴
            Vec3d at = spotBeside(p, 1.2);
            NbtCompound n = nbt();
            n.putInt("e", sk.getId());
            AWNet.fx(w, sk.getPos(), "warlock_unsummon", n);
            sk.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), 0);
            sk.setTarget(null);
            AWNet.fx(w, at, "warlock_summon", nbt());
            Necromancy.playSummon(w, at);
            AWNet.anim(p, "warlock.collect");
            d.startCooldown(SkillSlot.SKILL1, CD_S1);
            return true;
        }
        d.startCooldown(SkillSlot.SKILL1, CD_S1);
        d.setBusy(12);
        AWNet.anim(p, "warlock.skill1");
        Vec3d at = spotBeside(p, 1.2);
        NbtCompound n = nbt();
        n.putBoolean("big", false);
        AWNet.fx(w, at, "warlock_summon", n);
        sound(p, AWSounds.WARLOCK_CHANT, 1.0f, 1.0f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 8) {
                    MobEntity s = Necromancy.summonSkeleton(p, g, at);
                    if (s != null) {
                        Necromancy.playSummon(w, at);
                        NbtCompound m = nbt();
                        m.putInt("e", s.getId());
                        AWNet.fx(w, at, "warlock_minion", m);
                    }
                    syncSouls(p);
                }
                return tt >= 8;
            }
        });
        return true;
    }

    static void onSkeletonDied(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, CD_S1_DEAD);
        d.dirty = true;
        p.sendMessage(Text.translatable("abilitywar.warlock.skeleton_died"), true);
    }

    // =================================================================== 2. 사령 소환

    private boolean raise(ServerPlayerEntity p, AbilityData d) {
        Grimoire g = d.grimoire;
        long now = p.getWorld().getTime();
        if (g.souls.isEmpty()) {
            deny(p, "abilitywar.warlock.no_souls");
            return false;
        }
        if (g.summonedCount() >= Necromancy.MAX_RAISED) {
            deny(p, "abilitywar.warlock.too_many", Necromancy.MAX_RAISED);
            return false;
        }
        Grimoire.Soul s = g.soul(g.selected);
        if (s == null || !s.ready(now)) {
            s = null;
            for (int i = g.souls.size() - 1; i >= 0; i--) {
                if (g.souls.get(i).ready(now)) {
                    s = g.souls.get(i);
                    break;
                }
            }
        }
        if (s == null) {
            deny(p, "abilitywar.warlock.no_ready");
            return false;
        }
        final Grimoire.Soul soul = s;
        soul.summoned = p.getUuid(); // 소환 중 표시 (실제 엔티티로 곧 바뀜)
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(14);
        AWNet.anim(p, "warlock.skill2");
        ServerWorld w = p.getServerWorld();
        Vec3d at = spotBeside(p, -1.4);
        NbtCompound n = nbt();
        n.putBoolean("big", true);
        AWNet.fx(w, at, "warlock_summon", n);
        sound(p, AWSounds.WARLOCK_CHANT, 1.2f, 0.8f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 10) {
                    soul.summoned = null;
                    MobEntity m = Necromancy.raise(p, soul, at);
                    if (m != null) {
                        Necromancy.playSummon(w, at);
                        NbtCompound k = nbt();
                        k.putInt("e", m.getId());
                        AWNet.fx(w, at, "warlock_minion", k);
                        p.sendMessage(Text.translatable("abilitywar.warlock.raised", m.getType().getName()), true);
                    } else {
                        g.souls.remove(soul);
                    }
                    syncSouls(p);
                }
                return tt >= 10;
            }

            @Override
            protected void cleanup() {
                if (soul.summoned != null && soul.summoned.equals(p.getUuid())) soul.summoned = null;
            }
        });
        return true;
    }

    // =================================================================== 3. 흑마법 광선

    private boolean beam(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        d.setBusy(16);
        AWNet.anim(p, "warlock.skill3");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 14);
        AWNet.fx(p.getServerWorld(), p.getPos(), "warlock_beam", n);
        sound(p, AWSounds.WARLOCK_BEAM, 1.3f, 1.0f);
        ServerActions.start(new TimedAction(p) {
            final Map<LivingEntity, Integer> hits = new HashMap<>();

            @Override
            protected boolean step(int tt) {
                ServerWorld w = world();
                Combat.setVelocitySelf(p, new Vec3d(0, Math.min(0, p.getVelocity().y), 0));
                if (tt >= 2 && tt <= 12 && tt % 2 == 0) {
                    Vec3d eye = p.getEyePos().subtract(0, 0.2, 0);
                    Vec3d dir = p.getRotationVec(1f);
                    for (LivingEntity e : inBeam(p, eye, dir, 16, 0.9)) {
                        int k = hits.merge(e, 1, Integer::sum);
                        Combat.damage(e, AWDamage.source(w, AWDamage.SKILL, p), BEAM_TOTAL / BEAM_HITS, k == BEAM_HITS ? AWNet.DMG_CRIT : AWNet.DMG_SKILL);
                        Necromancy.onHit(p, e);
                        if (k == BEAM_HITS) {
                            // 다 맞음: 2초 쓰러짐
                            Combat.stun(e, 40, p);
                            Combat.setVelocity(e, Combat.horizontal(e.getPos().subtract(p.getPos())).multiply(0.4).add(0, 0.2, 0));
                            if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.slammed");
                            AWNet.damageNumber(p, 0, AWNet.DMG_TEXT_CHAIN, e);
                        }
                        d.addUlt(1.5f);
                    }
                }
                return tt >= 14;
            }
        });
        return true;
    }

    static List<LivingEntity> inBeam(ServerPlayerEntity p, Vec3d from, Vec3d dir, double range, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Vec3d to = from.add(dir.multiply(range));
        // 벽에서 끊김
        var hit = p.getWorld().raycast(new net.minecraft.world.RaycastContext(from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, p));
        double len = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? range : hit.getPos().distanceTo(from);
        Box box = new Box(from, from.add(dir.multiply(len))).expand(radius + 1);
        for (Entity e : p.getWorld().getOtherEntities(p, box, e -> Combat.canTarget(p, e))) {
            LivingEntity le = (LivingEntity) e;
            Vec3d c = le.getBoundingBox().getCenter();
            double t = c.subtract(from).dotProduct(dir);
            if (t < 0 || t > len + 0.5) continue;
            Vec3d closest = from.add(dir.multiply(t));
            double r = radius + le.getWidth() * 0.5;
            if (closest.squaredDistanceTo(c) <= r * r + le.getHeight() * le.getHeight() * 0.25) out.add(le);
        }
        return out;
    }

    // =================================================================== 4. 속박

    private boolean bind(ServerPlayerEntity p, AbilityData d) {
        LivingEntity t = Combat.findTarget(p, 16, 10);
        ServerWorld w = p.getServerWorld();
        AWNet.anim(p, "warlock.skill4");
        if (t == null) {
            d.startCooldown(SkillSlot.SKILL4, CD_S4_MISS);
            sound(p, AWSounds.WARLOCK_CHAIN, 0.8f, 1.4f);
            return true;
        }
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        d.setBusy(8);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", t.getId());
        n.putInt("d", 100);
        AWNet.fx(w, t.getPos(), "warlock_bind", n);
        sound(t, AWSounds.WARLOCK_CHAIN, 1.4f, 0.8f);
        Combat.stun(t, 100, p);
        Combat.setVelocity(t, Vec3d.ZERO);
        if (t instanceof ServerPlayerEntity) AWNet.anim(t, "common.held");
        Necromancy.onHit(p, t);
        d.addUlt(4);
        return true;
    }

    // =================================================================== R. 영혼 강탈

    @Override
    public boolean canUseUltimate(ServerPlayerEntity player, AbilityData data) {
        return super.canUseUltimate(player, data);
    }

    private boolean harvest(ServerPlayerEntity p, AbilityData d) {
        // 시선 원뿔 안에서 조건(체력 50%, 보스 20% 이하)을 만족하는 대상 중 조준선에 가장 가까운 것
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        LivingEntity t = null;
        double best = Double.MAX_VALUE;
        boolean any = false;
        for (LivingEntity e : Combat.findInCone(p, eye, look, 20, 15)) {
            if (!Combat.hasLineOfSight(p, e)) continue;
            any = true;
            float limit = Necromancy.isBoss(e) ? 0.2f : 0.5f;
            if (e.getHealth() > e.getMaxHealth() * limit) continue;
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            double score = Math.acos(Math.max(-1, Math.min(1, to.normalize().dotProduct(look)))) * 6 + to.length() * 0.05;
            if (score < best) {
                best = score;
                t = e;
            }
        }
        if (t == null) {
            if (any) deny(p, "abilitywar.warlock.ult_too_healthy", 50);
            else deny(p, "abilitywar.warlock.ult_no_target");
            return false;
        }
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.setBusy(ULT_CINEMATIC);
        d.superArmorUntil = now + ULT_CINEMATIC;
        d.invulnUntil = now + ULT_FREEZE;
        d.frozenUntil = now + ULT_FREEZE;
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Combat.stun(p, ULT_CINEMATIC - 6, p);
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "warlock_ult", ULT_CINEMATIC, t.getPos());
            if (o != p && o != t) {
                AbilityData od = AbilityManager.data(o);
                od.setNow(now);
                od.frozenUntil = now + ULT_FREEZE;
                od.invulnUntil = now + ULT_FREEZE;
                Combat.stun(o, ULT_FREEZE, p);
                Combat.setVelocity(o, Vec3d.ZERO);
                AbilityManager.setBlocking(o, false);
            }
        }
        Combat.stun(t, ULT_CINEMATIC, p);
        if (t instanceof ServerPlayerEntity tp) {
            AbilityManager.setBlocking(tp, false);
            AWNet.anim(tp, "common.captured");
        }
        AWNet.anim(p, "warlock.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", t.getId());
        AWNet.fx(w, t.getPos(), "warlock_ult", n);
        ServerActions.start(new Harvest(p, d, t));
        return true;
    }

    class Harvest extends TimedAction {
        final ServerPlayerEntity c;
        final AbilityData d;
        final LivingEntity t;

        Harvest(ServerPlayerEntity c, AbilityData d, LivingEntity t) {
            super(c);
            this.c = c;
            this.d = d;
            this.t = t;
        }

        @Override
        protected boolean step(int tt) {
            ServerWorld w = world();
            if (tt == 1) sound(w, t.getPos(), AWSounds.WARLOCK_ULT, 4f, 0.8f);
            if (tt >= 2 && tt < 30 && t.isAlive()) {
                // 어둠의 손이 붙잡아 들어 올림
                Combat.hold(t, true);
                Combat.setVelocity(t, new Vec3d(0, tt < 20 ? 0.09 : 0, 0));
            }
            if (tt == 26) {
                sound(w, t.getPos(), AWSounds.WARLOCK_RIP, 4f, 0.9f);
                NbtCompound n = nbt();
                n.putInt("c", c.getId());
                n.putInt("e", t.getId());
                AWNet.fx(w, t.getPos(), "warlock_rip", n);
            }
            if (tt == 32 && t.isAlive() && !t.isRemoved()) {
                Combat.hold(t, false);
                Combat.unstun(t);
                Grimoire g = d.grimoire;
                if (t instanceof PlayerEntity) {
                    // 플레이어는 즉사
                    Combat.damage(t, AWDamage.source(w, AWDamage.ULTIMATE, c), t.getHealth() + t.getAbsorptionAmount() + 100f, AWNet.DMG_ULT);
                    AWNet.shakeAround(w, t.getPos(), 0.8f, 12, 20);
                } else if (t instanceof MobEntity && !(t instanceof EnderDragonEntity) && g.souls.size() < Grimoire.MAX_SOULS && !Necromancy.isMinion(t)) {
                    // 포획: 영혼을 뽑아 흑마법서에
                    Grimoire.Soul s = g.add(EntityType.getId(t.getType()).toString(), Necromancy.soulData(t), true);
                    NbtCompound n = nbt();
                    n.putInt("e", t.getId());
                    n.putInt("c", c.getId());
                    AWNet.fx(w, t.getPos(), "warlock_captured", n);
                    c.sendMessage(Text.translatable("abilitywar.warlock.captured", t.getType().getName()), true);
                    t.discard();
                    syncSouls(c);
                } else {
                    if (g.souls.size() >= Grimoire.MAX_SOULS) c.sendMessage(Text.translatable("abilitywar.warlock.grimoire_full"), true);
                    Combat.damage(t, AWDamage.source(w, AWDamage.ULTIMATE, c), t.getHealth() + 100f, AWNet.DMG_ULT);
                }
            }
            return tt >= ULT_CINEMATIC;
        }

        @Override
        protected void cleanup() {
            Combat.hold(t, false);
        }
    }

    // =================================================================== T. 영혼 수집

    private boolean collect(ServerPlayerEntity p, AbilityData d) {
        int got = Necromancy.collect(p, d.grimoire);
        if (got == -1) {
            deny(p, "abilitywar.warlock.grimoire_full");
            return false;
        }
        if (got == 0) {
            deny(p, "abilitywar.warlock.no_orbs");
            return false;
        }
        d.startCooldown(SkillSlot.PASSIVE, CD_T);
        AWNet.anim(p, "warlock.collect");
        sound(p, AWSounds.WARLOCK_SOUL, 1.0f, 1.0f + Math.min(5, got) * 0.05f);
        p.sendMessage(Text.translatable("abilitywar.warlock.collected", got, d.grimoire.souls.size(), Grimoire.MAX_SOULS), true);
        syncSouls(p);
        return true;
    }

    // =================================================================== 흑마법서 (G)

    public static void openGrimoire(ServerPlayerEntity p) {
        if (!"warlock".equals(((AWPlayer) p).aw$getAbilityKey())) return;
        Grimoire g = g(p);
        MobEntity sk = Necromancy.skeleton(p, g);
        if (sk != null) Necromancy.readGear(sk, g);
        p.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, inv, pl) -> new GrimoireScreenHandler(syncId, inv, g.gear, () -> {
            MobEntity s = Necromancy.skeleton(p, g);
            if (s != null) Necromancy.applyGear(s, g);
        }), Text.translatable("abilitywar.warlock.grimoire")));
        syncSouls(p);
    }

    /** 0 선택, 1 하수인 해제, 2 영혼 버리기, 3 모두 해제, 4 해골 해제. */
    public static void soulAction(ServerPlayerEntity p, int action, int id) {
        Grimoire g = g(p);
        switch (action) {
            case 0 -> g.selected = g.selected == id ? -1 : id;
            case 1 -> {
                Grimoire.Soul s = g.soul(id);
                if (s != null && s.summoned != null) Necromancy.dismiss(p, g, s.summoned);
            }
            case 2 -> {
                Grimoire.Soul s = g.soul(id);
                if (s != null) {
                    if (s.summoned != null) Necromancy.dismiss(p, g, s.summoned);
                    g.souls.remove(s);
                    if (g.selected == id) g.selected = -1;
                }
            }
            case 3 -> {
                for (MobEntity m : Necromancy.minionsOf(p.getUuid())) if (!Necromancy.isSkeleton(m)) Necromancy.dismiss(p, g, m.getUuid());
            }
            case 4 -> {
                if (g.skeleton != null) Necromancy.dismiss(p, g, g.skeleton);
            }
            default -> {
            }
        }
        syncSouls(p);
    }

    public static void syncSouls(ServerPlayerEntity p) {
        Grimoire g = g(p);
        long now = p.getWorld().getTime();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(g.souls.size());
        for (Grimoire.Soul s : g.souls) {
            buf.writeVarInt(s.id);
            buf.writeString(s.type);
            buf.writeBoolean(s.captured);
            buf.writeByte(s.summoned != null ? 1 : now < s.cooldownUntil ? 2 : 0);
            buf.writeVarInt((int) Math.max(0, s.cooldownUntil - now));
        }
        buf.writeVarInt(g.selected);
        buf.writeVarInt(Grimoire.MAX_SOULS);
        buf.writeVarInt(Necromancy.MAX_RAISED);
        buf.writeBoolean(g.skeleton != null);
        if (ServerPlayNetworking.canSend(p, AWNet.S2C_SOULS)) ServerPlayNetworking.send(p, AWNet.S2C_SOULS, buf);
        updateExtra(p, AbilityManager.data(p));
    }

    static void updateExtra(ServerPlayerEntity p, AbilityData d) {
        Grimoire g = d.grimoire;
        int ex = Math.min(31, g.souls.size()) | Math.min(7, g.summonedCount()) << 5 | (g.skeleton != null ? 1 : 0) << 8 | (Necromancy.soulsNearby(p) ? 1 : 0) << 9;
        if (d.extra != ex) {
            d.extra = ex;
            d.dirty = true;
        }
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        if (d.now() % 5 == 0) updateExtra(p, d);
        if (d.hasSuperArmor() && !d.isFrozen()) p.removeStatusEffect(AWEffects.CONCUSSION);
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        Necromancy.dismissAll(p, d.grimoire);
        d.extra = 0;
    }
}
