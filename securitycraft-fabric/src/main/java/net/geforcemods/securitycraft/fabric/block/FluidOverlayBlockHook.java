package net.geforcemods.securitycraft.fabric.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Fabric replacement for NeoForge's IBlockExtension#shouldDisplayFluidOverlay, implemented only by SecurityCraft's blocks
 * (reinforced glass). Fabric API already replaces vanilla's check in LiquidBlockRenderer with its per-block
 * FluidRenderHandlerRegistry#isBlockTransparent, so instead of another mixin into the fluid renderer, SecurityCraft's client
 * LiquidBlockRendererMixin registers every block implementing this interface whose {@link #shouldDisplayFluidOverlay}
 * returns true for its default state with FluidRenderHandlerRegistry#setBlockTransparency when the fluid sprites are
 * (re)loaded. This also makes the overlay work with renderers like Sodium that use Fabric's registry.
 */
public interface FluidOverlayBlockHook {
	/**
	 * @param state The state of this block
	 * @param level The level, or null when queried for the per-block registration described above
	 * @param pos The position of this block, or null when queried for the per-block registration described above
	 * @param fluidState The state of the fluid next to this block
	 * @return true if the fluid's side next to this block should use the overlay texture, like next to glass
	 */
	default boolean shouldDisplayFluidOverlay(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, FluidState fluidState) {
		return state.getBlock() instanceof HalfTransparentBlock || state.getBlock() instanceof LeavesBlock;
	}
}
