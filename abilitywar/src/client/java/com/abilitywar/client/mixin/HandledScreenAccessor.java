package com.abilitywar.client.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int aw$x();

    @Accessor("y")
    int aw$y();

    @Accessor("backgroundWidth")
    int aw$bw();

    @Accessor("backgroundHeight")
    int aw$bh();
}
