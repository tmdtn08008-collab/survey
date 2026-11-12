package com.abilitywar.client.mixin;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 엔더 드래곤 모델에 뿔 · 가시 · 발톱을 더함 (DragonLook). */
@Mixin(EnderDragonEntityRenderer.class)
public abstract class EnderDragonRendererMixin {
    @ModifyArg(method = "getTexturedModelData", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/TexturedModelData;of(Lnet/minecraft/client/model/ModelData;II)Lnet/minecraft/client/model/TexturedModelData;"), index = 0)
    private static ModelData aw$moreDragon(ModelData data) {
        try {
            com.abilitywar.client.fx.DragonLook.addParts(data.getRoot());
        } catch (Exception e) {
            com.abilitywar.AbilityWar.LOG.warn("[abilitywar] could not decorate the ender dragon model", e);
        }
        return data;
    }
}
