package net.geforcemods.securitycraft.fabricmixin.events;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.event.BlockPlacementCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Records the positions of the blocks changed through Level#setBlock while a capture is active, at the same point NeoForge
 * records its block snapshots: right before the chunk changes the block, removing the position again if nothing changed.
 * Captures are only started on the server (see EventHooks#useOnRecordingPlacements).
 */
@Mixin(Level.class)
public abstract class LevelPlacementCaptureMixin implements BlockPlacementCapture {
	@Unique
	private List<BlockPos> securitycraft$capturedPlacements;

	@Override
	public List<BlockPos> securitycraft$startCapturingPlacements() {
		List<BlockPos> previous = securitycraft$capturedPlacements;

		securitycraft$capturedPlacements = new ArrayList<>();
		return previous;
	}

	@Override
	public List<BlockPos> securitycraft$stopCapturingPlacements(List<BlockPos> previous) {
		List<BlockPos> captured = securitycraft$capturedPlacements;

		securitycraft$capturedPlacements = previous;
		return captured == null ? List.of() : captured;
	}

	@WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState securitycraft$recordPlacement(LevelChunk chunk, BlockPos pos, BlockState state, boolean isMoving, Operation<BlockState> original) {
		List<BlockPos> captured = securitycraft$capturedPlacements;

		if (captured == null)
			return original.call(chunk, pos, state, isMoving);

		BlockPos immutablePos = pos.immutable();
		int index = captured.size();

		captured.add(immutablePos);

		BlockState oldState = original.call(chunk, pos, state, isMoving);

		//nothing was changed, so remove the entry again. Block changes caused by this one have been added after it, so its index is still the same
		if (oldState == null && index < captured.size() && captured.get(index) == immutablePos)
			captured.remove(index);

		return oldState;
	}
}
