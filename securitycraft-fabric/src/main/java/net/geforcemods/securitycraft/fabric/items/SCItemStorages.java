package net.geforcemods.securitycraft.fabric.items;

/**
 * Exposes SecurityCraft's inventories to Fabric's Transfer API (ItemStorage.SIDED), replacing NeoForge's
 * RegisterCapabilitiesEvent registrations.
 *
 * PORT-TODO (inventory unit): implement. The original NeoForge registrations were:
 *
	 * @SubscribeEvent
	 * public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
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
	 * }
 */
public final class SCItemStorages {
	private SCItemStorages() {}

	public static void register() {
		throw new UnsupportedOperationException("PORT-TODO: SCItemStorages.register() is not implemented yet");
	}
}
