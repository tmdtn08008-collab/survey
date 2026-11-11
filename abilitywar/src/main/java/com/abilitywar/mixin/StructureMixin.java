package com.abilitywar.mixin;

import com.abilitywar.world.RpgStructures;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 수도와 왕령 (반지름 23청크) 에는 다른 지상 구조물 (마을 · 전초기지 · 다른 모드의 묘지 · 탑 등) 이 생기지 않게 함.
 * 지하 구조물 (요새 · 폐광 · 고대 도시) 은 그대로.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
    @Inject(method = "getValidStructurePosition", at = @At("HEAD"), cancellable = true)
    private void abilitywar$keepCapitalClear(Structure.Context ctx, CallbackInfoReturnable<Optional<Structure.StructurePosition>> cir) {
        Structure self = (Structure) (Object) this;
        if (self instanceof RpgStructures.RpgStructure) return;
        if (self.getFeatureGenerationStep() != GenerationStep.Feature.SURFACE_STRUCTURES) return;
        if (RpgStructures.nearCapital(ctx)) cir.setReturnValue(Optional.empty());
    }
}
