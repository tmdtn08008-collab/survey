package net.geforcemods.securitycraft.fabric.item;

import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Replacement for NeoForge's IBoatExtension#canBoatInFluid. Boats implementing this decide which fluids they float on.
 * Called from SecurityCraft's Boat mixin at the places NeoForge patched: the fluid checks in Boat#getWaterLevelAbove,
 * Boat#checkInWater, Boat#isUnderwater and Boat#checkFallDamage use {@link #canBoatInFluid(FluidState)}, and
 * Boat#canAddPassenger uses {@link #canBoatInFluidType(TagKey)} with the fluid the boat's eyes are in.
 */
public interface BoatFluidHook {
	/**
	 * @param state The fluid state to check
	 * @return true if the boat can be used on the given fluid. Like NeoForge's default (FluidType#supportsBoating, which is
	 *         only true for the water fluid type), this is true for all fluids in the water tag.
	 */
	default boolean canBoatInFluid(FluidState state) {
		return state.is(FluidTags.WATER);
	}

	/**
	 * Fabric has no fluid types, so NeoForge's canBoatInFluid(FluidType) is represented by the vanilla fluid tag grouping the
	 * fluids of that type: {@link FluidTags#WATER} for NeoForge's water type and {@link FluidTags#LAVA} for its lava type.
	 *
	 * @param fluidType The tag representing the fluid type to check
	 * @return true if the boat can be used on fluids of the given type. Like NeoForge's default, this is only true for water.
	 */
	default boolean canBoatInFluidType(TagKey<Fluid> fluidType) {
		return fluidType == FluidTags.WATER;
	}
}
