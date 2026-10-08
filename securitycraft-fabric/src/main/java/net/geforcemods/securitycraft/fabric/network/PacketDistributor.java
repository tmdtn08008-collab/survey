package net.geforcemods.securitycraft.fabric.network;

import java.util.Collection;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric stand-in for NeoForge's PacketDistributor, with the same static method names so call sites stay unchanged.
 */
public final class PacketDistributor {
	private PacketDistributor() {}

	public static void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
		ClientPayloadReceivers.send(payload);

		for (CustomPacketPayload other : payloads) {
			ClientPayloadReceivers.send(other);
		}
	}

	public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		send(player, payload, payloads);
	}

	public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToAll(PlayerLookup.world(level), payload, payloads);
	}

	public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded, double x, double y, double z, double radius, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		for (ServerPlayer player : PlayerLookup.around(level, new Vec3(x, y, z), radius)) {
			if (player != excluded)
				send(player, payload, payloads);
		}
	}

	public static void sendToAllPlayers(MinecraftServer server, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToAll(PlayerLookup.all(server), payload, payloads);
	}

	public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToAll(PlayerLookup.tracking(entity), payload, payloads);
	}

	public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToPlayersTrackingEntity(entity, payload, payloads);

		if (entity instanceof ServerPlayer player)
			send(player, payload, payloads);
	}

	public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunkPos, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToAll(PlayerLookup.tracking(level, chunkPos), payload, payloads);
	}

	public static void sendToPlayersTrackingBlock(ServerLevel level, BlockPos pos, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		sendToAll(PlayerLookup.tracking(level, pos), payload, payloads);
	}

	private static void sendToAll(Collection<ServerPlayer> players, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		for (ServerPlayer player : players) {
			send(player, payload, payloads);
		}
	}

	private static void send(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
		ServerPlayNetworking.send(player, payload);

		for (CustomPacketPayload other : payloads) {
			ServerPlayNetworking.send(player, other);
		}
	}
}
