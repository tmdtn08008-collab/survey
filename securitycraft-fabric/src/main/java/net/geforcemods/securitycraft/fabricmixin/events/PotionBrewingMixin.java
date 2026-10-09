package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.SCBrewingRecipes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Consults SecurityCraft's brewing recipes (fake water and fake lava buckets) where NeoForge consults its brewing recipe
 * registry. Ordinary brewing is unchanged, because these recipes only match water and lava buckets as input.
 */
@Mixin(PotionBrewing.class)
public abstract class PotionBrewingMixin {
	@ModifyReturnValue(method = "isIngredient", at = @At("RETURN"))
	private boolean securitycraft$isSCBrewingIngredient(boolean original, @Local(argsOnly = true) ItemStack stack) {
		return original || SCBrewingRecipes.isIngredient(stack);
	}

	@ModifyReturnValue(method = "hasMix", at = @At("RETURN"))
	private boolean securitycraft$hasSCBrewingMix(boolean original, @Local(argsOnly = true, ordinal = 0) ItemStack input, @Local(argsOnly = true, ordinal = 1) ItemStack ingredient) {
		return original || SCBrewingRecipes.hasOutput(input, ingredient);
	}

	/**
	 * Note that the parameters of PotionBrewing#mix are (ingredient, input).
	 */
	@ModifyReturnValue(method = "mix", at = @At("RETURN"))
	private ItemStack securitycraft$mixSCBrewingRecipe(ItemStack original, @Local(argsOnly = true, ordinal = 0) ItemStack ingredient, @Local(argsOnly = true, ordinal = 1) ItemStack input) {
		ItemStack output = SCBrewingRecipes.getOutput(input, ingredient);

		return output.isEmpty() ? original : output;
	}
}
