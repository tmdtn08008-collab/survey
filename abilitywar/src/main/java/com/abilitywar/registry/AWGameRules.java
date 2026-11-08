package com.abilitywar.registry;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public final class AWGameRules {
    /** 쿨타임 없음 (테스트용). */
    public static final GameRules.Key<GameRules.BooleanRule> NO_COOLDOWN =
            GameRuleRegistry.register("abilitywarNoCooldown", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
    /** 플레이어가 능력 선택 화면(B)으로 직접 능력을 고를 수 있는지. */
    public static final GameRules.Key<GameRules.BooleanRule> SELF_SELECT =
            GameRuleRegistry.register("abilitywarSelfSelect", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    /** 궁극기 연출(시네마틱)이 재생되는 반경 (블록). */
    public static final GameRules.Key<GameRules.IntRule> CINEMATIC_RADIUS =
            GameRuleRegistry.register("abilitywarCinematicRadius", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(10, 0, 64));
    /** 궁극기 게이지 충전 배율 (%). */
    public static final GameRules.Key<GameRules.IntRule> ULT_CHARGE_PERCENT =
            GameRuleRegistry.register("abilitywarUltChargePercent", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(100, 0, 10000));

    public static void init() {
    }

    private AWGameRules() {
    }
}
