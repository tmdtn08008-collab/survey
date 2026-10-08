package net.geforcemods.securitycraft.fabric.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-only half of {@link PayloadRegistrar} and {@link PacketDistributor}. Kept in its own class so that dedicated servers
 * never load the client networking classes.
 */
final class ClientPayloadReceivers {
	private ClientPayloadReceivers() {}

	static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
		ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context::player));
	}

	static void send(CustomPacketPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
