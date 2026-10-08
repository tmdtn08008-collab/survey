package net.geforcemods.securitycraft.fabricmixin.inventory;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.blockentity.FreshBlockEntityQueue;
import net.geforcemods.securitycraft.fabric.blockentity.IBlockEntityExtension;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Queues SecurityCraft's block entities for {@link IBlockEntityExtension#onLoad()} at the two places NeoForge does: when a
 * block entity is added to a chunk that is in the level, and when a loaded chunk registers all of its block entities.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
	@Shadow
	@Final
	Level level;

	@Inject(method = "addAndRegisterBlockEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V", shift = At.Shift.AFTER))
	private void securitycraft$queueOnLoad(BlockEntity blockEntity, CallbackInfo ci) {
		if (blockEntity instanceof IBlockEntityExtension)
			((FreshBlockEntityQueue) level).securitycraft$addFreshBlockEntity(blockEntity);
	}

	@Inject(method = "registerAllBlockEntitiesAfterLevelLoad", at = @At("HEAD"))
	private void securitycraft$queueOnLoadForAll(CallbackInfo ci) {
		for (BlockEntity blockEntity : ((LevelChunk) (Object) this).getBlockEntities().values()) {
			if (blockEntity instanceof IBlockEntityExtension)
				((FreshBlockEntityQueue) level).securitycraft$addFreshBlockEntity(blockEntity);
		}
	}
}
