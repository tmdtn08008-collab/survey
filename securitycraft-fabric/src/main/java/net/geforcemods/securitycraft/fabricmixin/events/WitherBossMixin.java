package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fires SecurityCraft's LivingDestroyBlockEvent stand-in where the wither destroys the blocks around itself, like NeoForge.
 * A canceled event keeps the block.
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
	@WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;canDestroy(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean securitycraft$fireLivingDestroyBlock(BlockState state, Operation<Boolean> original, @Local BlockPos pos) {
		return original.call(state) && EventHooks.onEntityDestroyBlock((WitherBoss) (Object) this, pos, state);
	}
}
