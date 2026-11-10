package com.abilitywar.rpg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 직업 트리: 1차 직업 → 전직 퀘스트로 얻는 2차 직업 (3차는 나중에). */
public final class ClassTree {
    public static final List<String> BASE = List.of("warrior", "mage", "tank", "healer");
    public static final Map<String, List<String>> ADVANCE = new LinkedHashMap<>();

    static {
        ADVANCE.put("warrior", List.of("blade", "blood", "dragon"));
        ADVANCE.put("mage", List.of("void", "warlock"));
        ADVANCE.put("tank", List.of("guardian"));
        ADVANCE.put("healer", List.of("angel", "cantor"));
    }

    public static boolean isBase(String key) {
        return BASE.contains(key);
    }

    /** 직업의 1차 직업 (모르면 ""). */
    public static String baseOf(String key) {
        if (key == null || key.isEmpty()) return "";
        if (isBase(key)) return key;
        for (Map.Entry<String, List<String>> e : ADVANCE.entrySet()) if (e.getValue().contains(key)) return e.getKey();
        return "";
    }

    /** 1 = 1차, 2 = 2차, 0 = 없음. */
    public static int tier(String key) {
        if (key == null || key.isEmpty()) return 0;
        return isBase(key) ? 1 : baseOf(key).isEmpty() ? 0 : 2;
    }

    /** 마법 계열 (지능이 피해를 올림), 아니면 물리 (힘). */
    public static boolean isMagic(String key) {
        return switch (key) {
            case "mage", "void", "warlock", "healer", "angel", "cantor" -> true;
            default -> false;
        };
    }

    private ClassTree() {
    }
}
