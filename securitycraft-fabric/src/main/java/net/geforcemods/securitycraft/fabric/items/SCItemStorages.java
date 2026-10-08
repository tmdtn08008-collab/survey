package net.geforcemods.securitycraft.fabric.items;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.geforcemods.securitycraft.SCContent;
import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.blockentities.AbstractKeypadFurnaceBlockEntity;
import net.geforcemods.securitycraft.blockentities.BlockPocketManagerBlockEntity;
import net.geforcemods.securitycraft.blockentities.ClaymoreBlockEntity;
import net.geforcemods.securitycraft.blockentities.InventoryScannerBlockEntity;
import net.geforcemods.securitycraft.blockentities.KeypadBarrelBlockEntity;
import net.geforcemods.securitycraft.blockentities.KeypadChestBlockEntity;
import net.geforcemods.securitycraft.blockentities.LaserBlockBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedChiseledBookshelfBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedDispenserBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedHopperBlockEntity;
import net.geforcemods.securitycraft.blockentities.SecureTradingStationBlockEntity;
import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.geforcemods.securitycraft.blockentities.TrophySystemBlockEntity;
import net.geforcemods.securitycraft.fabric.registry.DeferredHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Exposes SecurityCraft's inventories to Fabric's Transfer API (ItemStorage.SIDED), replacing NeoForge's
 * RegisterCapabilitiesEvent registrations. The original NeoForge registrations were:
 *
 * <pre>
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.KEYPAD_BLAST_FURNACE_BLOCK_ENTITY.get(), AbstractKeypadFurnaceBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.KEYPAD_FURNACE_BLOCK_ENTITY.get(), AbstractKeypadFurnaceBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.KEYPAD_SMOKER_BLOCK_ENTITY.get(), AbstractKeypadFurnaceBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.BLOCK_POCKET_MANAGER_BLOCK_ENTITY.get(), BlockPocketManagerBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.CLAYMORE_BLOCK_ENTITY.get(), ClaymoreBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.INVENTORY_SCANNER_BLOCK_ENTITY.get(), InventoryScannerBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.KEYPAD_BARREL_BLOCK_ENTITY.get(), KeypadBarrelBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.KEYPAD_CHEST_BLOCK_ENTITY.get(), (chest, dir) -> KeypadChestBlockEntity.getCapability((KeypadChestBlockEntity) chest, dir));
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.LASER_BLOCK_BLOCK_ENTITY.get(), LaserBlockBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.REINFORCED_HOPPER_BLOCK_ENTITY.get(), ReinforcedHopperBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.TROPHY_SYSTEM_BLOCK_ENTITY.get(), TrophySystemBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.REINFORCED_CHISELED_BOOKSHELF_BLOCK_ENTITY.get(), ReinforcedChiseledBookshelfBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.REINFORCED_DISPENSER_BLOCK_ENTITY.get(), ReinforcedDispenserBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.REINFORCED_DROPPER_BLOCK_ENTITY.get(), ReinforcedDropperBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.SECURITY_CAMERA_BLOCK_ENTITY.get(), SecurityCameraBlockEntity::getCapability);
 * event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SCContent.SECURE_TRADING_STATION_BLOCK_ENTITY.get(), SecureTradingStationBlockEntity::getCapability);
 * event.registerEntity(Capabilities.ItemHandler.ENTITY, SCContent.SECURITY_SEA_BOAT_ENTITY.get(), (boat, ctx) -> SecuritySeaBoat.getCapability(boat, null));
 * event.registerEntity(Capabilities.ItemHandler.ENTITY_AUTOMATION, SCContent.SECURITY_SEA_BOAT_ENTITY.get(), SecuritySeaBoat::getCapability);
 * event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), SCContent.FAKE_WATER_BUCKET);
 * event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), SCContent.FAKE_LAVA_BUCKET);
 * </pre>
 *
 * How they map to Fabric:
 * <ul>
 * <li>Block entity capabilities: one ItemStorage.SIDED provider is registered for every SecurityCraft block that has a
 * block entity. It looks up the capability method for the block entity's type and translates the returned item handler
 * with {@link ItemHandlerStorage}, which never returns null. Owned block entities that are a Container but had no
 * capability on NeoForge (Projector, Block Change Detector, and anything added later) get {@link Storage#empty()}: without
 * that, Fabric's fallback would wrap the Container with full access. Vanilla hoppers, hopper minecarts, droppers and
 * crafters only reach these providers because SecurityCraft's HopperBlockEntityMixin hides owned containers from vanilla's
 * own Container lookup.</li>
 * <li>Security Sea Boat (ENTITY and ENTITY_AUTOMATION): Fabric's Transfer API has no entity lookup, and vanilla
 * hoppers/hopper minecarts/droppers/crafters use the boat's Container directly. HopperBlockEntityMixin wraps owned container
 * entities in a {@link ProtectedEntityContainer}, which applies SecuritySeaBoat#getCapability's rule (extraction only by an
 * allowed extraction block below the boat, insertion always).</li>
 * <li>Fake water and lava buckets (FluidHandler.ITEM): nothing to register. Fabric's FluidStorage.ITEM already supports
 * every BucketItem whose fluid's getBucket() returns that bucket item, which is the case for both fake buckets
 * (FakeLiquidBucketItem extends BucketItem, FakeWaterFluid/FakeLavaFluid#getBucket return them), and Fabric's empty bucket
 * storage fills buckets with fake water/lava through the same mapping.</li>
 * </ul>
 */
public final class SCItemStorages {
	private static final Map<BlockEntityType<?>, CapabilityProvider> PROVIDERS = new IdentityHashMap<>();

	private SCItemStorages() {}

	public static void register() {
		registerCapability(SCContent.KEYPAD_BLAST_FURNACE_BLOCK_ENTITY.get(), (be, side) -> AbstractKeypadFurnaceBlockEntity.getCapability((AbstractKeypadFurnaceBlockEntity) be, side));
		registerCapability(SCContent.KEYPAD_FURNACE_BLOCK_ENTITY.get(), (be, side) -> AbstractKeypadFurnaceBlockEntity.getCapability((AbstractKeypadFurnaceBlockEntity) be, side));
		registerCapability(SCContent.KEYPAD_SMOKER_BLOCK_ENTITY.get(), (be, side) -> AbstractKeypadFurnaceBlockEntity.getCapability((AbstractKeypadFurnaceBlockEntity) be, side));
		registerCapability(SCContent.BLOCK_POCKET_MANAGER_BLOCK_ENTITY.get(), (be, side) -> BlockPocketManagerBlockEntity.getCapability((BlockPocketManagerBlockEntity) be, side));
		registerCapability(SCContent.CLAYMORE_BLOCK_ENTITY.get(), (be, side) -> ClaymoreBlockEntity.getCapability((ClaymoreBlockEntity) be, side));
		registerCapability(SCContent.INVENTORY_SCANNER_BLOCK_ENTITY.get(), (be, side) -> InventoryScannerBlockEntity.getCapability((InventoryScannerBlockEntity) be, side));
		registerCapability(SCContent.KEYPAD_BARREL_BLOCK_ENTITY.get(), (be, side) -> KeypadBarrelBlockEntity.getCapability((KeypadBarrelBlockEntity) be, side));
		registerCapability(SCContent.KEYPAD_CHEST_BLOCK_ENTITY.get(), (be, side) -> KeypadChestBlockEntity.getCapability((KeypadChestBlockEntity) be, side));
		registerCapability(SCContent.LASER_BLOCK_BLOCK_ENTITY.get(), (be, side) -> LaserBlockBlockEntity.getCapability((LaserBlockBlockEntity) be, side));
		registerCapability(SCContent.REINFORCED_HOPPER_BLOCK_ENTITY.get(), (be, side) -> ReinforcedHopperBlockEntity.getCapability((ReinforcedHopperBlockEntity) be, side));
		registerCapability(SCContent.TROPHY_SYSTEM_BLOCK_ENTITY.get(), (be, side) -> TrophySystemBlockEntity.getCapability((TrophySystemBlockEntity) be, side));
		registerCapability(SCContent.REINFORCED_CHISELED_BOOKSHELF_BLOCK_ENTITY.get(), (be, side) -> ReinforcedChiseledBookshelfBlockEntity.getCapability((ReinforcedChiseledBookshelfBlockEntity) be, side));
		registerCapability(SCContent.REINFORCED_DISPENSER_BLOCK_ENTITY.get(), (be, side) -> ReinforcedDispenserBlockEntity.getCapability((ReinforcedDispenserBlockEntity) be, side));
		registerCapability(SCContent.REINFORCED_DROPPER_BLOCK_ENTITY.get(), (be, side) -> ReinforcedDispenserBlockEntity.getCapability((ReinforcedDispenserBlockEntity) be, side));
		registerCapability(SCContent.SECURITY_CAMERA_BLOCK_ENTITY.get(), (be, side) -> SecurityCameraBlockEntity.getCapability((SecurityCameraBlockEntity) be, side));
		registerCapability(SCContent.SECURE_TRADING_STATION_BLOCK_ENTITY.get(), (be, side) -> SecureTradingStationBlockEntity.getCapability((SecureTradingStationBlockEntity) be, side));
		//NeoForge registered no capability for these owned Containers. Vanilla hoppers can no longer see them (see HopperBlockEntityMixin), so they must not be reachable through Fabric's Container fallback either
		registerCapability(SCContent.PROJECTOR_BLOCK_ENTITY.get(), (be, side) -> EmptyItemHandler.INSTANCE);
		registerCapability(SCContent.BLOCK_CHANGE_DETECTOR_BLOCK_ENTITY.get(), (be, side) -> EmptyItemHandler.INSTANCE);

		List<Block> blocks = new ArrayList<>();

		for (DeferredHolder<Block, ? extends Block> holder : SCContent.BLOCKS.getEntries()) {
			if (holder.get() instanceof EntityBlock)
				blocks.add(holder.get());
		}

		ItemStorage.SIDED.registerForBlocks(SCItemStorages::find, blocks.toArray(Block[]::new));
	}

	private static void registerCapability(BlockEntityType<?> type, CapabilityProvider provider) {
		PROVIDERS.put(type, provider);
	}

	@Nullable
	private static Storage<ItemVariant> find(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
		if (be == null)
			return null;

		CapabilityProvider provider = PROVIDERS.get(be.getType());

		if (provider != null)
			return ItemHandlerStorage.of(provider.getCapability(be, side), be::setChanged);
		else if (be instanceof IOwnable && be instanceof Container)
			return Storage.empty(); //never let Fabric's fallback expose an owned container

		return null;
	}

	@FunctionalInterface
	private interface CapabilityProvider {
		@Nullable
		IItemHandler getCapability(BlockEntity be, @Nullable Direction side);
	}
}
