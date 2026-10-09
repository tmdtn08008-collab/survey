package net.geforcemods.securitycraft.fabricmixin.camera;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.geforcemods.securitycraft.entity.camera.FrameFeedHandler;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * Renders the local player while the level is viewed through a mounted Security Camera or captured for a frame feed, so that
 * the player can see themselves in the camera view. NeoForge's LevelRenderer patch does this for every view whose camera
 * entity is not the player; vanilla never draws the local player unless it is the camera entity. On Fabric this is limited to
 * SecurityCraft's camera and frame views, so vanilla behavior is unchanged everywhere else.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	/**
	 * In the entity loop of renderLevel, vanilla skips an entity when
	 * {@code entity instanceof LocalPlayer && camera.getEntity() != entity}. In SecurityCraft camera views, the instanceof check
	 * (the only one for LocalPlayer in renderLevel) is made to fail for the local player when it is not spectating, which has
	 * the same effect as NeoForge's added {@code || (entity == minecraft.player && !minecraft.player.isSpectator())}.
	 */
	@WrapOperation(method = "renderLevel", constant = @Constant(classValue = LocalPlayer.class))
	private boolean securitycraft$renderLocalPlayerInCameraViews(Object entity, Operation<Boolean> original) {
		boolean isLocalPlayer = original.call(entity);

		if (isLocalPlayer && entity == minecraft.player && !minecraft.player.isSpectator() && (minecraft.cameraEntity instanceof SecurityCamera || FrameFeedHandler.isCapturingCamera()))
			return false;

		return isLocalPlayer;
	}
}
