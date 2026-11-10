package com.abilitywar.ability.basic;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.ability.angel.AngelAbility;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.rpg.RpgManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.List;

import static com.abilitywar.ability.AbilityFx.nbt;

/**
 * 1차 직업 공통: 평타 + 스킬 1·2·3 + T. 스킬 4 와 궁극기(R) 는 2차 전직 뒤에 열림.
 */
public abstract class BasicClass extends Ability {
    protected BasicClass(String key, int color) {
        super(AbilityWar.id(key), key, 0xFF000000 | color);
    }

    @Override
    public boolean useSkill(ServerPlayerEntity p, AbilityData d, SkillSlot slot, PacketByteBuf extra) {
        if (slot == SkillSlot.SKILL4 || slot == SkillSlot.ULTIMATE) {
            p.sendMessage(Text.translatable("rpg.abilitywar.locked_skill").formatted(Formatting.GRAY), true);
            return false;
        }
        return switch (slot) {
            case SKILL1 -> skill1(p, d);
            case SKILL2 -> skill2(p, d);
            case SKILL3 -> skill3(p, d);
            case PASSIVE -> passive(p, d);
            default -> false;
        };
    }

    protected abstract boolean skill1(ServerPlayerEntity p, AbilityData d);

    protected abstract boolean skill2(ServerPlayerEntity p, AbilityData d);

    protected abstract boolean skill3(ServerPlayerEntity p, AbilityData d);

    protected abstract boolean passive(ServerPlayerEntity p, AbilityData d);

    @Override
    public boolean canUseUltimate(ServerPlayerEntity player, AbilityData data) {
        return false;
    }

    @Override
    public boolean clientUltReady(net.minecraft.entity.player.PlayerEntity player, float ult) {
        return false;
    }

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

    // ------------------------------------------------------------------ 도우미
    protected static List<ServerPlayerEntity> party(ServerPlayerEntity p, double r) {
        return AngelAbility.party(p, p.getPos(), r);
    }

    /** 회복 (지혜 반영, 초록 숫자, 퀘스트 회복량). */
    protected static float heal(ServerPlayerEntity c, LivingEntity t, float amount) {
        amount *= RpgManager.healPower(c);
        float before = t.getHealth();
        if (before < t.getMaxHealth()) t.heal(amount);
        float got = t.getHealth() - before;
        if (got > 0.01f) {
            AWNet.damageNumber(t, got, AWNet.DMG_HEAL, c);
            RpgManager.onHeal(c, got);
        }
        NbtCompound n = nbt();
        n.putInt("e", t.getId());
        n.putFloat("a", amount);
        AWNet.fx(c.getServerWorld(), t.getPos(), "angel_heal", n);
        return got;
    }

    protected static void hit(ServerPlayerEntity p, LivingEntity t, float dmg, boolean skill, boolean heavy) {
        ServerWorld w = p.getServerWorld();
        Combat.damage(t, AWDamage.source(w, skill ? AWDamage.SKILL : AWDamage.PUNCH, p), dmg,
                skill ? AWNet.DMG_SKILL : heavy ? AWNet.DMG_CRIT : AWNet.DMG_NORMAL);
        Combat.hitFlinch(t);
    }

    /** 땅에 퍼지는 고리 (k: 0 = 함성, 1 = 서리, 2 = 치유, 3 = 축복, 4 = 도발). */
    protected static void ring(ServerPlayerEntity p, float r, int color, int k) {
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putFloat("r", r);
        n.putInt("col", color);
        n.putInt("k", k);
        AWNet.fx(p.getServerWorld(), p.getPos(), "basic_ring", n);
    }

    /** 손 → 대상 (또는 조준점) 으로 날아가는 구슬. */
    protected static void bolt(ServerPlayerEntity p, LivingEntity t, Vec3d point, int color, float size, int travel) {
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("e", t != null ? t.getId() : -1);
        n.putDouble("tx", point.x);
        n.putDouble("ty", point.y);
        n.putDouble("tz", point.z);
        n.putInt("col", color);
        n.putFloat("s", size);
        n.putInt("t", travel);
        AWNet.fx(p.getServerWorld(), p.getPos(), "basic_bolt", n);
    }

    protected static void bubble(LivingEntity e, int ticks, int color) {
        NbtCompound n = nbt();
        n.putInt("e", e.getId());
        n.putInt("d", ticks);
        n.putInt("col", color);
        AWNet.fx((ServerWorld) e.getWorld(), e.getPos(), "basic_bubble", n);
    }

    protected static void slash(ServerPlayerEntity p, int combo, int color, boolean full) {
        NbtCompound n = nbt();
        n.putInt("c", p.getId());
        n.putInt("k", combo);
        n.putInt("col", color);
        n.putBoolean("full", full);
        AWNet.fx(p.getServerWorld(), p.getPos(), "basic_slash", n);
    }

    protected static Vec3d aimPoint(ServerPlayerEntity p, double range) {
        var hit = p.raycast(range, 1f, false);
        return hit.getPos();
    }
}
