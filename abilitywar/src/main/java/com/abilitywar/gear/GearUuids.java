package com.abilitywar.gear;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** 능력치 수정치 UUID (자리 + 능력치마다 고정). */
public final class GearUuids {
    public static UUID of(String slot, GearStat stat) {
        return UUID.nameUUIDFromBytes(("abilitywar_gear:" + slot + ":" + stat.key).getBytes(StandardCharsets.UTF_8));
    }

    public static UUID set(GearStat stat) {
        return UUID.nameUUIDFromBytes(("abilitywar_gear_set:" + stat.key).getBytes(StandardCharsets.UTF_8));
    }

    private GearUuids() {
    }
}
