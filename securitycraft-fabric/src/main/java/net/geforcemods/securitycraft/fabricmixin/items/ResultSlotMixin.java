package net.geforcemods.securitycraft.fabricmixin.items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.util.CommonHooks;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Makes the player taking a crafting result available through {@link CommonHooks#getCraftingPlayer()} while the remaining
 * items of the recipe are computed, at the same place NeoForge sets its crafting player. SecurityCraft's reinforcer recipes
 * use it to damage the Universal Block Reinforcer in the crafting grid with the right player and level.
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@WrapOperation(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRemainingItemsFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
	private NonNullList<ItemStack> securitycraft$setCraftingPlayer(RecipeManager recipeManager, RecipeType<?> recipeType, RecipeInput input, Level level, Operation<NonNullList<ItemStack>> original, @Local(argsOnly = true) Player player) {
		Player previous = CommonHooks.getCraftingPlayer();

		CommonHooks.setCraftingPlayer(player);

		try {
			return original.call(recipeManager, recipeType, input, level);
		}
		finally {
			CommonHooks.setCraftingPlayer(previous);
		}
	}
}
