package net.geforcemods.securitycraft.api;

/**
 * Fabric replacement for SecurityCraft's NeoForge IMC messages. Other mods add an entrypoint named "securitycraft" in their
 * fabric.mod.json that implements this interface, and register their objects through the static methods of
 * {@link SecurityCraftAPI}. Plugins run during SecurityCraft's initialization, so they must not resolve other mods'
 * registry content in {@link #register()} itself.
 */
@FunctionalInterface
public interface SecurityCraftPlugin {
	void register();
}
