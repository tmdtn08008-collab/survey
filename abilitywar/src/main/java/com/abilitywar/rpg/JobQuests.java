package com.abilitywar.rpg;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 전직 퀘스트.
 * <ul>
 * <li>JOB   : 1차 → 2차 (전직 교관, 레벨 abilitywarJobLevel)</li>
 * <li>GRAND : 2차 → 3차 "대" (전직 교관, 레벨 abilitywarGrandLevel). 성기사의 3차는 왕이 줌</li>
 * <li>KING  : 왕의 특별 직업 (성기사). 정해진 1차 직업이면 2차 직업이 있어도 받을 수 있음</li>
 * </ul>
 */
public final class JobQuests {
    public enum Type {KILL, KILL_NIGHT, ITEM, HEAL, BUFF, TANK, BOSS}

    public enum Kind {JOB, GRAND, KING}

    public static final String CLASS_MASTER = "class_master";
    public static final String KING = "king";

    /** target: 엔티티 id / "#hostile" / "#undead" / 아이템 id. */
    public record Objective(Type type, String target, int count) {
    }

    /**
     * base: JOB = 1차 직업, GRAND = 지금 2차 직업, KING = 받을 수 있는 1차 직업들 (쉼표).
     * reward: 얻는 직업 (GRAND 는 base 와 같음). minLevel: KING 만 씀 (나머지는 게임 규칙).
     */
    public record Quest(String id, Kind kind, String giver, String base, String reward, List<Objective> objectives, int xp, int minLevel) {
        public boolean allows(String baseClass) {
            for (String b : base.split(",")) if (b.equals(baseClass)) return true;
            return false;
        }
    }

    public static final Map<String, Quest> ALL = new LinkedHashMap<>();

    static void q(String base, String reward, int xp, Objective... obj) {
        ALL.put("job_" + reward, new Quest("job_" + reward, Kind.JOB, CLASS_MASTER, base, reward, List.of(obj), xp, 0));
    }

    static void grand(String key, String giver, Objective... obj) {
        ALL.put("grand_" + key, new Quest("grand_" + key, Kind.GRAND, giver, key, key, List.of(obj), 3000, 0));
    }

    static Objective kill(String t, int n) {
        return new Objective(Type.KILL, t, n);
    }

    static Objective item(String t, int n) {
        return new Objective(Type.ITEM, t, n);
    }

    static Objective boss(int n) {
        return new Objective(Type.BOSS, "", n);
    }

    static {
        // ---- 2차 전직 (1차 직업과 이어짐)
        q("mage", "void", 600, kill("minecraft:enderman", 5), item("minecraft:ender_pearl", 8));
        q("mage", "warlock", 600, kill("#undead", 25), item("minecraft:bone", 32));
        q("warrior", "blade", 600, kill("#hostile", 40), item("minecraft:iron_ingot", 16));
        q("warrior", "blood", 600, new Objective(Type.KILL_NIGHT, "#hostile", 30), item("minecraft:spider_eye", 8));
        q("warrior", "dragon", 600, kill("minecraft:blaze", 8), item("minecraft:blaze_rod", 6));
        q("tank", "guardian", 600, new Objective(Type.TANK, "", 400), item("minecraft:iron_block", 4));
        q("healer", "angel", 600, new Objective(Type.HEAL, "", 200), item("minecraft:feather", 16));
        q("healer", "cantor", 600, new Objective(Type.BUFF, "", 30), item("minecraft:note_block", 4));
        // ---- 3차 "대" 전직 (1차·2차 직업과 이어지고, 보스를 쓰러뜨려야 함)
        grand("blade", CLASS_MASTER, kill("#hostile", 150), boss(1), item("minecraft:diamond", 8));
        grand("blood", CLASS_MASTER, new Objective(Type.KILL_NIGHT, "#hostile", 100), boss(1), item("minecraft:ghast_tear", 4));
        grand("dragon", CLASS_MASTER, kill("minecraft:blaze", 30), boss(1), item("minecraft:magma_cream", 16));
        grand("void", CLASS_MASTER, kill("minecraft:enderman", 30), boss(1), item("minecraft:ender_eye", 12));
        grand("warlock", CLASS_MASTER, kill("#undead", 120), boss(1), item("minecraft:bone_block", 16));
        grand("guardian", CLASS_MASTER, new Objective(Type.TANK, "", 3000), boss(1), item("minecraft:iron_block", 16));
        grand("angel", CLASS_MASTER, new Objective(Type.HEAL, "", 1500), boss(1), item("minecraft:golden_apple", 4));
        grand("cantor", CLASS_MASTER, new Objective(Type.BUFF, "", 200), boss(1), item("minecraft:amethyst_shard", 24));
        // ---- 왕의 퀘스트: 성기사 (전사·탱커·힐러)
        ALL.put("king_paladin", new Quest("king_paladin", Kind.KING, KING, "warrior,tank,healer", "paladin",
                List.of(kill("#undead", 60), boss(1), item("minecraft:gold_block", 4)), 1500, 40));
        grand("paladin", KING, kill("#undead", 200), boss(2), item("minecraft:totem_of_undying", 1));
    }

    /** 이 NPC 가 지금 플레이어에게 보여줄 퀘스트 (진행 중인 것 먼저). */
    public static List<Quest> forGiver(String giver, String cur, String base, String active) {
        List<Quest> out = new ArrayList<>();
        Quest a = ALL.get(active);
        if (a != null && a.giver().equals(giver)) out.add(a);
        // 3차 → 왕의 퀘스트 → 2차 순서 (2차를 이미 했으면 3차가 지금 할 일)
        for (Kind k : new Kind[]{Kind.GRAND, Kind.KING, Kind.JOB}) {
            for (Quest q : ALL.values()) {
                if (q.kind() != k || out.contains(q) || !q.giver().equals(giver)) continue;
                boolean show = switch (k) {
                    case JOB -> q.base().equals(base);
                    case GRAND -> q.base().equals(cur);
                    case KING -> true;
                };
                if (show) out.add(q);
            }
        }
        return out;
    }

    public static List<Quest> forBase(String base) {
        return ALL.values().stream().filter(q -> q.kind() == Kind.JOB && q.base().equals(base)).toList();
    }

    public static int requiredLevel(Quest q, int jobLevel, int grandLevel) {
        return switch (q.kind()) {
            case JOB -> jobLevel;
            case GRAND -> grandLevel;
            case KING -> q.minLevel();
        };
    }

    /** 이미 끝낸 퀘스트인지. */
    public static boolean finished(Quest q, String cur, boolean grand, Set<String> completed) {
        if (completed.contains(q.id())) return true;
        return switch (q.kind()) {
            case JOB, KING -> cur.equals(q.reward());
            case GRAND -> cur.equals(q.base()) && grand;
        };
    }

    /** 받을 수 있는지 (서버·클라이언트 공용). @return 안 되는 이유 번역 키, 되면 null. */
    @Nullable
    public static String check(Quest q, String cur, String base, int level, boolean grand, String active, int jobLevel, int grandLevel) {
        switch (q.kind()) {
            case JOB -> {
                if (!base.equals(q.base())) return "rpg.abilitywar.err.wrong_class";
                if (ClassTree.tier(cur) >= 2) return "rpg.abilitywar.err.already_advanced";
            }
            case GRAND -> {
                if (!cur.equals(q.base())) return "rpg.abilitywar.err.wrong_class";
                if (grand) return "rpg.abilitywar.err.already_grand";
            }
            case KING -> {
                if (!q.allows(base)) return "rpg.abilitywar.err.wrong_class";
                if (cur.equals(q.reward())) return "rpg.abilitywar.err.already_advanced";
            }
        }
        if (level < requiredLevel(q, jobLevel, grandLevel)) return "rpg.abilitywar.err.level";
        if (!active.isEmpty()) return "rpg.abilitywar.err.has_quest";
        return null;
    }

    private JobQuests() {
    }
}
