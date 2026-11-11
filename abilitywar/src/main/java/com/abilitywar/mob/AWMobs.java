package com.abilitywar.mob;

import com.abilitywar.AbilityWar;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.List;

/** 몹 · 보스 등록 + 자연 스폰. */
public final class AWMobs {
    static final List<Item> EGGS = new ArrayList<>();

    static <T extends HostileEntity> EntityType<T> reg(String name, EntityType.EntityFactory<T> f, float w, float h, boolean fireImmune, int c1, int c2) {
        var b = FabricEntityTypeBuilder.<T>create(SpawnGroup.MONSTER, f).dimensions(EntityDimensions.fixed(w, h)).trackRangeBlocks(h > 3 ? 128 : 64);
        if (fireImmune) b.fireImmune();
        EntityType<T> type = Registry.register(Registries.ENTITY_TYPE, AbilityWar.id(name), b.build());
        EGGS.add(Registry.register(Registries.ITEM, AbilityWar.id(name + "_spawn_egg"), new SpawnEggItem(type, c1, c2, new Item.Settings())));
        return type;
    }

    public static final EntityType<GoblinEntity> GOBLIN = reg("goblin", GoblinEntity::new, 0.5f, 1.45f, false, 0x4E8A2E, 0x7A4E22);
    public static final EntityType<OrcEntity> ORC = reg("orc", OrcEntity::new, 0.75f, 2.35f, false, 0x5E7040, 0x3A2A1A);
    public static final EntityType<SkeletonKnightEntity> SKELETON_KNIGHT = reg("skeleton_knight", SkeletonKnightEntity::new, 0.6f, 1.99f, false, 0xD8D4C8, 0x5A6070);
    public static final EntityType<WispEntity> WISP = reg("wisp", WispEntity::new, 0.5f, 0.5f, true, 0x7AF0FF, 0x2050A0);
    public static final EntityType<FallenKingEntity> FALLEN_KING = reg("fallen_king", FallenKingEntity::new, 1.4f, 4.6f, false, 0x2A2A34, 0xC02030);
    public static final EntityType<EmberColossusEntity> EMBER_COLOSSUS = reg("ember_colossus", EmberColossusEntity::new, 2.4f, 5.0f, true, 0x3A2A24, 0xFF7A20);
    public static final EntityType<FrostWitchEntity> FROST_WITCH = reg("frost_witch", FrostWitchEntity::new, 0.8f, 2.6f, false, 0xE8F6FF, 0x5AB8E8);
    public static final EntityType<AncientWyvernEntity> ANCIENT_WYVERN = reg("ancient_wyvern", AncientWyvernEntity::new, 4.5f, 3.0f, true, 0x5A2A1A, 0xFF8A30);

    public static void init() {
        FabricDefaultAttributeRegistry.register(GOBLIN, GoblinEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ORC, OrcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SKELETON_KNIGHT, SkeletonKnightEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WISP, WispEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FALLEN_KING, FallenKingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(EMBER_COLOSSUS, EmberColossusEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FROST_WITCH, FrostWitchEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ANCIENT_WYVERN, AncientWyvernEntity.createAttributes());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> EGGS.forEach(e::add));

        // 자연 스폰 (밤·어두운 곳, 몬스터 규칙)
        for (EntityType<? extends HostileEntity> t : List.of(GOBLIN, ORC, SKELETON_KNIGHT, WISP))
            SpawnRestriction.register(t, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST,
                BiomeKeys.MEADOW, BiomeKeys.SAVANNA, BiomeKeys.FLOWER_FOREST), SpawnGroup.MONSTER, GOBLIN, 40, 2, 4);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.SAVANNA, BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.BADLANDS, BiomeKeys.WOODED_BADLANDS,
                BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.DESERT), SpawnGroup.MONSTER, ORC, 25, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, SKELETON_KNIGHT, 12, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.DARK_FOREST), SpawnGroup.MONSTER, SKELETON_KNIGHT, 40, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP, BiomeKeys.DARK_FOREST, BiomeKeys.TAIGA,
                BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA), SpawnGroup.MONSTER, WISP, 20, 1, 2);
        BossSpawner.init();
        com.abilitywar.compat.UltrisCompat.init();
        BossAltar.init();
    }

    private AWMobs() {
    }
}
