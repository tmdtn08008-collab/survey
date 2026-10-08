package net.geforcemods.securitycraft.fabric.network;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Fabric stand-in for NeoForge's PayloadRegistrar. Payload types and server-bound receivers are registered right away (on
 * both sides); client-bound receivers are remembered and registered by {@link #registerClientReceivers()}, which must only be
 * called from the client initializer.
 */
public final class PayloadRegistrar {
	private static final List<ClientReceiver<?>> CLIENT_RECEIVERS = new ArrayList<>();

	public <T extends CustomPacketPayload> PayloadRegistrar playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
		PayloadTypeRegistry.playC2S().register(type, codec);
		ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.handle(payload, context::player));
		return this;
	}

	public <T extends CustomPacketPayload> PayloadRegistrar playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, IPayloadHandler<T> handler) {
		PayloadTypeRegistry.playS2C().register(type, codec);
		CLIENT_RECEIVERS.add(new ClientReceiver<>(type, handler));
		return this;
	}

	public static void registerClientReceivers() {
		for (ClientReceiver<?> receiver : CLIENT_RECEIVERS) {
			receiver.register();
		}
	}

	private record ClientReceiver<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, IPayloadHandler<T> handler) {
		void register() {
			ClientPayloadReceivers.register(type, handler);
		}
	}
}
