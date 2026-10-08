package net.geforcemods.securitycraft.mixin.fabric;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets SecurityCraft add blocks to vanilla point of interest types, like NeoForge's ExtendPoiTypesEvent.
 */
@Mixin(PoiTypes.class)
public interface PoiTypesAccessor {
	@Accessor("TYPE_BY_STATE")
	static Map<BlockState, Holder<PoiType>> securitycraft$getTypeByState() {
		throw new AssertionError();
	}
}
