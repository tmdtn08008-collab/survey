package com.abilitywar.ability.voidab;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.entity.VoidSingularityEntity;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEntities;
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
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 공허 (Void) 능력.
 * <pre>
 * 1  공허의 손아귀  : 끌어당긴 뒤 치명타 한 방 (둔화 1초 + 뇌진탕)
 * 2  공허 파동      : 큰 피해 + 멀리 날려버림 (뇌진탕)
 * 3  공허 난무      : 순간이동 → 4연타 → 바닥에 내리꽂기 (공중 3칸 이상이면 즉시 내리꽂기, 같은 피해)
 * 4  공허 반격      : 반격 자세. 공격(궁극기 제외)을 받으면 무효 → 얼굴 강타 → 내리찍기
 * R  사건의 지평선  : 블랙홀 소환. 붙잡히면 최대 체력 75% 피해, 주변 흡입. 10블록 내 연출
 * T  그림자 이동    : 그림자 속으로 들어가 원하는 그림자(어두운 곳/엔티티의 그림자)에서 나온다
 * </pre>
 */
public class VoidAbility extends Ability {
    public static final int CD_S1 = 180;
    public static final int CD_S1_MISS = 70;
    public static final int CD_S2 = 240;
    public static final int CD_S3 = 320;
    public static final int CD_S3_MISS = 40;
    public static final int CD_S4 = 200;
    public static final int CD_S4_SUCCESS = 160;
    public static final int CD_PASSIVE = 240;

    public static final int ULT_CINEMATIC = 70;
    public static final int ULT_DURATION = 230;

    private static final UUID SHADOW_SPEED_ID = UUID.fromString("b1d7c3a2-7c1e-4b8f-9a55-2a0f7d4e9c31");

    public VoidAbility() {
        super(AbilityWar.id("void"), "void", 0xFF9B5CFF);
    }

    // =================================================================== dispatch

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> grasp(p, d);
            case SKILL2 -> burst(p, d);
            case SKILL3 -> barrage(p, d);
            case SKILL4 -> counterStance(p, d);
            case ULTIMATE -> ultimate(p, d);
            case PASSIVE -> d.shadowForm ? shadowExit(p, d, extra) : shadowEnter(p, d);
            case DASH -> false; // 공통 대시는 AbilityManager 가 처리
        };
    }

    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 13 : 7;
    }

    // =================================================================== helpers

    static void sound(Entity at, SoundEvent s, float vol, float pitch) {
        at.getWorld().playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundCategory.PLAYERS, vol, pitch);
    }

    static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    static Vec3d chest(Entity e) {
        return e.getPos().add(0, e.getHeight() * 0.62, 0);
    }

    static NbtCompound nbt() {
        return new NbtCompound();
    }

    static void impact(ServerWorld w, Vec3d pos, String kind, float scale) {
        NbtCompound n = nbt();
        n.putString("k", kind);
        n.putFloat("s", scale);
        AWNet.fx(w, pos, "impact", n);
    }

    static void crater(ServerWorld w, Vec3d pos, float radius) {
        NbtCompound n = nbt();
        n.putFloat("r", radius);
        AWNet.fx(w, pos, "crater", n);
    }

    static void rift(ServerWorld w, Vec3d pos, float yaw, float scale) {
        NbtCompound n = nbt();
        n.putFloat("yaw", yaw);
        n.putFloat("s", scale);
        AWNet.fx(w, pos, "rift", n);
    }

    // =================================================================== basic attack

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        // 본인은 클라이언트에서 미리 재생하므로 다른 사람에게만
        animOthers(p, "void.attack" + (combo + 1));
        sound(p, AWSounds.VOID_SWING, 0.6f, 0.9f + combo * 0.12f + p.getRandom().nextFloat() * 0.1f);
        if (target == null) return;
        boolean finisher = combo == 2;
        float dmg = finisher ? 4.5f : 2.5f;
        Combat.damage(target, AWDamage.source(w, AWDamage.PUNCH, p), dmg, finisher ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
        Vec3d dir = Combat.horizontal(target.getPos().subtract(p.getPos()));
        if (finisher) {
            Combat.setVelocity(target, dir.multiply(0.75).add(0, 0.28, 0));
            AWNet.shake(target, 0.35f, 6);
            AWNet.shake(p, 0.15f, 4);
            sound(target, AWSounds.VOID_PUNCH_HEAVY, 0.9f, 1.0f);
        } else {
            Combat.setVelocity(target, target.getVelocity().multiply(0.3).add(dir.multiply(0.22)).add(0, 0.05, 0));
            sound(target, AWSounds.VOID_PUNCH, 0.8f, 0.9f + p.getRandom().nextFloat() * 0.3f);
        }
        impact(w, chest(target).add(dir.multiply(-target.getWidth() * 0.5)), finisher ? "heavy" : "punch", finisher ? 1.2f : 0.7f);
        Combat.hitFlinch(target);
    }

    static void animOthers(ServerPlayerEntity p, String anim) {
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeVarInt(p.getId());
        buf.writeString(anim);
        buf.writeFloat(1f);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) {
            if (o != p && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(o, AWNet.S2C_ANIM)) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(o, AWNet.S2C_ANIM, net.fabricmc.fabric.api.networking.v1.PacketByteBufs.copy(buf));
            }
        }
        buf.release();
    }

    // =================================================================== 1. 공허의 손아귀

    private boolean grasp(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 18, 9);
        AWNet.anim(p, "void.skill1");
        sound(p, AWSounds.VOID_GRAB, 1.0f, 1.0f);
        d.setBusy(target != null ? 24 : 12);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        if (target != null) {
            n.putInt("t", target.getId());
            n.putInt("d", 30);
            d.startCooldown(SkillSlot.SKILL1, CD_S1);
            AWNet.fx(p.getServerWorld(), p.getEyePos(), "tendril", n);
            ServerActions.start(new GraspAction(p, target));
        } else {
            Vec3d end = rayEnd(p, 18);
            n.putInt("t", -1);
            n.putDouble("ex", end.x);
            n.putDouble("ey", end.y);
            n.putDouble("ez", end.z);
            n.putInt("d", 12);
            AWNet.fx(p.getServerWorld(), p.getEyePos(), "tendril", n);
            d.startCooldown(SkillSlot.SKILL1, CD_S1_MISS);
        }
        return true;
    }

    static Vec3d rayEnd(LivingEntity p, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1f).multiply(range));
        BlockHitResult hit = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    static class GraspAction extends TimedAction {
        final ServerPlayerEntity caster;
        final LivingEntity target;
        int punchAt = -1;

        GraspAction(ServerPlayerEntity caster, LivingEntity target) {
            super(caster);
            this.caster = caster;
            this.target = target;
        }

        @Override
        protected boolean step(int t) {
            if (!target.isAlive() || target.isRemoved()) return true;
            ServerWorld w = world();
            if (t == 3) {
                Combat.stun(target, 20, caster);
                sound(target, AWSounds.VOID_PULL, 1.0f, 1.0f);
                if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.grabbed");
            }
            if (t >= 3 && punchAt < 0) {
                Vec3d look = Combat.horizontal(caster.getRotationVec(1f));
                Vec3d dest = caster.getPos().add(look.multiply(1.55 + target.getWidth() * 0.5));
                Vec3d vec = dest.subtract(target.getPos());
                double dist = vec.length();
                if (dist < 1.0 || t >= 14) {
                    punchAt = t + 1;
                    Combat.setVelocity(target, Vec3d.ZERO);
                } else {
                    double speed = Math.min(2.4, dist * 0.5 + 0.35);
                    Vec3d v = vec.normalize().multiply(speed);
                    if (target.isOnGround()) v = v.add(0, 0.1, 0);
                    Combat.setVelocity(target, v);
                }
            }
            if (t == punchAt) {
                AWNet.anim(caster, "void.skill1_punch");
            }
            if (punchAt > 0 && t == punchAt + 2) {
                Vec3d look = Combat.horizontal(caster.getRotationVec(1f));
                Combat.unstun(target);
                Combat.damage(target, AWDamage.source(w, AWDamage.GRAB, caster), 6.0f, AWNet.DMG_CRIT);
                Combat.concuss(target, 40, 20, caster);
                Combat.setVelocity(target, look.multiply(0.55).add(0, 0.32, 0));
                impact(w, chest(target).subtract(look.multiply(target.getWidth() * 0.5)), "crit", 1.6f);
                sound(target, AWSounds.VOID_CRIT, 1.2f, 1.0f);
                sound(target, AWSounds.VOID_IMPACT, 1.0f, 1.2f);
                AWNet.shake(target, 0.7f, 10);
                AWNet.shake(caster, 0.3f, 6);
                if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.stagger");
            }
            return punchAt > 0 && t >= punchAt + 10;
        }

        @Override
        protected void cleanup() {
            if (punchAt < 0 || t < punchAt + 2) Combat.unstun(target);
        }
    }

    // =================================================================== 2. 공허 파동

    private boolean burst(ServerPlayerEntity p, AbilityData d) {
        AWNet.anim(p, "void.skill2");
        d.startCooldown(SkillSlot.SKILL2, CD_S2);
        d.setBusy(18);
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 10);
        AWNet.fx(p.getServerWorld(), p.getPos(), "charge", n);
        sound(p, AWSounds.VOID_CHARGE, 1.0f, 1.0f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 3, false, false, false));
        ServerActions.start(new BurstAction(p));
        return true;
    }

    static class BurstAction extends TimedAction {
        final ServerPlayerEntity caster;
        final List<LivingEntity> launched = new ArrayList<>();
        final Set<LivingEntity> slammed = new HashSet<>();

        BurstAction(ServerPlayerEntity caster) {
            super(caster);
            this.caster = caster;
        }

        @Override
        protected boolean step(int t) {
            ServerWorld w = world();
            if (t == 10) {
                Vec3d look = caster.getRotationVec(1f);
                Vec3d lookH = Combat.horizontal(look);
                Vec3d origin = caster.getEyePos().add(0, -0.35, 0).add(look.multiply(0.9));
                NbtCompound n = nbt();
                n.putDouble("dx", look.x);
                n.putDouble("dy", look.y);
                n.putDouble("dz", look.z);
                n.putInt("c", caster.getId());
                AWNet.fx(w, origin, "burst", n);
                sound(w, origin, AWSounds.VOID_BLAST, 1.6f, 1.0f);
                AWNet.shakeAround(w, origin, 0.55f, 10, 14);
                for (LivingEntity e : Combat.findInCone(caster, caster.getEyePos(), look, 6.2, 40)) {
                    Combat.damage(e, AWDamage.source(w, AWDamage.SKILL, caster), 8.0f, AWNet.DMG_SKILL);
                    Vec3d v = lookH.multiply(3.1).add(0, 0.62 + Math.max(0, look.y) * 0.8, 0);
                    Combat.setVelocity(e, v);
                    Combat.concuss(e, 50, 24, caster);
                    NbtCompound tn = nbt();
                    tn.putInt("e", e.getId());
                    tn.putInt("d", 30);
                    AWNet.fx(w, e.getPos(), "trail", tn);
                    impact(w, chest(e), "heavy", 1.5f);
                    if (e instanceof ServerPlayerEntity) AWNet.anim(e, "common.launched");
                    AWNet.shake(e, 0.8f, 12);
                    launched.add(e);
                }
                Combat.setVelocity(caster, caster.getVelocity().add(lookH.multiply(-0.25)));
            }
            if (t > 11) {
                for (LivingEntity e : launched) {
                    if (slammed.contains(e) || !e.isAlive()) continue;
                    if (e.horizontalCollision && t < 40) {
                        slammed.add(e);
                        Combat.damage(e, AWDamage.source(w, AWDamage.SKILL, caster), 2.0f, AWNet.DMG_SKILL);
                        crater(w, chest(e), 1.8f);
                        sound(e, AWSounds.VOID_SLAM, 1.0f, 1.2f);
                        AWNet.shake(e, 0.6f, 8);
                    }
                }
            }
            return t >= 40;
        }
    }

    // =================================================================== 3. 공허 난무

    private boolean barrage(ServerPlayerEntity p, AbilityData d) {
        LivingEntity target = Combat.findTarget(p, 20, 10);
        if (target == null) {
            d.startCooldown(SkillSlot.SKILL3, CD_S3_MISS);
            p.playSound(AWSounds.UI_DENY, SoundCategory.MASTER, 0.7f, 0.7f);
            return true;
        }
        d.startCooldown(SkillSlot.SKILL3, CD_S3);
        boolean air = Combat.distanceToGround(target, 8) >= 3.0;
        d.setBusy(air ? 32 : 32);
        d.invulnUntil = d.now() + 3;
        rift(p.getServerWorld(), p.getPos(), p.getYaw(), 1.0f);
        sound(p, AWSounds.VOID_TELEPORT, 1.0f, 1.1f);
        AWNet.anim(p, "void.skill3_dash");
        Combat.stun(target, 40, p);
        ServerActions.start(new BarrageAction(p, target, air));
        return true;
    }

    static class BarrageAction extends TimedAction {
        final ServerPlayerEntity caster;
        final LivingEntity target;
        final boolean air;
        int impactAt = -1;
        boolean released;

        BarrageAction(ServerPlayerEntity caster, LivingEntity target, boolean air) {
            super(caster);
            this.caster = caster;
            this.target = target;
            this.air = air;
        }

        @Override
        protected boolean step(int t) {
            if (!target.isAlive() || target.isRemoved()) return true;
            ServerWorld w = world();
            if (t == 2) {
                Vec3d dir = Combat.horizontal(caster.getPos().subtract(target.getPos()));
                Vec3d dest = target.getPos().add(dir.multiply(1.25 + target.getWidth() * 0.5));
                if (air) dest = dest.add(0, 0.9, 0);
                Vec3d safe = Combat.safeSpot(caster, dest);
                if (safe == null) safe = Combat.safeSpot(caster, target.getPos().add(dir.multiply(0.6)));
                if (safe == null) safe = target.getPos();
                float[] rot = Combat.lookAt(safe.add(0, caster.getStandingEyeHeight(), 0), chest(target));
                Combat.teleport(caster, safe, rot[0], rot[1]);
                rift(w, safe, rot[0], 1.0f);
                sound(caster, AWSounds.VOID_TELEPORT, 1.0f, 1.4f);
                Combat.hold(target, true);
                Combat.setVelocity(target, Vec3d.ZERO);
                if (air) Combat.hold(caster, true);
                Combat.setVelocity(caster, Vec3d.ZERO);
                AWNet.anim(caster, air ? "void.skill3_air" : "void.skill3");
                if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.held");
            }
            if (t > 2 && !released) {
                Combat.setVelocity(target, Vec3d.ZERO);
                if (air && t < 5) Combat.setVelocity(caster, Vec3d.ZERO);
            }
            Vec3d look = Combat.horizontal(target.getPos().subtract(caster.getPos()));
            if (!air) {
                if (t == 6 || t == 9 || t == 12 || t == 15) {
                    int i = (t - 6) / 3;
                    Combat.damage(target, AWDamage.source(w, AWDamage.GRAB, caster), 1.25f, AWNet.DMG_SKILL);
                    Vec3d side = new Vec3d(-look.z, 0, look.x).multiply((i % 2 == 0 ? 0.25 : -0.25));
                    impact(w, chest(target).add(side).add(0, i >= 2 ? 0.25 : 0, 0).subtract(look.multiply(target.getWidth() * 0.5)), "punch", 0.9f + i * 0.1f);
                    sound(target, AWSounds.VOID_PUNCH, 1.0f, 0.9f + i * 0.08f);
                    AWNet.shake(target, 0.25f, 4);
                    AWNet.shake(caster, 0.12f, 3);
                    if (target instanceof ServerPlayerEntity) AWNet.anim(target, i % 2 == 0 ? "common.flinch" : "common.flinch2");
                }
                if (t == 21) {
                    release();
                    Combat.damage(target, AWDamage.source(w, AWDamage.GRAB, caster), 5.0f, AWNet.DMG_CRIT);
                    Combat.setVelocity(target, new Vec3d(look.x * 0.2, -1.4, look.z * 0.2));
                    Combat.concuss(target, 30, 20, caster);
                    crater(w, target.getPos(), 3.0f);
                    sound(target, AWSounds.VOID_SLAM, 1.4f, 1.0f);
                    AWNet.shakeAround(w, target.getPos(), 0.8f, 12, 16);
                    if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.slammed");
                }
                return t >= 28;
            } else {
                if (t == 5) {
                    release();
                    Combat.setVelocity(target, new Vec3d(0, -3.0, 0));
                    Combat.setVelocity(caster, new Vec3d(0, -2.4, 0));
                    impact(w, chest(target).add(0, 0.4, 0), "heavy", 1.4f);
                    sound(target, AWSounds.VOID_PUNCH_HEAVY, 1.2f, 0.8f);
                    NbtCompound tn = nbt();
                    tn.putInt("e", target.getId());
                    tn.putInt("d", 18);
                    AWNet.fx(w, target.getPos(), "trail", tn);
                    if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.launched_down");
                }
                if (t > 5 && impactAt < 0) {
                    if (target.isOnGround() || Combat.distanceToGround(target, 4) < 0.35 || t >= 24) {
                        impactAt = t;
                        Combat.damage(target, AWDamage.source(w, AWDamage.GRAB, caster), 10.0f, AWNet.DMG_CRIT);
                        Combat.concuss(target, 40, 24, caster);
                        crater(w, target.getPos(), 4.5f);
                        sound(target, AWSounds.VOID_SLAM, 1.8f, 0.8f);
                        sound(target, AWSounds.VOID_IMPACT, 1.4f, 0.7f);
                        AWNet.shakeAround(w, target.getPos(), 1.1f, 16, 22);
                        if (target instanceof ServerPlayerEntity) AWNet.anim(target, "common.slammed");
                    } else {
                        Combat.setVelocity(target, new Vec3d(0, -3.0, 0));
                    }
                }
                return impactAt > 0 && t >= impactAt + 6;
            }
        }

        void release() {
            if (released) return;
            released = true;
            Combat.hold(target, false);
            Combat.hold(caster, false);
            Combat.unstun(target);
        }

        @Override
        protected void cleanup() {
            release();
        }
    }

    // =================================================================== 4. 공허 반격

    private boolean counterStance(ServerPlayerEntity p, AbilityData d) {
        d.counterUntil = d.now() + 16;
        d.setBusy(16);
        d.startCooldown(SkillSlot.SKILL4, CD_S4);
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, true);
        AWNet.anim(p, "void.skill4");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 16);
        n.putInt("k", 0);
        AWNet.fx(p.getServerWorld(), p.getPos(), "shield", n);
        sound(p, AWSounds.VOID_COUNTER_STANCE, 1.0f, 1.0f);
        return true;
    }

    @Override
    public boolean onIncomingDamage(ServerPlayerEntity p, AbilityData d, DamageSource source, float amount) {
        if (!d.isCountering()) return true;
        if (source.isIn(AWDamage.TAG_ULTIMATE)) return true; // 궁극기는 반격 불가
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity la) || attacker == p || !Combat.canTarget(p, la)) return true;
        d.counterUntil = 0;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_COUNTER, false);
        d.startCooldown(SkillSlot.SKILL4, CD_S4_SUCCESS);
        d.invulnUntil = d.now() + 22;
        d.busyUntil = d.now() + 20;
        ServerActions.start(new CounterAction(p, la));
        return false;
    }

    static class CounterAction extends TimedAction {
        final ServerPlayerEntity caster;
        final LivingEntity attacker;
        boolean released;

        CounterAction(ServerPlayerEntity caster, LivingEntity attacker) {
            super(caster);
            this.caster = caster;
            this.attacker = attacker;
        }

        @Override
        protected boolean step(int t) {
            if (!attacker.isAlive() || attacker.isRemoved()) return true;
            ServerWorld w = world();
            if (t == 0) {
                NbtCompound n = nbt();
                n.putInt("c", caster.getId());
                AWNet.fx(w, caster.getPos(), "shield_break", n);
                sound(caster, AWSounds.VOID_COUNTER_SUCCESS, 1.3f, 1.0f);
                AWNet.damageNumber(caster, 0, AWNet.DMG_TEXT_COUNTER, attacker);
                Combat.stun(attacker, 24, caster);
                Combat.hold(attacker, true);
                Combat.setVelocity(attacker, Vec3d.ZERO);
                if (attacker instanceof ServerPlayerEntity) AWNet.anim(attacker, "common.held");
                double dist = caster.distanceTo(attacker);
                if (dist > 3.2 && dist < 16) {
                    Vec3d dir = Combat.horizontal(caster.getPos().subtract(attacker.getPos()));
                    Vec3d dest = Combat.safeSpot(caster, attacker.getPos().add(dir.multiply(1.25 + attacker.getWidth() * 0.5)));
                    if (dest != null) {
                        rift(w, caster.getPos(), caster.getYaw(), 0.9f);
                        float[] rot = Combat.lookAt(dest.add(0, caster.getStandingEyeHeight(), 0), attacker.getEyePos());
                        Combat.teleport(caster, dest, rot[0], rot[1]);
                        rift(w, dest, rot[0], 0.9f);
                        sound(caster, AWSounds.VOID_TELEPORT, 1.0f, 1.5f);
                    }
                } else {
                    float[] rot = Combat.lookAt(caster.getEyePos(), attacker.getEyePos());
                    caster.setYaw(rot[0]);
                    caster.setHeadYaw(rot[0]);
                }
                AWNet.anim(caster, "void.skill4_success");
                AWNet.shake(caster, 0.25f, 5);
            }
            if (t > 0 && !released) Combat.setVelocity(attacker, Vec3d.ZERO);
            if (t == 6) {
                Combat.damage(attacker, AWDamage.source(w, AWDamage.GRAB, caster), 4.0f, AWNet.DMG_CRIT);
                Vec3d look = Combat.horizontal(attacker.getPos().subtract(caster.getPos()));
                impact(w, attacker.getEyePos().subtract(look.multiply(attacker.getWidth() * 0.5)), "crit", 1.4f);
                sound(attacker, AWSounds.VOID_PUNCH_HEAVY, 1.3f, 0.9f);
                AWNet.shake(attacker, 0.6f, 8);
                if (attacker instanceof ServerPlayerEntity) AWNet.anim(attacker, "common.headsnap");
            }
            if (t == 13) {
                release();
                Combat.damage(attacker, AWDamage.source(w, AWDamage.GRAB, caster), 4.0f, AWNet.DMG_SKILL);
                Combat.setVelocity(attacker, new Vec3d(0, -1.6, 0));
                Combat.concuss(attacker, 40, 20, caster);
                crater(w, attacker.getPos(), 2.6f);
                sound(attacker, AWSounds.VOID_SLAM, 1.3f, 1.1f);
                AWNet.shakeAround(w, attacker.getPos(), 0.7f, 10, 14);
                if (attacker instanceof ServerPlayerEntity) AWNet.anim(attacker, "common.slammed");
            }
            return t >= 20;
        }

        void release() {
            if (released) return;
            released = true;
            Combat.hold(attacker, false);
            Combat.unstun(attacker);
        }

        @Override
        protected void cleanup() {
            release();
        }
    }

    // =================================================================== R. 사건의 지평선

    private boolean ultimate(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        Vec3d center = ultTarget(p);
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        // 시전자
        d.frozenUntil = now + ULT_CINEMATIC;
        d.invulnUntil = now + ULT_CINEMATIC;
        d.setBusy(ULT_CINEMATIC);
        Combat.setVelocity(p, Vec3d.ZERO);
        Combat.stun(p, ULT_CINEMATIC, p);
        // 10블록 안의 플레이어들은 연출을 본다 (+ 그동안 정지/무적)
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "void_ult", ULT_CINEMATIC, center);
            if (o != p) {
                AbilityData od = AbilityManager.data(o);
                od.setNow(now);
                od.frozenUntil = now + ULT_CINEMATIC;
                od.invulnUntil = now + ULT_CINEMATIC;
                Combat.stun(o, ULT_CINEMATIC, p);
                Combat.setVelocity(o, Vec3d.ZERO);
                AbilityManager.setBlocking(o, false);
            }
        }
        for (Entity e : w.getOtherEntities(p, new Box(p.getBlockPos()).expand(radius), e -> e instanceof MobEntity)) {
            Combat.stun((LivingEntity) e, ULT_CINEMATIC, p);
        }
        AWNet.anim(p, "void.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putDouble("tx", center.x);
        n.putDouble("ty", center.y);
        n.putDouble("tz", center.z);
        n.putInt("d", ULT_CINEMATIC);
        AWNet.fx(w, p.getPos(), "ult_cast", n);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), AWSounds.VOID_ULT_CAST, SoundCategory.PLAYERS, 4.0f, 1.0f);
        ServerActions.start(new UltAction(p, center));
        return true;
    }

    static Vec3d ultTarget(ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        LivingEntity aimed = Combat.findTarget(p, 18, 10);
        Vec3d pos;
        if (aimed != null) {
            pos = aimed.getPos().add(0, aimed.getHeight() + 1.2, 0);
        } else {
            Vec3d end = eye.add(look.multiply(16));
            BlockHitResult hit = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            pos = hit.getType() == HitResult.Type.MISS ? end : hit.getPos().subtract(look.multiply(1.5));
        }
        Vec3d flat = Combat.horizontal(look);
        if (pos.subtract(p.getPos()).horizontalLength() < 5) {
            pos = new Vec3d(p.getX() + flat.x * 7, Math.max(pos.y, p.getY()), p.getZ() + flat.z * 7);
        }
        Vec3d ground = Combat.groundBelow(p.getServerWorld(), pos, 24, p);
        if (ground != null && pos.y - ground.y < 3.0) pos = new Vec3d(pos.x, ground.y + 3.0, pos.z);
        if (ground != null && pos.y - ground.y > 7.0) pos = new Vec3d(pos.x, ground.y + 7.0, pos.z);
        return pos;
    }

    static class UltAction extends TimedAction {
        final ServerPlayerEntity caster;
        final Vec3d center;

        UltAction(ServerPlayerEntity caster, Vec3d center) {
            super(caster);
            this.caster = caster;
            this.center = center;
        }

        @Override
        protected boolean step(int t) {
            ServerWorld w = world();
            if (t == 20) sound(w, caster.getPos(), AWSounds.VOID_ULT_RISE, 3.0f, 1.0f);
            if (t == ULT_CINEMATIC) {
                VoidSingularityEntity bh = new VoidSingularityEntity(AWEntities.VOID_SINGULARITY, w);
                bh.setPosition(center.x, center.y, center.z);
                bh.setup(caster, ULT_DURATION);
                w.spawnEntity(bh);
                w.playSound(null, center.x, center.y, center.z, AWSounds.VOID_BLACKHOLE, SoundCategory.PLAYERS, 4.0f, 1.0f);
                AWNet.shakeAround(w, center, 0.9f, 20, 32);
                return true;
            }
            return false;
        }
    }

    // =================================================================== T. 그림자 이동

    private boolean shadowEnter(ServerPlayerEntity p, AbilityData d) {
        if (d.isOnCooldown(SkillSlot.PASSIVE)) return false;
        AbilityManager.setBlocking(p, false);
        d.shadowForm = true;
        d.shadowUntil = d.now() + 80;
        d.shadowStart = p.getPos();
        d.setBusy(6);
        // 가라앉는 모션이 보이도록 6틱 뒤에 모습을 감춘다
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int t) {
                if (!d.shadowForm) return true;
                if (t >= 6) {
                    ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_SHADOW, true);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 90, 0, false, false, false));
                    return true;
                }
                return false;
            }
        });
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SHADOW_SPEED_ID);
            speed.addTemporaryModifier(new EntityAttributeModifier(SHADOW_SPEED_ID, "abilitywar shadow", 0.5, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        AWNet.anim(p, "void.shadow_enter");
        NbtCompound n = nbt();
        n.putInt("k", 0);
        AWNet.fx(p.getServerWorld(), p.getPos(), "shadow_pool", n);
        sound(p, AWSounds.VOID_SHADOW_ENTER, 1.0f, 1.0f);
        d.dirty = true;
        return true;
    }

    private boolean shadowExit(ServerPlayerEntity p, AbilityData d, PacketByteBuf extra) {
        Vec3d dest = null;
        float yaw = p.getYaw();
        float pitch = p.getPitch();
        ServerWorld w = p.getServerWorld();
        try {
            if (extra.readableBytes() > 0) {
                int type = extra.readByte();
                if (type == 1) {
                    Entity e = w.getEntityById(extra.readVarInt());
                    if (e instanceof LivingEntity le && le != p && le.isAlive() && le.squaredDistanceTo(p) < 42 * 42) {
                        Vec3d back = Combat.horizontal(le.getRotationVec(1f)).multiply(-(1.1 + le.getWidth() * 0.5));
                        Vec3d want = le.getPos().add(back);
                        dest = Combat.safeSpot(p, want);
                        if (dest == null) dest = Combat.safeSpot(p, le.getPos());
                        if (dest != null) {
                            float[] rot = Combat.lookAt(dest.add(0, p.getStandingEyeHeight(), 0), chest(le));
                            yaw = rot[0];
                            pitch = rot[1];
                        }
                    }
                } else if (type == 2) {
                    BlockPos stand = extra.readBlockPos();
                    Vec3d want = Vec3d.ofBottomCenter(stand);
                    if (want.squaredDistanceTo(p.getPos()) < 42 * 42 && Combat.isShadowSpot(w, stand)) {
                        dest = Combat.safeSpot(p, want);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        doShadowExit(p, d, dest, yaw, pitch);
        return true;
    }

    private void doShadowExit(ServerPlayerEntity p, AbilityData d, @Nullable Vec3d dest, float yaw, float pitch) {
        ServerWorld w = p.getServerWorld();
        NbtCompound n0 = nbt();
        n0.putInt("k", 2);
        AWNet.fx(w, p.getPos(), "shadow_pool", n0);
        if (dest != null) {
            Combat.teleport(p, dest, yaw, pitch);
        }
        Vec3d at = dest != null ? dest : p.getPos();
        d.shadowForm = false;
        d.shadowStart = null;
        ((AWPlayer) p).aw$setFlag(AWPlayer.FLAG_SHADOW, false);
        p.removeStatusEffect(StatusEffects.INVISIBILITY);
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SHADOW_SPEED_ID);
        d.invulnUntil = d.now() + 8;
        d.setBusy(8);
        AWNet.anim(p, "void.shadow_exit");
        NbtCompound n = nbt();
        n.putInt("k", 1);
        AWNet.fx(w, at, "shadow_pool", n);
        sound(w, at, AWSounds.VOID_SHADOW_EXIT, 1.0f, 1.0f);
        d.startCooldown(SkillSlot.PASSIVE, CD_PASSIVE);
        d.dirty = true;
    }

    // =================================================================== tick / reset

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        AWPlayer awp = (AWPlayer) p;
        if (awp.aw$hasFlag(AWPlayer.FLAG_COUNTER) && !d.isCountering()) {
            // 반격 실패: 막는 모션만 나오고 끝
            awp.aw$setFlag(AWPlayer.FLAG_COUNTER, false);
            if (!ServerActions.hasAction(p, CounterAction.class)) {
                NbtCompound n = nbt();
                n.putInt("c", p.getId());
                AWNet.fx(p.getServerWorld(), p.getPos(), "shield_fade", n);
                sound(p, AWSounds.VOID_COUNTER_FAIL, 0.8f, 1.0f);
            }
        }
        if (d.shadowForm) {
            if (d.now() >= d.shadowUntil) doShadowExit(p, d, null, p.getYaw(), p.getPitch());
            else if (d.now() % 4 == 0) {
                NbtCompound n = nbt();
                n.putInt("k", 3);
                AWNet.fx(p.getServerWorld(), p.getPos(), "shadow_pool", n);
            }
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        if (d.shadowForm) {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SHADOW_SPEED_ID);
        Combat.hold(p, false);
    }
}
