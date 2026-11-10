package com.abilitywar.gear;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;

/** 장비 능력치 줄: 키 → 능력치 + 더하는 방식 + 표시 형식. */
public enum GearStat {
    ATTACK_DAMAGE("attack_damage", false),
    ATTACK_SPEED("attack_speed", false),
    ABILITY_DAMAGE("ability_damage", true),
    CRIT_CHANCE("crit_chance", true),
    CRIT_DAMAGE("crit_damage", true),
    MAX_HEALTH("max_health", false),
    MOVE_SPEED("move_speed", true),
    COOLDOWN("cooldown", true),
    LIFESTEAL("lifesteal", true),
    ULT_CHARGE("ult_charge", true),
    ARMOR("armor", false),
    TOUGHNESS("toughness", false),
    KNOCKBACK_RESISTANCE("knockback_resistance", true),
    DAMAGE_REDUCTION("damage_reduction", true),
    LUCK("luck", false);

    public final String key;
    public final boolean percent;

    GearStat(String key, boolean percent) {
        this.key = key;
        this.percent = percent;
    }

    public static GearStat byKey(String k) {
        for (GearStat s : values()) if (s.key.equals(k)) return s;
        return null;
    }

    public EntityAttribute attribute() {
        return switch (this) {
            case ATTACK_DAMAGE -> EntityAttributes.GENERIC_ATTACK_DAMAGE;
            case ATTACK_SPEED -> EntityAttributes.GENERIC_ATTACK_SPEED;
            case ABILITY_DAMAGE -> GearAttributes.ABILITY_DAMAGE;
            case CRIT_CHANCE -> GearAttributes.CRIT_CHANCE;
            case CRIT_DAMAGE -> GearAttributes.CRIT_DAMAGE;
            case MAX_HEALTH -> EntityAttributes.GENERIC_MAX_HEALTH;
            case MOVE_SPEED -> EntityAttributes.GENERIC_MOVEMENT_SPEED;
            case COOLDOWN -> GearAttributes.COOLDOWN;
            case LIFESTEAL -> GearAttributes.LIFESTEAL;
            case ULT_CHARGE -> GearAttributes.ULT_CHARGE;
            case ARMOR -> EntityAttributes.GENERIC_ARMOR;
            case TOUGHNESS -> EntityAttributes.GENERIC_ARMOR_TOUGHNESS;
            case KNOCKBACK_RESISTANCE -> EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE;
            case DAMAGE_REDUCTION -> GearAttributes.DAMAGE_REDUCTION;
            case LUCK -> EntityAttributes.GENERIC_LUCK;
        };
    }

    public EntityAttributeModifier.Operation operation() {
        return this == MOVE_SPEED ? EntityAttributeModifier.Operation.MULTIPLY_BASE : EntityAttributeModifier.Operation.ADDITION;
    }

    /** 능력치에 실제로 더하는 값 (공격력/공격 속도는 표시값에서 기본값을 뺌). */
    public double modifierValue(double shown) {
        return switch (this) {
            case ATTACK_DAMAGE -> shown - 1.0;
            case ATTACK_SPEED -> shown - 4.0;
            default -> shown;
        };
    }

    /** 화면 표시: "12.5", "+15%", "+3". */
    public String format(double v) {
        if (this == ATTACK_DAMAGE || this == ATTACK_SPEED) return trim(v);
        if (percent) return "+" + trim(v * 100) + "%";
        return "+" + trim(v);
    }

    static String trim(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
