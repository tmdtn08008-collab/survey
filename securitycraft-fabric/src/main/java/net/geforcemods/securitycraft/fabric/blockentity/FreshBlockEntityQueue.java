package net.geforcemods.securitycraft.fabric.blockentity;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Implemented by every {@link net.minecraft.world.level.Level} through a SecurityCraft mixin. Holds the block entities
 * whose {@link IBlockEntityExtension#onLoad()} still needs to be called, mirroring NeoForge's "fresh block entities" list.
 */
public interface FreshBlockEntityQueue {
	/**
	 * Queues the block entity so that {@link IBlockEntityExtension#onLoad()} is called at the start of the level's next
	 * block entity tick. Block entities that do not implement {@link IBlockEntityExtension} are ignored.
	 */
	void securitycraft$addFreshBlockEntity(BlockEntity blockEntity);
}
