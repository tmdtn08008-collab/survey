package com.abilitywar.client.anim;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * assets/&lt;ns&gt;/player_animations/*.json 에서 애니메이션을 읽는다. F3+T 로 다시 불러오기 가능.
 */
public final class AnimRegistry implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, AnimClip> CLIPS = new HashMap<>();
    private static int generation;

    @Nullable
    public static AnimClip get(String id) {
        return CLIPS.get(id);
    }

    public static int generation() {
        return generation;
    }

    @Override
    public Identifier getFabricId() {
        return AbilityWar.id("player_animations");
    }

    @Override
    public void reload(ResourceManager manager) {
        CLIPS.clear();
        Map<Identifier, Resource> found = manager.findResources("player_animations", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> e : found.entrySet()) {
            try (Reader r = e.getValue().getReader()) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonObject anims = root.getAsJsonObject("animations");
                for (Map.Entry<String, JsonElement> a : anims.entrySet()) {
                    try {
                        CLIPS.put(a.getKey(), parseClip(a.getKey(), a.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        AbilityWar.LOG.error("Bad animation {} in {}: {}", a.getKey(), e.getKey(), ex.toString());
                    }
                }
            } catch (Exception ex) {
                AbilityWar.LOG.error("Failed to load animations {}", e.getKey(), ex);
            }
        }
        generation++;
        AbilityWar.LOG.info("Loaded {} player animations", CLIPS.size());
    }

    static AnimClip parseClip(String id, JsonObject o) {
        AnimClip c = new AnimClip(id);
        if (o.has("length")) c.length = o.get("length").getAsFloat();
        if (o.has("loop")) c.loop = o.get("loop").getAsBoolean();
        if (o.has("blend_in")) c.blendIn = o.get("blend_in").getAsFloat();
        if (o.has("blend_out")) c.blendOut = o.get("blend_out").getAsFloat();
        if (o.has("head")) c.headLock = "lock".equals(o.get("head").getAsString());
        if (o.has("root_pivot")) c.rootPivot = o.get("root_pivot").getAsFloat();
        if (o.has("stride")) c.stride = o.get("stride").getAsFloat();
        if (o.has("fp_hide_missing")) c.fpHideMissing = o.get("fp_hide_missing").getAsBoolean();
        if (o.has("bones")) {
            JsonObject bones = o.getAsJsonObject("bones");
            for (int b = 0; b < AnimClip.BONES; b++) {
                if (!bones.has(AnimClip.BONE_NAMES[b])) continue;
                JsonObject bo = bones.getAsJsonObject(AnimClip.BONE_NAMES[b]);
                if (bo.has("rot")) c.rot[b] = parseChannel(bo.get("rot"), 1, 1, 1);
                if (bo.has("pos")) {
                    if (b == AnimClip.ROOT || b == AnimClip.ITEM) c.pos[b] = parseChannel(bo.get("pos"), 1, 1, 1);
                    else c.pos[b] = parseChannel(bo.get("pos"), -1, -1, -1); // [오른쪽,위,앞] -> 모델공간
                }
            }
        }
        if (o.has("fp")) {
            JsonObject fp = o.getAsJsonObject("fp");
            for (int b = 0; b < AnimClip.FP_BONES; b++) {
                if (!fp.has(AnimClip.FP_NAMES[b])) continue;
                JsonObject bo = fp.getAsJsonObject(AnimClip.FP_NAMES[b]);
                if (bo.has("rot")) c.fpRot[b] = parseChannel(bo.get("rot"), 1, 1, 1);
                if (bo.has("pos")) c.fpPos[b] = parseChannel(bo.get("pos"), 1, 1, 1);
                if (b < 2 && bo.has("vis")) c.fpVis[b] = parseChannel(bo.get("vis"), 1, 1, 1);
            }
            if (fp.has("fov")) c.fov = parseChannel(fp.get("fov"), 1, 1, 1);
        }
        return c;
    }

    static AnimChannel parseChannel(JsonElement el, float sx, float sy, float sz) {
        JsonObject o = el.getAsJsonObject();
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(o.entrySet());
        entries.sort((a, b) -> Float.compare(Float.parseFloat(a.getKey()), Float.parseFloat(b.getKey())));
        int n = entries.size();
        float[] times = new float[n];
        float[][] values = new float[n][3];
        Easing[] easings = new Easing[n];
        for (int i = 0; i < n; i++) {
            times[i] = Float.parseFloat(entries.get(i).getKey());
            JsonElement v = entries.get(i).getValue();
            Easing e = Easing.LINEAR;
            JsonArray arr;
            if (v.isJsonPrimitive()) {
                arr = new JsonArray();
                arr.add(v.getAsFloat());
            } else if (v.isJsonObject()) {
                JsonObject vo = v.getAsJsonObject();
                arr = vo.has("v") ? vo.getAsJsonArray("v") : new JsonArray();
                if (vo.has("e")) e = Easing.parse(vo.get("e").getAsString());
            } else {
                arr = v.getAsJsonArray();
            }
            int k = 0;
            for (JsonElement x : arr) {
                if (x.isJsonPrimitive() && x.getAsJsonPrimitive().isString()) {
                    e = Easing.parse(x.getAsString());
                } else if (k < 3) {
                    values[i][k++] = x.getAsFloat();
                }
            }
            values[i][0] *= sx;
            values[i][1] *= sy;
            values[i][2] *= sz;
            easings[i] = e;
        }
        return new AnimChannel(times, values, easings);
    }
}
