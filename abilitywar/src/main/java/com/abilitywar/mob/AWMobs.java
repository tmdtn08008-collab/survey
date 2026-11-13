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

import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
    // 2.9: 야생 생명체
    public static final EntityType<GoblinLordEntity> GOBLIN_LORD = reg("goblin_lord", GoblinLordEntity::new, 0.75f, 2.05f, false, 0x4E8A2E, 0xE8C040);
    public static final EntityType<OrcLordEntity> ORC_LORD = reg("orc_lord", OrcLordEntity::new, 0.95f, 2.85f, false, 0x5E7040, 0xB02818);
    public static final EntityType<BanditEntity> BANDIT = reg("bandit", BanditEntity::new, 0.6f, 1.95f, false, 0x6A4A30, 0xA02020);
    public static final EntityType<ThiefEntity> THIEF = reg("thief", ThiefEntity::new, 0.6f, 1.9f, false, 0x2A2A30, 0x6A6A78);
    public static final EntityType<PirateEntity> PIRATE = reg("pirate", PirateEntity::new, 0.6f, 1.95f, false, 0x1A2A4A, 0xC8A040);
    public static final EntityType<CultistEntity> CULTIST = reg("cultist", CultistEntity::new, 0.6f, 1.95f, false, 0x2A1030, 0x9A3CFF);
    public static final EntityType<WraithEntity> WRAITH = reg("wraith", WraithEntity::new, 0.6f, 1.9f, false, 0xB8D8E8, 0x406070);
    public static final EntityType<FairyEntity> FAIRY = reg("fairy", FairyEntity::new, 0.35f, 0.6f, false, 0xFFC8F4, 0x8AF0FF);
    public static final EntityType<MermaidEntity> MERMAID = reg("mermaid", MermaidEntity::new, 0.6f, 1.9f, false, 0x2AA8A0, 0xF0C8A0);
    public static final EntityType<DireWolfEntity> DIRE_WOLF = reg("dire_wolf", DireWolfEntity::new, 0.85f, 1.15f, false, 0x3A3A40, 0xC02020);
    public static final EntityType<AncientGolemEntity> ANCIENT_GOLEM = reg("ancient_golem", AncientGolemEntity::new, 1.4f, 2.7f, false, 0x6A7A60, 0x3AE0A0);
    public static final EntityType<EnhancedGolemEntity> ENHANCED_GOLEM = reg("enhanced_golem", EnhancedGolemEntity::new, 1.7f, 3.3f, false, 0x1A1424, 0xFFB040);
    public static final EntityType<DrakeEntity> DRAKE = reg("drake", DrakeEntity::new, 2.1f, 1.4f, true, 0x2A6A3A, 0x9AF060);
    public static final EntityType<SpiderQueenEntity> SPIDER_QUEEN = reg("spider_queen", SpiderQueenEntity::new, 2.2f, 1.4f, false, 0x2A1A2A, 0xC02030);
    public static final EntityType<DragonKingEntity> DRAGON_KING = reg("dragon_king", DragonKingEntity::new, 7.0f, 4.6f, true, 0x1A3A8A, 0xFFD060);

    public static void init() {
        FabricDefaultAttributeRegistry.register(GOBLIN, GoblinEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ORC, OrcEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SKELETON_KNIGHT, SkeletonKnightEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WISP, WispEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FALLEN_KING, FallenKingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(EMBER_COLOSSUS, EmberColossusEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FROST_WITCH, FrostWitchEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ANCIENT_WYVERN, AncientWyvernEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(GOBLIN_LORD, GoblinLordEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ORC_LORD, OrcLordEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BANDIT, BanditEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(THIEF, ThiefEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PIRATE, PirateEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CULTIST, CultistEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(WRAITH, WraithEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FAIRY, FairyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MERMAID, MermaidEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DIRE_WOLF, DireWolfEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ANCIENT_GOLEM, AncientGolemEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(ENHANCED_GOLEM, EnhancedGolemEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DRAKE, DrakeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DRAGON_KING, DragonKingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SPIDER_QUEEN, SpiderQueenEntity.createAttributes());
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
        wildSpawns();
        BossSpawner.init();
        com.abilitywar.compat.UltrisCompat.init();
        BossAltar.init();
    }

    // ================================================================ 2.9 야생 생명체 스폰
    /** 바닐라 생물군계 + 공통 태그 (c:) — Terralith 같은 지형 모드의 생물군계에도 나오게. */
    @SafeVarargs
    static Predicate<BiomeSelectionContext> where(List<TagKey<Biome>> tags, RegistryKey<Biome>... keys) {
        Predicate<BiomeSelectionContext> p = BiomeSelectors.includeByKey(keys);
        for (TagKey<Biome> t : tags) p = p.or(BiomeSelectors.tag(t));
        return p.and(BiomeSelectors.foundInOverworld());
    }

    /** 어두우면 언제나, 밝은 바깥이면 가끔 (사람 · 요정 · 비룡). */
    static boolean anyLight(EntityType<? extends HostileEntity> t, ServerWorldAccess w, SpawnReason reason, BlockPos pos, Random r) {
        if (w.getDifficulty() == Difficulty.PEACEFUL || !HostileEntity.canSpawnIgnoreLightLevel(t, w, reason, pos, r)) return false;
        if (HostileEntity.isSpawnDark(w, pos, r)) return true;
        return r.nextInt(4) == 0 && w.isSkyVisible(pos);
    }

    static boolean drakeSpawn(EntityType<? extends HostileEntity> t, ServerWorldAccess w, SpawnReason reason, BlockPos pos, Random r) {
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION) return anyLight(t, w, reason, pos, r);
        return pos.getY() > w.getSeaLevel() + 30 && anyLight(t, w, reason, pos, r);
    }

    static boolean mermaidSpawn(EntityType<MermaidEntity> t, ServerWorldAccess w, SpawnReason reason, BlockPos pos, Random r) {
        if (w.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (!w.getFluidState(pos).isIn(FluidTags.WATER) || !w.getFluidState(pos.down()).isIn(FluidTags.WATER)) return false;
        if (reason == SpawnReason.SPAWNER || reason == SpawnReason.SPAWN_EGG) return true;
        return r.nextInt(6) == 0 && (HostileEntity.isSpawnDark(w, pos, r) || r.nextInt(3) == 0);
    }

    static void wildSpawns() {
        for (EntityType<? extends HostileEntity> t : List.of(CULTIST, WRAITH, DIRE_WOLF, ANCIENT_GOLEM, ENHANCED_GOLEM, GOBLIN_LORD, ORC_LORD))
            SpawnRestriction.register(t, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        for (EntityType<? extends HostileEntity> t : List.of(BANDIT, THIEF, PIRATE, FAIRY))
            SpawnRestriction.register(t, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AWMobs::anyLight);
        SpawnRestriction.register(DRAKE, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AWMobs::drakeSpawn);
        SpawnRestriction.register(DRAGON_KING, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnIgnoreLightLevel);
        SpawnRestriction.register(MERMAID, SpawnRestriction.Location.IN_WATER, Heightmap.Type.OCEAN_FLOOR, AWMobs::mermaidSpawn);

        var PLAINS = ConventionalBiomeTags.PLAINS;
        BiomeModifications.addSpawn(where(List.of(PLAINS, ConventionalBiomeTags.FOREST), BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.FOREST,
                BiomeKeys.BIRCH_FOREST, BiomeKeys.MEADOW, BiomeKeys.SAVANNA), SpawnGroup.MONSTER, GOBLIN_LORD, 3, 1, 1);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.SAVANNA, ConventionalBiomeTags.BADLANDS, ConventionalBiomeTags.TAIGA), BiomeKeys.SAVANNA,
                BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.BADLANDS, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.TAIGA), SpawnGroup.MONSTER, ORC_LORD, 2, 1, 1);
        BiomeModifications.addSpawn(where(List.of(PLAINS, ConventionalBiomeTags.SAVANNA), BiomeKeys.PLAINS, BiomeKeys.SUNFLOWER_PLAINS, BiomeKeys.SAVANNA,
                BiomeKeys.SAVANNA_PLATEAU, BiomeKeys.FOREST, BiomeKeys.BIRCH_FOREST, BiomeKeys.MEADOW), SpawnGroup.MONSTER, BANDIT, 12, 2, 4);
        BiomeModifications.addSpawn(where(List.of(PLAINS, ConventionalBiomeTags.FOREST, ConventionalBiomeTags.TAIGA), BiomeKeys.PLAINS, BiomeKeys.FOREST,
                BiomeKeys.BIRCH_FOREST, BiomeKeys.TAIGA, BiomeKeys.MEADOW), SpawnGroup.MONSTER, THIEF, 6, 1, 1);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.BEACH, ConventionalBiomeTags.STONY_SHORES), BiomeKeys.BEACH, BiomeKeys.SNOWY_BEACH,
                BiomeKeys.STONY_SHORE), SpawnGroup.MONSTER, PIRATE, 30, 2, 3);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.SWAMP), BiomeKeys.DARK_FOREST, BiomeKeys.SWAMP, BiomeKeys.MANGROVE_SWAMP,
                BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.OLD_GROWTH_PINE_TAIGA), SpawnGroup.MONSTER, CULTIST, 18, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, CULTIST, 3, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, WRAITH, 6, 1, 1);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.SWAMP, ConventionalBiomeTags.DEAD), BiomeKeys.SWAMP, BiomeKeys.DARK_FOREST,
                BiomeKeys.MANGROVE_SWAMP, BiomeKeys.DEEP_DARK), SpawnGroup.MONSTER, WRAITH, 22, 1, 2);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.FLOWER_FORESTS, ConventionalBiomeTags.FLORAL), BiomeKeys.FLOWER_FOREST, BiomeKeys.MEADOW,
                BiomeKeys.CHERRY_GROVE, BiomeKeys.SUNFLOWER_PLAINS), SpawnGroup.MONSTER, FAIRY, 20, 1, 3);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.OCEAN), BiomeKeys.OCEAN, BiomeKeys.DEEP_OCEAN, BiomeKeys.WARM_OCEAN, BiomeKeys.LUKEWARM_OCEAN,
                BiomeKeys.DEEP_LUKEWARM_OCEAN, BiomeKeys.COLD_OCEAN, BiomeKeys.DEEP_COLD_OCEAN), SpawnGroup.MONSTER, MERMAID, 10, 1, 2);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.TAIGA, ConventionalBiomeTags.SNOWY), BiomeKeys.TAIGA, BiomeKeys.SNOWY_TAIGA,
                BiomeKeys.OLD_GROWTH_PINE_TAIGA, BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA, BiomeKeys.SNOWY_PLAINS, BiomeKeys.GROVE, BiomeKeys.FOREST), SpawnGroup.MONSTER, DIRE_WOLF, 15, 3, 5);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.JUNGLE, ConventionalBiomeTags.MOUNTAIN, ConventionalBiomeTags.DESERT, ConventionalBiomeTags.BADLANDS),
                BiomeKeys.JUNGLE, BiomeKeys.BAMBOO_JUNGLE, BiomeKeys.DESERT, BiomeKeys.BADLANDS, BiomeKeys.STONY_PEAKS, BiomeKeys.WINDSWEPT_HILLS), SpawnGroup.MONSTER, ANCIENT_GOLEM, 5, 1, 1);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.MOUNTAIN_PEAK, ConventionalBiomeTags.BADLANDS), BiomeKeys.BADLANDS, BiomeKeys.ERODED_BADLANDS,
                BiomeKeys.STONY_PEAKS, BiomeKeys.JAGGED_PEAKS), SpawnGroup.MONSTER, ENHANCED_GOLEM, 1, 1, 1);
        BiomeModifications.addSpawn(where(List.of(ConventionalBiomeTags.MOUNTAIN, ConventionalBiomeTags.MOUNTAIN_PEAK, ConventionalBiomeTags.EXTREME_HILLS),
                BiomeKeys.STONY_PEAKS, BiomeKeys.JAGGED_PEAKS, BiomeKeys.FROZEN_PEAKS, BiomeKeys.WINDSWEPT_HILLS, BiomeKeys.MEADOW, BiomeKeys.GROVE), SpawnGroup.MONSTER, DRAKE, 6, 1, 2);
    }

    private AWMobs() {
    }
}
