package com.abilitywar.client.anim;

import net.minecraft.client.MinecraftClient;

/** 일시정지 시 멈추는 클라이언트 애니메이션 시계 (틱 단위). 타격 정지(히트스톱) 시 느려진다. */
public final class ClientClock {
    private static long ticks;
    private static float base;
    private static float rate = 1f;
    private static float stop;

    public static void tick() {
        ticks++;
        base += rate;
        if (stop > 0) {
            stop = Math.max(0, stop - 1);
            rate = 0.12f;
        } else {
            rate = Math.min(1f, rate + 0.45f);
        }
    }

    public static void hitstop(float ticksAmount) {
        stop = Math.max(stop, ticksAmount);
        rate = 0.12f;
    }

    public static long ticks() {
        return ticks;
    }

    public static float now() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return base + mc.getTickDelta() * rate;
    }

    private ClientClock() {
    }
}
