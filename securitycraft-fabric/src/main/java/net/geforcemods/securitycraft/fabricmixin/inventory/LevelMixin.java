package net.geforcemods.securitycraft.fabricmixin.inventory;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.blockentity.FreshBlockEntityQueue;
import net.geforcemods.securitycraft.fabric.blockentity.IBlockEntityExtension;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Calls {@link IBlockEntityExtension#onLoad()} for SecurityCraft's block entities at the same point NeoForge does: at the
 * start of Level#tickBlockEntities, for every block entity that was added to the level since the last block entity tick.
 * Block entities added while block entities are ticking are handled one tick later, like on NeoForge.
 */
@Mixin(Level.class)
public abstract class LevelMixin implements FreshBlockEntityQueue {
	@Shadow
	private boolean tickingBlockEntities;
	@Unique
	private final List<BlockEntity> securitycraft$freshBlockEntities = new ArrayList<>();
	@Unique
	private final List<BlockEntity> securitycraft$pendingFreshBlockEntities = new ArrayList<>();

	@Override
	public void securitycraft$addFreshBlockEntity(BlockEntity blockEntity) {
		if (blockEntity instanceof IBlockEntityExtension)
			(tickingBlockEntities ? securitycraft$pendingFreshBlockEntities : securitycraft$freshBlockEntities).add(blockEntity);
	}

	@Inject(method = "tickBlockEntities", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;tickingBlockEntities:Z", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
	private void securitycraft$callOnLoad(CallbackInfo ci) {
		if (!securitycraft$pendingFreshBlockEntities.isEmpty()) {
			securitycraft$freshBlockEntities.addAll(securitycraft$pendingFreshBlockEntities);
			securitycraft$pendingFreshBlockEntities.clear();
		}

		if (!securitycraft$freshBlockEntities.isEmpty()) {
			//block entities queued by onLoad itself go to the pending list, because tickingBlockEntities is true at this point
			for (BlockEntity blockEntity : securitycraft$freshBlockEntities) {
				//only call onLoad on block entities that have been fully added to the level
				if (!blockEntity.isRemoved() && blockEntity.hasLevel() && blockEntity instanceof IBlockEntityExtension extension)
					extension.onLoad();
			}

			securitycraft$freshBlockEntities.clear();
		}
	}
}
