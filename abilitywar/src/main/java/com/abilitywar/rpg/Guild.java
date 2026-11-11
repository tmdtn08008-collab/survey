package com.abilitywar.rpg;

import com.abilitywar.gear.GearGrade;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 모험가 길드: 접수원에게 가입하면 F 등급. 자기 등급의 퀘스트만 받을 수 있고 (F 는 F 퀘스트만),
 * 퀘스트를 끝내면 길드 포인트. 포인트를 채우고 레벨이 되면 접수원에게 승급 심사 → 다음 등급 (최고 SSS).
 * 길드 퀘스트는 전직 퀘스트와 따로 하나씩 진행 (둘을 같이 해도 됨), 몇 번이든 다시 받을 수 있음.
 */
public final class Guild {
    public static final String GIVER = "guild";
    public static final String[] RANKS = {"F", "E", "D", "C", "B", "A", "S", "SS", "SSS"};
    public static final int TOP = RANKS.length - 1;
    /** 이 등급이 되려면 필요한 레벨. */
    public static final int[] MIN_LEVEL = {1, 5, 12, 20, 30, 40, 55, 70, 85};
    /** 이 등급에서 다음 등급으로 오르는 데 필요한 길드 포인트. */
    public static final int[] PROMOTE_POINTS = {30, 48, 60, 80, 100, 120, 160, 200, 0};
    public static final int[] COLORS = {0xA8A8A8, 0x6ACB5A, 0x4FC3C3, 0x5A8CFF, 0xB070FF, 0xFF9A3A, 0xFF4A4A, 0xFFD040, 0xFFF0A0};

    /** 길드 퀘스트: 목표는 전직 퀘스트와 같은 종류. 보상: 포인트 · 경험치 · 에메랄드 · 장비 (확률). */
    public record GQuest(String id, int rank, List<JobQuests.Objective> objectives, int points, int xp, int gold, GearGrade gear, float gearChance) {
    }

    public static final Map<String, GQuest> ALL = new LinkedHashMap<>();

    static final int[] POINTS = {10, 12, 15, 20, 25, 30, 40, 50, 60};
    static final int[] XP = {80, 200, 400, 700, 1100, 1600, 2400, 3500, 5000};
    /** 보상 돈 (G). */
    static final int[] GOLD = {60, 120, 200, 320, 480, 700, 1000, 1500, 2400};
    static final GearGrade[] GEAR = {GearGrade.COMMON, GearGrade.GOOD, GearGrade.GOOD, GearGrade.RARE, GearGrade.RARE, GearGrade.HERO, GearGrade.LEGENDARY, GearGrade.MYTHIC, GearGrade.SECRET};
    static final float[] GEAR_CHANCE = {0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f, 0.6f, 0.7f, 0.8f};

    static void q(int rank, int n, JobQuests.Objective... obj) {
        String id = "g_" + RANKS[rank].toLowerCase() + n;
        ALL.put(id, new GQuest(id, rank, List.of(obj), POINTS[rank], XP[rank], GOLD[rank], GEAR[rank], GEAR_CHANCE[rank]));
    }

    static JobQuests.Objective kill(String t, int n) {
        return new JobQuests.Objective(JobQuests.Type.KILL, t, n);
    }

    static JobQuests.Objective night(String t, int n) {
        return new JobQuests.Objective(JobQuests.Type.KILL_NIGHT, t, n);
    }

    static JobQuests.Objective item(String t, int n) {
        return new JobQuests.Objective(JobQuests.Type.ITEM, t, n);
    }

    static JobQuests.Objective boss(int n) {
        return new JobQuests.Objective(JobQuests.Type.BOSS, "", n);
    }

    static {
        // F: 처음 모험가
        q(0, 1, kill("minecraft:zombie", 10));
        q(0, 2, kill("minecraft:spider", 8));
        q(0, 3, item("minecraft:wheat", 20));
        q(0, 4, kill("abilitywar:goblin", 6));
        // E
        q(1, 1, kill("minecraft:skeleton", 12));
        q(1, 2, kill("minecraft:creeper", 6));
        q(1, 3, item("minecraft:leather", 12));
        q(1, 4, kill("abilitywar:goblin", 15));
        // D
        q(2, 1, night("#undead", 20));
        q(2, 2, kill("abilitywar:orc", 6));
        q(2, 3, item("minecraft:iron_ingot", 16));
        q(2, 4, kill("minecraft:witch", 3));
        // C
        q(3, 1, kill("abilitywar:skeleton_knight", 8));
        q(3, 2, kill("minecraft:enderman", 6));
        q(3, 3, kill("abilitywar:wisp", 6));
        q(3, 4, item("minecraft:gold_ingot", 16));
        // B
        q(4, 1, kill("minecraft:blaze", 10));
        q(4, 2, kill("abilitywar:orc", 15));
        q(4, 3, kill("#hostile", 60));
        q(4, 4, item("minecraft:diamond", 3));
        // A
        q(5, 1, kill("minecraft:wither_skeleton", 10));
        q(5, 2, boss(1));
        q(5, 3, kill("minecraft:ghast", 4));
        q(5, 4, item("minecraft:ender_pearl", 16));
        // S
        q(6, 1, boss(2));
        q(6, 2, kill("abilitywar:fallen_king", 1));
        q(6, 3, kill("#hostile", 150));
        q(6, 4, kill("abilitywar:frost_witch", 1));
        // SS
        q(7, 1, boss(3));
        q(7, 2, kill("abilitywar:ancient_wyvern", 1));
        q(7, 3, kill("abilitywar:ember_colossus", 1));
        q(7, 4, item("minecraft:netherite_scrap", 4));
        // SSS
        q(8, 1, kill("minecraft:wither", 1));
        q(8, 2, kill("minecraft:ender_dragon", 1));
        q(8, 3, boss(5));
        q(8, 4, kill("minecraft:warden", 1));
    }

    /** 길드 의뢰 보상: 강화석 (등급 + 1). */
    public static int stones(int rank) {
        return rank + 1;
    }

    /** 길드 의뢰 보상 마정석의 등급: F~D 초록, C · B 푸른, A 보랏빛, S 황금, SS 붉은, SSS 청록. */
    public static GearGrade crystal(int rank) {
        return new GearGrade[]{GearGrade.GOOD, GearGrade.GOOD, GearGrade.GOOD, GearGrade.RARE, GearGrade.RARE, GearGrade.HERO,
                GearGrade.LEGENDARY, GearGrade.MYTHIC, GearGrade.SECRET}[Math.max(0, Math.min(TOP, rank))];
    }

    /** 길드 의뢰 보상: 승급석 (C 등급부터 1, S 부터 2). */
    public static int promotionStones(int rank) {
        return rank >= 6 ? 2 : rank >= 3 ? 1 : 0;
    }

    public static List<GQuest> forRank(int rank) {
        List<GQuest> out = new ArrayList<>();
        for (GQuest q : ALL.values()) if (q.rank() == rank) out.add(q);
        return out;
    }

    public static String rankName(int rank) {
        return rank < 0 ? "-" : RANKS[Math.min(rank, TOP)];
    }

    public static int color(int rank) {
        return rank < 0 ? 0x707070 : COLORS[Math.min(rank, TOP)];
    }

    /** 받을 수 있는지 (서버·클라이언트 공용). @return 안 되는 이유 번역 키, 되면 null. */
    @Nullable
    public static String check(GQuest q, int rank, String active) {
        if (rank < 0) return "guild.abilitywar.err.not_member";
        if (q.rank() != rank) return "guild.abilitywar.err.rank";
        if (!active.isEmpty()) return "guild.abilitywar.err.has_quest";
        return null;
    }

    /** 승급할 수 있는지. @return 안 되는 이유 번역 키, 되면 null. */
    @Nullable
    public static String checkPromote(int rank, int points, int level) {
        if (rank < 0) return "guild.abilitywar.err.not_member";
        if (rank >= TOP) return "guild.abilitywar.err.top";
        if (points < PROMOTE_POINTS[rank]) return "guild.abilitywar.err.points";
        if (level < MIN_LEVEL[rank + 1]) return "guild.abilitywar.err.level";
        return null;
    }

    private Guild() {
    }
}
