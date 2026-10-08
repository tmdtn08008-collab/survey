package net.geforcemods.securitycraft.fabric.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Fabric stand-in for NeoForge's ServerLifecycleHooks#getCurrentServer. {@link #init()} must be called from the mod
 * initializer.
 */
public final class ServerLifecycleHooks {
	private static MinecraftServer currentServer;

	private ServerLifecycleHooks() {}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
	}

	public static MinecraftServer getCurrentServer() {
		return currentServer;
	}
}
