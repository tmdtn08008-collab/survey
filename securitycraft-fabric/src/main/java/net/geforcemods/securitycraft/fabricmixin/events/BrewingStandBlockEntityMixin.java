package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.SCBrewingRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * Lets hoppers put the inputs of SecurityCraft's brewing recipes (water and lava buckets) into the empty bottom slots of a
 * brewing stand, like NeoForge's version of this method, which uses PotionBrewing#isInput.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
	@ModifyReturnValue(method = "canPlaceItem", at = @At("RETURN"))
	private boolean securitycraft$allowSCBrewingInputs(boolean original, @Local(argsOnly = true) int slot, @Local(argsOnly = true) ItemStack stack) {
		if (!original && slot >= 0 && slot < 3 && SCBrewingRecipes.isInput(stack))
			return ((BrewingStandBlockEntity) (Object) this).getItem(slot).isEmpty();

		return original;
	}
}
