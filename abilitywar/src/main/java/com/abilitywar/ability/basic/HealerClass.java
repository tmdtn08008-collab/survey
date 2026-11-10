package com.abilitywar.ability.basic;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.ability.angel.AngelAbility;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.rpg.RpgManager;
import com.abilitywar.server.ServerActions;
import com.abilitywar.server.TimedAction;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 힐러 (1차): 파티 회복.
 * 평타 빛 구슬 (아군 회복 / 적 피해) · 1 치유 (한 명) · 2 정화의 빛 (주변 정화 + 회복) · 3 축복 (파티 수호) · T 회복의 기도 (파티 재생).
 */
public class HealerClass extends BasicClass {
    public static final int COLOR = 0xA8F0B0;
    public static final int GOLD = 0xFFE08A;

    public HealerClass() {
        super("healer", COLOR);
    }

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + combo * 0.1f);
        LivingEntity t = AngelAbility.aimedAlly(p, 16, 9);
        boolean healing = t != null;
        if (!healing) t = target != null ? target : Combat.findTarget(p, 15, 8);
        boolean fin = combo == 2;
        Vec3d end = t != null ? chest(t) : aimPoint(p, 15);
        int travel = t != null ? Math.max(2, Math.min(5, (int) (t.distanceTo(p) / 3.5))) : 6;
        bolt(p, t, end, healing ? COLOR : GOLD, fin ? 1.2f : 0.8f, travel);
        if (t == null) return;
        final LivingEntity tgt = t;
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                if (!tgt.isAlive()) return true;
                if (healing) heal(p, tgt, fin ? 2.5f : 1.5f);
                else {
                    hit(p, tgt, fin ? 2.5f : 1.5f, false, fin);
                    impact(world(), chest(tgt), "punch", 0.5f, GOLD);
                }
                return true;
            }
        });
    }

    /** 1. 치유: 조준한 아군 (없으면 나) 체력 6 회복. */
    @Override
    protected boolean skill1(ServerPlayerEntity p, AbilityData d) {
        ServerPlayerEntity t = AngelAbility.aimedAlly(p, 24, 12);
        if (t == null) t = p;
        d.startCooldown(SkillSlot.SKILL1, 120);
        d.setBusy(8);
        AWNet.anim(p, "healer.skill1");
        sound(p, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.3f);
        ServerPlayerEntity tgt = t;
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 5 && tgt.isAlive()) {
                    heal(p, tgt, 6f);
                    NbtCompound n = nbt();
                    n.putInt("e", tgt.getId());
                    n.putBoolean("big", false);
                    AWNet.fx(world(), tgt.getPos(), "angel_buffed", n);
                }
                return tt >= 6;
            }
        });
        return true;
    }

    /** 2. 정화의 빛: 주위 5칸 파티의 해로운 효과 정화 + 체력 3 회복. */
    @Override
    protected boolean skill2(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 240);
        d.setBusy(8);
        AWNet.anim(p, "healer.skill2");
        sound(p, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        ring(p, 5, COLOR, 2);
        for (ServerPlayerEntity a : party(p, 5)) {
            AngelAbility.cleanse(a);
            heal(p, a, 3f);
        }
        return true;
    }

    /** 3. 축복: 주위 6칸 파티에게 수호 (받는 피해 -15%) 8초. */
    @Override
    protected boolean skill3(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL3, 320);
        d.setBusy(8);
        AWNet.anim(p, "healer.skill3");
        sound(p, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);
        ring(p, 6, GOLD, 3);
        int n = 0;
        for (ServerPlayerEntity a : party(p, 6)) {
            a.addStatusEffect(new StatusEffectInstance(AWEffects.AEGIS, 160, 0, false, false, true), p);
            NbtCompound m = nbt();
            m.putInt("e", a.getId());
            m.putBoolean("big", false);
            AWNet.fx(p.getServerWorld(), a.getPos(), "angel_buffed", m);
            n++;
        }
        RpgManager.onBuff(p, n);
        return true;
    }

    /** T. 회복의 기도: 주위 6칸 파티 재생 6초. */
    @Override
    protected boolean passive(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.PASSIVE, 500);
        AWNet.anim(p, "healer.passive");
        sound(p, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
        ring(p, 6, COLOR, 2);
        for (ServerPlayerEntity a : party(p, 6)) a.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0, false, false, true), p);
        RpgManager.onBuff(p, party(p, 6).size());
        return true;
    }
}
