package com.abilitywar.data;

import com.abilitywar.ability.Abilities;
import com.abilitywar.ability.Ability;
import com.abilitywar.ability.SkillSlot;
import com.abilitywar.registry.AWGameRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 플레이어 한 명의 능력 상태 (서버 기준).
 */
public class AbilityData {
    public static final float ULT_MAX = 100f;
    private static final int N = SkillSlot.VALUES.length;

    private final PlayerEntity player;
    private long now;

    public final long[] cdEnd = new long[N];
    public final int[] cdTotal = new int[N];
    public float ult;

    public boolean blocking;
    public long busyUntil;
    public int combo;
    public long lastAttack;
    public long nextAttack;

    public long counterUntil;
    public long invulnUntil;
    /** 슈퍼아머: 다른 사람의 스킬로 밀려나지 않음. */
    public long superArmorUntil;
    public long lastArmorText;
    public long frozenUntil;

    /** 능력마다 쓰는 작은 상태값 (HUD 로 보냄). 성가사: 선율(0~2) + 음표 수 * 4. */
    public int extra;
    public boolean shadowForm;
    public long shadowUntil;
    @Nullable
    public Vec3d shadowStart;

    public boolean dirty = true;
    public boolean forcedNoGravity;

    public AbilityData(PlayerEntity player) {
        this.player = player;
    }

    @Nullable
    private int[] pendingRemaining;

    public void setNow(long now) {
        this.now = now;
        if (pendingRemaining != null) {
            for (int i = 0; i < N && i < pendingRemaining.length; i++) cdEnd[i] = now + pendingRemaining[i];
            pendingRemaining = null;
            dirty = true;
        }
    }

    public long now() {
        return now;
    }

    @Nullable
    public Ability ability() {
        return Abilities.byString(((AWPlayer) player).aw$getAbilityKey());
    }

    public boolean isOnCooldown(SkillSlot slot) {
        return cdEnd[slot.ordinal()] > now;
    }

    public int remaining(SkillSlot slot) {
        return (int) Math.max(0, cdEnd[slot.ordinal()] - now);
    }

    public void startCooldown(SkillSlot slot, int ticks) {
        if (player instanceof ServerPlayerEntity sp && sp.getWorld().getGameRules().getBoolean(AWGameRules.NO_COOLDOWN)) {
            ticks = Math.min(ticks, 10);
        }
        cdEnd[slot.ordinal()] = now + ticks;
        cdTotal[slot.ordinal()] = ticks;
        dirty = true;
    }

    public void clearCooldowns() {
        for (int i = 0; i < N; i++) {
            cdEnd[i] = 0;
            cdTotal[i] = 0;
        }
        dirty = true;
    }

    public boolean isBusy() {
        return busyUntil > now;
    }

    public void setBusy(int ticks) {
        busyUntil = Math.max(busyUntil, now + ticks);
    }

    public boolean hasSuperArmor() {
        return superArmorUntil > now;
    }

    public boolean isInvulnerable() {
        return invulnUntil > now || shadowForm;
    }

    public boolean isFrozen() {
        return frozenUntil > now;
    }

    public boolean isCountering() {
        return counterUntil > now;
    }

    public void addUlt(float amount) {
        if (amount <= 0) return;
        if (player instanceof ServerPlayerEntity sp) {
            amount *= sp.getWorld().getGameRules().getInt(AWGameRules.ULT_CHARGE_PERCENT) / 100f;
        }
        float before = ult;
        ult = Math.min(ULT_MAX, ult + amount);
        if (ult != before) dirty = true;
    }

    public boolean ultReady() {
        return ult >= ULT_MAX - 0.001f;
    }

    /** 일시 상태 초기화 (사망, 능력 변경 등). */
    public void resetTransient() {
        blocking = false;
        busyUntil = 0;
        counterUntil = 0;
        invulnUntil = 0;
        frozenUntil = 0;
        superArmorUntil = 0;
        shadowForm = false;
        shadowStart = null;
        combo = 0;
        dirty = true;
        AWPlayer p = (AWPlayer) player;
        p.aw$setFlag(AWPlayer.FLAG_BLOCKING, false);
        p.aw$setFlag(AWPlayer.FLAG_COUNTER, false);
        p.aw$setFlag(AWPlayer.FLAG_SHADOW, false);
        p.aw$setFlag(AWPlayer.FLAG_CINEMATIC, false);
        p.aw$setFlag(AWPlayer.FLAG_TRANSFORMED, false);
    }

    public void writeNbt(NbtCompound nbt) {
        NbtCompound tag = new NbtCompound();
        tag.putString("Ability", ((AWPlayer) player).aw$getAbilityKey());
        tag.putFloat("Ult", ult);
        int[] rem = new int[N];
        int[] tot = new int[N];
        for (int i = 0; i < N; i++) {
            rem[i] = (int) Math.max(0, cdEnd[i] - now);
            tot[i] = cdTotal[i];
        }
        tag.putIntArray("CdRemaining", rem);
        tag.putIntArray("CdTotal", tot);
        tag.putBoolean("ForcedNoGravity", forcedNoGravity);
        nbt.put("AbilityWar", tag);
    }

    public void readNbt(NbtCompound nbt) {
        if (!nbt.contains("AbilityWar")) return;
        NbtCompound tag = nbt.getCompound("AbilityWar");
        ((AWPlayer) player).aw$setAbilityKey(tag.getString("Ability"));
        ult = tag.getFloat("Ult");
        int[] rem = tag.getIntArray("CdRemaining");
        int[] tot = tag.getIntArray("CdTotal");
        for (int i = 0; i < N && i < tot.length; i++) cdTotal[i] = tot[i];
        pendingRemaining = rem;
        if (tag.getBoolean("ForcedNoGravity")) {
            player.setNoGravity(false);
        }
        dirty = true;
    }

    /** 다른 플레이어 객체(리스폰)로 복사. */
    public void copyFrom(AbilityData other) {
        ult = other.ult;
        for (int i = 0; i < N; i++) {
            cdEnd[i] = other.cdEnd[i];
            cdTotal[i] = other.cdTotal[i];
        }
        dirty = true;
    }
}
