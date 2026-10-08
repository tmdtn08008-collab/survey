package net.geforcemods.securitycraft.fabric.world;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;

/**
 * Fabric stand-in for NeoForge's TicketController, backed by vanilla region tickets. Unlike NeoForge's forced chunks, these
 * tickets are not saved with the world; SecurityCraft's camera chunk tickets were discarded on load anyway. The "ticking"
 * flag (NeoForge's forced random ticks) is not supported; SecurityCraft never sets it.
 */
public final class TicketController {
	private static final int TICKET_DISTANCE = 2;
	private final TicketType<BlockPos> blockTicket;
	private final TicketType<UUID> entityTicket;

	public TicketController(ResourceLocation id) {
		blockTicket = TicketType.create(id + "_block", Vec3i::compareTo);
		entityTicket = TicketType.create(id + "_entity", UUID::compareTo);
	}

	public boolean forceChunk(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		return forceChunk(level, blockTicket, owner.immutable(), chunkX, chunkZ, add);
	}

	public boolean forceChunk(ServerLevel level, Entity owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		return forceChunk(level, owner.getUUID(), chunkX, chunkZ, add, ticking);
	}

	public boolean forceChunk(ServerLevel level, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
		return forceChunk(level, entityTicket, owner, chunkX, chunkZ, add);
	}

	private <T> boolean forceChunk(ServerLevel level, TicketType<T> type, T owner, int chunkX, int chunkZ, boolean add) {
		ChunkPos pos = new ChunkPos(chunkX, chunkZ);

		if (add) {
			level.getChunk(chunkX, chunkZ);
			level.getChunkSource().addRegionTicket(type, pos, TICKET_DISTANCE, owner);
		}
		else
			level.getChunkSource().removeRegionTicket(type, pos, TICKET_DISTANCE, owner);

		return true;
	}
}
