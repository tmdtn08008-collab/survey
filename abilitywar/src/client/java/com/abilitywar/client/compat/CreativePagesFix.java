package com.abilitywar.client.compat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;

/**
 * 크리에이티브 창의 탭 페이지 버튼 겹침 고치기.
 * Re:Avaritia 는 Forge 식 페이지 넘김 ("<" ">" 버튼, 7번째 칸 이상의 탭만 다음 장으로) 을 크리에이티브 창 오른쪽 위에 따로 붙이는데,
 * Fabric API 가 이미 같은 자리에 페이지 버튼을 두고 모든 모드 탭을 0..4 칸에 놓기 때문에 아바리티아 쪽 버튼은 아무 일도 안 하면서
 * 클릭만 먼저 가져감 → "다음 페이지로 안 넘어감". 아바리티아 버튼만 빼고 Fabric 버튼을 남김.
 */
public final class CreativePagesFix {
    public static void init() {
        // 테스트 모드 (abilitywar.test) 에서는 아바리티아 없이도 켜서 Fabric 버튼이 남는지 확인
        if (!FabricLoader.getInstance().isModLoaded("avaritia") && System.getProperty("abilitywar.test") == null) return;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof CreativeInventoryScreen)) return;
            // 아바리티아 버튼: 바닐라 ButtonWidget 그대로 (Fabric 버튼은 하위 클래스), 글자가 "<" 또는 ">"
            Screens.getButtons(screen).removeIf(b -> b.getClass() == ButtonWidget.class
                    && (b.getMessage().getString().equals("<") || b.getMessage().getString().equals(">")));
        });
    }

    private CreativePagesFix() {
    }
}
