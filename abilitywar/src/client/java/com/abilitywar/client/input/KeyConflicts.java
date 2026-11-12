package com.abilitywar.client.input;

import com.abilitywar.AbilityWar;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 같이 넣은 모드의 키가 능력 키 (1~4 · R · T · F · Q · K · J · B · G · Y · N) 와 겹치면 한 번만 옮김.
 * <ul>
 *   <li>Iris (셰이더): 셰이더 다시 불러오기 (R) · 셰이더 켜기/끄기 (K) → 키 없음. 셰이더 팩 화면 (O) 은 그대로.</li>
 *   <li>Saint's Dragons (드래곤 탈 때): 겹치는 키 → 비어 있는 V · C · Z · X · H · U · I · P 중 하나.</li>
 * </ul>
 * 옮긴 키는 config/abilitywar_keys.txt 에 적어 두고 다시 건드리지 않음 (설정 → 조작에서 마음대로 바꿔도 됨).
 */
public final class KeyConflicts {
    static boolean done;
    static final String[] MODS = {"iris.keybind.", "key.saintsdragons."};
    static final int[] FREE = {InputUtil.GLFW_KEY_V, InputUtil.GLFW_KEY_C, InputUtil.GLFW_KEY_Z, InputUtil.GLFW_KEY_X, InputUtil.GLFW_KEY_H,
            InputUtil.GLFW_KEY_U, InputUtil.GLFW_KEY_I, InputUtil.GLFW_KEY_P};

    public static void tick(MinecraftClient mc) {
        if (done || mc.options == null || mc.player == null) return;
        done = true;
        try {
            Path f = FabricLoader.getInstance().getConfigDir().resolve("abilitywar_keys.txt");
            Set<String> fixed = new HashSet<>(Files.exists(f) ? Files.readAllLines(f, StandardCharsets.UTF_8) : List.of());
            Set<InputUtil.Key> ours = new HashSet<>(), used = new HashSet<>();
            for (KeyBinding k : KeyBinding.KEYS_BY_ID.values()) {
                if (InputHandler.isOurs(k)) ours.add(k.boundKey);
                used.add(k.boundKey);
            }
            List<String> changed = new ArrayList<>();
            for (KeyBinding k : KeyBinding.KEYS_BY_ID.values()) {
                String id = k.getTranslationKey();
                if (InputHandler.isOurs(k) || fixed.contains(id) || !ours.contains(k.boundKey)) continue;
                boolean iris = id.startsWith(MODS[0]);
                if (!iris && !id.startsWith(MODS[1])) continue;
                InputUtil.Key to = InputUtil.UNKNOWN_KEY;
                if (!iris) for (int c : FREE) {
                    InputUtil.Key cand = InputUtil.Type.KEYSYM.createFromCode(c);
                    if (!used.contains(cand)) {
                        to = cand;
                        break;
                    }
                }
                used.add(to);
                mc.options.setKeyCode(k, to);
                fixed.add(id);
                changed.add(id + " -> " + to.getTranslationKey());
            }
            if (!changed.isEmpty()) {
                KeyBinding.updateKeysByCode();
                mc.options.write();
                Files.write(f, fixed, StandardCharsets.UTF_8);
                AbilityWar.LOG.info("[abilitywar] moved keys that clashed with ability keys: {}", changed);
            }
        } catch (Exception e) {
            AbilityWar.LOG.warn("[abilitywar] could not check key conflicts", e);
        }
    }

    private KeyConflicts() {
    }
}
