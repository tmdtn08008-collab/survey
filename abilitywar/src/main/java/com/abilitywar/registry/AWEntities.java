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

    public static void init() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(JOB_MASTER, com.abilitywar.rpg.JobMasterEntity.createAttributes());
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.SPAWN_EGGS).register(e -> e.add(JOB_MASTER_EGG));
    }

    private AWEntities() {
    }
}
