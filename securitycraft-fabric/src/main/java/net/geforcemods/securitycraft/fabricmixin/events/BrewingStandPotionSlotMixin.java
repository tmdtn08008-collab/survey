package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.SCBrewingRecipes;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the inputs of SecurityCraft's brewing recipes (water and lava buckets) be put into the bottom slots of the brewing
 * stand menu, like NeoForge's PotionBrewing#isInput. This is also used when shift-clicking items into the menu.
 */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class BrewingStandPotionSlotMixin {
	@ModifyReturnValue(method = "mayPlaceItem(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
	private static boolean securitycraft$allowSCBrewingInputs(boolean original, @Local(argsOnly = true) ItemStack stack) {
		return original || SCBrewingRecipes.isInput(stack);
	}
}
