package net.geforcemods.securitycraft;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.geforcemods.securitycraft.api.IReinforcedBlock;
import net.geforcemods.securitycraft.api.SecurityCraftAPI;
import net.geforcemods.securitycraft.blocks.AbstractKeypadFurnaceBlock;
import net.geforcemods.securitycraft.blocks.InventoryScannerBlock;
import net.geforcemods.securitycraft.blocks.KeypadBarrelBlock;
import net.geforcemods.securitycraft.blocks.KeypadBlock;
import net.geforcemods.securitycraft.blocks.KeypadChestBlock;
import net.geforcemods.securitycraft.blocks.KeypadTrapDoorBlock;
import net.geforcemods.securitycraft.blocks.SecureRedstoneInterfaceBlock;
import net.geforcemods.securitycraft.blocks.mines.IMSBlock;
import net.geforcemods.securitycraft.blocks.reinforced.ReinforcedCauldronBlock.IReinforcedCauldronInteraction;
import net.geforcemods.securitycraft.blocks.reinforced.ReinforcedHopperBlock;
import net.geforcemods.securitycraft.blocks.reinforced.ReinforcedPressurePlateBlock;
import net.geforcemods.securitycraft.blocks.reinforced.ReinforcedRedstoneBlock;
import net.geforcemods.securitycraft.commands.SCCommand;
import net.geforcemods.securitycraft.items.SCManualItem;
import net.geforcemods.securitycraft.misc.CommonDoorActivator;
import net.geforcemods.securitycraft.misc.ConfigAttackTargetCheck;
import net.geforcemods.securitycraft.misc.PageGroup;
import net.geforcemods.securitycraft.misc.SCManualPage;
import net.geforcemods.securitycraft.util.HasManualPage;
import net.geforcemods.securitycraft.util.Reinforced;
import net.geforcemods.securitycraft.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.geforcemods.securitycraft.api.SecurityCraftPlugin;
import net.geforcemods.securitycraft.fabric.event.NeoForge;
import net.geforcemods.securitycraft.fabric.registry.DeferredBlock;
import net.geforcemods.securitycraft.misc.OwnershipEvent;
import net.geforcemods.securitycraft.fabric.registry.DeferredHolder;
import net.geforcemods.securitycraft.fabric.util.ServerLifecycleHooks;
import net.geforcemods.securitycraft.fabric.world.TicketController;
import net.neoforged.fml.config.ModConfig;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.geforcemods.securitycraft.fabric.event.NeoForge;
import net.geforcemods.securitycraft.fabric.registry.DeferredBlock;
import net.geforcemods.securitycraft.misc.OwnershipEvent;
import net.geforcemods.securitycraft.fabric.registry.DeferredHolder;

public class SecurityCraft implements ModInitializer {
	public static final Logger LOGGER = LogUtils.getLogger();
	public static final String MODID = "securitycraft";
	public static GameRules.Key<GameRules.BooleanValue> RULE_FAKE_WATER_SOURCE_CONVERSION;
	public static GameRules.Key<GameRules.BooleanValue> RULE_FAKE_LAVA_SOURCE_CONVERSION;
	public static final Random RANDOM = new Random();
	public static final TicketController CAMERA_TICKET_CONTROLLER = new TicketController(resLoc("camera_chunks"));

	@Override
	public void onInitialize() {
		ConfigHandler.registerConfigEvents();
		NeoForgeConfigRegistry.INSTANCE.register(MODID, ModConfig.Type.SERVER, ConfigHandler.SERVER_SPEC);
		ServerLifecycleHooks.init();
		RULE_FAKE_WATER_SOURCE_CONVERSION = GameRuleRegistry.register("fakeWaterSourceConversion", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(true));
		RULE_FAKE_LAVA_SOURCE_CONVERSION = GameRuleRegistry.register("fakeLavaSourceConversion", GameRules.Category.UPDATES, GameRuleFactory.createBooleanRule(false));
		//same order in which NeoForge fires its registry events
		RegistrationHandler.registerSounds();
		SCContent.FLUIDS.register();
		SCContent.BLOCKS.register();
		SCContent.DATA_COMPONENTS.register();
		SCContent.ENTITY_TYPES.register();
		SCContent.ARMOR_MATERIALS.register();
		SCContent.ITEMS.register();
		RegistrationHandler.registerBlockItems();
		SCContent.BLOCK_ENTITY_TYPES.register();
		SCContent.PARTICLE_TYPES.register();
		SCContent.MENU_TYPES.register();
		SCContent.RECIPE_SERIALIZERS.register();
		SCContent.COMMAND_ARGUMENT_TYPES.register();
		SCContent.LOOT_ITEM_CONDITION_TYPES.register();
		SCContent.DATA_SERIALIZERS.register();
		SCCreativeModeTabs.CREATIVE_MODE_TABS.register();
		RegistrationHandler.init();
		//sets the owner of placed SecurityCraft blocks, so this must never be removed
		NeoForge.EVENT_BUS.addListener(OwnershipEvent.class, SCEventHandler::onOwnership);
		SCEventHandler.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SCCommand.register(dispatcher));
		registerApiObjects();
		collectSCContentData();
		IReinforcedCauldronInteraction.bootStrap();
	}

	private static void registerApiObjects() {
		SecurityCraftAPI.registerExtractionBlock(new ReinforcedHopperBlock.ExtractionBlock());
		SecurityCraftAPI.registerExtractionBlock(new IMSBlock.ExtractionBlock());
		SecurityCraftAPI.registerPasscodeConvertible(new KeypadBlock.Convertible());
		SecurityCraftAPI.registerPasscodeConvertible(new KeypadBarrelBlock.Convertible());
		SecurityCraftAPI.registerPasscodeConvertible(new KeypadChestBlock.Convertible());
		SecurityCraftAPI.registerPasscodeConvertible(new KeypadTrapDoorBlock.Convertible());
		SecurityCraftAPI.registerPasscodeConvertible(new AbstractKeypadFurnaceBlock.Convertible(Blocks.FURNACE, SCContent.KEYPAD_FURNACE.get()));
		SecurityCraftAPI.registerPasscodeConvertible(new AbstractKeypadFurnaceBlock.Convertible(Blocks.SMOKER, SCContent.KEYPAD_SMOKER.get()));
		SecurityCraftAPI.registerPasscodeConvertible(new AbstractKeypadFurnaceBlock.Convertible(Blocks.BLAST_FURNACE, SCContent.KEYPAD_BLAST_FURNACE.get()));
		SecurityCraftAPI.registerSentryAttackTargetCheck(new ConfigAttackTargetCheck());
		SecurityCraftAPI.registerDoorActivator(new CommonDoorActivator());
		SecurityCraftAPI.registerDoorActivator(new InventoryScannerBlock.DoorActivator());
		SecurityCraftAPI.registerDoorActivator(new ReinforcedPressurePlateBlock.DoorActivator());
		SecurityCraftAPI.registerDoorActivator(new ReinforcedRedstoneBlock.DoorActivator());
		SecurityCraftAPI.registerDoorActivator(new SecureRedstoneInterfaceBlock.DoorActivator());

		for (SecurityCraftPlugin plugin : FabricLoader.getInstance().getEntrypoints(MODID, SecurityCraftPlugin.class)) {
			plugin.register();
		}

		SecurityCraftAPI.freeze();
	}

	public static void collectSCContentData() {
		Map<PageGroup, List<ItemStack>> groupStacks = new EnumMap<>(PageGroup.class);

		for (Field field : SCContent.class.getFields()) {
			try {
				if (field.isAnnotationPresent(Reinforced.class)) {
					Block block = ((DeferredBlock<Block>) field.get(null)).get();
					IReinforcedBlock rb = (IReinforcedBlock) block;

					IReinforcedBlock.VANILLA_TO_SECURITYCRAFT.put(rb.getVanillaBlock(), block);
					IReinforcedBlock.SECURITYCRAFT_TO_VANILLA.put(block, rb.getVanillaBlock());
				}

				if (field.isAnnotationPresent(HasManualPage.class)) {
					Object o = ((DeferredHolder<?, ?>) field.get(null)).get();
					HasManualPage hmp = field.getAnnotation(HasManualPage.class);
					Item item = ((ItemLike) o).asItem();
					PageGroup group = hmp.value();
					boolean wasNotAdded = false;
					Component title = Component.translatable("");
					String key = "help.";

					if (group != PageGroup.NONE) {
						if (!groupStacks.containsKey(group)) {
							groupStacks.put(group, new ArrayList<>());
							title = Utils.localize(group.getTitle());
							key += group.getSpecialInfoKey();
							wasNotAdded = true;
						}

						groupStacks.get(group).add(new ItemStack(item));
					}
					else {
						title = Utils.localize(item.getDescriptionId());
						key += item.getDescriptionId().substring(5) + ".info";
					}

					if (group == PageGroup.NONE || wasNotAdded)
						SCManualItem.PAGES.add(new SCManualPage(item, group, title, Component.translatable(key.replace("..", ".")), hmp.designedBy(), hmp.hasRecipeDescription()));
				}
			}
			catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}

		groupStacks.forEach((group, list) -> group.setItems(Ingredient.of(list.stream())));
	}

	public static String getVersion() {
		return "v" + FabricLoader.getInstance().getModContainer(MODID).orElseThrow().getMetadata().getVersion().getFriendlyString();
	}

	public static ResourceLocation resLoc(String path) {
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}

	public static ResourceLocation mcResLoc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}
}
