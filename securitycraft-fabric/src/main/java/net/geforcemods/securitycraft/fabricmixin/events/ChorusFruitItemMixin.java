package net.geforcemods.securitycraft.fabricmixin.events;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.event.EventHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ChorusFruitItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Fires SecurityCraft's EntityTeleportEvent.ChorusFruit stand-in before an entity that ate a chorus fruit teleports, like
 * NeoForge. Like on NeoForge, a canceled event immediately returns the eaten stack, without trying other positions and
 * without the cooldown.
 */
@Mixin(ChorusFruitItem.class)
public abstract class ChorusFruitItemMixin {
	//PORT-NOTE: NeoForge also teleports to a target changed by the event. SecurityCraft's handler only cancels, so the target is not replaced here
	@Inject(method = "finishUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;randomTeleport(DDDZ)Z"), cancellable = true)
	private void securitycraft$fireChorusFruitTeleport(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir, @Local(ordinal = 0) double x, @Local(ordinal = 1) double y, @Local(ordinal = 2) double z, @Local(ordinal = 1) ItemStack result) {
		if (EventHooks.onChorusFruitTeleport(entity, x, y, z).isCanceled())
			cir.setReturnValue(result);
	}
}
