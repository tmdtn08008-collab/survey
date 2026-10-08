package net.geforcemods.securitycraft.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.geforcemods.securitycraft.SecurityCraft;

/**
 * Client half of {@link VersionCheck}. Client only: must only be referenced from the client initializer.
 */
public final class VersionCheckClient {
	private VersionCheckClient() {}

	/**
	 * Answers the server's version check with this client's SecurityCraft version. Registering this receiver is also what tells
	 * the server that this client has SecurityCraft. Must be called exactly once, from the client initializer, after
	 * {@link VersionCheck#register()}.
	 */
	public static void register() {
		ClientConfigurationNetworking.registerGlobalReceiver(VersionCheck.TYPE, (payload, context) -> context.responseSender().sendPacket(new VersionCheck(SecurityCraft.getVersion())));
	}
}
