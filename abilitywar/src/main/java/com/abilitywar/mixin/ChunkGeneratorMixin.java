package com.abilitywar.mixin;

import com.abilitywar.world.RpgStructures;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** RPG 구조물 (수도·마을·던전) 이 걸친 청크에는 나무·풀 같은 식생을 심지 않음 (건물 안에 나뭇잎이 생기지 않게). */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @WrapOperation(method = "generateFeatures", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/gen/feature/PlacedFeature;generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean abilitywar$noVegetationInRpgStructures(PlacedFeature feature, StructureWorldAccess world, ChunkGenerator gen, Random random, BlockPos pos,
                                                           Operation<Boolean> original, @Local(argsOnly = true) Chunk chunk) {
        if (RpgStructures.suppressFeature(chunk, feature, gen)) return false;
        return original.call(feature, world, gen, random, pos);
    }
}
