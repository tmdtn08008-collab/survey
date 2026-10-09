package net.geforcemods.securitycraft.fabricmixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.entity.camera.CameraClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;

/**
 * Fires SecurityCraft's camera hooks where NeoForge's Minecraft patch fires InputEvent.InteractionKeyMappingTriggered (for the
 * use and pick block keys; the attack key goes through Fabric's ClientPreAttackCallback, see CameraClientEvents#register) and
 * the client-side LevelEvent.Unload.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Shadow
	public ClientLevel level;

	/**
	 * NeoForge fires the use key event at the start of each hand's iteration in startUseItem, after rightClickDelay has been
	 * set, and returns from the method when it is canceled. The first getItemInHand call is the first statement of that loop.
	 */
	@Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
	private void securitycraft$onUseInput(CallbackInfo ci, @Local InteractionHand hand) {
		if (CameraClientEvents.onUseInput(hand))
			ci.cancel();
	}

	/**
	 * NeoForge fires the pick block key event at the start of pickBlock (inside a check that does nothing when it fails), and
	 * returns from the method when it is canceled.
	 */
	@Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
	private void securitycraft$onPickBlockInput(CallbackInfo ci) {
		if (CameraClientEvents.onPickBlockInput())
			ci.cancel();
	}

	/**
	 * LevelEvent.Unload in setLevel: posted first thing, for the level that is being replaced
	 */
	@Inject(method = "setLevel", at = @At("HEAD"))
	private void securitycraft$onSetLevel(ClientLevel newLevel, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
		if (level != null)
			CameraClientEvents.onLevelUnload();
	}

	/**
	 * LevelEvent.Unload in disconnect: posted right after the forced tick, if there is a level
	 */
	@Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V", shift = At.Shift.AFTER))
	private void securitycraft$onDisconnect(Screen screen, boolean keepResourcePacks, CallbackInfo ci) {
		if (level != null)
			CameraClientEvents.onLevelUnload();
	}

	/**
	 * LevelEvent.Unload in clearClientLevel: posted right before the forced tick, if there is a level
	 */
	@Inject(method = "clearClientLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateScreenAndTick(Lnet/minecraft/client/gui/screens/Screen;)V"))
	private void securitycraft$onClearClientLevel(Screen screen, CallbackInfo ci) {
		if (level != null)
			CameraClientEvents.onLevelUnload();
	}
}
