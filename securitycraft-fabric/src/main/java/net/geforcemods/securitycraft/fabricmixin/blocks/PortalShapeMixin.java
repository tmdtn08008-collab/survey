package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.block.PortalFrameBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;

/**
 * Lets SecurityCraft blocks (reinforced obsidian) be part of a nether portal frame, like NeoForge's patched PortalShape,
 * whose FRAME predicate asks IBlockExtension#isPortalFrame. Instead of replacing the static predicate, every test of it is
 * extended, which also keeps working if another mod changes the predicate.
 */
@Mixin(PortalShape.class)
public abstract class PortalShapeMixin {
	@WrapOperation(method = {
			"getDistanceUntilEdgeAboveFrame", "hasTopFrame", "getDistanceUntilTop"
	}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockBehaviour$StatePredicate;test(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
	private boolean securitycraft$isPortalFrame(BlockBehaviour.StatePredicate predicate, BlockState state, BlockGetter level, BlockPos pos, Operation<Boolean> original) {
		return original.call(predicate, state, level, pos) || state.getBlock() instanceof PortalFrameBlockHook hook && hook.isPortalFrame(state, level, pos);
	}
}
