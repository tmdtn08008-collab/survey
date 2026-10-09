package net.geforcemods.securitycraft.fabricmixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.geforcemods.securitycraft.fabric.client.DisguiseParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the texture of the disguise instead of the real block's while the player's head is inside a disguised block, like
 * NeoForge's ScreenEffectRenderer patch, which looks up the overlay texture with the model data at the overlay block's
 * position (BlockModelShaper#getTexture(BlockState, Level, BlockPos)). Vanilla only passes the block state on, so the position
 * the state was found at is remembered here.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	/**
	 * The position of the block that getViewBlockingState returned last. Only accessed on the render thread.
	 */
	@Unique
	private static BlockPos securitycraft$viewBlockingPos;

	/**
	 * The first return is the one inside the loop that returns the view blocking state; the second one returns null
	 */
	@Inject(method = "getViewBlockingState", at = @At(value = "RETURN", ordinal = 0))
	private static void securitycraft$rememberViewBlockingPos(Player player, CallbackInfoReturnable<BlockState> cir, @Local BlockPos.MutableBlockPos pos) {
		securitycraft$viewBlockingPos = pos.immutable();
	}

	@WrapOperation(method = "renderScreenEffect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockModelShaper;getParticleIcon(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
	private static TextureAtlasSprite securitycraft$useDisguisedOverlayTexture(BlockModelShaper modelShaper, BlockState state, Operation<TextureAtlasSprite> original, @Local(argsOnly = true) Minecraft mc) {
		BlockPos pos = securitycraft$viewBlockingPos;

		securitycraft$viewBlockingPos = null;
		return DisguiseParticles.getTexture(modelShaper, state, mc.level, pos, original.call(modelShaper, state));
	}
}
