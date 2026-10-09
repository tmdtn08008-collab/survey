package net.geforcemods.securitycraft.fabricmixin.camera;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;

/**
 * Gives FrameFeedHandler the two frustums NeoForge's LevelRenderer#getFrustum chooses from (that method does not exist in
 * vanilla).
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("capturedFrustum")
	Frustum securitycraft$getCapturedFrustum();

	@Accessor("cullingFrustum")
	Frustum securitycraft$getCullingFrustum();
}
