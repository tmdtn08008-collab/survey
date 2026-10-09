package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.geforcemods.securitycraft.fabric.block.WeakPowerBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft blocks decide whether they conduct the strong redstone power they receive, like NeoForge's patched
 * SignalGetter#getSignal(BlockPos, Direction), which asks IBlockExtension#shouldCheckWeakPower instead of
 * BlockState#isRedstoneConductor. Keypads, scanners and similar blocks return false so that strongly powering them does not
 * also power the door next to them.
 * <p>
 * getSignal is a default method of this interface, so this is an interface mixin. To only rely on plain Mixin injectors
 * there, SecurityCraft blocks get vanilla's one-line computation with the hook's decision in place of the
 * isRedstoneConductor check, and all other blocks run vanilla's method unchanged.
 */
@Mixin(SignalGetter.class)
public interface SignalGetterMixin {
	@Inject(method = "getSignal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I", at = @At("HEAD"), cancellable = true)
	private void securitycraft$shouldCheckWeakPower(BlockPos pos, Direction side, CallbackInfoReturnable<Integer> cir) {
		SignalGetter level = (SignalGetter) this;
		BlockState state = level.getBlockState(pos);

		if (state.getBlock() instanceof WeakPowerBlockHook hook) {
			int signal = state.getSignal(level, pos, side);

			cir.setReturnValue(hook.shouldCheckWeakPower(state, level, pos, side) ? Math.max(signal, level.getDirectSignalTo(pos)) : signal);
		}
	}
}
