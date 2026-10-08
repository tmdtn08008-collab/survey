package net.geforcemods.securitycraft.fabricmixin.inventory;

import java.util.Optional;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.blockentity.IBlockEntityExtension;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Calls {@link IBlockEntityExtension#onDataPacket} for SecurityCraft's block entities when a block entity data packet
 * arrives, in place of vanilla's loadWithComponents call (the place NeoForge patched).
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@WrapOperation(method = "handleBlockEntityData", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
	private void securitycraft$callOnDataPacket(Optional<BlockEntity> blockEntity, Consumer<BlockEntity> vanillaHandler, Operation<Void> original, @Local(argsOnly = true) ClientboundBlockEntityDataPacket packet) {
		original.call(blockEntity, (Consumer<BlockEntity>) be -> {
			if (be instanceof IBlockEntityExtension extension) {
				ClientPacketListener self = (ClientPacketListener) (Object) this;

				extension.onDataPacket(self.getConnection(), packet, self.registryAccess());
			}
			else
				vanillaHandler.accept(be);
		});
	}
}
