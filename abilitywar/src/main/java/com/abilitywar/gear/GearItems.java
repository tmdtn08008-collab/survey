package com.abilitywar.gear;

import com.abilitywar.AbilityWar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** RPG 장비 등록: 무기 470개 + 갑옷 470세트 (1880 부위). 등급마다 크리에이티브 탭 하나. */
public final class GearItems {
    public static final Map<GearGrade, List<Item>> WEAPONS = new EnumMap<>(GearGrade.class);
    public static final Map<GearGrade, List<GearDefs.ArmorSet>> SETS = new EnumMap<>(GearGrade.class);
    public static final Map<String, Item> BY_ID = new java.util.HashMap<>();
    static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    public static void init() {
        GearDefs.load();
        for (GearGrade g : GearGrade.values()) {
            WEAPONS.put(g, new ArrayList<>());
            SETS.put(g, new ArrayList<>());
        }
        for (GearDefs.Weapon w : GearDefs.WEAPONS) {
            Item item = Registry.register(Registries.ITEM, AbilityWar.id(w.id()), new GearWeaponItem(w, settings(w.grade())));
            WEAPONS.get(w.grade()).add(item);
            BY_ID.put(w.id(), item);
        }
        for (GearDefs.ArmorSet set : GearDefs.ARMORS) {
            for (ArmorItem.Type t : TYPES) {
                GearDefs.Piece p = set.pieces().get(GearArmorMaterial.pieceKey(t));
                if (p == null) continue;
                Item item = Registry.register(Registries.ITEM, AbilityWar.id(p.id()), new GearArmorItem(set, p, t, settings(set.grade())));
                BY_ID.put(p.id(), item);
            }
            SETS.get(set.grade()).add(set);
        }
        for (GearGrade g : GearGrade.values()) {
            if (WEAPONS.get(g).isEmpty()) continue;
            Item icon = WEAPONS.get(g).get(0);
            Registry.register(Registries.ITEM_GROUP, AbilityWar.id("gear_" + g.key), FabricItemGroup.builder()
                    .displayName(Text.translatable("gear.abilitywar.ui.tab", g.displayName()))
                    .icon(() -> new ItemStack(icon))
                    .entries((ctx, entries) -> {
                        for (Item w : WEAPONS.get(g)) entries.add(w);
                        for (GearDefs.ArmorSet s : SETS.get(g)) for (GearDefs.Piece p : s.pieces().values()) entries.add(BY_ID.get(p.id()));
                    })
                    .build());
        }
        AbilityWar.LOG.info("RPG gear: {} weapons, {} armor sets", GearDefs.WEAPONS.size(), GearDefs.ARMORS.size());
    }

    static Item.Settings settings(GearGrade g) {
        Item.Settings s = new Item.Settings().maxCount(1).rarity(g.tier() >= 5 ? Rarity.EPIC : g.tier() >= 3 ? Rarity.RARE : g.tier() >= 1 ? Rarity.UNCOMMON : Rarity.COMMON);
        if (g.tier() >= GearGrade.LEGENDARY.tier()) s.fireproof();
        return s;
    }

    public static List<Item> setItems(GearDefs.ArmorSet s) {
        List<Item> l = new ArrayList<>();
        for (GearDefs.Piece p : s.pieces().values()) l.add(BY_ID.get(p.id()));
        return l;
    }

    private GearItems() {
    }
}
