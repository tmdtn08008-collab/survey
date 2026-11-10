package com.abilitywar.mixin;

import com.abilitywar.gear.GearItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** RPG 장비는 바닐라 능력치 줄('주로 사용하는 손에 있을 때: ...')을 숨기고 직접 그린 능력치 표를 보여줌. */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getHideFlags", at = @At("RETURN"), cancellable = true)
    private void aw$hideGearModifiers(CallbackInfoReturnable<Integer> cir) {
        if (((ItemStack) (Object) this).getItem() instanceof GearItem) {
            cir.setReturnValue(cir.getReturnValueI() | ItemStack.TooltipSection.MODIFIERS.getFlag() | ItemStack.TooltipSection.UNBREAKABLE.getFlag());
        }
    }
}
