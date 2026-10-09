package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.block.NeighborChangeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Calls {@link NeighborChangeBlockHook#onNeighborChange} for SecurityCraft blocks next to a position whose block entity
 * changed, at the places NeoForge calls IBlockExtension#onNeighborChange:
 * <ul>
 * <li>Level#updateNeighbourForOutputSignal (a block entity's contents changed or a container was removed). NeoForge calls
 * it for the blocks in all six directions, while vanilla's comparator updates there only look at the four horizontal
 * ones. Vanilla's comparator updates are left untouched.</li>
 * <li>Level#removeBlockEntity, after which NeoForge runs updateNeighbourForOutputSignal. Only the SecurityCraft hooks are
 * called there, so vanilla comparators are not updated more often than in vanilla.</li>
 * </ul>
 */
@Mixin(Level.class)
public abstract class LevelNeighborChangeMixin {
	@Inject(method = "updateNeighbourForOutputSignal", at = @At("HEAD"))
	private void securitycraft$onUpdateNeighbourForOutputSignal(BlockPos pos, Block block, CallbackInfo ci) {
		securitycraft$callOnNeighborChange(pos);
	}

	@Inject(method = "removeBlockEntity", at = @At("TAIL"))
	private void securitycraft$onRemoveBlockEntity(BlockPos pos, CallbackInfo ci) {
		securitycraft$callOnNeighborChange(pos);
	}

	@Unique
	private void securitycraft$callOnNeighborChange(BlockPos pos) {
		Level level = (Level) (Object) this;

		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = pos.relative(direction);

			if (level.hasChunkAt(neighborPos)) {
				BlockState neighborState = level.getBlockState(neighborPos);

				if (neighborState.getBlock() instanceof NeighborChangeBlockHook hook)
					hook.onNeighborChange(neighborState, level, neighborPos, pos);
			}
		}
	}
}
