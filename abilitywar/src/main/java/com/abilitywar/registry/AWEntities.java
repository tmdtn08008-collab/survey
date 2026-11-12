package com.abilitywar.registry;

import com.abilitywar.AbilityWar;
import com.abilitywar.entity.VoidSingularityEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class AWEntities {
    public static final EntityType<VoidSingularityEntity> VOID_SINGULARITY = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("void_singularity"),
            FabricEntityTypeBuilder.<VoidSingularityEntity>create(SpawnGroup.MISC, VoidSingularityEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0f, 2.0f))
                    .trackRangeBlocks(160)
                    .trackedUpdateRate(1)
                    .fireImmune()
                    .disableSummon()
                    .build());

    /** 전직 교관 NPC. */
    public static final EntityType<com.abilitywar.rpg.JobMasterEntity> JOB_MASTER = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("job_master"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.JobMasterEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.JobMasterEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item JOB_MASTER_EGG = Registry.register(Registries.ITEM, AbilityWar.id("job_master_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(JOB_MASTER, 0x2A2440, 0xF0C860, new net.minecraft.item.Item.Settings()));

    /** 왕 NPC (성기사 퀘스트). */
    public static final EntityType<com.abilitywar.rpg.KingEntity> KING = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("king"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.KingEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.KingEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item KING_EGG = Registry.register(Registries.ITEM, AbilityWar.id("king_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(KING, 0x8A1020, 0xF0C860, new net.minecraft.item.Item.Settings()));

    /** 모험가 길드 접수원 NPC. */
    public static final EntityType<com.abilitywar.rpg.GuildClerkEntity> GUILD_CLERK = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("guild_clerk"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.GuildClerkEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.GuildClerkEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item GUILD_CLERK_EGG = Registry.register(Registries.ITEM, AbilityWar.id("guild_clerk_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(GUILD_CLERK, 0x2A4A7A, 0xD8B060, new net.minecraft.item.Item.Settings()));

    /** 물약 상인 NPC (시장 · 길드 · 마을 여관). */
    public static final EntityType<com.abilitywar.rpg.ShopNpcEntity> APOTHECARY = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("apothecary"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.ShopNpcEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.ShopNpcEntity::apothecary)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item APOTHECARY_EGG = Registry.register(Registries.ITEM, AbilityWar.id("apothecary_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(APOTHECARY, 0x3A6A3A, 0xE04040, new net.minecraft.item.Item.Settings()));

    /** 대장장이 NPC: 장비 강화 · 승급 (수도 대장간 · 마을 대장간). */
    public static final EntityType<com.abilitywar.rpg.ShopNpcEntity> BLACKSMITH = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("blacksmith"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.ShopNpcEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.ShopNpcEntity::blacksmith)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item BLACKSMITH_EGG = Registry.register(Registries.ITEM, AbilityWar.id("blacksmith_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(BLACKSMITH, 0x4A3A2A, 0xFF8A30, new net.minecraft.item.Item.Settings()));

    /** 탑지기 NPC: 심연의 탑 (수도 모험가 길드 옆). */
    public static final EntityType<com.abilitywar.rpg.ShopNpcEntity> TOWER_KEEPER = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("tower_keeper"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.ShopNpcEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.ShopNpcEntity::towerKeeper)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item TOWER_KEEPER_EGG = Registry.register(Registries.ITEM, AbilityWar.id("tower_keeper_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(TOWER_KEEPER, 0x1A1030, 0xB070FF, new net.minecraft.item.Item.Settings()));

    /** 던전 안내인 NPC: 인스턴스 던전 (수도 모험가 길드 옆, 탑 문 반대편). */
    public static final EntityType<com.abilitywar.rpg.ShopNpcEntity> DUNGEON_GUIDE = Registry.register(Registries.ENTITY_TYPE,
            AbilityWar.id("dungeon_guide"),
            FabricEntityTypeBuilder.<com.abilitywar.rpg.ShopNpcEntity>create(SpawnGroup.MISC, com.abilitywar.rpg.ShopNpcEntity::dungeonGuide)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .fireImmune()
                    .build());
    public static final net.minecraft.item.Item DUNGEON_GUIDE_EGG = Registry.register(Registries.ITEM, AbilityWar.id("dungeon_guide_spawn_egg"),
            new net.minecraft.item.SpawnEggItem(DUNGEON_GUIDE, 0x3A2A1A, 0xE0A040, new net.minecraft.item.Item.Settings()));

    public static void init() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(KING, com.abilitywar.rpg.JobMasterEntity.createAttributes());
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> e.add(KING_EGG));
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(JOB_MASTER, com.abilitywar.rpg.JobMasterEntity.createAttributes());
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> e.add(JOB_MASTER_EGG));
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(GUILD_CLERK, com.abilitywar.rpg.JobMasterEntity.createAttributes());
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> e.add(GUILD_CLERK_EGG));
        for (var t : java.util.List.of(APOTHECARY, BLACKSMITH, TOWER_KEEPER, DUNGEON_GUIDE))
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(t, com.abilitywar.rpg.JobMasterEntity.createAttributes());
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> {
            e.add(APOTHECARY_EGG);
            e.add(BLACKSMITH_EGG);
            e.add(TOWER_KEEPER_EGG);
            e.add(DUNGEON_GUIDE_EGG);
        });
    }

    private AWEntities() {
    }
}
