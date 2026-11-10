package com.abilitywar.server;

import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWDamage;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 서버측 능력 처리의 중심. */
public final class AbilityManager {
    private static final UUID BLOCK_SLOW_ID = UUID.fromString("6f0f8a8e-2f5b-4a39-9b1e-6e1a3c0b7d11");
    private static final EntityAttributeModifier BLOCK_SLOW =
            new EntityAttributeModifier(BLOCK_SLOW_ID, "abilitywar block", -0.55, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    public static AbilityData data(ServerPlayerEntity p) {
        return ((AWPlayer) p).aw$data();
    }

    @Nullable
    public static Ability ability(ServerPlayerEntity p) {
        return data(p).ability();
    }

    public static void setAbility(ServerPlayerEntity player, @Nullable Ability ability) {
        AbilityData d = data(player);
        Ability old = d.ability();
        if (old != null) old.reset(player, d);
        ServerActions.cancelFor(player);
        setBlocking(player, false);
        d.resetTransient();
        d.clearCooldowns();
        d.ult = 0;
        ((AWPlayer) player).aw$setAbilityKey(ability == null ? "" : ability.key);
        com.abilitywar.rpg.RpgManager.onAbilitySet(player, ability == null ? "" : ability.key);
        d.dirty = true;
        AWNet.sendState(player);
        if (ability != null) {
            player.sendMessage(Text.translatable("abilitywar.msg.selected", Text.translatable(ability.translationKey())), false);
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), AWSounds.UI_ULT_READY, SoundCategory.PLAYERS, 0.8f, 0.8f);
            AWNet.anim(player, ability.key + ".select");
        } else {
            player.sendMessage(Text.translatable("abilitywar.msg.cleared"), false);
        }
    }

    // ------------------------------------------------------------------ input

    public static boolean canAct(ServerPlayerEntity player, AbilityData d) {
        return player.isAlive() && !player.isSpectator() && !AWEffects.isStunned(player) && !d.isFrozen();
    }

    public static void handleSkill(ServerPlayerEntity player, SkillSlot slot, PacketByteBuf extra) {
        AbilityData d = data(player);
        Ability a = d.ability();
        if (a == null) return;
        d.setNow(player.getServer().getTicks());
        if (slot == SkillSlot.DASH) {
            handleDash(player, d, a, extra);
            return;
        }
        // 그림자 상태에서 T = 탈출 (쿨타임 무관)
        if (d.shadowForm) {
            if (slot == SkillSlot.PASSIVE) a.useSkill(player, d, slot, extra);
            return;
        }
        if (!canAct(player, d) || (d.isBusy() && !a.allowWhileBusy(player, d, slot)) || d.blocking) {
            deny(player);
            return;
        }
        if (d.isOnCooldown(slot)) {
            deny(player);
            return;
        }
        if (player.hasStatusEffect(AWEffects.SILENCE)) {
            // 침묵: 스킬 사용 불가
            deny(player);
            return;
        }
        if (slot == SkillSlot.ULTIMATE && !a.canUseUltimate(player, d)) {
            deny(player);
            return;
        }
        if (a.useSkill(player, d, slot, extra)) {
            d.dirty = true;
        }
    }

    public static final int DASH_COOLDOWN = 60;

    /**
     * 공통 대시 (Q). 이동 자체는 대시한 클라이언트가 바로 처리하고, 서버는 쿨타임 확인 + 주변에 연출을 알린다.
     * extra: [방향 x(float), 방향 z(float), 방향 종류(byte: 0 앞, 1 뒤, 2 왼쪽, 3 오른쪽)]
     */
    static void handleDash(ServerPlayerEntity player, AbilityData d, Ability a, PacketByteBuf extra) {
        if (!canAct(player, d) || d.isBusy() || d.shadowForm || d.isOnCooldown(SkillSlot.DASH)) {
            deny(player);
            return;
        }
        float dx = 0, dz = 1;
        int kind = 0;
        try {
            dx = extra.readFloat();
            dz = extra.readFloat();
            kind = extra.readByte();
        } catch (Exception ignored) {
        }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3 || !Double.isFinite(len)) return;
        dx /= len;
        dz /= len;
        kind = Math.max(0, Math.min(3, kind));
        setBlocking(player, false);
        d.startCooldown(SkillSlot.DASH, DASH_COOLDOWN);
        d.setBusy(4);
        player.fallDistance = 0;
        String[] dirs = {"forward", "back", "left", "right"};
        AWNet.animOthers(player, a.key + ".dash_" + dirs[kind]);
        NbtCompound n = new NbtCompound();
        n.putInt("e", player.getId());
        n.putString("ab", a.key);
        n.putInt("c", a.color);
        n.putFloat("dx", dx);
        n.putFloat("dz", dz);
        AWNet.fx(player.getServerWorld(), player.getPos(), "dash", n);
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), AWSounds.DASH, SoundCategory.PLAYERS, 0.9f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        d.dirty = true;
    }

    public static void handleAttack(ServerPlayerEntity player, int targetId) {
        AbilityData d = data(player);
        Ability a = d.ability();
        if (a == null) return;
        long now = player.getServer().getTicks();
        d.setNow(now);
        if (!canAct(player, d) || d.isBusy() || d.blocking || d.shadowForm) return;
        if (now + 2 < d.nextAttack) return;
        if (now - d.lastAttack > 24) d.combo = 0;
        LivingEntity target = null;
        if (targetId >= 0) {
            Entity e = player.getWorld().getEntityById(targetId);
            if (e instanceof LivingEntity le && Combat.canTarget(player, le)) {
                double reach = 4.6 + le.getWidth() * 0.5;
                if (player.getEyePos().squaredDistanceTo(le.getBoundingBox().getCenter()) <= reach * reach && Combat.hasLineOfSight(player, le)) {
                    target = le;
                }
            }
        }
        a.basicAttack(player, d, target);
        d.nextAttack = now + AWEffects.attackInterval(player, a.attackInterval(d.combo));
        d.lastAttack = now;
        d.combo = (d.combo + 1) % 3;
        player.updateLastActionTime();
    }

    public static void handleBlock(ServerPlayerEntity player, boolean pressed) {
        AbilityData d = data(player);
        if (d.ability() == null) return;
        d.setNow(player.getServer().getTicks());
        if (pressed) {
            if (!canAct(player, d) || d.isBusy() || d.shadowForm) return;
            setBlocking(player, true);
        } else {
            setBlocking(player, false);
        }
    }

    public static void setBlocking(ServerPlayerEntity player, boolean on) {
        AbilityData d = data(player);
        if (d.blocking == on) return;
        d.blocking = on;
        ((AWPlayer) player).aw$setFlag(AWPlayer.FLAG_BLOCKING, on);
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(BLOCK_SLOW_ID);
            if (on) speed.addTemporaryModifier(BLOCK_SLOW);
        }
        if (on) {
            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), AWSounds.VOID_BLOCK, SoundCategory.PLAYERS, 0.5f, 1.4f);
        }
        d.dirty = true;
    }

    public static void deny(ServerPlayerEntity player) {
        player.playSound(AWSounds.UI_DENY, SoundCategory.MASTER, 0.5f, 1.0f);
    }

    // ------------------------------------------------------------------ tick

    public static void tickPlayer(ServerPlayerEntity player, long now) {
        AbilityData d = data(player);
        d.setNow(now);
        Ability a = d.ability();
        if (a == null) {
            if (d.dirty) {
                AWNet.sendState(player);
                d.dirty = false;
            }
            return;
        }
        if (!player.isAlive()) return;
        if (d.blocking && (!canAct(player, d) || d.isBusy())) setBlocking(player, false);
        // 시간에 따른 궁 게이지 (2초에 1)
        if (now % 40 == 0 && a.passiveGauge()) d.addUlt(1f);
        boolean ready = d.ultReady();
        ((AWPlayer) player).aw$setFlag(AWPlayer.FLAG_ULT_READY, ready);
        ((AWPlayer) player).aw$setFlag(AWPlayer.FLAG_CINEMATIC, d.isFrozen());
        a.tick(player, d);
        // 신속: 쿨타임이 더 빨리 돎 (궁극기 제외)
        net.minecraft.entity.effect.StatusEffectInstance haste = player.getStatusEffect(AWEffects.HASTE);
        if (haste != null && now % (haste.getAmplifier() >= 1 ? 2 : 4) == 0) {
            for (SkillSlot s : SkillSlot.VALUES) {
                if (s == SkillSlot.ULTIMATE) continue;
                if (d.cdEnd[s.ordinal()] > now) d.cdEnd[s.ordinal()]--;
            }
            if (now % 10 == 0) d.dirty = true;
        }
        if (d.dirty || now % 20 == 0) {
            AWNet.sendState(player);
            d.dirty = false;
        }
    }

    public static void tickServer(MinecraftServer server) {
        long now = server.getTicks();
        ServerActions.tick();
        com.abilitywar.ability.warlock.Necromancy.tick(server);
        com.abilitywar.ability.guardian.GuardianAbility.tickWorld(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            tickPlayer(p, now);
            com.abilitywar.gear.GearEffects.tick(p);
            com.abilitywar.rpg.RpgManager.tick(p);
        }
    }

    // ------------------------------------------------------------------ damage

    /** @return false 면 피해 취소. */
    public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.getWorld().isClient) return true;
        // 흑마법사 하수인: 주인 / 같은 팀 / 같은 주인의 하수인끼리는 피해 없음
        Entity src = source.getAttacker();
        if (src != null && src != victim && (com.abilitywar.ability.warlock.Necromancy.isMinion(src) || com.abilitywar.ability.warlock.Necromancy.isMinion(victim))
                && com.abilitywar.ability.warlock.Necromancy.allied(src, victim)) return false;
        if (victim instanceof ServerPlayerEntity sp) {
            AbilityData d = data(sp);
            d.setNow(sp.getServer().getTicks());
            if (d.isInvulnerable() || d.isFrozen()) {
                if (source.getAttacker() != null && amount > 0) {
                    AWNet.damageNumber(sp, 0, AWNet.DMG_TEXT_IMMUNE, source.getAttacker());
                }
                return source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD) || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL);
            }
            Ability a = d.ability();
            if (a != null && !a.onIncomingDamage(sp, d, source, amount)) return false;
        }
        return true;
    }

    /** 버프/디버프 배율 (용맹·불협화음·수호) + 막기(F) 피해 감소. */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.getWorld().isClient) return amount;
        if (!source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            float mult = 1f;
            if (source.getAttacker() instanceof LivingEntity la && la != victim) {
                net.minecraft.entity.effect.StatusEffectInstance v = la.getStatusEffect(AWEffects.VALOR);
                if (v != null) mult *= 1f + 0.15f * (v.getAmplifier() + 1);
                if (la.hasStatusEffect(AWEffects.DISSONANCE)) mult *= 0.8f;
                // 되살린 몹은 피해 절반
                if (com.abilitywar.ability.warlock.Necromancy.isRaised(la)) mult *= 0.5f;
            }
            net.minecraft.entity.effect.StatusEffectInstance ag = victim.getStatusEffect(AWEffects.AEGIS);
            if (ag != null) mult *= Math.max(0.4f, 1f - 0.15f * (ag.getAmplifier() + 1));
            amount *= mult;
            // RPG 장비: 능력 피해 / 처형 / 치명타, 받는 피해 감소
            if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity pa && pa != victim)
                amount = com.abilitywar.gear.GearEffects.outgoing(pa, victim, source, amount);
            else com.abilitywar.gear.GearEffects.lastCrit = false;
            amount = com.abilitywar.gear.GearEffects.incoming(victim, amount);
            amount = com.abilitywar.ability.guardian.GuardianAbility.incoming(victim, source, amount);
        }
        if (!(victim instanceof ServerPlayerEntity sp)) return amount;
        AbilityData d = data(sp);
        if (!d.blocking || source.isIn(AWDamage.TAG_UNBLOCKABLE)) return amount;
        Entity src = source.getSource() != null ? source.getSource() : source.getAttacker();
        if (src == null) return amount;
        Vec3d look = Combat.horizontal(sp.getRotationVec(1f));
        Vec3d to = Combat.horizontal(src.getPos().subtract(sp.getPos()));
        if (look.dotProduct(to) < 0.1) return amount; // 뒤/옆에서 맞음
        float factor = source.isIn(AWDamage.TAG_ABILITY_SKILL) ? 0.5f : 0.2f;
        float result = amount * factor;
        com.abilitywar.rpg.RpgManager.onTanked(sp, amount - result);
        ServerWorld world = sp.getServerWorld();
        NbtCompound n = new NbtCompound();
        n.putInt("e", sp.getId());
        n.putString("ab", ((AWPlayer) sp).aw$getAbilityKey());
        AWNet.fx(world, sp.getPos(), "block_hit", n);
        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), AWSounds.VOID_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f + sp.getRandom().nextFloat() * 0.3f);
        AWNet.damageNumber(sp, amount - result, AWNet.DMG_TEXT_BLOCK, source.getAttacker());
        AWNet.anim(sp, d.ability() != null ? d.ability().key + ".block_hit" : "common.flinch");
        return result;
    }

    /** 실제로 체력이 깎인 뒤: 데미지 숫자, 궁 게이지. */
    public static void afterDamage(LivingEntity victim, DamageSource source, float lost, byte kindHint) {
        if (victim.getWorld().isClient || lost <= 0.001f) return;
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) || source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) return;
        Entity attacker = source.getAttacker();
        if (attacker != null && attacker != victim) com.abilitywar.ability.warlock.Necromancy.onHit(attacker, victim);
        com.abilitywar.ability.warlock.Necromancy.onHurt(victim, lost);
        byte kind = kindHint;
        if (kind < 0) {
            if (source.isIn(AWDamage.TAG_ULTIMATE)) kind = AWNet.DMG_ULT;
            else if (source.isIn(AWDamage.TAG_ABILITY_SKILL) || source.isOf(AWDamage.GRAB)) kind = AWNet.DMG_SKILL;
            else kind = AWNet.DMG_NORMAL;
        }
        if (com.abilitywar.gear.GearEffects.lastCrit && kind != AWNet.DMG_ULT) kind = AWNet.DMG_CRIT;
        com.abilitywar.gear.GearEffects.lastCrit = false;
        AWNet.damageNumber(victim, lost, kind, attacker);
        if (attacker instanceof ServerPlayerEntity gp && gp != victim) com.abilitywar.gear.GearEffects.afterHit(gp, victim, source, lost);
        com.abilitywar.gear.GearEffects.thorns(victim, source, lost);
        boolean ult = source.isIn(AWDamage.TAG_ULTIMATE);
        if (attacker instanceof ServerPlayerEntity ap && ap != victim && !ult) {
            AbilityData ad = data(ap);
            if (ad.ability() != null) ad.ability().onDealtDamage(ap, ad, lost);
        }
        if (victim instanceof ServerPlayerEntity vp) {
            com.abilitywar.rpg.RpgManager.onTanked(vp, lost);
            AbilityData vd = data(vp);
            if (vd.ability() != null) vd.ability().onDamaged(vp, vd, lost);
        }
    }

    public static void onDeathOrRespawn(ServerPlayerEntity player) {
        AbilityData d = data(player);
        Ability a = d.ability();
        if (a != null) a.reset(player, d);
        ServerActions.cancelFor(player);
        setBlocking(player, false);
        d.resetTransient();
    }

    private AbilityManager() {
    }
}
