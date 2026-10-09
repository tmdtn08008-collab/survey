package net.geforcemods.securitycraft;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import net.geforcemods.securitycraft.blockentities.AbstractKeypadFurnaceBlockEntity;
import net.geforcemods.securitycraft.blockentities.BlockPocketManagerBlockEntity;
import net.geforcemods.securitycraft.blockentities.ClaymoreBlockEntity;
import net.geforcemods.securitycraft.blockentities.InventoryScannerBlockEntity;
import net.geforcemods.securitycraft.blockentities.KeypadBarrelBlockEntity;
import net.geforcemods.securitycraft.blockentities.KeypadChestBlockEntity;
import net.geforcemods.securitycraft.blockentities.LaserBlockBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedChiseledBookshelfBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedDispenserBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedDropperBlockEntity;
import net.geforcemods.securitycraft.blockentities.ReinforcedHopperBlockEntity;
import net.geforcemods.securitycraft.blockentities.SecureTradingStationBlockEntity;
import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.geforcemods.securitycraft.blockentities.TrophySystemBlockEntity;
import net.geforcemods.securitycraft.entity.SecuritySeaBoat;
import net.geforcemods.securitycraft.misc.SCSounds;
import net.geforcemods.securitycraft.network.client.InteractWithFrame;
import net.geforcemods.securitycraft.network.client.OpenScreen;
import net.geforcemods.securitycraft.network.client.RefreshDisguisableModel;
import net.geforcemods.securitycraft.network.client.SetCameraView;
import net.geforcemods.securitycraft.network.client.SetTrophySystemTarget;
import net.geforcemods.securitycraft.network.client.ToggleAlarmSound;
import net.geforcemods.securitycraft.network.client.UpdateLaserColors;
import net.geforcemods.securitycraft.network.client.UpdateLogger;
import net.geforcemods.securitycraft.network.server.CheckBriefcasePasscode;
import net.geforcemods.securitycraft.network.server.CheckPasscode;
import net.geforcemods.securitycraft.network.server.ClearChangeDetectorServer;
import net.geforcemods.securitycraft.network.server.ClearLoggerServer;
import net.geforcemods.securitycraft.network.server.DismountCamera;
import net.geforcemods.securitycraft.network.server.MountCamera;
import net.geforcemods.securitycraft.network.server.RemoteControlMine;
import net.geforcemods.securitycraft.network.server.RemoveCameraTag;
import net.geforcemods.securitycraft.network.server.RemoveMineFromMRAT;
import net.geforcemods.securitycraft.network.server.RemovePositionFromSSS;
import net.geforcemods.securitycraft.network.server.RemoveSentryFromSRAT;
import net.geforcemods.securitycraft.network.server.RequestSecureTradingStationTransactions;
import net.geforcemods.securitycraft.network.server.SetBriefcasePasscodeAndOwner;
import net.geforcemods.securitycraft.network.server.SetCameraPowered;
import net.geforcemods.securitycraft.network.server.SetDefaultCameraViewingDirection;
import net.geforcemods.securitycraft.network.server.SetGhostSlot;
import net.geforcemods.securitycraft.network.server.SetKeycardUses;
import net.geforcemods.securitycraft.network.server.SetListModuleData;
import net.geforcemods.securitycraft.network.server.SetPasscode;
import net.geforcemods.securitycraft.network.server.SetSentryMode;
import net.geforcemods.securitycraft.network.server.SetStateOnDisguiseModule;
import net.geforcemods.securitycraft.network.server.SyncAlarmSettings;
import net.geforcemods.securitycraft.network.server.SyncBlockChangeDetector;
import net.geforcemods.securitycraft.network.server.SyncBlockPocketManager;
import net.geforcemods.securitycraft.network.server.SyncBlockReinforcer;
import net.geforcemods.securitycraft.network.server.SyncFrame;
import net.geforcemods.securitycraft.network.server.SyncKeycardSettings;
import net.geforcemods.securitycraft.network.server.SyncLaserSideConfig;
import net.geforcemods.securitycraft.network.server.SyncProjector;
import net.geforcemods.securitycraft.network.server.SyncRiftStabilizer;
import net.geforcemods.securitycraft.network.server.SyncSSSSettingsOnServer;
import net.geforcemods.securitycraft.network.server.SyncSecureRedstoneInterface;
import net.geforcemods.securitycraft.network.server.SyncTrophySystem;
import net.geforcemods.securitycraft.network.server.ToggleBlockPocketManager;
import net.geforcemods.securitycraft.network.server.ToggleModule;
import net.geforcemods.securitycraft.network.server.ToggleNightVision;
import net.geforcemods.securitycraft.network.server.ToggleOption;
import net.geforcemods.securitycraft.network.server.UpdateSliderValue;
import net.geforcemods.securitycraft.util.RegisterItemBlock;
import net.geforcemods.securitycraft.util.Reinforced;
import net.geforcemods.securitycraft.util.SCItemGroup;
import net.geforcemods.securitycraft.util.Utils;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.geforcemods.securitycraft.fabric.event.BuildCreativeModeTabContentsEvent;
import net.geforcemods.securitycraft.fabric.items.SCItemStorages;
import net.geforcemods.securitycraft.fabric.network.PayloadRegistrar;
import net.geforcemods.securitycraft.fabric.registry.DeferredBlock;
import net.geforcemods.securitycraft.fabricmixin.core.PoiTypeAccessor;
import net.geforcemods.securitycraft.fabricmixin.core.PoiTypesAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public class RegistrationHandler {
	private RegistrationHandler() {}

	/**
	 * Registers everything that NeoForge handled through mod bus events, apart from the deferred registers. Called from
	 * {@link SecurityCraft#onInitialize()} after all registries have been filled.
	 */
	public static void init() {
		FabricDefaultAttributeRegistry.register(SCContent.SENTRY_ENTITY.get(), Mob.createMobAttributes());
		addBlockToPoi(PoiTypes.LIGHTNING_ROD, SCContent.REINFORCED_LIGHTNING_ROD.get());
		registerPayloads();
		SCItemStorages.register();
		ItemGroupEvents.MODIFY_ENTRIES_ALL.register((tab, entries) -> onCreativeModeTabRegister(new BuildCreativeModeTabContentsEvent(tab, entries)));
	}

	public static void registerBlockItems() {
		//register item blocks from annotated fields
		for (Field field : SCContent.class.getFields()) {
			try {
				if (field.isAnnotationPresent(Reinforced.class) && field.getAnnotation(Reinforced.class).registerBlockItem()) {
					SCItemGroup group = field.getAnnotation(Reinforced.class).itemGroup();
					Block block = ((DeferredBlock<Block>) field.get(null)).get();
					Item blockItem = new BlockItem(block, new Item.Properties().fireResistant());

					Registry.register(BuiltInRegistries.ITEM, Utils.getRegistryName(block), blockItem);

					if (group != SCItemGroup.MANUAL)
						SCCreativeModeTabs.STACKS_FOR_ITEM_GROUPS.get(group).add(new ItemStack(blockItem));
				}
				else if (field.isAnnotationPresent(RegisterItemBlock.class)) {
					SCItemGroup group = field.getAnnotation(RegisterItemBlock.class).value();
					Block block = ((DeferredBlock<Block>) field.get(null)).get();
					Item blockItem = new BlockItem(block, new Item.Properties());

					Registry.register(BuiltInRegistries.ITEM, Utils.getRegistryName(block), blockItem);

					if (group != SCItemGroup.MANUAL)
						SCCreativeModeTabs.STACKS_FOR_ITEM_GROUPS.get(group).add(new ItemStack(blockItem));
				}
			}
			catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	public static void registerSounds() {
		for (int i = 0; i < SCSounds.values().length; i++) {
			SCSounds sound = SCSounds.values()[i];

			Registry.register(BuiltInRegistries.SOUND_EVENT, sound.location, sound.event);
		}
	}

	/**
	 * Replacement for NeoForge's ExtendPoiTypesEvent#addBlockToPoi
	 */
	private static void addBlockToPoi(ResourceKey<PoiType> poiKey, Block block) {
		Holder<PoiType> poi = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(poiKey);
		Set<BlockState> matchingStates = new HashSet<>(poi.value().matchingStates());

		for (BlockState state : block.getStateDefinition().getPossibleStates()) {
			PoiTypesAccessor.securitycraft$getTypeByState().putIfAbsent(state, poi);
			matchingStates.add(state);
		}

		((PoiTypeAccessor) (Object) poi.value()).securitycraft$setMatchingStates(Set.copyOf(matchingStates));
	}

	private static void registerPayloads() {
		PayloadRegistrar registrar = new PayloadRegistrar();

		registrar.playToClient(InteractWithFrame.TYPE, InteractWithFrame.STREAM_CODEC, InteractWithFrame::handle);
		registrar.playToClient(OpenScreen.TYPE, OpenScreen.STREAM_CODEC, OpenScreen::handle);
		registrar.playToClient(RefreshDisguisableModel.TYPE, RefreshDisguisableModel.STREAM_CODEC, RefreshDisguisableModel::handle);
		registrar.playToClient(SetCameraView.TYPE, SetCameraView.STREAM_CODEC, SetCameraView::handle);
		registrar.playToClient(SetTrophySystemTarget.TYPE, SetTrophySystemTarget.STREAM_CODEC, SetTrophySystemTarget::handle);
		registrar.playToClient(ToggleAlarmSound.TYPE, ToggleAlarmSound.STREAM_CODEC, ToggleAlarmSound::handle);
		registrar.playToClient(UpdateLaserColors.TYPE, UpdateLaserColors.STREAM_CODEC, UpdateLaserColors::handle);
		registrar.playToClient(UpdateLogger.TYPE, UpdateLogger.STREAM_CODEC, UpdateLogger::handle);
		registrar.playToServer(CheckBriefcasePasscode.TYPE, CheckBriefcasePasscode.STREAM_CODEC, CheckBriefcasePasscode::handle);
		registrar.playToServer(CheckPasscode.TYPE, CheckPasscode.STREAM_CODEC, CheckPasscode::handle);
		registrar.playToServer(ClearChangeDetectorServer.TYPE, ClearChangeDetectorServer.STREAM_CODEC, ClearChangeDetectorServer::handle);
		registrar.playToServer(ClearLoggerServer.TYPE, ClearLoggerServer.STREAM_CODEC, ClearLoggerServer::handle);
		registrar.playToServer(DismountCamera.TYPE, DismountCamera.STREAM_CODEC, DismountCamera::handle);
		registrar.playToServer(MountCamera.TYPE, MountCamera.STREAM_CODEC, MountCamera::handle);
		registrar.playToServer(RemoteControlMine.TYPE, RemoteControlMine.STREAM_CODEC, RemoteControlMine::handle);
		registrar.playToServer(RemoveCameraTag.TYPE, RemoveCameraTag.STREAM_CODEC, RemoveCameraTag::handle);
		registrar.playToServer(RemoveMineFromMRAT.TYPE, RemoveMineFromMRAT.STREAM_CODEC, RemoveMineFromMRAT::handle);
		registrar.playToServer(RemovePositionFromSSS.TYPE, RemovePositionFromSSS.STREAM_CODEC, RemovePositionFromSSS::handle);
		registrar.playToServer(RemoveSentryFromSRAT.TYPE, RemoveSentryFromSRAT.STREAM_CODEC, RemoveSentryFromSRAT::handle);
		registrar.playToServer(RequestSecureTradingStationTransactions.TYPE, RequestSecureTradingStationTransactions.STREAM_CODEC, RequestSecureTradingStationTransactions::handle);
		registrar.playToServer(SetBriefcasePasscodeAndOwner.TYPE, SetBriefcasePasscodeAndOwner.STREAM_CODEC, SetBriefcasePasscodeAndOwner::handle);
		registrar.playToServer(SetCameraPowered.TYPE, SetCameraPowered.STREAM_CODEC, SetCameraPowered::handle);
		registrar.playToServer(SetDefaultCameraViewingDirection.TYPE, SetDefaultCameraViewingDirection.STREAM_CODEC, SetDefaultCameraViewingDirection::handle);
		registrar.playToServer(SetGhostSlot.TYPE, SetGhostSlot.STREAM_CODEC, SetGhostSlot::handle);
		registrar.playToServer(SetKeycardUses.TYPE, SetKeycardUses.STREAM_CODEC, SetKeycardUses::handle);
		registrar.playToServer(SetListModuleData.TYPE, SetListModuleData.STREAM_CODEC, SetListModuleData::handle);
		registrar.playToServer(SetPasscode.TYPE, SetPasscode.STREAM_CODEC, SetPasscode::handle);
		registrar.playToServer(SetSentryMode.TYPE, SetSentryMode.STREAM_CODEC, SetSentryMode::handle);
		registrar.playToServer(SetStateOnDisguiseModule.TYPE, SetStateOnDisguiseModule.STREAM_CODEC, SetStateOnDisguiseModule::handle);
		registrar.playToServer(SyncAlarmSettings.TYPE, SyncAlarmSettings.STREAM_CODEC, SyncAlarmSettings::handle);
		registrar.playToServer(SyncBlockChangeDetector.TYPE, SyncBlockChangeDetector.STREAM_CODEC, SyncBlockChangeDetector::handle);
		registrar.playToServer(SyncBlockPocketManager.TYPE, SyncBlockPocketManager.STREAM_CODEC, SyncBlockPocketManager::handle);
		registrar.playToServer(SyncBlockReinforcer.TYPE, SyncBlockReinforcer.STREAM_CODEC, SyncBlockReinforcer::handle);
		registrar.playToServer(SyncFrame.TYPE, SyncFrame.STREAM_CODEC, SyncFrame::handle);
		registrar.playToServer(SyncKeycardSettings.TYPE, SyncKeycardSettings.STREAM_CODEC, SyncKeycardSettings::handle);
		registrar.playToServer(SyncLaserSideConfig.TYPE, SyncLaserSideConfig.STREAM_CODEC, SyncLaserSideConfig::handle);
		registrar.playToServer(SyncProjector.TYPE, SyncProjector.STREAM_CODEC, SyncProjector::handle);
		registrar.playToServer(SyncRiftStabilizer.TYPE, SyncRiftStabilizer.STREAM_CODEC, SyncRiftStabilizer::handle);
		registrar.playToServer(SyncSSSSettingsOnServer.TYPE, SyncSSSSettingsOnServer.STREAM_CODEC, SyncSSSSettingsOnServer::handle);
		registrar.playToServer(SyncSecureRedstoneInterface.TYPE, SyncSecureRedstoneInterface.STREAM_CODEC, SyncSecureRedstoneInterface::handle);
		registrar.playToServer(SyncTrophySystem.TYPE, SyncTrophySystem.STREAM_CODEC, SyncTrophySystem::handle);
		registrar.playToServer(ToggleBlockPocketManager.TYPE, ToggleBlockPocketManager.STREAM_CODEC, ToggleBlockPocketManager::handle);
		registrar.playToServer(ToggleModule.TYPE, ToggleModule.STREAM_CODEC, ToggleModule::handle);
		registrar.playToServer(ToggleNightVision.TYPE, ToggleNightVision.STREAM_CODEC, ToggleNightVision::handle);
		registrar.playToServer(ToggleOption.TYPE, ToggleOption.STREAM_CODEC, ToggleOption::handle);
		registrar.playToServer(UpdateSliderValue.TYPE, UpdateSliderValue.STREAM_CODEC, UpdateSliderValue::handle);
	}

	private static void onCreativeModeTabRegister(BuildCreativeModeTabContentsEvent event) {
		ResourceKey<CreativeModeTab> tabKey = event.getTabKey();

		//@formatter:off
		if (tabKey.equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
			event.insertAfter(new ItemStack(Items.LEVER), new ItemStack(SCContent.REINFORCED_LEVER.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.STONE_BUTTON), new ItemStack(SCContent.REINFORCED_OAK_BUTTON.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.REINFORCED_OAK_BUTTON.get()), new ItemStack(SCContent.REINFORCED_STONE_BUTTON.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.REINFORCED_STONE_BUTTON.get()), new ItemStack(SCContent.PANIC_BUTTON.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.STONE_PRESSURE_PLATE), new ItemStack(SCContent.REINFORCED_OAK_PRESSURE_PLATE.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.REINFORCED_OAK_PRESSURE_PLATE.get()), new ItemStack(SCContent.REINFORCED_STONE_PRESSURE_PLATE.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.STICKY_PISTON), new ItemStack(SCContent.REINFORCED_PISTON.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.REINFORCED_PISTON.get()), new ItemStack(SCContent.REINFORCED_STICKY_PISTON.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.DROPPER), new ItemStack(SCContent.REINFORCED_DISPENSER.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.REINFORCED_DISPENSER.get()), new ItemStack(SCContent.REINFORCED_DROPPER.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.HOPPER), new ItemStack(SCContent.REINFORCED_HOPPER.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.LECTERN), new ItemStack(SCContent.REINFORCED_LECTERN.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
		}
		else if (tabKey.equals(CreativeModeTabs.COLORED_BLOCKS)) {
			//@formatter:off
			event.acceptAll(List.of(
					new ItemStack(SCContent.REINFORCED_WHITE_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_RED_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_LIME_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_PINK_WOOL.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_RED_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_LIME_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_PINK_CARPET.get()),
					new ItemStack(SCContent.REINFORCED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_RED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIME_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_PINK_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_RED_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_LIME_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_PINK_CONCRETE.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_RED_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIME_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_PINK_GLAZED_TERRACOTTA.get()),
					new ItemStack(SCContent.REINFORCED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_TINTED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_RED_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_LIME_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_PINK_STAINED_GLASS.get()),
					new ItemStack(SCContent.REINFORCED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_WHITE_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_GRAY_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_GRAY_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_BLACK_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_BROWN_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_RED_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_ORANGE_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_YELLOW_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_LIME_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_GREEN_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_CYAN_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_LIGHT_BLUE_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_BLUE_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_PURPLE_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_MAGENTA_STAINED_GLASS_PANE.get()),
					new ItemStack(SCContent.REINFORCED_PINK_STAINED_GLASS_PANE.get())));
			//@formatter:on
		}
		else if (tabKey.equals(SCCreativeModeTabs.DECORATION_TAB.getKey())) {
			try {
				event.insertAfter(new ItemStack(SCContent.REINFORCED_LADDER.get()), new ItemStack(SCContent.REINFORCED_SCAFFOLDING_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
				event.insertAfter(new ItemStack(SCContent.REINFORCED_LECTERN.get()), new ItemStack(SCContent.SECRET_OAK_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			}
			catch (IllegalArgumentException e) {
				event.accept(new ItemStack(SCContent.REINFORCED_SCAFFOLDING_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
				event.accept(new ItemStack(SCContent.SECRET_OAK_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			}

			event.insertAfter(new ItemStack(SCContent.SECRET_OAK_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_OAK_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_OAK_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_SPRUCE_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_SPRUCE_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_SPRUCE_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_SPRUCE_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_BIRCH_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_BIRCH_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_BIRCH_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_BIRCH_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_JUNGLE_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_JUNGLE_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_JUNGLE_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_JUNGLE_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_ACACIA_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_ACACIA_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_ACACIA_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_ACACIA_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_DARK_OAK_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_DARK_OAK_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_DARK_OAK_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_DARK_OAK_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_MANGROVE_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_MANGROVE_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_MANGROVE_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_MANGROVE_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_CHERRY_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_CHERRY_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_CHERRY_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_CHERRY_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_BAMBOO_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_BAMBOO_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_BAMBOO_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_BAMBOO_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_CRIMSON_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_CRIMSON_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_CRIMSON_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_CRIMSON_HANGING_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_WARPED_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(SCContent.SECRET_WARPED_SIGN_ITEM.get()), new ItemStack(SCContent.SECRET_WARPED_HANGING_SIGN_ITEM.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
		}
		else if (tabKey.equals(CreativeModeTabs.OP_BLOCKS)) {
			if (event.getParameters().hasPermissions()) {
				event.accept(new ItemStack(SCContent.ADMIN_TOOL.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
				event.accept(new ItemStack(SCContent.CODEBREAKER.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			}
		}
		else if (tabKey.equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
			event.insertAfter(new ItemStack(Items.OAK_CHEST_BOAT), new ItemStack(SCContent.OAK_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.SPRUCE_CHEST_BOAT), new ItemStack(SCContent.SPRUCE_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.BIRCH_CHEST_BOAT), new ItemStack(SCContent.BIRCH_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.JUNGLE_CHEST_BOAT), new ItemStack(SCContent.JUNGLE_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.ACACIA_CHEST_BOAT), new ItemStack(SCContent.ACACIA_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.DARK_OAK_CHEST_BOAT), new ItemStack(SCContent.DARK_OAK_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.MANGROVE_CHEST_BOAT), new ItemStack(SCContent.MANGROVE_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.CHERRY_CHEST_BOAT), new ItemStack(SCContent.CHERRY_SECURITY_SEA_BOAT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
			event.insertAfter(new ItemStack(Items.BAMBOO_CHEST_RAFT), new ItemStack(SCContent.BAMBOO_SECURITY_SEA_RAFT.get()), TabVisibility.PARENT_AND_SEARCH_TABS);
		}
	}

}
