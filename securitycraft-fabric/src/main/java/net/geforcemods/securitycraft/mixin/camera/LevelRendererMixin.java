package net.geforcemods.securitycraft.mixin.camera;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.geforcemods.securitycraft.compat.ium.IumCompat;
import net.geforcemods.securitycraft.entity.camera.CameraViewAreaExtension;
import net.geforcemods.securitycraft.entity.camera.FrameFeedHandler;
import net.geforcemods.securitycraft.util.PlayerUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

@Mixin(value = LevelRenderer.class, priority = 1100)
public class LevelRendererMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private SectionRenderDispatcher sectionRenderDispatcher;
	@Shadow
	private ClientLevel level;

	/**
	 * Fixes camera chunks disappearing when the player entity moves while mounted to a camera (e.g. while being in a minecart or
	 * falling)
	 */
	// require = 0: Sodium @Overwrites LevelRenderer#setupRender, and its version has no ViewArea#repositionCamera call. This is the
	// same with Sodium on NeoForge; Sodium manages its own render sections there.
	@WrapWithCondition(method = "setupRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ViewArea;repositionCamera(DD)V"), require = 0)
	private boolean securitycraft$shouldRepositionCamera(ViewArea viewArea, double x, double z) {
		return !PlayerUtils.isPlayerMountedOnCamera(minecraft.player);
	}

	/**
	 * When rendering the world in a frame, the necessary visible sections are captured manually within SecurityCraft. Vanilla
	 * usually does the same process in setupRender, so that method is exited early when a frame feed is rendered. However, when
	 * Embeddium or Sodium is installed, these mods may perform their visible section capture themselves since it's much more
	 * performant, and since that happens in setupRender too, the method is not exited early in this case.
	 * On Fabric, only Sodium can be installed (Embeddium is NeoForge-only). This HEAD injection also applies to Sodium's
	 * overwritten setupRender.
	 */
	@Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
	private void securitycraft$onSetupRender(Camera camera, Frustum frustum, boolean hasCapturedFrustum, boolean isSpectator, CallbackInfo ci) {
		if (FrameFeedHandler.isCapturingCamera() && !IumCompat.isActive())
			ci.cancel();
	}

	/**
	 * Updates the camera view area with the refreshed section render dispatcher when F3+A is pressed
	 */
	@Inject(method = "allChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;waitAndReset(Lnet/minecraft/client/renderer/ViewArea;)V"))
	private void securitycraft$onAllChanged(CallbackInfo ci) {
		CameraViewAreaExtension.allChanged(sectionRenderDispatcher, level);
	}

	/**
	 * If rendering a frame camera, makes sure that all compiled sections within the camera view area extension are properly
	 * treated as compiled (e.g. for the purpose of entity rendering)
	 */
	@Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true)
	private void securitycraft$onIsSectionCompiled(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (FrameFeedHandler.isCapturingCamera()) {
			SectionPos sectionPos = SectionPos.of(pos);
			SectionRenderDispatcher.RenderSection renderSection = CameraViewAreaExtension.rawFetch(sectionPos.x(), sectionPos.y(), sectionPos.z(), false);

			if (renderSection != null && renderSection.compiled.get() != SectionRenderDispatcher.CompiledSection.UNCOMPILED)
				cir.setReturnValue(true);
		}
	}

	/**
	 * Sets the correct fog distance for rendering a frame feed, depending on clientside view distance configuration settings.
	 * Note that the frame block entity chunk loading distance option is not respected for this, since it is only supposed to
	 * affect the server by setting a limit on forceloaded chunks and unfit to be handled on the client side.
	 */
	// PORT-NOTE: Upstream uses @ModifyVariable(ordinal = 1) on a float local at the FogRenderer#setupColor call. At that call the
	// only float local is the partial tick; the render distance local is only stored afterwards from GameRenderer#getRenderDistance,
	// so the upstream injection matched nothing and silently did nothing (also on NeoForge, where the code has the same layout).
	// With "defaultRequire": 1 it would crash the game, so it now modifies the value of the getRenderDistance call itself (the only
	// one in renderLevel), which is the value the fog distance is computed from. Frame feeds therefore now get the documented fog
	// distance, where upstream used the player's render distance.
	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getRenderDistance()F"))
	private float securitycraft$modifyFogRenderDistance(float original) {
		if (FrameFeedHandler.isCapturingCamera())
			return FrameFeedHandler.getFrameFeedViewDistance(null) * 16;

		return original;
	}
}
