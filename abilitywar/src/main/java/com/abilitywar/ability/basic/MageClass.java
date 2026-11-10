package com.abilitywar.ability.basic;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
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

import static com.abilitywar.ability.AbilityFx.*;

/**
 * 마법사 (1차): 원거리 마법.
 * 평타 마력탄 · 1 화염구 (폭발 + 불) · 2 서리 고리 (주변 느리게) · 3 순간이동 · T 마나 보호막.
 */
public class MageClass extends BasicClass {
    public static final int COLOR = 0x7AA8FF;
    public static final int FIRE = 0xFF7A30, FROST = 0xA8E8FF;

    public MageClass() {
        super("mage", COLOR);
    }

    @Override
    public void basicAttack(ServerPlayerEntity p, AbilityData d, @Nullable LivingEntity target) {
        int combo = d.combo;
        AWNet.animOthers(p, attackAnim(p, combo));
        sound(p, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 1.4f + combo * 0.1f);
        LivingEntity tg = target != null ? target : Combat.findTarget(p, 18, 9);
        boolean fin = combo == 2;
        Vec3d end = tg != null ? chest(tg) : aimPoint(p, 18);
        int travel = tg != null ? Math.max(2, Math.min(6, (int) (tg.distanceTo(p) / 3.5))) : 6;
        bolt(p, tg, end, COLOR, fin ? 1.4f : 0.9f, travel);
        if (tg == null) return;
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                if (tg.isAlive()) {
                    hit(p, tg, fin ? 3.5f : 2f, false, fin);
                    impact(world(), chest(tg), fin ? "heavy" : "punch", fin ? 0.9f : 0.5f, COLOR);
                }
                return true;
            }
        });
    }

    /** 1. 화염구: 조준한 곳으로 날아가 폭발 (반지름 2.5, 피해 6 + 3초 불). */
    @Override
    protected boolean skill1(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL1, 120);
        d.setBusy(8);
        AWNet.anim(p, "mage.skill1");
        sound(p, SoundEvents.ENTITY_BLAZE_SHOOT, 1f, 0.9f);
        LivingEntity tg = Combat.findTarget(p, 22, 7);
        Vec3d end = tg != null ? chest(tg) : aimPoint(p, 22);
        int travel = Math.max(3, Math.min(9, (int) (end.distanceTo(p.getEyePos()) / 2.6)));
        bolt(p, tg, end, FIRE, 2.2f, travel);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt < travel) return false;
                ServerWorld w = world();
                Vec3d at = tg != null && tg.isAlive() ? chest(tg) : end;
                sound(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                impact(w, at, "crit", 1.4f, FIRE);
                crater(w, at.add(0, -1, 0), 2.5f, FIRE);
                for (LivingEntity o : Combat.findInRadius(p, at, 2.5)) {
                    if (!Combat.canTarget(p, o)) continue;
                    hit(p, o, 6f, true, false);
                    o.setOnFireFor(3);
                }
                AWNet.shakeAround(w, at, 0.3f, 6, 10);
                return true;
            }
        });
        return true;
    }

    /** 2. 서리 고리: 주위 5칸 적 피해 4 + 3초 느리게. */
    @Override
    protected boolean skill2(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.SKILL2, 240);
        d.setBusy(8);
        AWNet.anim(p, "mage.skill2");
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int tt) {
                if (tt == 4) {
                    sound(p, SoundEvents.BLOCK_GLASS_BREAK, 1.2f, 0.6f);
                    sound(p, SoundEvents.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
                    ring(p, 5, FROST, 1);
                    for (LivingEntity t : Combat.findInRadius(p, p.getPos().add(0, 0.8, 0), 5)) {
                        if (!Combat.canTarget(p, t)) continue;
                        hit(p, t, 4f, true, false);
                        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), p);
                        t.setFrozenTicks(Math.max(t.getFrozenTicks(), 160));
                    }
                }
                return tt >= 6;
            }
        });
        return true;
    }

    /** 3. 순간이동: 바라보는 쪽으로 최대 8칸. */
    @Override
    protected boolean skill3(ServerPlayerEntity p, AbilityData d) {
        Vec3d look = p.getRotationVec(1f);
        var hit = p.raycast(8, 1f, false);
        Vec3d to = hit.getPos().subtract(look.multiply(0.6));
        Vec3d spot = Combat.safeSpot(p, new Vec3d(to.x, Math.max(to.y - 0.2, p.getY() - 2), to.z));
        if (spot == null) spot = Combat.safeSpot(p, p.getPos().add(Combat.horizontal(look).normalize().multiply(4)));
        if (spot == null) return false;
        d.startCooldown(SkillSlot.SKILL3, 160);
        NbtCompound n = nbt();
        n.putDouble("fx", p.getX());
        n.putDouble("fy", p.getY());
        n.putDouble("fz", p.getZ());
        n.putInt("col", COLOR);
        AWNet.fx(p.getServerWorld(), spot, "basic_blink", n);
        sound(p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
        Combat.teleport(p, spot, p.getYaw(), p.getPitch());
        p.fallDistance = 0;
        AWNet.anim(p, "mage.skill3");
        return true;
    }

    /** T. 마나 보호막: 10초 동안 흡수 보호막 (체력 4). */
    @Override
    protected boolean passive(ServerPlayerEntity p, AbilityData d) {
        d.startCooldown(SkillSlot.PASSIVE, 500);
        AWNet.anim(p, "mage.passive");
        sound(p, SoundEvents.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1, false, false, true));
        bubble(p, 200, COLOR);
        return true;
    }
}
