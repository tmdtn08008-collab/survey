package com.abilitywar.client.mixin;

import com.abilitywar.client.gear.GearClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TooltipBackgroundRenderer.class)
public abstract class TooltipBackgroundRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private static void aw$gearBorder(DrawContext context, int x, int y, int width, int height, int z, CallbackInfo ci) {
        GearClient.tooltipBorder(context, x, y, width, height, z);
    }
}
