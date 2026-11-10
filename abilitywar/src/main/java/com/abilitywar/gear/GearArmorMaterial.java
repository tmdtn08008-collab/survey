package com.abilitywar.gear;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** 세트마다 하나: 부위별 방어력, 방어 강도, 밀쳐내기 저항. 내구도 없음 (부서지지 않음). */
public class GearArmorMaterial implements ArmorMaterial {
    final GearDefs.ArmorSet set;

    public GearArmorMaterial(GearDefs.ArmorSet set) {
        this.set = set;
    }

    static String pieceKey(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
        };
    }

    double stat(ArmorItem.Type type, GearStat s) {
        GearDefs.Piece p = set.pieces().get(pieceKey(type));
        return p == null ? 0 : p.stats().getOrDefault(s, 0.0);
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return 0;
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return (int) Math.round(stat(type, GearStat.ARMOR));
    }

    @Override
    public int getEnchantability() {
        return 10 + set.grade().tier() * 2;
    }

    @Override
    public SoundEvent getEquipSound() {
        return switch (set.style()) {
            case "chain" -> SoundEvents.ITEM_ARMOR_EQUIP_CHAIN;
            case "leather", "robe", "assassin" -> SoundEvents.ITEM_ARMOR_EQUIP_LEATHER;
            case "dragon", "demon" -> SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE;
            case "angel", "crystal" -> SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND;
            case "knight", "plate", "scale" -> SoundEvents.ITEM_ARMOR_EQUIP_IRON;
            default -> SoundEvents.ITEM_ARMOR_EQUIP_GENERIC;
        };
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public String getName() {
        return "abilitywar_gear";
    }

    @Override
    public float getToughness() {
        return (float) stat(ArmorItem.Type.CHESTPLATE, GearStat.TOUGHNESS);
    }

    @Override
    public float getKnockbackResistance() {
        return (float) stat(ArmorItem.Type.CHESTPLATE, GearStat.KNOCKBACK_RESISTANCE);
    }
}
