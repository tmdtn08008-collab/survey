package com.abilitywar.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** RPG 월드 공통 (수도 위치 등). 수도 안 = 안전 지대 (야생 보스가 안 나옴). */
public final class RpgWorld {
    /** 수도 중심 (월드마다, 서버가 시작할 때 정해짐). null 이면 아직 모름. */
    public static BlockPos capital;
    public static final int SAFE_RADIUS = 220;

    public static boolean inSafeZone(ServerWorld w, BlockPos pos) {
        return capital != null && w.getRegistryKey() == net.minecraft.world.World.OVERWORLD && capital.getSquaredDistance(pos.getX(), capital.getY(), pos.getZ()) < SAFE_RADIUS * SAFE_RADIUS;
    }

    private RpgWorld() {
    }
}
