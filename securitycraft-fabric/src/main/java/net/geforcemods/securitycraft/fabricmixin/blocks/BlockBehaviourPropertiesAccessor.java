package net.geforcemods.securitycraft.fabricmixin.blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Reads the sound type that has been set on block properties. Used by OwnableFenceGateBlock, whose sound-based constructor
 * (a NeoForge addition to FenceGateBlock) has to hand vanilla's FenceGateBlock a WoodType carrying that sound type.
 */
@Mixin(BlockBehaviour.Properties.class)
public interface BlockBehaviourPropertiesAccessor {
	@Accessor("soundType")
	SoundType securitycraft$getSoundType();
}
