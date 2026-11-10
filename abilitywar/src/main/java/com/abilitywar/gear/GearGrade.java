package com.abilitywar.gear;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** 장비 등급: 일반 → 좋음 → 희귀 → 영웅 → 전설 → 신화 → 시크릿 → ?. */
public enum GearGrade {
    COMMON("common", 0xC8C8C8),
    GOOD("good", 0x6BE36B),
    RARE("rare", 0x4FA3FF),
    HERO("hero", 0xB45CFF),
    LEGENDARY("legendary", 0xFFB020),
    MYTHIC("mythic", 0xFF4848),
    SECRET("secret", 0x2EF2D0),
    UNKNOWN("unknown", 0xFFFFFF);

    public final String key;
    public final int color;

    GearGrade(String key, int color) {
        this.key = key;
        this.color = color;
    }

    public int tier() {
        return ordinal();
    }

    /** 착용 레벨 (게임 규칙 abilitywarGearLevel 이 켜져 있을 때). */
    public int requiredLevel() {
        return new int[]{1, 10, 20, 35, 50, 65, 80, 90}[ordinal()];
    }

    public Text displayName() {
        return Text.translatable("gear.abilitywar.grade." + key);
    }

    public static GearGrade byKey(String k) {
        for (GearGrade g : values()) if (g.key.equals(k)) return g;
        return COMMON;
    }

    /** 이름 글자 색: 시크릿은 청록↔금빛, ? 는 깜빡이는 흑백/분홍 (time = 밀리초). */
    public int colorAt(int index, long time) {
        if (this == SECRET) {
            double k = 0.5 + 0.5 * Math.sin(time / 300.0 - index * 0.55);
            return lerp(0x2EF2D0, 0xFFE27A, k);
        }
        if (this == UNKNOWN) {
            long f = (time / 90 + index * 7L) % 11;
            return f < 2 ? 0xFF4CF0 : f < 4 ? 0x60FFF4 : f < 5 ? 0x707070 : 0xF4F4F4;
        }
        if (this == MYTHIC) {
            double k = 0.5 + 0.5 * Math.sin(time / 420.0 - index * 0.4);
            return lerp(0xFF4848, 0xFF9A5A, k);
        }
        return color;
    }

    public static int lerp(int a, int b, double k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public Formatting fallbackFormatting() {
        return switch (this) {
            case COMMON -> Formatting.WHITE;
            case GOOD -> Formatting.GREEN;
            case RARE -> Formatting.BLUE;
            case HERO -> Formatting.DARK_PURPLE;
            case LEGENDARY -> Formatting.GOLD;
            case MYTHIC -> Formatting.RED;
            case SECRET -> Formatting.AQUA;
            case UNKNOWN -> Formatting.WHITE;
        };
    }
}
