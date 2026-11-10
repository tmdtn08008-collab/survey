package com.abilitywar.client.mixin;

import com.abilitywar.client.gear.GearClient;
import com.abilitywar.gear.GearItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 장비 칸 배경을 등급 색으로, 장비 툴팁은 등급 색 테두리. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenGearMixin {
    @Shadow
    protected Slot focusedSlot;

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void aw$gearSlot(DrawContext context, Slot slot, CallbackInfo ci) {
        if (slot.hasStack()) GearClient.slotBackground(context, slot.x, slot.y, slot.getStack());
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"))
    private void aw$gearTooltipStart(DrawContext context, int x, int y, CallbackInfo ci) {
        GearClient.tooltipGrade = focusedSlot != null && focusedSlot.hasStack() && focusedSlot.getStack().getItem() instanceof GearItem gi ? gi.grade() : null;
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("RETURN"))
    private void aw$gearTooltipEnd(DrawContext context, int x, int y, CallbackInfo ci) {
        GearClient.tooltipGrade = null;
    }
}
