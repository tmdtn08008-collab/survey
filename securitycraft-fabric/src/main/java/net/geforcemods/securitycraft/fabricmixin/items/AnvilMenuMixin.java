package net.geforcemods.securitycraft.fabricmixin.items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.item.BookEnchantableItemHook;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Calls {@link BookEnchantableItemHook#isBookEnchantable} like NeoForge's patch to AnvilMenu#createResult does with
 * IItemExtension#isBookEnchantable: when an enchanted book (any stack with stored enchantments) is in the right slot and the
 * item in the left slot does not accept it, the anvil has no result. NeoForge empties the result right before the cost is
 * calculated, so the cost is still set, and so is it here, because only the final result is replaced.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
	private AnvilMenuMixin(MenuType<?> menuType, int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(menuType, containerId, inventory, access);
	}

	/**
	 * The first ResultContainer#setItem after the cost is clamped is the one that sets the combined result.
	 */
	@WrapOperation(method = "createResult", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(JJJ)J")), at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultContainer;setItem(ILnet/minecraft/world/item/ItemStack;)V", ordinal = 0))
	private void securitycraft$checkBookEnchantable(ResultContainer resultContainer, int slot, ItemStack result, Operation<Void> original) {
		ItemStack left = inputSlots.getItem(0);
		ItemStack right = inputSlots.getItem(1);

		if (!result.isEmpty() && !right.isEmpty() && right.has(DataComponents.STORED_ENCHANTMENTS) && left.getItem() instanceof BookEnchantableItemHook hook && !hook.isBookEnchantable(left, right))
			result = ItemStack.EMPTY;

		original.call(resultContainer, slot, result);
	}
}
