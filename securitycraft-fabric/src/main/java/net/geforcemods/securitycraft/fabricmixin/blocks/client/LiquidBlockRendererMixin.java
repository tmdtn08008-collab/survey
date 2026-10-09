package net.geforcemods.securitycraft.fabricmixin.blocks.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.geforcemods.securitycraft.fabric.block.FluidOverlayBlockHook;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Replacement for NeoForge's IBlockExtension#shouldDisplayFluidOverlay, which NeoForge asks in LiquidBlockRenderer#tesselate
 * to decide whether water next to a block uses the overlay texture (like next to glass). Fabric API already replaces
 * vanilla's HalfTransparentBlock/LeavesBlock check there with FluidRenderHandlerRegistry#isBlockTransparent, so every block
 * implementing {@link FluidOverlayBlockHook} that wants the overlay is registered with Fabric's registry whenever the fluid
 * sprites are set up (on every resource reload, after all blocks have been registered). This also works with renderers
 * like Sodium that use Fabric's registry. Fabric's registry is per block, so the hook is asked for each block's default
 * state without a level and position, which matches how all SecurityCraft implementations behave.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {
	@Inject(method = "setupSprites", at = @At("TAIL"))
	private void securitycraft$registerFluidOverlayBlocks(CallbackInfo ci) {
		FluidState water = Fluids.WATER.defaultFluidState();

		for (Block block : BuiltInRegistries.BLOCK) {
			if (block instanceof FluidOverlayBlockHook hook && hook.shouldDisplayFluidOverlay(block.defaultBlockState(), null, null, water))
				FluidRenderHandlerRegistry.INSTANCE.setBlockTransparency(block, true);
		}
	}
}
