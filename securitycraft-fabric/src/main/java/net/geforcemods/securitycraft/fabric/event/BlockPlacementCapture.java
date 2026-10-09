package net.geforcemods.securitycraft.fabric.event;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

/**
 * Implemented by Level (through SecurityCraft's LevelPlacementCaptureMixin). While capturing on the server, every block
 * change made through Level#setBlock records the changed position, in the order the changes were started. This replaces
 * NeoForge's block snapshot capturing, which SecurityCraft needs for {@link BlockEvent.EntityPlaceEvent}.
 */
public interface BlockPlacementCapture {
	/**
	 * Starts recording block changes in this level.
	 *
	 * @return The list of the capture that was active before, which needs to be handed to
	 *         {@link #securitycraft$stopCapturingPlacements(List)}
	 */
	@Nullable
	List<BlockPos> securitycraft$startCapturingPlacements();

	/**
	 * Stops recording block changes and restores the capture that was active before.
	 *
	 * @param previous The value {@link #securitycraft$startCapturingPlacements()} returned
	 * @return The positions of all blocks that were changed while capturing, in the order the changes were started
	 */
	List<BlockPos> securitycraft$stopCapturingPlacements(@Nullable List<BlockPos> previous);
}
