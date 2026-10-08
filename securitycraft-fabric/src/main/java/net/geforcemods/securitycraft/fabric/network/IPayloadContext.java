package net.geforcemods.securitycraft.fabric.network;

import net.minecraft.world.entity.player.Player;

/**
 * Fabric stand-in for NeoForge's IPayloadContext. Fabric already invokes play payload handlers on the main thread, so
 * {@link #enqueueWork(Runnable)} runs the task right away.
 */
public interface IPayloadContext {
	Player player();

	default void enqueueWork(Runnable task) {
		task.run();
	}
}
