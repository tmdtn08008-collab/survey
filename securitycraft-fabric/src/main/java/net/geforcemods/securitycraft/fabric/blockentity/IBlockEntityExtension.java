package net.geforcemods.securitycraft.fabric.blockentity;

import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.geforcemods.securitycraft.fabric.model.ModelData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Fabric stand-in for the parts of NeoForge's IBlockEntityExtension that SecurityCraft's block entities override. Only
 * SecurityCraft's own block entities implement this (it is deliberately not injected into every vanilla block entity), and
 * the hooks are called from SecurityCraft's own mixins at the places NeoForge patched:
 * <ul>
 * <li>{@link #onLoad()}: called at the start of the next block entity tick of the level after the block entity was added
 * to a loaded chunk or its chunk was loaded (LevelChunk#addAndRegisterBlockEntity and
 * LevelChunk#registerAllBlockEntitiesAfterLevelLoad queue it, Level#tickBlockEntities drains the queue), on both
 * sides.</li>
 * <li>{@link #onDataPacket}: called on the client by ClientPacketListener#handleBlockEntityData instead of the vanilla
 * loadWithComponents call.</li>
 * <li>{@link #getModelData()}: passed to Fabric's renderer as this block entity's render data, so models can read it
 * through {@code blockView.getBlockEntityRenderData(pos)}. Every class that overrides getModelData() must also declare
 * {@code getRenderData()} itself and return getModelData() from it: Fabric API's rendering-data-attachment module adds a
 * concrete getRenderData() to BlockEntity, and a method inherited from a superclass always wins over an interface
 * default, so a default method here would never be called.</li>
 * </ul>
 */
public interface IBlockEntityExtension extends RenderDataBlockEntity {
	/**
	 * Called on the client when a block entity data packet for this block entity arrives. By default, this loads the data
	 * the same way vanilla does.
	 */
	default void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider lookupProvider) {
		CompoundTag tag = packet.getTag();

		if (!tag.isEmpty())
			((BlockEntity) this).loadWithComponents(tag, lookupProvider);
	}

	/**
	 * Called once this block entity has been fully added to the level, right before its first tick. NeoForge's default
	 * calls requestModelDataUpdate(), which has nothing to do on Fabric (see {@link #requestModelDataUpdate()}).
	 */
	default void onLoad() {}

	/**
	 * On NeoForge, this schedules a refresh of the cached model data. Fabric's renderer reads {@link #getRenderData()}
	 * whenever the chunk section is rebuilt, so there is nothing to refresh: rebuild the section instead when the model data
	 * changes.
	 */
	default void requestModelDataUpdate() {}

	/**
	 * @return Additional data for this block entity's model
	 */
	default ModelData getModelData() {
		return ModelData.EMPTY;
	}

}
