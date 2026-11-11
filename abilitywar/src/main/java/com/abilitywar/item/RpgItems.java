package com.abilitywar.item;

import com.abilitywar.AbilityWar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** RPG 소모품 · 재료: 회복 물약 3, 강화석, 보호 주문서, 승급석. */
public final class RpgItems {
    public static final Item HEALTH_POTION_SMALL = reg("health_potion_small", new HealthPotionItem(0.25f, new Item.Settings().maxCount(16)));
    public static final Item HEALTH_POTION = reg("health_potion", new HealthPotionItem(0.5f, new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));
    public static final Item HEALTH_POTION_LARGE = reg("health_potion_large", new HealthPotionItem(1.0f, new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
    public static final Item[] HEALTH_POTIONS = {HEALTH_POTION_SMALL, HEALTH_POTION, HEALTH_POTION_LARGE};
    public static final Item ENHANCE_STONE = reg("enhance_stone", new Material(new Item.Settings().rarity(Rarity.UNCOMMON), "item.abilitywar.enhance_stone.desc", false));
    public static final Item PROTECTION_SCROLL = reg("protection_scroll", new Material(new Item.Settings().maxCount(16).rarity(Rarity.RARE), "item.abilitywar.protection_scroll.desc", false));
    public static final Item PROMOTION_STONE = reg("promotion_stone", new Material(new Item.Settings().rarity(Rarity.EPIC), "item.abilitywar.promotion_stone.desc", true));
    /** 승급 재료 마정석: 승급할 등급마다 하나 (초록 = 좋음 … 청록 = 시크릿), 혼돈의 파편 = ? 등급. */
    public static final Item CRYSTAL_GOOD = reg("crystal_good", new Material(new Item.Settings().rarity(Rarity.UNCOMMON), "item.abilitywar.crystal.desc", false));
    public static final Item CRYSTAL_RARE = reg("crystal_rare", new Material(new Item.Settings().rarity(Rarity.UNCOMMON), "item.abilitywar.crystal.desc", false));
    public static final Item CRYSTAL_HERO = reg("crystal_hero", new Material(new Item.Settings().rarity(Rarity.RARE), "item.abilitywar.crystal.desc", false));
    public static final Item CRYSTAL_LEGENDARY = reg("crystal_legendary", new Material(new Item.Settings().rarity(Rarity.RARE), "item.abilitywar.crystal.desc", true));
    public static final Item CRYSTAL_MYTHIC = reg("crystal_mythic", new Material(new Item.Settings().rarity(Rarity.EPIC), "item.abilitywar.crystal.desc", true));
    public static final Item CRYSTAL_SECRET = reg("crystal_secret", new Material(new Item.Settings().rarity(Rarity.EPIC), "item.abilitywar.crystal.desc", true));
    public static final Item CHAOS_SHARD = reg("chaos_shard", new Material(new Item.Settings().rarity(Rarity.EPIC), "item.abilitywar.chaos_shard.desc", true));

    /** 그 등급으로 승급할 때 쓰는 마정석 (? 등급은 혼돈의 파편). */
    public static Item crystal(com.abilitywar.gear.GearGrade g) {
        return switch (g) {
            case COMMON, GOOD -> CRYSTAL_GOOD;
            case RARE -> CRYSTAL_RARE;
            case HERO -> CRYSTAL_HERO;
            case LEGENDARY -> CRYSTAL_LEGENDARY;
            case MYTHIC -> CRYSTAL_MYTHIC;
            case SECRET -> CRYSTAL_SECRET;
            case UNKNOWN -> CHAOS_SHARD;
        };
    }

    static Item reg(String id, Item item) {
        return Registry.register(Registries.ITEM, AbilityWar.id(id), item);
    }

    /** 설명이 붙는 재료 (승급석은 반짝임). */
    public static class Material extends Item {
        final String desc;
        final boolean glint;

        public Material(Settings s, String desc, boolean glint) {
            super(s);
            this.desc = desc;
            this.glint = glint;
        }

        @Override
        public boolean hasGlint(ItemStack stack) {
            return glint || super.hasGlint(stack);
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
            tooltip.add(Text.translatable(desc).formatted(Formatting.GRAY));
        }
    }

    public static void init() {
        Registry.register(Registries.ITEM_GROUP, AbilityWar.id("rpg_items"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.abilitywar.rpg_items"))
                .icon(() -> new ItemStack(ENHANCE_STONE))
                .entries((ctx, e) -> {
                    for (Item i : HEALTH_POTIONS) e.add(i);
                    e.add(ENHANCE_STONE);
                    e.add(PROTECTION_SCROLL);
                    e.add(PROMOTION_STONE);
                    for (Item c : new Item[]{CRYSTAL_GOOD, CRYSTAL_RARE, CRYSTAL_HERO, CRYSTAL_LEGENDARY, CRYSTAL_MYTHIC, CRYSTAL_SECRET, CHAOS_SHARD}) e.add(c);
                    e.add(com.abilitywar.registry.AWEntities.APOTHECARY_EGG);
                    e.add(com.abilitywar.registry.AWEntities.BLACKSMITH_EGG);
                    e.add(com.abilitywar.registry.AWEntities.GUILD_CLERK_EGG);
                    e.add(com.abilitywar.registry.AWEntities.JOB_MASTER_EGG);
                })
                .build());
    }

    private RpgItems() {
    }
}
