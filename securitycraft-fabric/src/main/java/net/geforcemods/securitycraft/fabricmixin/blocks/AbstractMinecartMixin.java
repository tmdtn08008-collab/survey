package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.block.MinecartPassBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Calls {@link MinecartPassBlockHook#onMinecartPass} for SecurityCraft rails (the track mine) every tick a minecart moves
 * along them, like NeoForge's patched AbstractMinecart#moveAlongTrack. NeoForge calls it right before the powered rail
 * handling at the end of the method, which only returns early for powered rails, so calling it at the end is equivalent for
 * every other rail.
 */
@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {
	// RETURN, not TAIL: moveAlongTrack has an early return in its powered rail branch, and NeoForge calls onMinecartPass before it
	@Inject(method = "moveAlongTrack", at = @At("RETURN"))
	private void securitycraft$callOnMinecartPass(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (state.getBlock() instanceof MinecartPassBlockHook hook) {
			AbstractMinecart self = (AbstractMinecart) (Object) this;

			hook.onMinecartPass(state, self.level(), pos, self);
		}
	}
}
