package net.geforcemods.securitycraft.fabricmixin.items.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.geforcemods.securitycraft.fabric.item.ReequipAnimationItemHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;

/**
 * Calls {@link ReequipAnimationItemHook#shouldCauseReequipAnimation} where NeoForge calls IItemExtension#shouldCauseReequipAnimation
 * in ItemInHandRenderer#tick: in the branch for hands that are not busy, for the previously held item, when neither the
 * previously held nor the newly held stack is empty. The selected hotbar slot is tracked like NeoForge does, so the hook knows
 * whether the slot changed. Items without the hook behave like in vanilla, which is what NeoForge's default implementation
 * does as well.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererReequipMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private ItemStack mainHandItem;
	@Shadow
	private ItemStack offHandItem;
	@Unique
	private int securitycraft$slotMainHand = 0;

	@Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isHandsBusy()Z"))
	private void securitycraft$applyReequipAnimationHook(CallbackInfo ci) {
		LocalPlayer player = minecraft.player;

		if (player.isHandsBusy())
			return;

		mainHandItem = securitycraft$applyReequipAnimationHook(mainHandItem, player.getMainHandItem(), player.getInventory().selected);
		offHandItem = securitycraft$applyReequipAnimationHook(offHandItem, player.getOffhandItem(), -1);
	}

	/**
	 * Vanilla plays the reequip animation (lowering the hand) whenever the previously held stack is not the same object as the
	 * newly held one. This returns the stack to use as the previously held stack so vanilla's check gives the hook's result.
	 */
	@Unique
	private ItemStack securitycraft$applyReequipAnimationHook(ItemStack from, ItemStack to, int slot) {
		if (from.isEmpty() || to.isEmpty())
			return from;

		boolean slotChanged = false;

		if (slot != -1) {
			slotChanged = slot != securitycraft$slotMainHand;
			securitycraft$slotMainHand = slot;
		}

		if (from.getItem() instanceof ReequipAnimationItemHook hook) {
			if (!hook.shouldCauseReequipAnimation(from, to, slotChanged))
				return to;
			else if (from == to) //vanilla's check at the start of tick already made them the same object, which would skip the animation that the hook asks for
				return to.copy();
		}

		return from;
	}
}
