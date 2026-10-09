package net.geforcemods.securitycraft.fabricmixin.blocks.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.block.CloneItemStackBlockHook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Uses the context-aware {@link CloneItemStackBlockHook#getCloneItemStack} when picking a SecurityCraft block (disguised
 * blocks and mines give their disguise, display cases their displayed item...), like NeoForge's patched Minecraft#pickBlock.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPickBlockMixin {
	@WrapOperation(method = "pickBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getCloneItemStack(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack securitycraft$getCloneItemStack(Block block, LevelReader level, BlockPos pos, BlockState state, Operation<ItemStack> original) {
		if (block instanceof CloneItemStackBlockHook hook) {
			Minecraft self = (Minecraft) (Object) this;

			return hook.getCloneItemStack(state, self.hitResult, level, pos, self.player);
		}

		return original.call(block, level, pos, state);
	}
}
