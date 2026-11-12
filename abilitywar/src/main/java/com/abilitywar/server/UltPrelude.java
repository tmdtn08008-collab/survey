package com.abilitywar.server;

import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.combat.Combat;
import com.abilitywar.data.AbilityData;
import com.abilitywar.net.AWNet;
import com.abilitywar.registry.AWGameRules;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * 궁극기 각성 연출 (모든 2차 · 3차 직업 궁극기 앞, 2초).
 * <ul>
 *   <li>시간 정지: 주변 (연출 반경) 플레이어 · 몹이 멈추고, 화면이 능력 색 흑백으로.</li>
 *   <li>카메라: 얼굴 클로즈업 → 낮은 각도로 돌며 올려다봄 → 확 빠지며 위로 (CameraController "&lt;능력&gt;_pre").</li>
 *   <li>화면: 집중선 · 궁극기 이름 컷인 띠 · 마지막에 번쩍 (CinematicOverlay).</li>
 *   <li>몸: 웅크려 힘을 모으다 팔을 활짝 펼침 (common.ult_awaken), 발밑 마법진 · 모여드는 빛 · 번개 · 충격파 (Effects.UltAwaken).</li>
 * </ul>
 * 끝나면 원래 궁극기를 씀 (게이지는 그때 씀). 게임 규칙 abilitywarUltPrelude 로 끌 수 있음.
 */
public final class UltPrelude {
    public static final int LENGTH = 40;
    static final Set<String> KEYS = Set.of("void", "dragon", "blade", "blood", "cantor", "warlock", "angel", "guardian", "paladin");

    public static boolean applies(ServerPlayerEntity p, Ability a) {
        return KEYS.contains(a.key) && p.getWorld().getGameRules().getBoolean(AWGameRules.ULT_PRELUDE);
    }

    public static void start(ServerPlayerEntity p, AbilityData d, Ability a, PacketByteBuf extra) {
        ServerWorld w = p.getServerWorld();
        PacketByteBuf copy = PacketByteBufs.copy(extra);
        long now = d.now();
        int radius = w.getGameRules().getInt(AWGameRules.CINEMATIC_RADIUS);
        // 시전자: 멈춤 · 무적
        d.frozenUntil = now + LENGTH;
        d.invulnUntil = now + LENGTH;
        d.setBusy(LENGTH + 2);
        AbilityManager.setBlocking(p, false);
        Combat.setVelocity(p, Vec3d.ZERO);
        Combat.stun(p, LENGTH, p);
        Vec3d ahead = p.getPos().add(Combat.horizontal(p.getRotationVec(1f)).multiply(6));
        for (ServerPlayerEntity o : PlayerLookup.around(w, p.getPos(), radius)) {
            if (o.isSpectator()) continue;
            AWNet.cinematic(o, p, a.key + "_pre", LENGTH, ahead);
            if (o != p) {
                AbilityData od = AbilityManager.data(o);
                od.setNow(now);
                od.frozenUntil = now + LENGTH;
                od.invulnUntil = now + LENGTH;
                Combat.stun(o, LENGTH, p);
                Combat.setVelocity(o, Vec3d.ZERO);
                AbilityManager.setBlocking(o, false);
            }
        }
        // 시간 정지: 주변 몹
        for (Entity e : w.getOtherEntities(p, new Box(p.getBlockPos()).expand(radius + 4), e -> e instanceof MobEntity)) {
            Combat.stun((LivingEntity) e, LENGTH + 2, p);
            Combat.setVelocity(e, Vec3d.ZERO);
        }
        AWNet.anim(p, "common.ult_awaken");
        NbtCompound n = new NbtCompound();
        n.putInt("c", p.getId());
        n.putInt("d", LENGTH);
        n.putInt("col", a.color & 0xFFFFFF);
        n.putString("k", a.key);
        AWNet.fx(w, p.getPos(), "ult_awaken", n);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 2.5f, 1.15f);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 2.0f, 0.6f);
        ServerActions.start(new TimedAction(p) {
            @Override
            protected boolean step(int t) {
                if (t == 14) w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.0f, 0.8f);
                if (t == 24) w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 3.5f, 0.9f);
                if (t == 30) w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 3.0f, 1.4f);
                if (t < LENGTH) return false;
                // 각성 끝: 충격파 소리 + 원래 궁극기
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 1.6f, 1.3f);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.6f);
                d.setBusy(0);
                d.frozenUntil = 0;
                Combat.unstun(p);
                d.dirty = true;
                if (AbilityManager.data(p).ability() == a) a.useSkill(p, d, SkillSlot.ULTIMATE, copy);
                return true;
            }

            @Override
            protected void cleanup() {
                copy.release();
            }
        });
        d.dirty = true;
    }

    private UltPrelude() {
    }
}
