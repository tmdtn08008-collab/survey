package com.abilitywar.client.mixin;

import com.abilitywar.client.input.InputHandler;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @Inject(method = "updateKeysByCode", at = @At("TAIL"))
    private static void aw$fix(CallbackInfo ci) {
        InputHandler.fixKeyMap();
    }
}
