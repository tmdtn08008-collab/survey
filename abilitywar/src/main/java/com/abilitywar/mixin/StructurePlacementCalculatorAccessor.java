package com.abilitywar.mixin;

import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StructurePlacementCalculator.class)
public interface StructurePlacementCalculatorAccessor {
    @Accessor("biomeSource")
    BiomeSource abilitywar$getBiomeSource();
}
