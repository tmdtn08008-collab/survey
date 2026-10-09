package net.geforcemods.securitycraft.fabricmixin.items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.item.BoatFluidHook;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * Lets boats implementing {@link BoatFluidHook} (the Security Sea Boat) decide which fluids they float on, at the places
 * where NeoForge replaced Boat's water checks with IBoatExtension#canBoatInFluid. Other boats keep vanilla's water check.
 */
@Mixin(Boat.class)
public abstract class BoatMixin {
	/**
	 * The fluid checks in getWaterLevelAbove, checkInWater, isUnderwater and checkFallDamage, which are the only calls to
	 * FluidState#is(TagKey) in these methods.
	 */
	@WrapOperation(method = {
			"getWaterLevelAbove()F",
			"checkInWater()Z",
			"isUnderwater()Lnet/minecraft/world/entity/vehicle/Boat$Status;",
			"checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"
	}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"), require = 4, allow = 4)
	private boolean securitycraft$canBoatInFluid(FluidState state, TagKey<Fluid> tag, Operation<Boolean> original) {
		if ((Object) this instanceof BoatFluidHook hook && tag == FluidTags.WATER)
			return hook.canBoatInFluid(state);

		return original.call(state, tag);
	}

	/**
	 * NeoForge replaces the check whether the boat's eyes are in water with canBoatInFluid(getEyeInFluidType()). Fluid types
	 * are represented by the water and lava fluid tags on Fabric, see {@link BoatFluidHook#canBoatInFluidType(TagKey)}.
	 */
	@WrapOperation(method = "canAddPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/Boat;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean securitycraft$canBoatInEyeFluid(Boat boat, TagKey<Fluid> tag, Operation<Boolean> original) {
		if (boat instanceof BoatFluidHook hook && tag == FluidTags.WATER) {
			if (original.call(boat, FluidTags.WATER) && hook.canBoatInFluidType(FluidTags.WATER))
				return true;

			return original.call(boat, FluidTags.LAVA) && hook.canBoatInFluidType(FluidTags.LAVA);
		}

		return original.call(boat, tag);
	}
}
