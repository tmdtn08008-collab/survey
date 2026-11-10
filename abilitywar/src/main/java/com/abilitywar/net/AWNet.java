package com.abilitywar.net;

import com.abilitywar.AbilityWar;
import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.data.AWPlayer;
import com.abilitywar.data.AbilityData;
import com.abilitywar.registry.AWEffects;
import com.abilitywar.registry.AWGameRules;
import com.abilitywar.server.AbilityManager;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class AWNet {
    // C2S
    public static final Identifier C2S_SKILL = AbilityWar.id("skill");
    public static final Identifier C2S_ATTACK = AbilityWar.id("attack");
    public static final Identifier C2S_BLOCK = AbilityWar.id("block");
    public static final Identifier C2S_SELECT = AbilityWar.id("select");
    // S2C
    public static final Identifier S2C_STATE = AbilityWar.id("state");
    public static final Identifier S2C_OPEN_SELECT = AbilityWar.id("open_select");
    public static final Identifier S2C_SOULS = AbilityWar.id("souls");
    public static final Identifier C2S_GRIMOIRE = AbilityWar.id("grimoire");
    public static final Identifier C2S_SOUL_ACTION = AbilityWar.id("soul_action");
    public static final Identifier S2C_ANIM = AbilityWar.id("anim");
    public static final Identifier S2C_FX = AbilityWar.id("fx");
    public static final Identifier S2C_DAMAGE = AbilityWar.id("damage");
    public static final Identifier S2C_CINEMATIC = AbilityWar.id("cinematic");

    // damage number kinds
    public static final byte DMG_NORMAL = 0;
    public static final byte DMG_CRIT = 1;
    public static final byte DMG_SKILL = 2;
    public static final byte DMG_ULT = 3;
    public static final byte DMG_BLOCKED = 4;
    public static final byte DMG_TEXT_COUNTER = 10;
    public static final byte DMG_TEXT_BLOCK = 11;
    public static final byte DMG_TEXT_IMMUNE = 12;
    public static final byte DMG_TEXT_CHAIN = 13;
    public static final byte DMG_TEXT_ARMOR = 14;
    /** 회복량 (초록색 +숫자). */
    public static final byte DMG_HEAL = 15;

    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_SKILL, (server, player, handler, buf, sender) -> {
            int slotIdx = buf.readByte();
            PacketByteBuf extra = PacketByteBufs.copy(buf);
            server.execute(() -> {
                SkillSlot slot = SkillSlot.byIndex(slotIdx);
                if (slot != null) AbilityManager.handleSkill(player, slot, extra);
                extra.release();
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_ATTACK, (server, player, handler, buf, sender) -> {
            int target = buf.readVarInt();
            server.execute(() -> AbilityManager.handleAttack(player, target));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_BLOCK, (server, player, handler, buf, sender) -> {
            boolean pressed = buf.readBoolean();
            server.execute(() -> AbilityManager.handleBlock(player, pressed));
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_GRIMOIRE, (server, player, handler, buf, sender) ->
                server.execute(() -> com.abilitywar.ability.warlock.WarlockAbility.openGrimoire(player)));
        ServerPlayNetworking.registerGlobalReceiver(C2S_SOUL_ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readByte();
            int id = buf.readVarInt();
            server.execute(() -> {
                if ("warlock".equals(((com.abilitywar.data.AWPlayer) player).aw$getAbilityKey()))
                    com.abilitywar.ability.warlock.WarlockAbility.soulAction(player, action, id);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(C2S_SELECT, (server, player, handler, buf, sender) -> {
            String key = buf.readString(64);
            server.execute(() -> {
                // 아직 직업이 없으면 (처음 접속) 게임 규칙과 상관없이 고를 수 있음
                boolean first = ((com.abilitywar.data.AWPlayer) player).aw$getAbilityKey().isEmpty();
                if (first && key.isEmpty()) return;
                // 처음 고를 때는 1차 직업만 (관리자는 아무거나)
                if (first && !com.abilitywar.rpg.ClassTree.isBase(key) && !player.hasPermissionLevel(2)) return;
                if (!first && !player.getWorld().getGameRules().getBoolean(AWGameRules.SELF_SELECT) && !player.hasPermissionLevel(2)) {
                    player.sendMessage(Text.translatable("abilitywar.msg.select_locked"), true);
                    return;
                }
                Ability a = key.isEmpty() ? null : Abilities.byString(key);
                if (!key.isEmpty() && a == null) return;
                AbilityManager.setAbility(player, a);
            });
        });
    }

    // ---------------------------------------------------------------- senders

    /** 직업 선택 화면 열기 (forced = 고르기 전에는 닫을 수 없음). */
    public static void openSelect(ServerPlayerEntity p, boolean forced) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(forced);
        send(p, S2C_OPEN_SELECT, buf);
    }

    private static void send(ServerPlayerEntity p, Identifier id, PacketByteBuf buf) {
        if (ServerPlayNetworking.canSend(p, id)) {
            ServerPlayNetworking.send(p, id, PacketByteBufs.copy(buf));
        }
    }

    /** 엔티티를 보고 있는 플레이어 + (플레이어면) 본인. */
    public static Collection<ServerPlayerEntity> watchers(Entity e) {
        Set<ServerPlayerEntity> set = new HashSet<>(PlayerLookup.tracking(e));
        if (e instanceof ServerPlayerEntity sp) set.add(sp);
        return set;
    }

    public static void sendState(ServerPlayerEntity player) {
        AbilityData d = ((AWPlayer) player).aw$data();
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(((AWPlayer) player).aw$getAbilityKey());
        buf.writeFloat(d.ult);
        buf.writeByte(SkillSlot.VALUES.length);
        for (SkillSlot s : SkillSlot.VALUES) {
            buf.writeVarInt(d.remaining(s));
            buf.writeVarInt(d.cdTotal[s.ordinal()]);
        }
        buf.writeBoolean(d.shadowForm);
        buf.writeVarInt((int) Math.max(0, d.shadowUntil - d.now()));
        buf.writeVarInt((int) Math.max(0, d.busyUntil - d.now()));
        buf.writeVarInt(d.extra);
        send(player, S2C_STATE, buf);
        buf.release();
    }

    /** 엔티티에 액션 애니메이션 재생. */
    public static void anim(Entity e, String anim) {
        anim(e, anim, 1.0f);
    }

    public static void anim(Entity e, String anim, float speed) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(e.getId());
        buf.writeString(anim);
        buf.writeFloat(speed);
        for (ServerPlayerEntity p : watchers(e)) send(p, S2C_ANIM, buf);
        buf.release();
    }

    /** 본인을 제외한 주변 플레이어에게만 애니메이션 (본인은 클라이언트에서 미리 재생). */
    public static void animOthers(ServerPlayerEntity p, String anim) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(p.getId());
        buf.writeString(anim);
        buf.writeFloat(1f);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) if (o != p) send(o, S2C_ANIM, buf);
        buf.release();
    }

    /** 범위 내 플레이어에게 이펙트 생성. */
    public static void fx(ServerWorld world, Vec3d pos, String type, NbtCompound data) {
        data.putDouble("x", pos.x);
        data.putDouble("y", pos.y);
        data.putDouble("z", pos.z);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(type);
        buf.writeNbt(data);
        for (ServerPlayerEntity p : PlayerLookup.around(world, pos, 128)) send(p, S2C_FX, buf);
        buf.release();
    }

    public static void fx(ServerWorld world, Vec3d pos, String type) {
        fx(world, pos, type, new NbtCompound());
    }

    /** 특정 플레이어에게만 이펙트 (화면 흔들림 등). */
    public static void fxTo(ServerPlayerEntity player, String type, NbtCompound data) {
        Vec3d pos = player.getPos();
        data.putDouble("x", pos.x);
        data.putDouble("y", pos.y);
        data.putDouble("z", pos.z);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(type);
        buf.writeNbt(data);
        send(player, S2C_FX, buf);
        buf.release();
    }

    public static void shake(Entity e, float intensity, int duration) {
        if (e instanceof ServerPlayerEntity sp) {
            NbtCompound n = new NbtCompound();
            n.putFloat("i", intensity);
            n.putInt("d", duration);
            fxTo(sp, "shake", n);
        }
    }

    /** 주변에 흔들림 (거리 감쇠). */
    public static void shakeAround(ServerWorld world, Vec3d pos, float intensity, int duration, double radius) {
        for (ServerPlayerEntity p : PlayerLookup.around(world, pos, radius)) {
            double d = p.getPos().distanceTo(pos);
            float f = (float) (intensity * (1.0 - d / radius));
            if (f > 0.02f) shake(p, f, duration);
        }
    }

    public static void damageNumber(LivingEntity victim, float amount, byte kind, Entity attacker) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(victim.getId());
        buf.writeFloat(amount);
        buf.writeByte(kind);
        buf.writeDouble(victim.getX());
        buf.writeDouble(victim.getY() + victim.getHeight() * 0.85);
        buf.writeDouble(victim.getZ());
        Set<ServerPlayerEntity> to = new HashSet<>();
        for (ServerPlayerEntity p : PlayerLookup.tracking(victim)) {
            if (p.squaredDistanceTo(victim) < 40 * 40) to.add(p);
        }
        if (victim instanceof ServerPlayerEntity sp) to.add(sp);
        if (attacker instanceof ServerPlayerEntity sp) to.add(sp);
        for (ServerPlayerEntity p : to) send(p, S2C_DAMAGE, buf);
        buf.release();
    }

    /** 궁극기 연출. */
    public static void cinematic(ServerPlayerEntity to, Entity caster, String type, int duration, Vec3d target) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(caster.getId());
        buf.writeString(type);
        buf.writeVarInt(duration);
        buf.writeDouble(target.x);
        buf.writeDouble(target.y);
        buf.writeDouble(target.z);
        send(to, S2C_CINEMATIC, buf);
        buf.release();
    }

    public static boolean stunned(LivingEntity e) {
        return e.hasStatusEffect(AWEffects.STUN);
    }

    private AWNet() {
    }
}
