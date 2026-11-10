package com.abilitywar.gear;

import com.abilitywar.AbilityWar;
import net.minecraft.entity.attribute.ClampedEntityAttribute;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** 장비 전용 능력치 (플레이어에게 붙음). 값은 비율 (0.15 = 15%). */
public final class GearAttributes {
    public static final EntityAttribute ABILITY_DAMAGE = attr("ability_damage", 0, 0, 10);
    public static final EntityAttribute CRIT_CHANCE = attr("crit_chance", 0, 0, 1);
    public static final EntityAttribute CRIT_DAMAGE = attr("crit_damage", 0.5, 0, 10);
    public static final EntityAttribute COOLDOWN = attr("cooldown_reduction", 0, 0, 0.6);
    public static final EntityAttribute LIFESTEAL = attr("lifesteal", 0, 0, 1);
    public static final EntityAttribute ULT_CHARGE = attr("ult_charge", 0, 0, 5);
    public static final EntityAttribute DAMAGE_REDUCTION = attr("damage_reduction", 0, 0, 0.6);
    /** 스탯: 힘 → 물리 피해, 지능 → 마법 피해, 지혜 → 회복량. */
    public static final EntityAttribute PHYSICAL_DAMAGE = attr("physical_damage", 0, 0, 10);
    public static final EntityAttribute MAGIC_DAMAGE = attr("magic_damage", 0, 0, 10);
    public static final EntityAttribute HEAL_POWER = attr("heal_power", 0, 0, 10);

    static EntityAttribute attr(String name, double base, double min, double max) {
        return new ClampedEntityAttribute("attribute.name.abilitywar." + name, base, min, max).setTracked(true);
    }

    public static void init() {
        reg("ability_damage", ABILITY_DAMAGE);
        reg("crit_chance", CRIT_CHANCE);
        reg("crit_damage", CRIT_DAMAGE);
        reg("cooldown_reduction", COOLDOWN);
        reg("lifesteal", LIFESTEAL);
        reg("ult_charge", ULT_CHARGE);
        reg("damage_reduction", DAMAGE_REDUCTION);
        reg("physical_damage", PHYSICAL_DAMAGE);
        reg("magic_damage", MAGIC_DAMAGE);
        reg("heal_power", HEAL_POWER);
    }

    static void reg(String name, EntityAttribute a) {
        Registry.register(Registries.ATTRIBUTE, AbilityWar.id(name), a);
    }

    /** 플레이어 기본 능력치 목록에 추가 (PlayerEntityMixin). */
    public static void addTo(DefaultAttributeContainer.Builder b) {
        b.add(ABILITY_DAMAGE).add(CRIT_CHANCE).add(CRIT_DAMAGE).add(COOLDOWN).add(LIFESTEAL).add(ULT_CHARGE).add(DAMAGE_REDUCTION)
                .add(PHYSICAL_DAMAGE).add(MAGIC_DAMAGE).add(HEAL_POWER);
    }

    private GearAttributes() {
    }
}
