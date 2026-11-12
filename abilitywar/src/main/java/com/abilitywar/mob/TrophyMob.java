package com.abilitywar.mob;

import net.minecraft.item.Item;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

/** 플레이어가 쓰러뜨리면 전리품 (재료) 을 떨어뜨리는 몹 (Shops.drops) + 처치 G 덤 (RpgManager.goldFor). */
public interface TrophyMob {
    @Nullable
    Item trophy();

    /** 떨어뜨릴 확률 (%). */
    int trophyChance();

    default int trophyCount(Random r) {
        return 1;
    }

    /** 처치 G 에 더하는 값. */
    default long goldBonus() {
        return 0;
    }
}
