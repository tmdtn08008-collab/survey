package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.block.PortalFrameBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets fire placed next to SecurityCraft portal frame blocks (reinforced obsidian) try to light a nether portal, like
 * NeoForge's patched BaseFireBlock#isPortal, which asks IBlockExtension#isPortalFrame instead of checking for obsidian.
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
	@WrapOperation(method = "isPortal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
	private static boolean securitycraft$isPortalFrame(BlockState state, Block block, Operation<Boolean> original, @Local(argsOnly = true) Level level, @Local BlockPos.MutableBlockPos framePos) {
		return original.call(state, block) || state.getBlock() instanceof PortalFrameBlockHook hook && hook.isPortalFrame(state, level, framePos);
	}
}
