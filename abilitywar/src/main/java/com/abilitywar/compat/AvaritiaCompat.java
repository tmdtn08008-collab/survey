package com.abilitywar.compat;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Re:Avaritia (아바리티아, 모드팩에 들어 있음) 호환: ? 장비를 정제 +99 까지 키워 승급하면 진짜 아바리티아 인피니티 장비를 줌.
 * 모드가 없으면 이 모드의 인피니티 등급 장비 (GearGrade.ARVANTIA) 로 대신함.
 */
public final class AvaritiaCompat {
    public static final String NS = "avaritia";

    /** 아바리티아 인피니티 장비가 있는지 (서버 · 클라이언트 둘 다 레지스트리로 확인). */
    public static boolean available() {
        return Registries.ITEM.containsId(new Identifier(NS, "infinity_sword"));
    }

    @Nullable
    static Item item(String id) {
        Identifier i = new Identifier(NS, id);
        return Registries.ITEM.containsId(i) ? Registries.ITEM.get(i) : null;
    }

    /**
     * 무기 종류 → 아바리티아 인피니티 무기: 날붙이 = 우주의 검 · 도끼 = 자연의 파멸 · 망치 · 철퇴 = 세계 파괴자 (곡괭이) ·
     * 창 = 심해의 삼지창 · 지팡이 · 완드 = 천상의 장궁.
     */
    @Nullable
    public static Item weapon(String type) {
        String id = switch (type) {
            case "axe" -> "infinity_axe";
            case "hammer", "mace" -> "infinity_pickaxe";
            case "spear" -> "infinity_trident";
            case "staff", "wand" -> "infinity_bow";
            default -> "infinity_sword";
        };
        Item it = item(id);
        return it != null ? it : item("infinity_sword");
    }

    /** 갑옷 부위 (helmet · chestplate · leggings · boots) → 인피니티 갑옷. */
    @Nullable
    public static Item armor(String slot) {
        return item(switch (slot) {
            case "helmet", "head" -> "infinity_helmet";
            case "leggings", "legs" -> "infinity_pants";
            case "boots", "feet" -> "infinity_boots";
            default -> "infinity_chestplate";
        });
    }

    private AvaritiaCompat() {
    }
}
