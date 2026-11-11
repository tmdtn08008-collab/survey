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
