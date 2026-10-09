package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.geforcemods.securitycraft.fabric.block.RedstoneConnectionBlockHook;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft blocks decide whether redstone dust visually connects to them, like NeoForge's patched
 * RedStoneWireBlock#getConnectingSide, which asks IBlockExtension#canConnectRedstone. Vanilla's getConnectingSide does all
 * its connection checks through the static shouldConnectTo(BlockState, Direction) (the one-argument overload delegates to
 * it with a null direction), so hooking it covers the same checks. It does not know the level and position, which no
 * SecurityCraft block needs.
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {
	@Inject(method = "shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
	private static void securitycraft$canConnectRedstone(BlockState state, Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (state.getBlock() instanceof RedstoneConnectionBlockHook hook)
			cir.setReturnValue(hook.canConnectRedstone(state, null, null, direction));
	}
}
