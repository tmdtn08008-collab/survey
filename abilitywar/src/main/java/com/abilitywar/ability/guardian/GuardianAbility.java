package com.abilitywar.ability.guardian;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.ability.angel.AngelAbility;
import com.abilitywar.ability.basic.TankClass;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWGameRules;
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
 * 수호기사 — 탱커 2차 직업. 거대한 방패로 파티를 지킨다. 궁극기 게이지는 맞거나 막아서 찬다.
 * <pre>
 * 평타 방패 치기 3연타 (마지막 기절)
 * 1    도발의 함성 : 주위 12칸 몹이 나를 노림 + 다른 팀 불협화음, 나는 저항 + 보호막
 * 2    방패 돌진   : 7칸 돌진하며 부딪힌 적을 밀고 가다 기절
 * 3    수호의 결계 : 6초 동안 반지름 5 결계 — 안의 아군 받는 피해 -30%, 적은 느려짐
 * 4    대지 강타   : 뛰어올라 내리찍기 — 반지름 4 피해 + 띄움 + 느리게
 * R    금성철벽    : 10초 동안 거대한 방패 — 10칸 안 아군 받는 피해 -60%, 12칸 안 적을 끌어당겨 도발
 * T    수호 태세   : 켜면 8칸 안 같은 팀이 받는 피해의 40% 를 대신 받음 (나는 받는 피해 -25%, 느려짐)
 * </pre>
 */
public class GuardianAbility extends Ability {
    public static final int COLOR = 0xF0C860;
    public static final int STEEL = 0x8FB8E8;
    public static final int ULT_CINEMATIC = 60;
    public static final int ULT_FREEZE = 26;
    static final UUID STANCE_SLOW = UUID.fromString("4b9d6c02-7a1e-4f53-9e1c-3c7b0f2d8a11");

    public GuardianAbility() {
        super(AbilityWar.id("guardian"), "guardian", 0xFF000000 | COLOR);
    }

    // ================================================================ 결계 / 요새 상태 (서버)
    record Zone(UUID owner, ServerWorld world, Vec3d pos, double r, long until, double factor, boolean ult) {
    }

    static final List<Zone> ZONES = new ArrayList<>();
    static final Set<UUID> STANCE = new HashSet<>();

    public static boolean inStance(Entity e) {
        return STANCE.contains(e.getUuid());
    }

    /** 맞는 쪽 피해 조정 (AbilityManager.modifyDamage): 결계/요새 안의 아군, 수호 태세, 대신 맞기. */
    public static float incoming(LivingEntity victim, DamageSource source, float amount) {
        if (!(victim instanceof ServerPlayerEntity vp) || amount <= 0) return amount;
        long now = vp.getServer().getTicks();
        float mult = 1f;
        for (Zone z : ZONES) {
            if (z.until < now || z.world != vp.getServerWorld()) continue;
            ServerPlayerEntity owner = vp.getServer().getPlayerManager().getPlayer(z.owner);
            if (owner == null || !(owner == vp || owner.isTeammate(vp))) continue;
            if (vp.getPos().squaredDistanceTo(z.pos) <= z.r * z.r) mult = (float) Math.min(mult, z.factor);
        }
        amount *= mult;
        if (inStance(vp)) amount *= 0.75f;
        // 수호 태세인 같은 팀 수호기사가 40% 를 대신 맞음
        if (!com.abilitywar.gear.GearEffects.inEffect && !inStance(vp) && !source.isIn(AWDamage.TAG_UNBLOCKABLE)) {
            for (UUID id : STANCE) {
                ServerPlayerEntity g = vp.getServer().getPlayerManager().getPlayer(id);
                if (g == null || g == vp || g == source.getAttacker() || !g.isAlive() || !g.isTeammate(vp) || g.getServerWorld() != vp.getServerWorld() || g.squaredDistanceTo(vp) > 64) continue;
                float red = amount * 0.4f;
                amount -= red;
                com.abilitywar.gear.GearEffects.inEffect = true;
                try {
                    g.timeUntilRegen = 0;
                    g.damage(source, red);
                } finally {
                    com.abilitywar.gear.GearEffects.inEffect = false;
                }
                NbtCompound n = nbt();
                n.putInt("a", g.getId());
                n.putInt("b", vp.getId());
                AWNet.fx(g.getServerWorld(), vp.getPos(), "guardian_intercept", n);
                break;
            }
        }
        return amount;
    }

    public static void tickWorld(net.minecraft.server.MinecraftServer server) {
        long now = server.getTicks();
        for (Iterator<Zone> it = ZONES.iterator(); it.hasNext(); ) {
            Zone z = it.next();
            if (z.until < now) {
                it.remove();
                continue;
            }
            if (now % 10 != 0) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(z.owner);
            if (owner == null) continue;
            // 안에 들어온 적은 느려짐 (요새는 끌어당김)
            for (LivingEntity e : z.world.getEntitiesByClass(LivingEntity.class, new Box(z.pos, z.pos).expand(z.r), e -> e.isAlive() && e != owner)) {
                if (!Combat.canTarget(owner, e) || e.getPos().squaredDistanceTo(z.pos) > z.r * z.r) continue;
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, z.ult ? 2 : 1), owner);
            }
        }
    }

    // ================================================================ 기본
    @Override
    public int attackInterval(int combo) {
        return combo == 2 ? 13 : 9;
    }

    @Override
    public void onDealtDamage(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 2f);
    }

    @Override
    public void onDamaged(ServerPlayerEntity player, AbilityData data, float lost) {
        data.addUlt(lost * 4f);
    }

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        ServerWorld w = p.getServerWorld();
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.ITEM_SHIELD_BLOCK, 0.8f, 0.7f + combo * 0.1f);
        boolean fin = combo == 2;
        Vec3d look = Combat.horizontal(p.getRotationVec(1f));
        for (LivingEntity t : Combat.findInCone(p, p.getEyePos(), p.getRotationVec(1f), 3.4, fin ? 60 : 40)) {
            Combat.damage(t, AWDamage.source(w, AWDamage.PUNCH, p), fin ? 4.5f : 2.5f, fin ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
            Combat.setVelocity(t, t.getVelocity().multiply(0.3).add(look.multiply(fin ? 0.9 : 0.3)).add(0, fin ? 0.3 : 0.05, 0));
            if (fin) {
                Combat.stun(t, 12, p);
                AWNet.shake(t, 0.35f, 6);
            }
            impact(w, chest(t), fin ? "heavy" : "punch", fin ? 1.1f : 0.6f, COLOR);
            Combat.hitFlinch(t);
        }
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        return switch (slot) {
            case SKILL1 -> shout(p, d);
            case SKILL2 -> rush(p, d);
            case SKILL3 -> bulwark(p, d);
            case SKILL4 -> slam(p, d);
            case ULTIMATE -> fortress(p, d);
            case PASSIVE -> stance(p, d);
            case DASH -> false;
        };
    }

    // ================================================================ 1. 도발의 함성
    boolean shout(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, 240);
        d.setBusy(10);
        AWNet.anim(p, "guardian.skill1");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 4) {
                    sound(p, SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
                    sound(p, SoundEvents.ITEM_SHIELD_BLOCK, 1.4f, 0.5f);
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", 12);
                    AWNet.fx(world(), p.getPos(), "guardian_shout", n);
                    TankClass.taunt(p, 12);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, false, false, true));
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 160, 1, false, false, true));
                    AWNet.shakeAround(world(), p.getPos(), 0.25f, 6, 10);
                }
                return tt >= 6;
            }
        });
        return true;
    }

    // ================================================================ 2. 방패 돌진
    boolean rush(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 200);
        d.setBusy(12);
        AWNet.anim(p, "guardian.skill2");
        sound(p, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.8f);
        Vec3d dir = Combat.horizontal(p.getRotationVec(1f)).normalize();
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putFloat("dx", (float) dir.x);
        n.putFloat("dz", (float) dir.z);
        n.putInt("d", 8);
        AWNet.fx(p.getServerWorld(), p.getPos(), "guardian_rush", n);
        Set<LivingEntity> carried = new HashSet<>();
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                ServerWorld w = world();
                if (tt < 7) {
                    Combat.setVelocitySelf(p, dir.multiply(1.1).add(0, 0.02, 0));
                    for (LivingEntity t : Combat.findInRadius(p, p.getPos().add(dir.multiply(0.8)).add(0, 1, 0), 1.7)) {
                        if (!Combat.canTarget(p, t)) continue;
                        if (carried.add(t)) {
                            Combat.damage(t, AWDamage.source(w, AWDamage.SKILL, p), 5f, AWNet.DMG_SKILL);
                            impact(w, chest(t), "heavy", 1f, COLOR);
                            sound(t, SoundEvents.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);
                        }
                    }
                    // 부딪힌 적을 방패 앞에 붙여서 밀고 감
                    for (LivingEntity t : carried) {
                        if (!t.isAlive()) continue;
                        Vec3d front = p.getPos().add(dir.multiply(1.2));
                        Combat.setVelocity(t, front.subtract(t.getPos()).multiply(0.6).add(dir.multiply(0.7)).add(0, 0.05, 0));
                    }
                }
                if (tt == 7) {
                    Combat.setVelocitySelf(p, Vec3d.ZERO);
                    for (LivingEntity t : carried) {
                        if (!t.isAlive()) continue;
                        Combat.stun(t, 30, p);
                        Combat.setVelocity(t, dir.multiply(0.6).add(0, 0.3, 0));
                        impact(w, chest(t), "crit", 1.2f, COLOR);
                    }
                    if (!carried.isEmpty()) AWNet.shakeAround(w, p.getPos(), 0.35f, 6, 8);
                }
                return tt >= 8;
            }
        });
        return true;
    }

    // ================================================================ 3. 수호의 결계
    boolean bulwark(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, 440);
        d.setBusy(12);
        AWNet.anim(p, "guardian.skill3");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 6) {
                    ServerWorld w = world();
                    Vec3d at = p.getPos();
                    sound(w, at, SoundEvents.BLOCK_ANVIL_LAND, 0.9f, 0.6f);
                    sound(w, at, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
                    ZONES.add(new Zone(p.getUuid(), w, at, 5, w.getServer().getTicks() + 120, 0.7, false));
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", 5);
                    n.putInt("d", 120);
                    n.putBoolean("u", false);
                    AWNet.fx(w, at, "guardian_dome", n);
                    crater(w, at.add(0, -0.5, 0), 2.2f, COLOR);
                    for (ServerPlayerEntity a : AngelAbility.party(p, at, 5))
                        a.addStatusEffect(new StatusEffectInstance(AWEffects.AEGIS, 120, 1, false, false, true), p);
                }
                return tt >= 8;
            }
        });
        return true;
    }

    // ================================================================ 4. 대지 강타
    boolean slam(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL4, 280);
        d.setBusy(16);
        d.superArmorUntil = d.now() + 16;
        AWNet.anim(p, "guardian.skill4");
        sound(p, SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
        Vec3d dir = Combat.horizontal(p.getRotationVec(1f)).normalize();
        ServerActions.start(new TimedAction(p) {
            boolean landed;

            @Override
            protected boolean step(int tt) {
                if (tt == 1) Combat.setVelocitySelf(p, dir.multiply(0.5).add(0, 0.85, 0));
                if (tt > 4 && !landed && (p.isOnGround() || tt >= 14)) {
                    landed = true;
                    ServerWorld w = world();
                    Vec3d at = p.getPos();
                    sound(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                    sound(w, at, SoundEvents.BLOCK_ANVIL_LAND, 1f, 0.5f);
                    crater(w, at, 4f, COLOR);
                    NbtCompound n = nbt();
                    n.putInt("c", p.getId());
                    n.putFloat("r", 4);
                    AWNet.fx(w, at, "guardian_slam", n);
                    for (LivingEntity t : Combat.findInRadius(p, at.add(0, 0.6, 0), 4)) {
                        if (!Combat.canTarget(p, t)) continue;
                        Combat.damage(t, AWDamage.source(w, AWDamage.SKILL, p), 7f, AWNet.DMG_SKILL);
                        Combat.setVelocity(t, new Vec3d(0, 0.65, 0).add(Combat.horizontal(t.getPos().subtract(at)).multiply(0.15)));
                        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), p);
                    }
                    AWNet.shakeAround(w, at, 0.55f, 9, 14);
                }
                if (tt > 2 && tt < 6 && !landed) Combat.setVelocitySelf(p, p.getVelocity().add(0, -0.05, 0));
                if (tt >= 6 && !landed) Combat.setVelocitySelf(p, new Vec3d(p.getVelocity().x * 0.5, -1.4, p.getVelocity().z * 0.5));
                return landed && tt >= 16 || tt >= 30;
            }
        });
        return true;
    }

    // ================================================================ R. 금성철벽
    boolean fortress(ServerPlayerEntity p, AbilityData d) {
        ServerWorld w = p.getServerWorld();
        d.ult = 0;
        d.dirty = true;
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        long now = d.now();
        d.frozenUntil = now + ULT_FREEZE;
        d.invulnUntil = now + ULT_FREEZE;
        d.setBusy(ULT_FREEZE + 4);
        d.superArmorUntil = now + 200;
        Combat.setVelocitySelf(p, Vec3d.ZERO);
        Vec3d focus = p.getPos().add(Combat.horizontal(p.getRotationVec(1f)).multiply(6));
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, "guardian_ult", ULT_CINEMATIC, focus);
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
        AWNet.anim(p, "guardian.ult");
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("d", 200 + ULT_FREEZE);
        AWNet.fx(w, p.getPos(), "guardian_ult", n);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                ServerWorld ww = world();
                if (tt == 2) sound(ww, p.getPos(), SoundEvents.BLOCK_BELL_USE, 3f, 0.5f);
                if (tt == ULT_FREEZE - 4) {
                    Vec3d at = p.getPos();
                    sound(ww, at, SoundEvents.BLOCK_ANVIL_LAND, 2f, 0.4f);
                    sound(ww, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 0.6f);
                    ZONES.add(new Zone(p.getUuid(), ww, at, 10, ww.getServer().getTicks() + 200, 0.4, true));
                    NbtCompound m = nbt();
                    m.putInt("c", p.getId());
                    m.putFloat("r", 10);
                    m.putInt("d", 200);
                    m.putBoolean("u", true);
                    AWNet.fx(ww, at, "guardian_dome", m);
                    crater(ww, at, 5f, COLOR);
                    // 12칸 안 적을 끌어당겨 도발
                    for (LivingEntity t : ww.getEntitiesByClass(LivingEntity.class, new Box(p.getBlockPos()).expand(12), e -> e != p && e.isAlive())) {
                        if (!Combat.canTarget(p, t) || t.squaredDistanceTo(p) > 144) continue;
                        Vec3d to = p.getPos().subtract(t.getPos());
                        Combat.setVelocity(t, to.multiply(0.18).add(0, 0.35, 0));
                        Combat.damage(t, AWDamage.source(ww, AWDamage.ULTIMATE, p), 4f, AWNet.DMG_ULT);
                    }
                    TankClass.taunt(p, 12);
                    AWNet.shakeAround(ww, at, 0.6f, 12, 16);
                }
                return tt >= ULT_FREEZE;
            }
        });
        return true;
    }

    // ================================================================ T. 수호 태세
    boolean stance(ServerPlayerEntity p, AbilityData d) {
        boolean on = !inStance(p);
        setStance(p, d, on);
        d.startCooldown(SkillSlot.PASSIVE, 20);
        AWNet.anim(p, "guardian.passive");
        sound(p, on ? SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE : SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1f, on ? 0.8f : 1.2f);
        return true;
    }

    static void setStance(ServerPlayerEntity p, AbilityData d, boolean on) {
        if (on) STANCE.add(p.getUuid());
        else STANCE.remove(p.getUuid());
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(STANCE_SLOW);
            if (on) speed.addTemporaryModifier(new EntityAttributeModifier(STANCE_SLOW, "Guardian stance", -0.15, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        }
        d.extra = on ? 1 : 0;
        d.dirty = true;
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putBoolean("on", on);
        AWNet.fx(p.getServerWorld(), p.getPos(), "guardian_stance", n);
    }

    @Override
    public void tick(ServerPlayerEntity p, AbilityData d) {
        // 다른 사람이 나중에 들어와도 보이게 가끔 다시 알림
        if (inStance(p) && d.extra != 1) setStance(p, d, true); // 재접속 등으로 상태가 어긋나면 맞춤
        if (inStance(p) && p.age % 40 == 0) {
            NbtCompound n = nbt();
            n.putInt("c", p.getId());
            n.putBoolean("on", true);
            n.putBoolean("q", true);
            AWNet.fx(p.getServerWorld(), p.getPos(), "guardian_stance", n);
        }
    }

    @Override
    public void reset(ServerPlayerEntity p, AbilityData d) {
        if (inStance(p)) setStance(p, d, false);
        d.extra = 0;
    }
}
