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
            GameRuleRegistry.register("abilitywarSelfSelect", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
    /** 궁극기 연출(시네마틱)이 재생되는 반경 (블록). */
    public static final GameRules.Key<GameRules.IntRule> CINEMATIC_RADIUS =
            GameRuleRegistry.register("abilitywarCinematicRadius", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(10, 0, 64));
    /** 궁극기 앞의 각성 연출 (2초: 시간 정지 · 얼굴 클로즈업 · 컷인). 끄면 바로 궁극기. */
    public static final GameRules.Key<GameRules.BooleanRule> ULT_PRELUDE =
            GameRuleRegistry.register("abilitywarUltPrelude", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    /** 궁극기 게이지 충전 배율 (%). */
    public static final GameRules.Key<GameRules.IntRule> ULT_CHARGE_PERCENT =
            GameRuleRegistry.register("abilitywarUltChargePercent", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(100, 0, 10000));

    /** 직업(능력)이 없는 플레이어가 접속하면 직업 선택 화면을 띄움 (고르기 전에는 닫을 수 없음). */
    public static final GameRules.Key<GameRules.BooleanRule> FIRST_JOIN_SELECT =
            GameRuleRegistry.register("abilitywarFirstJoinSelect", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    /** 2차 전직 퀘스트를 받을 수 있는 레벨. */
    public static final GameRules.Key<GameRules.IntRule> JOB_LEVEL =
            GameRuleRegistry.register("abilitywarJobLevel", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(30, 1, 100));
    /** 3차 전직 ("대") 퀘스트를 받을 수 있는 레벨. */
    public static final GameRules.Key<GameRules.IntRule> GRAND_LEVEL =
            GameRuleRegistry.register("abilitywarGrandLevel", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(60, 1, 100));
    /** 야생 보스가 나타나는지. */
    public static final GameRules.Key<GameRules.BooleanRule> WILD_BOSSES =
            GameRuleRegistry.register("abilitywarWildBosses", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));
    /** 5분마다 플레이어마다 야생 보스가 나올 확률 (%). */
    public static final GameRules.Key<GameRules.IntRule> WILD_BOSS_CHANCE =
            GameRuleRegistry.register("abilitywarWildBossChance", GameRules.Category.SPAWNING, GameRuleFactory.createIntRule(15, 0, 100));
    /** 경험치 배율 (%). */
    public static final GameRules.Key<GameRules.IntRule> XP_RATE =
            GameRuleRegistry.register("abilitywarXpRate", GameRules.Category.PLAYER, GameRuleFactory.createIntRule(100, 0, 100000));
    /** 장비 착용 레벨 제한을 쓸지. */
    public static final GameRules.Key<GameRules.BooleanRule> GEAR_LEVEL =
            GameRuleRegistry.register("abilitywarGearLevel", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    public static void init() {
    }

    private AWGameRules() {
    }
}
