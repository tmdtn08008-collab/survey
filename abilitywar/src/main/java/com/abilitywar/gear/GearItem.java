package com.abilitywar.gear;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** RPG 장비 공통. */
public interface GearItem {
    GearGrade grade();

    String gearId();

    /** 클라이언트에서 설정: 신화·시크릿·? 이름을 글자마다 색이 흐르게 꾸밈 (서버에서는 null). */
    BiFunction<GearGrade, String, Text>[] NAMER = new BiFunction[1];
    /** 클라이언트에서 설정: 툴팁에서 '지금 입고 있는 세트 부위 수' 를 셀 플레이어. */
    Supplier<PlayerEntity>[] CLIENT_PLAYER = new Supplier[1];
    /** 클라이언트에서 설정: 내 RPG 레벨 (툴팁의 착용 레벨 색). */
    java.util.function.IntSupplier[] CLIENT_LEVEL = new java.util.function.IntSupplier[1];

    static Text name(GearGrade grade, String translationKey) {
        if (NAMER[0] != null && grade.tier() >= GearGrade.MYTHIC.tier()) return NAMER[0].apply(grade, translationKey);
        MutableText t = Text.translatable(translationKey);
        return t.styled(s -> s.withColor(TextColor.fromRgb(grade.color)));
    }
}
