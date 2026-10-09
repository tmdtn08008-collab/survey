package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.blocks.reinforced.ReinforcedLadderBlock;
import net.geforcemods.securitycraft.fabric.block.ClimbableBlockHook;
import net.geforcemods.securitycraft.fabric.block.SoundTypeBlockHook;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wires SecurityCraft's block hooks into LivingEntity at the places NeoForge patched:
 * <ul>
 * <li>LivingEntity#onClimbable asks {@link ClimbableBlockHook#isLadder} instead of checking the #minecraft:climbable tag
 * (only the owner can climb reinforced scaffolding, even though it is in that tag).</li>
 * <li>LivingEntity#trapdoorUsableAsLadder also accepts the reinforced ladder below an open trapdoor. NeoForge replaces this
 * check with TrapDoorBlock#isLadder, which by default accepts any LadderBlock below the trapdoor
 * (IBlockExtension#makesOpenTrapdoorAboveClimbable).</li>
 * <li>LivingEntity#handleOnClimbable asks {@link ClimbableBlockHook#isScaffolding} instead of checking for vanilla
 * scaffolding.</li>
 * <li>LivingEntity#playBlockFallSound uses the position-aware {@link SoundTypeBlockHook#getSoundType} (disguised blocks
 * sound like their disguise).</li>
 * </ul>
 * Vanilla blocks behave exactly like in vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@WrapOperation(method = "onClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean securitycraft$isLadder(BlockState state, TagKey<Block> tag, Operation<Boolean> original, @Local BlockPos pos) {
		if (state.getBlock() instanceof ClimbableBlockHook hook) {
			LivingEntity self = (LivingEntity) (Object) this;

			return hook.isLadder(state, self.level(), pos, self);
		}

		return original.call(state, tag);
	}

	@WrapOperation(method = "trapdoorUsableAsLadder", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean securitycraft$isReinforcedLadderBelowTrapdoor(BlockState stateBelow, Block block, Operation<Boolean> original) {
		return original.call(stateBelow, block) || block == Blocks.LADDER && stateBelow.getBlock() instanceof ReinforcedLadderBlock;
	}

	@WrapOperation(method = "handleOnClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
	private boolean securitycraft$isScaffolding(BlockState state, Block block, Operation<Boolean> original) {
		if (state.getBlock() instanceof ClimbableBlockHook hook) {
			LivingEntity self = (LivingEntity) (Object) this;

			return hook.isScaffolding(state, self.level(), self.blockPosition(), self);
		}

		return original.call(state, block);
	}

	@WrapOperation(method = "playBlockFallSound", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"))
	private SoundType securitycraft$getFallSoundType(BlockState state, Operation<SoundType> original) {
		if (state.getBlock() instanceof SoundTypeBlockHook hook) {
			LivingEntity self = (LivingEntity) (Object) this;
			//the same position vanilla gets the block state from
			BlockPos pos = new BlockPos(Mth.floor(self.getX()), Mth.floor(self.getY() - 0.2F), Mth.floor(self.getZ()));

			return hook.getSoundType(state, self.level(), pos, self);
		}

		return original.call(state);
	}
}
