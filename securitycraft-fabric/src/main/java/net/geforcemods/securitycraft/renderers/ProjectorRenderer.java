package net.geforcemods.securitycraft.renderers;

import com.mojang.blaze3d.vertex.PoseStack;

import net.geforcemods.securitycraft.ClientHandler;
import net.geforcemods.securitycraft.blockentities.ProjectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ProjectorRenderer implements BlockEntityRenderer<ProjectorBlockEntity> {
	public ProjectorRenderer(BlockEntityRendererProvider.Context ctx) {}

	@Override
	public void render(ProjectorBlockEntity be, float partialTicks, PoseStack pose, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
		ClientHandler.DISGUISED_BLOCK_RENDER_DELEGATE.tryRenderDelegate(be, partialTicks, pose, buffer, combinedLight, combinedOverlay);

		if (be.isActive() && !be.isEmpty()) {
			Level level = be.getLevel();
			BlockPos projectorPos = be.getBlockPos();
			BlockState state = be.getProjectedState();
			AABB projectedBlocksArea = be.getProjectedBlocksArea().deflate(0.1D);
			BlockPos minPos = BlockPos.containing(projectedBlocksArea.getMinPosition());
			BlockPos maxPos = BlockPos.containing(projectedBlocksArea.getMaxPosition());
			BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

			ModelBlockRenderer.enableCaching();

			for (BlockPos relativePos : BlockPos.betweenClosed(minPos, maxPos)) {
				BlockPos projectionPos = projectorPos.offset(relativePos);

				pose.pushPose();
				pose.translate(relativePos.getX(), relativePos.getY(), relativePos.getZ());

				if (be.isOverridingBlocks() || level.isEmptyBlock(projectionPos)) {
					//PORT-NOTE: NeoForge renders the model once per render type of the model (by default the block's render layer).
					//Fabric has no per-model render types, so the block is rendered in its block render layer, like vanilla does
					RenderType renderType = ItemBlockRenderTypes.getChunkRenderType(state);

					dispatcher.renderBatched(state, projectionPos, be.getLevel(), pose, buffer.getBuffer(renderType), true, be.getLevel().random);

					ClientHandler.PROJECTOR_RENDER_DELEGATE.tryRenderDelegate(be, partialTicks, pose, buffer, combinedLight, combinedOverlay);
				}

				pose.popPose();
			}

			ModelBlockRenderer.clearCache();
		}
	}

	@Override
	public boolean shouldRenderOffScreen(ProjectorBlockEntity be) {
		return true;
	}

	@Override
	public boolean shouldRender(ProjectorBlockEntity be, Vec3 cameraPosition) {
		return true; //Projected blocks should always render, regardless of distance
	}

	/**
	 * PORT-NOTE: Overrode NeoForge's IBlockEntityRendererExtension#getRenderBoundingBox, which NeoForge uses for frustum culling of
	 * block entity renderers. Vanilla and Fabric do not cull block entity renderers by a bounding box, and this renderer renders
	 * off screen anyway ({@link #shouldRenderOffScreen}), so nothing calls this on Fabric and nothing gets culled.
	 */
	public AABB getRenderBoundingBox(ProjectorBlockEntity be) {
		AABB projectorBoundingBox = new AABB(be.getBlockPos());

		return be.isEmpty() ? projectorBoundingBox.inflate(1.0D) : be.getProjectedBlocksArea().move(be.getBlockPos()).minmax(projectorBoundingBox).inflate(1.0D);
	}
}
