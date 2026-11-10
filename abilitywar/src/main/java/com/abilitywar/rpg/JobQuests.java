package com.abilitywar.rpg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 2차 전직 퀘스트: 1차 직업과 이어지는 목표. */
public final class JobQuests {
    public enum Type {KILL, KILL_NIGHT, ITEM, HEAL, BUFF, TANK}

    /** target: 엔티티 id / "#hostile" / "#undead" / 아이템 id. */
    public record Objective(Type type, String target, int count) {
    }

    public record Quest(String id, String base, String reward, List<Objective> objectives, int xp) {
    }

    public static final Map<String, Quest> ALL = new LinkedHashMap<>();

    static void q(String base, String reward, int xp, Objective... obj) {
        ALL.put("job_" + reward, new Quest("job_" + reward, base, reward, List.of(obj), xp));
    }

    static Objective kill(String t, int n) {
        return new Objective(Type.KILL, t, n);
    }

    static Objective item(String t, int n) {
        return new Objective(Type.ITEM, t, n);
    }

    static {
        q("mage", "void", 600, kill("minecraft:enderman", 5), item("minecraft:ender_pearl", 8));
        q("mage", "warlock", 600, kill("#undead", 25), item("minecraft:bone", 32));
        q("warrior", "blade", 600, kill("#hostile", 40), item("minecraft:iron_ingot", 16));
        q("warrior", "blood", 600, new Objective(Type.KILL_NIGHT, "#hostile", 30), item("minecraft:spider_eye", 8));
        q("warrior", "dragon", 600, kill("minecraft:blaze", 8), item("minecraft:blaze_rod", 6));
        q("tank", "guardian", 600, new Objective(Type.TANK, "", 400), item("minecraft:iron_block", 4));
        q("healer", "angel", 600, new Objective(Type.HEAL, "", 200), item("minecraft:feather", 16));
        q("healer", "cantor", 600, new Objective(Type.BUFF, "", 30), item("minecraft:note_block", 4));
    }

    public static List<Quest> forBase(String base) {
        return ALL.values().stream().filter(q -> q.base().equals(base)).toList();
    }

    private JobQuests() {
    }
}
