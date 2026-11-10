package com.abilitywar.ability.basic;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.server.ServerActions;
import com.abilitywar.server.TimedAction;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 탱커 (1차): 방패로 막고 버팀.
 * 평타 방패 치기 · 1 도발 (몹이 나를 노림) · 2 방패 돌격 (기절) · 3 철벽 (저항 + 보호막) · T 버티기 (회복).
 */
public class TankClass extends BasicClass {
    public static final int COLOR = 0x9AB4D0;

    public TankClass() {
        super("tank", COLOR);
    }

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.ITEM_SHIELD_BLOCK, 0.7f, 0.8f + combo * 0.1f);
        if (target == null) return;
        boolean fin = combo == 2;
        hit(p, target, fin ? 3f : 2f, false, fin);
        Vec3d dir = Combat.horizontal(target.getPos().subtract(p.getPos()));
        if (dir.lengthSquared() > 1e-4) Combat.setVelocity(target, dir.normalize().multiply(fin ? 0.8 : 0.3).add(0, fin ? 0.25 : 0.05, 0));
        if (fin) Combat.stun(target, 10, p);
        impact(p.getServerWorld(), chest(target), fin ? "heavy" : "punch", fin ? 1f : 0.6f, COLOR);
    }

    /** 1. 도발: 주위 10칸 몹이 나를 노림, 다른 팀 플레이어는 불협화음 (주는 피해 -20%). 나는 저항 5초. */
    @Override
    protected boolean skill1(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, 240);
        d.setBusy(8);
        AWNet.anim(p, "tank.skill1");
        sound(p, SoundEvents.ENTITY_IRON_GOLEM_HURT, 1f, 0.6f);
        sound(p, SoundEvents.ENTITY_RAVAGER_ROAR, 0.6f, 1.5f);
        ring(p, 10, 0xFF5A4A, 4);
        taunt(p, 10);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, false, false, true));
        return true;
    }

    public static void taunt(ServerPlayerEntity p, double r) {
        for (LivingEntity e : p.getServerWorld().getEntitiesByClass(LivingEntity.class, new Box(p.getBlockPos()).expand(r), e -> e != p && e.isAlive())) {
            if (!Combat.canTarget(p, e) || e.squaredDistanceTo(p) > r * r) continue;
            if (e instanceof MobEntity m) {
                m.setTarget(p);
                m.setAttacker(p);
            } else if (e instanceof PlayerEntity) {
                e.addStatusEffect(new StatusEffectInstance(AWEffects.DISSONANCE, 100, 0, false, false, true), p);
            }
            NbtCompound n = nbt();
            n.putInt("e", e.getId());
            n.putInt("c", p.getId());
            AWNet.fx(p.getServerWorld(), e.getPos(), "basic_taunted", n);
        }
    }

    /** 2. 방패 돌격: 앞으로 5칸, 처음 부딪힌 적 피해 4 + 1초 기절. */
    @Override
    protected boolean skill2(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 180);
        d.setBusy(8);
        AWNet.anim(p, "tank.skill2");
        sound(p, SoundEvents.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.7f);
        Vec3d dir = Combat.horizontal(p.getRotationVec(1f)).normalize();
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putString("ab", key);
        n.putInt("c", color);
        n.putFloat("dx", (float) dir.x);
        n.putFloat("dz", (float) dir.z);
        AWNet.fx(p.getServerWorld(), p.getPos(), "dash", n);
        ServerActions.start(new TimedAction(p) {
            boolean done;

            @Override
            protected boolean step(int tt) {
                if (done) return true;
                Combat.setVelocitySelf(p, dir.multiply(1.05).add(0, 0.02, 0));
                for (LivingEntity t : Combat.findInRadius(p, p.getPos().add(dir.multiply(0.6)).add(0, 1, 0), 1.5)) {
                    if (!Combat.canTarget(p, t)) continue;
                    hit(p, t, 4f, true, true);
                    Combat.stun(t, 20, p);
                    Combat.setVelocity(t, dir.multiply(0.9).add(0, 0.3, 0));
                    impact(world(), chest(t), "crit", 1.2f, COLOR);
                    sound(t, SoundEvents.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);
                    AWNet.shake(p, 0.3f, 5);
                    Combat.setVelocitySelf(p, Vec3d.ZERO);
                    done = true;
                    return true;
                }
                if (tt >= 5) Combat.setVelocitySelf(p, p.getVelocity().multiply(0.2));
                return tt >= 5;
            }
        });
        return true;
    }

    /** 3. 철벽: 5초 저항 II + 10초 보호막 + 밀려나지 않음. */
    @Override
    protected boolean skill3(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, 360);
        d.setBusy(6);
        AWNet.anim(p, "tank.skill3");
        sound(p, SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 1, false, false, true));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0, false, false, true));
        d.superArmorUntil = d.now() + 100;
        bubble(p, 100, COLOR);
        return true;
    }

    /** T. 버티기: 체력 25% 회복 + 재생 5초. */
    @Override
    protected boolean passive(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.PASSIVE, 600);
        AWNet.anim(p, "tank.passive");
        sound(p, SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        heal(p, p, p.getMaxHealth() * 0.25f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, false, false, true));
        return true;
    }
}
