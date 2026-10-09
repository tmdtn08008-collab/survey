package net.geforcemods.securitycraft.fabric.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric replacement for NeoForge's IBaseRailBlockExtension#onMinecartPass, implemented only by SecurityCraft's rails (the
 * track mine). SecurityCraft's AbstractMinecartMixin calls it on the server at the end of AbstractMinecart#moveAlongTrack,
 * every tick a minecart moves along this rail.
 */
public interface MinecartPassBlockHook {
	/**
	 * Called every tick a minecart moves along this rail.
	 *
	 * @param state The state of the rail
	 * @param level The level the rail is in
	 * @param pos The position of the rail
	 * @param cart The minecart that is passing over the rail
	 */
	default void onMinecartPass(BlockState state, Level level, BlockPos pos, AbstractMinecart cart) {}
}
