package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.geforcemods.securitycraft.fabric.event.UseItemOnBlockEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Fires SecurityCraft's UseItemOnBlockEvent (phase ITEM_AFTER_BLOCK) and BlockEvent.EntityPlaceEvent stand-ins from
 * ItemStack#useOn, where NeoForge fires them.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	//PORT-NOTE: NeoForge also fires UseItemOnBlockEvent with the phases ITEM_BEFORE_BLOCK (in ItemStack#onItemUseFirst) and BLOCK. SecurityCraft only listens to ITEM_AFTER_BLOCK, so the other phases are not fired
	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void securitycraft$fireUseItemOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		UseItemOnBlockEvent event = EventHooks.onUseItemOnBlock(context);

		if (event.isCanceled())
			cir.setReturnValue(event.getCancellationResult().result());
	}

	/**
	 * NeoForge records all block changes while the item is used on the server and fires EntityPlaceEvent for them.
	 */
	@WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult securitycraft$recordPlacedBlocks(Item item, UseOnContext context, Operation<InteractionResult> original) {
		return EventHooks.useOnRecordingPlacements(item, context, () -> original.call(item, context));
	}
}
