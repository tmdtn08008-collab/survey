package com.abilitywar.client;

import com.abilitywar.ability.SkillSlot;
import com.abilitywar.registry.AWSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;

/** 로컬 플레이어의 능력 상태 (서버 동기화). */
public final class ClientState {
    public static final int N = SkillSlot.VALUES.length;
    public static String ability = "";
    public static float ult;
    public static float ultDisplay;
    public static final float[] remaining = new float[N];
    public static final int[] total = new int[N];
    public static final float[] readyFlash = new float[N];
    public static final float[] pressFlash = new float[N];
    public static boolean shadowForm;
    public static float shadowRemaining;
    public static float busy;
    /** 능력별 작은 상태값 (AbilityData.extra). */
    public static int extra;
    private static boolean ultWasReady;

    public static void read(PacketByteBuf buf) {
        String ab = buf.readString();
        float u = buf.readFloat();
        int n = buf.readByte();
        boolean changed = !ab.equals(ability);
        ability = ab;
        ult = u;
        if (changed) ultDisplay = u;
        for (int i = 0; i < n; i++) {
            int rem = buf.readVarInt();
            int tot = buf.readVarInt();
            if (i < N) {
                if (tot != total[i] || Math.abs(rem - remaining[i]) > 2 || changed) remaining[i] = rem;
                total[i] = tot;
            }
        }
        shadowForm = buf.readBoolean();
        shadowRemaining = buf.readVarInt();
        busy = buf.readVarInt();
        extra = buf.isReadable() ? buf.readVarInt() : 0;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < N; i++) {
            if (remaining[i] > 0) {
                remaining[i] = Math.max(0, remaining[i] - 1);
                if (remaining[i] == 0 && total[i] > 20) {
                    readyFlash[i] = 1f;
                    if (mc.player != null && !ability.isEmpty()) {
                        mc.getSoundManager().play(PositionedSoundInstance.master(AWSounds.UI_READY, 1.6f + i * 0.05f, 0.35f));
                    }
                }
            }
            readyFlash[i] = Math.max(0, readyFlash[i] - 0.06f);
            pressFlash[i] = Math.max(0, pressFlash[i] - 0.12f);
        }
        if (shadowRemaining > 0) shadowRemaining--;
        if (busy > 0) busy--;
        ultDisplay += (ult - ultDisplay) * 0.25f;
        boolean ready = ult >= 99.99f;
        if (ready && !ultWasReady && !ability.isEmpty() && mc.player != null) {
            readyFlash[SkillSlot.ULTIMATE.ordinal()] = 1f;
            mc.getSoundManager().play(PositionedSoundInstance.master(AWSounds.UI_ULT_READY, 1.0f, 0.8f));
        }
        ultWasReady = ready;
    }

    /** 0 = 사용 가능, 1 = 막 쿨타임 시작. */
    public static float cooldownFraction(int slot, float tickDelta) {
        if (total[slot] <= 0 || remaining[slot] <= 0) return 0;
        return Math.max(0, Math.min(1, (remaining[slot] - tickDelta) / total[slot]));
    }

    public static boolean ready(SkillSlot s) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        com.abilitywar.ability.Ability a = com.abilitywar.ability.Abilities.byString(ability);
        boolean cd = remaining[s.ordinal()] <= 0;
        if (a != null && mc.player != null) {
            if (s == SkillSlot.ULTIMATE) return a.clientUltReady(mc.player, ult);
            if (s == SkillSlot.PASSIVE) return a.clientPassiveReady(mc.player, ult, cd);
        }
        if (s == SkillSlot.ULTIMATE) return ult >= 99.99f;
        return cd;
    }

    public static void reset() {
        ability = "";
        ult = ultDisplay = 0;
        for (int i = 0; i < N; i++) {
            remaining[i] = 0;
            total[i] = 0;
        }
        shadowForm = false;
    }

    private ClientState() {
    }
}
