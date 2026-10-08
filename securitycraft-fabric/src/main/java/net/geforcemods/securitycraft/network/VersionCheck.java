package net.geforcemods.securitycraft.network;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.geforcemods.securitycraft.SecurityCraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;

/**
 * Fabric replacement for NeoForge's channel negotiation. On NeoForge, SecurityCraft's payloads are registered as required and
 * versioned with {@link SecurityCraft#getVersion()}, so a client that does not have SecurityCraft, or has a different version
 * of it, is disconnected while the connection is being configured. Fabric has no such check, so this adds one: during the
 * configuration phase the server sends its version, the client answers with its own, and the client may only continue if
 * both match. Clients that cannot receive this payload (no SecurityCraft) are disconnected right away. Call
 * {@link #register()} once from the common initializer and {@link VersionCheckClient#register()} once from the client
 * initializer.
 */
// PORT-NOTE: Only the server enforces the check. NeoForge would also stop a client with SecurityCraft from joining a server
// without it (vanilla or modded); on Fabric such a client can join, which is harmless because the server has none of
// SecurityCraft's content and ignores payloads it does not know.
public record VersionCheck(String version) implements CustomPacketPayload {
	public static final Type<VersionCheck> TYPE = new Type<>(SecurityCraft.resLoc("version_check"));
	//@formatter:off
	public static final StreamCodec<ByteBuf, VersionCheck> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, VersionCheck::version,
			VersionCheck::new);
	//@formatter:on

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/**
	 * Registers the payload for both configuration directions and the server half of the check. Must be called exactly once,
	 * from the common initializer, before {@link VersionCheckClient#register()}.
	 */
	public static void register() {
		PayloadTypeRegistry.configurationS2C().register(TYPE, STREAM_CODEC);
		PayloadTypeRegistry.configurationC2S().register(TYPE, STREAM_CODEC);
		ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
			if (ServerConfigurationNetworking.canSend(handler, TYPE))
				handler.addTask(new VersionCheckTask());
			else {
				SecurityCraft.LOGGER.info("Disconnecting {}: the client does not have SecurityCraft installed", handler.getOwner().getName());
				handler.disconnect(incompatibleClientMessage());
			}
		});
		ServerConfigurationNetworking.registerGlobalReceiver(TYPE, (payload, context) -> {
			if (SecurityCraft.getVersion().equals(payload.version()))
				context.networkHandler().completeTask(VersionCheckTask.TYPE);
			else {
				SecurityCraft.LOGGER.info("Disconnecting {}: the client has SecurityCraft {}, but the server has SecurityCraft {}", context.networkHandler().getOwner().getName(), payload.version(), SecurityCraft.getVersion());
				context.networkHandler().disconnect(incompatibleClientMessage());
			}
		});
	}

	private static Component incompatibleClientMessage() {
		return Component.translatable("multiplayer.disconnect.incompatible", "SecurityCraft " + SecurityCraft.getVersion());
	}

	private record VersionCheckTask() implements ConfigurationTask {
		private static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(VersionCheck.TYPE.id().toString());

		@Override
		public void start(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createS2CPacket(new VersionCheck(SecurityCraft.getVersion())));
		}

		@Override
		public ConfigurationTask.Type type() {
			return TYPE;
		}
	}
}
