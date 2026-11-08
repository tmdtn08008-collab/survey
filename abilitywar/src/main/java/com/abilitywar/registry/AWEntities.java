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

    public static void init() {
    }

    private AWEntities() {
    }
}
