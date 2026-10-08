package net.geforcemods.securitycraft.fabricmixin.inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.player.Player;

/**
 * NeoForge makes Player#closeContainer public with its access transformer. This invoker calls it virtually, so the
 * ServerPlayer and LocalPlayer overrides run exactly as they would on NeoForge.
 */
@Mixin(Player.class)
public interface PlayerInvoker {
	@Invoker("closeContainer")
	void securitycraft$closeContainer();
}
