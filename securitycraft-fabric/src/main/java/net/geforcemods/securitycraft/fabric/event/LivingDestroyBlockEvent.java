package net.geforcemods.securitycraft.fabric.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric stand-in for NeoForge's LivingDestroyBlockEvent. SecurityCraft only fires it where the wither destroys blocks
 * around itself (WitherBoss#customServerAiStep), since the wither is the only entity SecurityCraft's listener acts on.
 * Canceling it keeps the block.
 */
public class LivingDestroyBlockEvent extends CancellableEvent {
	private final LivingEntity entity;
	private final BlockPos pos;
	private final BlockState state;

	public LivingDestroyBlockEvent(LivingEntity entity, BlockPos pos, BlockState state) {
		this.entity = entity;
		this.pos = pos;
		this.state = state;
	}

	public LivingEntity getEntity() {
		return entity;
	}

	public BlockPos getPos() {
		return pos;
	}

	public BlockState getState() {
		return state;
	}
}
