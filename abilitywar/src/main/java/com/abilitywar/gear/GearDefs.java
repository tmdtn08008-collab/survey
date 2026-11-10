package com.abilitywar.gear;

import com.abilitywar.AbilityWar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** tools/gen_gear.py 가 만든 data/abilitywar/gear/gear.json 을 읽은 정의. */
public final class GearDefs {
    public record Effect(String id, double chance, double seconds, double power) {
    }

    public record Weapon(String id, GearGrade grade, String type, Map<GearStat, Double> stats, List<Effect> effects) {
    }

    public record Piece(String id, String slot, Map<GearStat, Double> stats) {
    }

    public record ArmorSet(String id, GearGrade grade, String style, Map<String, Piece> pieces, Map<GearStat, Double> setStats,
                           List<String> setEffects, boolean glow) {
    }

    public static final List<Weapon> WEAPONS = new ArrayList<>();
    public static final List<ArmorSet> ARMORS = new ArrayList<>();
    public static final Map<String, ArmorSet> SET_BY_ID = new LinkedHashMap<>();

    public static void load() {
        try (InputStream in = GearDefs.class.getResourceAsStream("/data/abilitywar/gear/gear.json")) {
            if (in == null) {
                AbilityWar.LOG.error("gear.json not found");
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("weapons")) {
                JsonObject o = e.getAsJsonObject();
                WEAPONS.add(new Weapon(o.get("id").getAsString(), GearGrade.byKey(o.get("grade").getAsString()), o.get("type").getAsString(),
                        stats(o.getAsJsonObject("stats")), effects(o.getAsJsonArray("effects"))));
            }
            for (JsonElement e : root.getAsJsonArray("armors")) {
                JsonObject o = e.getAsJsonObject();
                Map<String, Piece> pieces = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> pe : o.getAsJsonObject("pieces").entrySet()) {
                    JsonObject po = pe.getValue().getAsJsonObject();
                    pieces.put(pe.getKey(), new Piece(po.get("id").getAsString(), pe.getKey(), stats(po.getAsJsonObject("stats"))));
                }
                List<String> fx = new ArrayList<>();
                for (JsonElement f : o.getAsJsonArray("set_effects")) fx.add(f.getAsString());
                ArmorSet set = new ArmorSet(o.get("id").getAsString(), GearGrade.byKey(o.get("grade").getAsString()), o.get("style").getAsString(),
                        pieces, stats(o.getAsJsonObject("set_stats")), Collections.unmodifiableList(fx), o.get("glow").getAsBoolean());
                ARMORS.add(set);
                SET_BY_ID.put(set.id(), set);
            }
        } catch (Exception ex) {
            AbilityWar.LOG.error("Failed to load gear.json", ex);
        }
    }

    static Map<GearStat, Double> stats(JsonObject o) {
        Map<GearStat, Double> m = new LinkedHashMap<>();
        if (o == null) return m;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            GearStat s = GearStat.byKey(e.getKey());
            if (s != null) m.put(s, e.getValue().getAsDouble());
        }
        return m;
    }

    static List<Effect> effects(JsonArray a) {
        List<Effect> l = new ArrayList<>();
        if (a == null) return l;
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            l.add(new Effect(o.get("id").getAsString(), o.get("chance").getAsDouble(), o.get("seconds").getAsDouble(), o.get("power").getAsDouble()));
        }
        return l;
    }

    private GearDefs() {
    }
}
