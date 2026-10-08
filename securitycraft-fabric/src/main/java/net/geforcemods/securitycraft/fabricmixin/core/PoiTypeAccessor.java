package net.geforcemods.securitycraft.fabricmixin.core;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(PoiType.class)
public interface PoiTypeAccessor {
	@Mutable
	@Accessor("matchingStates")
	void securitycraft$setMatchingStates(Set<BlockState> matchingStates);
}
