package com.abilitywar.client.hud;

import com.abilitywar.AbilityWar;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * assets/&lt;ns&gt;/hud/&lt;능력키&gt;.json : 능력 전용 스킬바 그림과 슬롯 위치.
 * (tools/gen_hud.py 로 생성)
 */
public final class HudLayouts implements SimpleSynchronousResourceReloadListener {
    public static final class Layout {
        public Identifier texture, mask;
        public float width, height;
        public final Map<String, float[]> slots = new HashMap<>();
        public final Map<String, float[]> labels = new HashMap<>();
        /** 빛 색 (쿨타임 바늘, 궁극기 경계선 등) / 진한 색 (차오른 게이지). */
        public float[] accent = {0.75f, 0.55f, 1f}, accent2 = {0.45f, 0.2f, 0.85f};
        /** 위쪽 띠에 흐르는 마력 잔상 [x0, y0, x1, y1] (그림 좌표), 없으면 null. */
        public float[] trail;
        /** 빛나는 문양 [x, y, 반지름] (그림 좌표), 없으면 null. */
        public float[] emblem;
    }

    private static final Map<String, Layout> LAYOUTS = new HashMap<>();

    @Nullable
    public static Layout get(String ability) {
        return LAYOUTS.get(ability);
    }

    @Override
    public Identifier getFabricId() {
        return AbilityWar.id("hud_layouts");
    }

    @Override
    public void reload(ResourceManager manager) {
        LAYOUTS.clear();
        Map<Identifier, Resource> found = manager.findResources("hud", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            try (Reader r = e.getValue().getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                Layout l = new Layout();
                l.texture = new Identifier(o.get("texture").getAsString());
                l.mask = new Identifier(o.get("mask").getAsString());
                l.width = o.get("width").getAsFloat();
                l.height = o.get("height").getAsFloat();
                readMap(o.getAsJsonObject("slots"), l.slots);
                if (o.has("labels")) readMap(o.getAsJsonObject("labels"), l.labels);
                if (o.has("accent")) l.accent = readVec(o.getAsJsonArray("accent"));
                if (o.has("accent2")) l.accent2 = readVec(o.getAsJsonArray("accent2"));
                if (o.has("trail")) l.trail = readVec(o.getAsJsonArray("trail"));
                if (o.has("emblem")) l.emblem = readVec(o.getAsJsonArray("emblem"));
                String path = e.getKey().getPath();
                String key = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
                LAYOUTS.put(key, l);
            } catch (Exception ex) {
                AbilityWar.LOG.error("Bad HUD layout {}", e.getKey(), ex);
            }
        }
    }

    private static void readMap(JsonObject o, Map<String, float[]> out) {
        for (Map.Entry<String, JsonElement> s : o.entrySet()) {
            JsonArray a = s.getValue().getAsJsonArray();
            float[] v = new float[a.size()];
            for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
            out.put(s.getKey(), v);
        }
    }

    private static float[] readVec(JsonArray a) {
        float[] v = new float[a.size()];
        for (int i = 0; i < v.length; i++) v[i] = a.get(i).getAsFloat();
        return v;
    }
}
