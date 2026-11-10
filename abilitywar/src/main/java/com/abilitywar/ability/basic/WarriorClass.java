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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 전사 (1차): 근접 물리.
 * 평타 베기 3연타 · 1 돌진 베기 · 2 회전 베기 · 3 전투 함성 (파티 용맹) · T 숨 고르기 (회복).
 */
public class WarriorClass extends BasicClass {
    public static final int COLOR = 0xE8A048;

    public WarriorClass() {
        super("warrior", COLOR);
    }

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.0f + combo * 0.1f);
        slash(p, combo, COLOR, false);
        boolean fin = combo == 2;
        Vec3d look = Combat.horizontal(p.getRotationVec(1f));
        for (LivingEntity t : Combat.findInCone(p, p.getEyePos(), p.getRotationVec(1f), 3.6, 55)) {
            hit(p, t, fin ? 4f : 2.5f, false, fin);
            Combat.setVelocity(t, t.getVelocity().multiply(0.3).add(look.multiply(fin ? 0.7 : 0.25)).add(0, fin ? 0.25 : 0.05, 0));
            impact(p.getServerWorld(), chest(t), fin ? "heavy" : "punch", fin ? 1f : 0.6f, COLOR);
            if (fin) AWNet.shake(t, 0.3f, 5);
        }
    }

    /** 1. 돌진 베기: 앞으로 6칸 돌진하며 지나가는 적을 벰. */
    @Override
    protected boolean skill1(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, 160);
        d.setBusy(8);
        AWNet.anim(p, "warrior.skill1");
        sound(p, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.8f);
        Vec3d dir = Combat.horizontal(p.getRotationVec(1f)).normalize();
        NbtCompound n = nbt();
        n.putInt("e", p.getId());
        n.putString("ab", key);
        n.putInt("c", color);
        n.putFloat("dx", (float) dir.x);
        n.putFloat("dz", (float) dir.z);
        AWNet.fx(p.getServerWorld(), p.getPos(), "dash", n);
        Set<LivingEntity> hitSet = new HashSet<>();
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < 5) Combat.setVelocitySelf(p, dir.multiply(1.25).add(0, 0.02, 0));
                for (LivingEntity t : Combat.findInRadius(p, p.getPos().add(0, 1, 0), 1.8)) {
                    if (!Combat.canTarget(p, t) || !hitSet.add(t)) continue;
                    hit(p, t, 4f, true, false);
                    Combat.setVelocity(t, dir.multiply(0.5).add(0, 0.3, 0));
                    impact(world(), chest(t), "heavy", 0.9f, COLOR);
                }
                if (tt == 5) {
                    Combat.setVelocitySelf(p, p.getVelocity().multiply(0.2));
                    slash(p, 1, COLOR, false);
                }
                return tt >= 7;
            }
        });
        return true;
    }

    /** 2. 회전 베기: 주위 3.5칸 적에게 피해 + 밀쳐냄. */
    @Override
    protected boolean skill2(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 200);
        d.setBusy(10);
        AWNet.anim(p, "warrior.skill2");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 3) {
                    ServerWorld w = world();
                    sound(p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
                    slash(p, 0, COLOR, true);
                    for (LivingEntity t : Combat.findInRadius(p, p.getPos().add(0, 0.9, 0), 3.5)) {
                        if (!Combat.canTarget(p, t)) continue;
                        hit(p, t, 5f, true, false);
                        Vec3d away = Combat.horizontal(t.getPos().subtract(p.getPos()));
                        if (away.lengthSquared() > 1e-4) Combat.setVelocity(t, away.normalize().multiply(0.8).add(0, 0.3, 0));
                        impact(w, chest(t), "heavy", 0.8f, COLOR);
                    }
                    AWNet.shakeAround(w, p.getPos(), 0.25f, 5, 8);
                }
                return tt >= 6;
            }
        });
        return true;
    }

    /** 3. 전투 함성: 나와 주위 8칸 파티에게 용맹 (피해 +15%) 8초, 나는 저항 5초. */
    @Override
    protected boolean skill3(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, 400);
        d.setBusy(8);
        AWNet.anim(p, "warrior.skill3");
        sound(p, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.3f);
        ring(p, 8, COLOR, 0);
        for (ServerPlayerEntity a : party(p, 8)) a.addStatusEffect(new StatusEffectInstance(AWEffects.VALOR, 160, 0, false, false, true), p);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, false, false, true));
        return true;
    }

    /** T. 숨 고르기: 체력 30% 회복 + 재생 4초. */
    @Override
    protected boolean passive(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.PASSIVE, 600);
        AWNet.anim(p, "warrior.passive");
        sound(p, SoundEvents.ENTITY_PLAYER_BREATH, 1f, 0.9f);
        heal(p, p, p.getMaxHealth() * 0.3f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 0, false, false, true));
        return true;
    }
}
