package net.geforcemods.securitycraft;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.geforcemods.securitycraft.api.IDisguisable;
import net.geforcemods.securitycraft.api.IOwnable;
import net.geforcemods.securitycraft.api.IPasscodeProtected;
import net.geforcemods.securitycraft.api.IReinforcedBlock;
import net.geforcemods.securitycraft.blockentities.AlarmBlockEntity;
import net.geforcemods.securitycraft.blockentities.FrameBlockEntity;
import net.geforcemods.securitycraft.blockentities.InventoryScannerBlockEntity;
import net.geforcemods.securitycraft.blockentities.LaserBlockBlockEntity;
import net.geforcemods.securitycraft.blockentities.RiftStabilizerBlockEntity;
import net.geforcemods.securitycraft.blockentities.SecureRedstoneInterfaceBlockEntity;
import net.geforcemods.securitycraft.blockentities.SonicSecuritySystemBlockEntity;
import net.geforcemods.securitycraft.blockentities.UsernameLoggerBlockEntity;
import net.geforcemods.securitycraft.blocks.InventoryScannerFieldBlock;
import net.geforcemods.securitycraft.blocks.LaserFieldBlock;
import net.geforcemods.securitycraft.blocks.SecureRedstoneInterfaceBlock;
import net.geforcemods.securitycraft.components.SavedBlockState;
import net.geforcemods.securitycraft.entity.camera.CameraClientEvents;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.geforcemods.securitycraft.fabric.blockentity.IBlockEntityExtension;
import net.geforcemods.securitycraft.fabric.client.SCRenderLayers;
import net.geforcemods.securitycraft.fabric.registry.DeferredBlock;
import net.geforcemods.securitycraft.fabricmixin.client.PlayerRendererInvoker;
import net.geforcemods.securitycraft.inventory.KeycardHolderMenu;
import net.geforcemods.securitycraft.items.CodebreakerItem;
import net.geforcemods.securitycraft.items.KeycardHolderItem;
import net.geforcemods.securitycraft.items.LensItem;
import net.geforcemods.securitycraft.items.TaserItem;
import net.geforcemods.securitycraft.items.properties.BlockLinked;
import net.geforcemods.securitycraft.items.properties.CodebreakerState;
import net.geforcemods.securitycraft.items.properties.HitCheck;
import net.geforcemods.securitycraft.items.properties.SentryLinked;
import net.geforcemods.securitycraft.misc.KeyBindings;
import net.geforcemods.securitycraft.misc.LayerToggleHandler;
import net.geforcemods.securitycraft.misc.Tips;
import net.geforcemods.securitycraft.misc.TintMode;
import net.geforcemods.securitycraft.models.BlockMineModel;
import net.geforcemods.securitycraft.models.BulletModel;
import net.geforcemods.securitycraft.models.DisguisableDynamicBakedModel;
import net.geforcemods.securitycraft.models.IMSBombModel;
import net.geforcemods.securitycraft.models.SecureRedstoneInterfaceBakedModel;
import net.geforcemods.securitycraft.models.SecureRedstoneInterfaceDishModel;
import net.geforcemods.securitycraft.models.SecurityCameraModel;
import net.geforcemods.securitycraft.models.SentryModel;
import net.geforcemods.securitycraft.models.SonicSecuritySystemModel;
import net.geforcemods.securitycraft.particle.FloorTrapCloudParticle;
import net.geforcemods.securitycraft.particle.InterfaceHighlightParticle;
import net.geforcemods.securitycraft.renderers.BlockPocketManagerRenderer;
import net.geforcemods.securitycraft.renderers.BouncingBettyRenderer;
import net.geforcemods.securitycraft.renderers.BulletRenderer;
import net.geforcemods.securitycraft.renderers.ClaymoreRenderer;
import net.geforcemods.securitycraft.renderers.DisguisableBlockEntityRenderer;
import net.geforcemods.securitycraft.renderers.DisplayCaseItemRenderer;
import net.geforcemods.securitycraft.renderers.DisplayCaseRenderer;
import net.geforcemods.securitycraft.renderers.FrameBlockEntityRenderer;
import net.geforcemods.securitycraft.renderers.IMSBombRenderer;
import net.geforcemods.securitycraft.renderers.KeypadChestItemRenderer;
import net.geforcemods.securitycraft.renderers.KeypadChestRenderer;
import net.geforcemods.securitycraft.renderers.ProjectorRenderer;
import net.geforcemods.securitycraft.renderers.ReinforcedPistonHeadRenderer;
import net.geforcemods.securitycraft.renderers.RetinalScannerRenderer;
import net.geforcemods.securitycraft.renderers.SecretHangingSignRenderer;
import net.geforcemods.securitycraft.renderers.SecretSignRenderer;
import net.geforcemods.securitycraft.renderers.SecureRedstoneInterfaceRenderer;
import net.geforcemods.securitycraft.renderers.SecureTradingStationRenderer;
import net.geforcemods.securitycraft.renderers.SecurityCameraItemRenderer;
import net.geforcemods.securitycraft.renderers.SecurityCameraRenderer;
import net.geforcemods.securitycraft.renderers.SecuritySeaBoatRenderer;
import net.geforcemods.securitycraft.renderers.SentryRenderer;
import net.geforcemods.securitycraft.renderers.SonicSecuritySystemRenderer;
import net.geforcemods.securitycraft.renderers.TrophySystemRenderer;
import net.geforcemods.securitycraft.screen.AlarmScreen;
import net.geforcemods.securitycraft.screen.BlockChangeDetectorScreen;
import net.geforcemods.securitycraft.screen.BlockPocketManagerScreen;
import net.geforcemods.securitycraft.screen.BlockReinforcerScreen;
import net.geforcemods.securitycraft.screen.BriefcasePasscodeScreen;
import net.geforcemods.securitycraft.screen.CameraMonitorScreen;
import net.geforcemods.securitycraft.screen.CheckPasscodeScreen;
import net.geforcemods.securitycraft.screen.CustomizeBlockScreen;
import net.geforcemods.securitycraft.screen.DisguiseModuleScreen;
import net.geforcemods.securitycraft.screen.EditModuleScreen;
import net.geforcemods.securitycraft.screen.FrameScreen;
import net.geforcemods.securitycraft.screen.InventoryScannerScreen;
import net.geforcemods.securitycraft.screen.ItemInventoryScreen;
import net.geforcemods.securitycraft.screen.KeyChangerScreen;
import net.geforcemods.securitycraft.screen.KeycardReaderScreen;
import net.geforcemods.securitycraft.screen.KeypadBlastFurnaceScreen;
import net.geforcemods.securitycraft.screen.KeypadFurnaceScreen;
import net.geforcemods.securitycraft.screen.KeypadSmokerScreen;
import net.geforcemods.securitycraft.screen.LaserBlockScreen;
import net.geforcemods.securitycraft.screen.MineRemoteAccessToolScreen;
import net.geforcemods.securitycraft.screen.ProjectorScreen;
import net.geforcemods.securitycraft.screen.ReinforcedLecternScreen;
import net.geforcemods.securitycraft.screen.RiftStabilizerScreen;
import net.geforcemods.securitycraft.screen.SCManualScreen;
import net.geforcemods.securitycraft.screen.SSSItemScreen;
import net.geforcemods.securitycraft.screen.SecureRedstoneInterfaceScreen;
import net.geforcemods.securitycraft.screen.SecureTradingStationScreen;
import net.geforcemods.securitycraft.screen.SentryRemoteAccessToolScreen;
import net.geforcemods.securitycraft.screen.SetPasscodeScreen;
import net.geforcemods.securitycraft.screen.SingleLensScreen;
import net.geforcemods.securitycraft.screen.SonicSecuritySystemScreen;
import net.geforcemods.securitycraft.screen.TrophySystemScreen;
import net.geforcemods.securitycraft.screen.UsernameLoggerScreen;
import net.geforcemods.securitycraft.util.BlockEntityRenderDelegate;
import net.geforcemods.securitycraft.util.ClientUtils;
import net.geforcemods.securitycraft.util.Reinforced;
import net.geforcemods.securitycraft.util.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.AtlasSet.StitchResult;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * PORT-NOTE: On NeoForge, this class subscribed to the client mod bus registration events. On Fabric, {@link #init()} (called
 * from SecurityCraftClient#onInitializeClient) registers the same things through Fabric API and vanilla registries.
 */
public class ClientHandler {
	public static final ModelLayerLocation BULLET_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("bullet"), "main");
	public static final ModelLayerLocation IMS_BOMB_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("ims_bomb"), "main");
	public static final ModelLayerLocation DISPLAY_CASE_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("display_case"), "main");
	public static final ModelLayerLocation GLOW_DISPLAY_CASE_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("glow_display_case"), "main");
	public static final ModelLayerLocation SENTRY_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("sentry"), "main");
	public static final ModelLayerLocation SECURE_REDSTONE_INTERFACE_DISH_LAYER_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("secure_redstone_interface_dish"), "main");
	public static final ModelLayerLocation SECURITY_CAMERA_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("security_camera"), "main");
	public static final ModelLayerLocation SONIC_SECURITY_SYSTEM_LOCATION = new ModelLayerLocation(SecurityCraft.resLoc("sonic_security_system"), "main");
	public static final BlockEntityRenderDelegate DISGUISED_BLOCK_RENDER_DELEGATE = new BlockEntityRenderDelegate();
	public static final BlockEntityRenderDelegate PROJECTOR_RENDER_DELEGATE = new BlockEntityRenderDelegate();
	public static final ResourceLocation CAMERA_LAYER = SecurityCraft.resLoc("camera_overlay");
	private static Map<Block, Integer> blocksWithReinforcedTint = new HashMap<>();
	private static Map<Block, Integer> blocksWithCustomTint = new HashMap<>();
	//@formatter:off
	private static Supplier<Block[]> disguisableBlocks = Suppliers.memoize(() -> new Block[] {
			SCContent.BLOCK_CHANGE_DETECTOR.get(),
			SCContent.CAGE_TRAP.get(),
			SCContent.FLOOR_TRAP.get(),
			SCContent.INVENTORY_SCANNER.get(),
			SCContent.KEYCARD_READER.get(),
			SCContent.KEYPAD.get(),
			SCContent.KEYPAD_BARREL.get(),
			SCContent.KEYPAD_BLAST_FURNACE.get(),
			SCContent.KEYPAD_CHEST.get(),
			SCContent.KEYPAD_DOOR.get(),
			SCContent.KEYPAD_FURNACE.get(),
			SCContent.KEYPAD_SMOKER.get(),
			SCContent.KEYPAD_TRAPDOOR.get(),
			SCContent.LASER_BLOCK.get(),
			SCContent.PROJECTOR.get(),
			SCContent.PROTECTO.get(),
			SCContent.REINFORCED_DISPENSER.get(),
			SCContent.REINFORCED_DROPPER.get(),
			SCContent.REINFORCED_HOPPER.get(),
			SCContent.REINFORCED_OBSERVER.get(),
			SCContent.RETINAL_SCANNER.get(),
			SCContent.RIFT_STABILIZER.get(),
			SCContent.SCANNER_DOOR.get(),
			SCContent.SCANNER_TRAPDOOR.get(),
			SCContent.SECURE_TRADING_STATION.get(),
			SCContent.SECURITY_CAMERA.get(),
			//Excluded because it has its own custom baked model
			//SCContent.SECURE_REDSTONE_INTERFACE.get(),
			SCContent.SENTRY_DISGUISE.get(),
			SCContent.SONIC_SECURITY_SYSTEM.get(),
			SCContent.TROPHY_SYSTEM.get(),
			SCContent.USERNAME_LOGGER.get()
	});
	private static final String[] MINES = {
			"ancient_debris",
			"blast_furnace",
			"coal_ore",
			"cobbled_deepslate",
			"cobblestone",
			"copper_ore",
			"deepslate",
			"deepslate_coal_ore",
			"deepslate_copper_ore",
			"deepslate_diamond_ore",
			"deepslate_emerald_ore",
			"deepslate_gold_ore",
			"deepslate_iron_ore",
			"deepslate_lapis_ore",
			"deepslate_redstone_ore",
			"diamond_ore",
			"dirt",
			"emerald_ore",
			"gravel",
			"gold_ore",
			"gilded_blackstone",
			"furnace",
			"iron_ore",
			"lapis_ore",
			"nether_gold_ore",
			"redstone_ore",
			"sand",
			"smoker",
			"stone",
			"suspicious_gravel",
			"suspicious_sand"
	};
	public static final RenderType.CompositeRenderType OVERLAY_LINES = RenderType.create(
			"overlay_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES,
			1536,
			RenderType.CompositeState.builder()
				.setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
				.setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
				.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
				.setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
				.setOutputState(RenderStateShard.OUTLINE_TARGET)
				.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
				.setCullState(RenderStateShard.NO_CULL)
				.createCompositeState(false));
	//@formatter:on
	public static final ResourceLocation LINKING_STATE_PROPERTY = SecurityCraft.resLoc("linking_state");
	private static ShaderInstance frameFeedShader;
	private static ModelBakery.TextureGetter textureGetter;
	/**
	 * The mine item models (key) and the item models of the blocks they look like (value), see
	 * {@link #registerBlockMineModel}
	 */
	private static Map<ModelResourceLocation, ResourceLocation> blockMineModels;
	/**
	 * The block state models of the disguisable blocks and the secure redstone interface, and their block states
	 */
	private static Map<ModelResourceLocation, BlockState> disguisableBlockStateModels;

	private ClientHandler() {}

	/**
	 * Registers everything NeoForge's client mod bus and game bus registration events used to register. Called once from
	 * SecurityCraftClient#onInitializeClient. Nothing in here may access Minecraft's options or levels, as Minecraft is not
	 * fully initialized when client entrypoints run.
	 */
	public static void init() {
		SCRenderLayers.register();
		ModelLoadingPlugin.register(pluginContext -> {
			onModelRegisterAdditional(pluginContext);
			pluginContext.modifyModelAfterBake().register(ClientHandler::onModelModifyBakingResult);
		});
		onFMLClientSetup();
		registerMenuScreens();
		registerGuiLayers();
		registerEntityRenderers();
		registerLayerDefinitions();
		registerParticleProviders();
		CoreShaderRegistrationCallback.EVENT.register(ClientHandler::onRegisterShaders);
		onRegisterClientExtensions();
		onRegisterColorHandlersBlock();
		onRegisterColorHandlersItem();
		KeyBindings.registerKeyMappings();
		SCClientEventHandler.register();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Tips.onLoggingIn());
		CameraClientEvents.register();

		if (!RendererAccess.INSTANCE.hasRenderer())
			SecurityCraft.LOGGER.warn("No implementation of the Fabric Rendering API is present (Indigo or Sodium provide one). Disguised blocks will look like their actual block instead of their disguise!");
	}

	public static void onModelRegisterAdditional(ModelLoadingPlugin.Context pluginContext) {
		ResourceLocation sriName = Utils.getRegistryName(SCContent.SECURE_REDSTONE_INTERFACE.get()).withPrefix("block/");

		pluginContext.addModels(sriName.withSuffix("_sender_on"), sriName.withSuffix("_receiver_on"));
		disguisableBlockStateModels = new HashMap<>();

		for (Block block : disguisableBlocks.get()) {
			for (BlockState state : block.getStateDefinition().getPossibleStates()) {
				disguisableBlockStateModels.put(BlockModelShaper.stateToModelLocation(state), state);
			}
		}

		for (BlockState state : SCContent.SECURE_REDSTONE_INTERFACE.get().getStateDefinition().getPossibleStates()) {
			disguisableBlockStateModels.put(BlockModelShaper.stateToModelLocation(state), state);
		}

		blockMineModels = new HashMap<>();

		for (String mine : MINES) {
			registerBlockMineModel(SecurityCraft.resLoc(mine.replace("_ore", "") + "_mine"), SecurityCraft.mcResLoc(mine));
		}

		registerBlockMineModel(SecurityCraft.resLoc("quartz_mine"), SecurityCraft.mcResLoc("nether_quartz_ore"));
	}

	/**
	 * PORT-NOTE: Replaces NeoForge's ModelEvent.ModifyBakingResult, which replaced the baked models in the finished model map.
	 * Fabric's model loading API hands over each top level model right after it was baked instead, and the replacement is
	 * returned.
	 */
	public static BakedModel onModelModifyBakingResult(BakedModel model, ModelModifier.AfterBake.Context context) {
		ModelResourceLocation topLevelId = context.topLevelId();

		if (model == null || topLevelId == null)
			return model;

		ResourceLocation realBlockModel = blockMineModels.get(topLevelId);

		if (realBlockModel != null)
			return new BlockMineModel(bakeModel(context.baker(), realBlockModel, BlockModelRotation.X0_Y0, model), model);

		BlockState state = disguisableBlockStateModels.get(topLevelId);

		if (state == null)
			return model;
		else if (state.is(SCContent.SECURE_REDSTONE_INTERFACE.get())) {
			ResourceLocation modelBase = Utils.getRegistryName(SCContent.SECURE_REDSTONE_INTERFACE.get()).withPrefix("block/");
			ResourceLocation poweredModel = modelBase.withSuffix(state.getValue(SecureRedstoneInterfaceBlock.SENDER) ? "_sender_on" : "_receiver_on");

			return new SecureRedstoneInterfaceBakedModel(bakeDirectionalModel(context.baker(), poweredModel, state.getValue(SecureRedstoneInterfaceBlock.FACING)), model);
		}
		else
			return new DisguisableDynamicBakedModel(model);
	}

	private static BakedModel bakeDirectionalModel(ModelBaker baker, ResourceLocation location, Direction facing) {
		return bakeModel(baker, location, modelRotationFromDirection(facing), null);
	}

	private static BakedModel bakeModel(ModelBaker baker, ResourceLocation location, BlockModelRotation rotation, BakedModel fallback) {
		try {
			BakedModel model = baker.bake(location, rotation);

			return model != null ? model : fallback;
		}
		catch (Exception exception) {
			SecurityCraft.LOGGER.warn("Unable to bake standalone model: '{}': {}", location, exception);
			return fallback;
		}
	}

	private static void registerBlockMineModel(ResourceLocation mineRl, ResourceLocation realBlockRl) {
		blockMineModels.put(new ModelResourceLocation(mineRl, "inventory"), realBlockRl.withPrefix("item/"));
	}

	public static void onFMLClientSetup() {
		RenderType translucent = RenderType.translucent();

		BlockRenderLayerMap.INSTANCE.putFluids(translucent, SCContent.FAKE_WATER.get(), SCContent.FLOWING_FAKE_WATER.get());
		//PORT-NOTE: On NeoForge, the fake fluids use the vanilla water and lava fluid types, whose client extensions provide
		//the textures and the biome water tint. On Fabric, the same is achieved by rendering them with vanilla's handlers.
		FluidRenderHandlerRegistry.INSTANCE.register(SCContent.FAKE_WATER.get(), SCContent.FLOWING_FAKE_WATER.get(), FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER));
		FluidRenderHandlerRegistry.INSTANCE.register(SCContent.FAKE_LAVA.get(), SCContent.FLOWING_FAKE_LAVA.get(), FluidRenderHandlerRegistry.INSTANCE.get(Fluids.LAVA));

		BlockLinked cameraProperty = new BlockLinked(SCContent.BOUND_CAMERAS, HitCheck.SECURITY_CAMERA);
		BlockLinked mineProperty = new BlockLinked(SCContent.BOUND_MINES, HitCheck.EXPLOSIVE_BLOCK);
		BlockLinked sssProperty = new BlockLinked(SCContent.SSS_LINKED_BLOCKS, HitCheck.LOCKABLE);
		CodebreakerState codebreakerProperty = new CodebreakerState(HitCheck.CODEBREAKABLE);

		ItemProperties.register(SCContent.KEYCARD_HOLDER.get(), KeycardHolderItem.COUNT_PROPERTY, (stack, level, entity, id) -> KeycardHolderItem.getCardCount(stack) / (float) KeycardHolderMenu.CONTAINER_SIZE);
		ItemProperties.register(SCContent.LENS.get(), LensItem.COLOR_PROPERTY, (stack, level, entity, id) -> stack.has(DataComponents.DYED_COLOR) ? 1.0F : 0.0F);
		ItemProperties.register(SCContent.CAMERA_MONITOR.get(), LINKING_STATE_PROPERTY, cameraProperty::get);
		ItemProperties.register(SCContent.MINE_REMOTE_ACCESS_TOOL.get(), LINKING_STATE_PROPERTY, mineProperty::get);
		ItemProperties.register(SCContent.SENTRY_REMOTE_ACCESS_TOOL.get(), LINKING_STATE_PROPERTY, SentryLinked::get);
		ItemProperties.register(SCContent.SONIC_SECURITY_SYSTEM_ITEM.get(), LINKING_STATE_PROPERTY, sssProperty::get);
		ItemProperties.register(SCContent.CODEBREAKER.get(), CodebreakerItem.STATE_PROPERTY, codebreakerProperty::get);
	}

	public static void registerMenuScreens() {
		MenuScreens.register(SCContent.BLOCK_REINFORCER_MENU.get(), BlockReinforcerScreen::new);
		MenuScreens.register(SCContent.BRIEFCASE_INVENTORY_MENU.get(), ItemInventoryScreen.Briefcase::new);
		MenuScreens.register(SCContent.CUSTOMIZE_BLOCK_MENU.get(), CustomizeBlockScreen::new);
		MenuScreens.register(SCContent.CUSTOMIZE_ENTITY_MENU.get(), CustomizeBlockScreen::new);
		MenuScreens.register(SCContent.DISGUISE_MODULE_MENU.get(), DisguiseModuleScreen::new);
		MenuScreens.register(SCContent.INVENTORY_SCANNER_MENU.get(), InventoryScannerScreen::new);
		MenuScreens.register(SCContent.KEYPAD_FURNACE_MENU.get(), KeypadFurnaceScreen::new);
		MenuScreens.register(SCContent.KEYPAD_SMOKER_MENU.get(), KeypadSmokerScreen::new);
		MenuScreens.register(SCContent.KEYPAD_BLAST_FURNACE_MENU.get(), KeypadBlastFurnaceScreen::new);
		MenuScreens.register(SCContent.KEYCARD_READER_MENU.get(), KeycardReaderScreen::new);
		MenuScreens.register(SCContent.BLOCK_POCKET_MANAGER_MENU.get(), BlockPocketManagerScreen::new);
		MenuScreens.register(SCContent.PROJECTOR_MENU.get(), ProjectorScreen::new);
		MenuScreens.register(SCContent.BLOCK_CHANGE_DETECTOR_MENU.get(), BlockChangeDetectorScreen::new);
		MenuScreens.register(SCContent.KEYCARD_HOLDER_MENU.get(), ItemInventoryScreen.KeycardHolder::new);
		MenuScreens.register(SCContent.TROPHY_SYSTEM_MENU.get(), TrophySystemScreen::new);
		MenuScreens.register(SCContent.SINGLE_LENS_MENU.get(), SingleLensScreen::new);
		MenuScreens.register(SCContent.LASER_BLOCK_MENU.get(), LaserBlockScreen::new);
		MenuScreens.register(SCContent.REINFORCED_LECTERN_MENU.get(), ReinforcedLecternScreen::new);
		MenuScreens.register(SCContent.SECURE_TRADING_STATION_MENU.get(), SecureTradingStationScreen::new);
	}

	/**
	 * PORT-NOTE: NeoForge's GUI layer above all others becomes a HUD callback, which Fabric runs after all of vanilla's HUD. As
	 * with NeoForge's layer, it is only drawn while LayerToggleHandler has it enabled.
	 */
	public static void registerGuiLayers() {
		HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> {
			if (!LayerToggleHandler.isDisabled(CAMERA_LAYER))
				SCClientEventHandler.cameraOverlay(guiGraphics, deltaTracker);
		});
		LayerToggleHandler.disable(CAMERA_LAYER);
	}

	public static void registerEntityRenderers() {
		EntityRendererRegistry.register(SCContent.BOUNCING_BETTY_ENTITY.get(), BouncingBettyRenderer::new);
		EntityRendererRegistry.register(SCContent.IMS_BOMB_ENTITY.get(), IMSBombRenderer::new);
		EntityRendererRegistry.register(SCContent.SECURITY_CAMERA_ENTITY.get(), NoopRenderer::new);
		EntityRendererRegistry.register(SCContent.SENTRY_ENTITY.get(), SentryRenderer::new);
		EntityRendererRegistry.register(SCContent.BULLET_ENTITY.get(), BulletRenderer::new);
		EntityRendererRegistry.register(SCContent.SECURITY_SEA_BOAT_ENTITY.get(), SecuritySeaBoatRenderer::new);
		//normal renderers
		BlockEntityRenderers.register(SCContent.BLOCK_POCKET_MANAGER_BLOCK_ENTITY.get(), BlockPocketManagerRenderer::new);
		BlockEntityRenderers.register(SCContent.CLAYMORE_BLOCK_ENTITY.get(), ClaymoreRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_CHEST_BLOCK_ENTITY.get(), KeypadChestRenderer::new);
		BlockEntityRenderers.register(SCContent.DISPLAY_CASE_BLOCK_ENTITY.get(), ctx -> new DisplayCaseRenderer(ctx, false));
		BlockEntityRenderers.register(SCContent.GLOW_DISPLAY_CASE_BLOCK_ENTITY.get(), ctx -> new DisplayCaseRenderer(ctx, true));
		BlockEntityRenderers.register(SCContent.FRAME_BLOCK_ENTITY.get(), FrameBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.REINFORCED_LECTERN_BLOCK_ENTITY.get(), LecternRenderer::new);
		BlockEntityRenderers.register(SCContent.PROJECTOR_BLOCK_ENTITY.get(), ProjectorRenderer::new);
		BlockEntityRenderers.register(SCContent.REINFORCED_PISTON_BLOCK_ENTITY.get(), ReinforcedPistonHeadRenderer::new);
		BlockEntityRenderers.register(SCContent.RETINAL_SCANNER_BLOCK_ENTITY.get(), RetinalScannerRenderer::new);
		BlockEntityRenderers.register(SCContent.SECURE_REDSTONE_INTERFACE_BLOCK_ENTITY.get(), SecureRedstoneInterfaceRenderer::new);
		BlockEntityRenderers.register(SCContent.SECURE_TRADING_STATION_BLOCK_ENTITY.get(), SecureTradingStationRenderer::new);
		BlockEntityRenderers.register(SCContent.SECURITY_CAMERA_BLOCK_ENTITY.get(), SecurityCameraRenderer::new);
		BlockEntityRenderers.register(SCContent.SECRET_HANGING_SIGN_BLOCK_ENTITY.get(), SecretHangingSignRenderer::new);
		BlockEntityRenderers.register(SCContent.SECRET_SIGN_BLOCK_ENTITY.get(), SecretSignRenderer::new);
		BlockEntityRenderers.register(SCContent.SONIC_SECURITY_SYSTEM_BLOCK_ENTITY.get(), SonicSecuritySystemRenderer::new);
		BlockEntityRenderers.register(SCContent.TROPHY_SYSTEM_BLOCK_ENTITY.get(), TrophySystemRenderer::new);
		//disguisable block entity renderers
		BlockEntityRenderers.register(SCContent.BLOCK_CHANGE_DETECTOR_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.CAGE_TRAP_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.DISGUISABLE_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.FLOOR_TRAP_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.INVENTORY_SCANNER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYCARD_READER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_BARREL_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_BLAST_FURNACE_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_DOOR_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_TRAPDOOR_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_FURNACE_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.KEYPAD_SMOKER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.LASER_BLOCK_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.OBSERVER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.PROTECTO_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.REINFORCED_DISPENSER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.REINFORCED_DROPPER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.REINFORCED_HOPPER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.RIFT_STABILIZER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.SCANNER_DOOR_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.SCANNER_TRAPDOOR_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
		BlockEntityRenderers.register(SCContent.USERNAME_LOGGER_BLOCK_ENTITY.get(), DisguisableBlockEntityRenderer::new);
	}

	public static void registerLayerDefinitions() {
		EntityModelLayerRegistry.registerModelLayer(BULLET_LOCATION, BulletModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(IMS_BOMB_LOCATION, IMSBombModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(DISPLAY_CASE_LOCATION, DisplayCaseRenderer::createModelLayer);
		EntityModelLayerRegistry.registerModelLayer(GLOW_DISPLAY_CASE_LOCATION, DisplayCaseRenderer::createModelLayer);
		EntityModelLayerRegistry.registerModelLayer(SENTRY_LOCATION, SentryModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(SECURITY_CAMERA_LOCATION, SecurityCameraModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(SONIC_SECURITY_SYSTEM_LOCATION, SonicSecuritySystemModel::createLayer);
		EntityModelLayerRegistry.registerModelLayer(SECURE_REDSTONE_INTERFACE_DISH_LAYER_LOCATION, SecureRedstoneInterfaceDishModel::createLayer);
	}

	public static void registerParticleProviders() {
		ParticleFactoryRegistry.getInstance().register(SCContent.FLOOR_TRAP_CLOUD.get(), FloorTrapCloudParticle.Provider::new);
		ParticleFactoryRegistry.getInstance().register(SCContent.INTERFACE_HIGHLIGHT.get(), InterfaceHighlightParticle.Provider::new);
	}

	public static void onRegisterShaders(CoreShaderRegistrationCallback.RegistrationContext context) {
		try {
			context.register(SecurityCraft.resLoc("frame_draw_fb_in_area"), DefaultVertexFormat.POSITION_TEX, loadedShader -> frameFeedShader = loadedShader);
		}
		catch (Exception e) {
			throw new IllegalStateException("Camera feed shader does not exist", e);
		}
	}

	/**
	 * PORT-NOTE: The block entity item renderers NeoForge's IClientItemExtensions provided are registered with Fabric's
	 * BuiltinItemRendererRegistry. The taser's first person hand transform and third person arm pose, which were also
	 * IClientItemExtensions, are applied by fabricmixin.client.ItemInHandRendererMixin and HumanoidModelMixin through
	 * {@link #applyTaserHandTransform} and {@link #hasTaserArmPose}.
	 */
	public static void onRegisterClientExtensions() {
		BlockEntityWithoutLevelRenderer displayCaseItemRenderer = new DisplayCaseItemRenderer(false);
		BlockEntityWithoutLevelRenderer glowDisplayCaseItemRenderer = new DisplayCaseItemRenderer(true);
		BlockEntityWithoutLevelRenderer keypadChestItemRenderer = new KeypadChestItemRenderer();
		BlockEntityWithoutLevelRenderer securityCameraItemRenderer = new SecurityCameraItemRenderer();

		BuiltinItemRendererRegistry.INSTANCE.register(SCContent.DISPLAY_CASE_ITEM.get(), displayCaseItemRenderer::renderByItem);
		BuiltinItemRendererRegistry.INSTANCE.register(SCContent.GLOW_DISPLAY_CASE_ITEM.get(), glowDisplayCaseItemRenderer::renderByItem);
		BuiltinItemRendererRegistry.INSTANCE.register(SCContent.KEYPAD_CHEST_ITEM.get(), keypadChestItemRenderer::renderByItem);
		BuiltinItemRendererRegistry.INSTANCE.register(SCContent.SECURITY_CAMERA.get().asItem(), securityCameraItemRenderer::renderByItem);
	}

	/**
	 * The taser's first person transform (NeoForge: IClientItemExtensions#applyForgeHandTransform)
	 *
	 * @return true if the transform has been applied and vanilla's arm transforms should be skipped
	 */
	public static boolean applyTaserHandTransform(PoseStack pose, ItemStack stack, float swingProgress) {
		if (stack.getItem() instanceof TaserItem && swingProgress < 0.001F) {
			pose.translate(0.02F, -0.4F, -0.5F);
			return true;
		}

		return false;
	}

	/**
	 * Whether the given entity's arms are posed like holding a taser in third person (NeoForge: the arm pose that
	 * IClientItemExtensions#getArmPose returned for the taser, which PlayerRenderer uses for players). The taser pose is
	 * applied after the other arm's pose, so it decides the rotation of both arms, unless an item is being used, in which case
	 * only the arm using it is posed.
	 */
	public static boolean hasTaserArmPose(LivingEntity entity) {
		if (!(entity instanceof AbstractClientPlayer player) || player.isSpectator() || player.isUsingItem())
			return false;

		if (player.getMainHandItem().getItem() instanceof TaserItem)
			return true;

		//the off hand pose is only used if the main hand pose is not a two-handed one
		return player.getOffhandItem().getItem() instanceof TaserItem && !PlayerRendererInvoker.securitycraft$getArmPose(player, InteractionHand.MAIN_HAND).isTwoHanded();
	}

	private static void initTint() {
		for (Field field : SCContent.class.getFields()) {
			if (field.isAnnotationPresent(Reinforced.class)) {
				try {
					Block block = ((DeferredBlock<Block>) field.get(null)).get();
					int customTint = field.getAnnotation(Reinforced.class).customTint();

					if (field.getAnnotation(Reinforced.class).hasReinforcedTint())
						blocksWithReinforcedTint.put(block, customTint);
					else if (customTint != 0xFFFFFFFF)
						blocksWithCustomTint.put(block, customTint);
				}
				catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}

		blocksWithReinforcedTint.put(SCContent.BLOCK_POCKET_MANAGER.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithReinforcedTint.put(SCContent.BLOCK_POCKET_WALL.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CRYSTAL_QUARTZ_SLAB.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.SMOOTH_CRYSTAL_QUARTZ.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CHISELED_CRYSTAL_QUARTZ.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CRYSTAL_QUARTZ_BLOCK.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CRYSTAL_QUARTZ_BRICKS.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CRYSTAL_QUARTZ_PILLAR.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.CRYSTAL_QUARTZ_STAIRS.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.SMOOTH_CRYSTAL_QUARTZ_SLAB.get(), SCContent.CRYSTAL_QUARTZ_TINT);
		blocksWithCustomTint.put(SCContent.SMOOTH_CRYSTAL_QUARTZ_STAIRS.get(), SCContent.CRYSTAL_QUARTZ_TINT);
	}

	public static void onRegisterColorHandlersBlock() {
		initTint();
		blocksWithReinforcedTint.forEach((block, tint) -> ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			if (tintIndex == 0)
				return mixWithReinforcedTintIfEnabled(tint, safeGetBlockEntity(level, pos) instanceof IOwnable ownable ? ownable : null);
			else
				return 0xFFFFFFFF;
		}, block));
		blocksWithCustomTint.forEach((block, tint) -> ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			if (tintIndex == 0)
				return tint;
			else
				return 0xFFFFFFFF;
		}, block));

		BlockColor disguisableBlockColor = (state, level, pos, tintIndex) -> {
			Block block = state.getBlock();

			if (block instanceof IDisguisable disguisedBlock) {
				Block blockFromItem = Block.byItem(disguisedBlock.getDisguisedStack(level, pos).getItem());
				BlockState defaultBlockState = blockFromItem.defaultBlockState();

				if (!defaultBlockState.isAir() && !(blockFromItem instanceof IDisguisable))
					return Minecraft.getInstance().getBlockColors().getColor(defaultBlockState, level, pos, tintIndex);
			}

			if (block instanceof IReinforcedBlock)
				return mixWithReinforcedTintIfEnabled(0xFFFFFFFF, safeGetBlockEntity(level, pos) instanceof IOwnable ownable ? ownable : null);
			else
				return 0xFFFFFFFF;
		};

		ColorProviderRegistry.BLOCK.register(disguisableBlockColor, disguisableBlocks.get());
		ColorProviderRegistry.BLOCK.register(disguisableBlockColor, SCContent.SECURE_REDSTONE_INTERFACE.get());
		ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			if (tintIndex == 1 && !state.getValue(SnowyDirtBlock.SNOWY)) {
				int grassTint = level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.get(0.5D, 1.0D);

				return mixWithReinforcedTintIfEnabled(grassTint, safeGetBlockEntity(level, pos) instanceof IOwnable ownable ? ownable : null);
			}

			return mixWithReinforcedTintIfEnabled(0xFFFFFFFF, safeGetBlockEntity(level, pos) instanceof IOwnable ownable ? ownable : null);
		}, SCContent.REINFORCED_GRASS_BLOCK.get());
		ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			if (tintIndex == 1)
				return level != null && pos != null ? BiomeColors.getAverageWaterColor(level, pos) : -1;

			return mixWithReinforcedTintIfEnabled(0xFFFFFFFF, safeGetBlockEntity(level, pos) instanceof IOwnable ownable ? ownable : null);
		}, SCContent.REINFORCED_WATER_CAULDRON.get());
		ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			Direction direction = LaserFieldBlock.getFieldDirection(state);

			return iterateFields(level, pos, direction, ConfigHandler.SERVER.laserBlockRange.get(), SCContent.LASER_BLOCK.get(), LaserBlockBlockEntity.class::isInstance, be -> ((LaserBlockBlockEntity) be).getLensContainer().getItem(direction.getOpposite().ordinal()));
		}, SCContent.LASER_FIELD.get());
		ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> {
			Direction direction = state.getValue(InventoryScannerFieldBlock.FACING);

			return iterateFields(level, pos, direction, ConfigHandler.SERVER.inventoryScannerRange.get(), SCContent.INVENTORY_SCANNER.get(), InventoryScannerBlockEntity.class::isInstance, be -> ((InventoryScannerBlockEntity) be).getLensContainer().getItem(0));
		}, SCContent.INVENTORY_SCANNER_FIELD.get());
	}

	public static int iterateFields(BlockAndTintGetter level, BlockPos pos, Direction direction, int range, Block block, Predicate<BlockEntity> beTest, Function<BlockEntity, ItemStack> lensGetter) {
		if (level != null && pos != null) {
			try {
				return iterateFieldsInternal(level, pos, direction, range, block, beTest, lensGetter);
			}
			catch (Exception e1) {
				direction = direction.getOpposite();

				try {
					return iterateFieldsInternal(level, pos, direction, range, block, beTest, lensGetter);
				}
				catch (Exception e2) {}
			}
		}

		return -1;
	}

	private static int iterateFieldsInternal(BlockAndTintGetter level, BlockPos pos, Direction direction, int range, Block block, Predicate<BlockEntity> beTest, Function<BlockEntity, ItemStack> lensGetter) throws ArrayIndexOutOfBoundsException {
		MutableBlockPos mutablePos = new MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());

		for (int i = 0; i < range; i++) {
			if (level.getBlockState(mutablePos).is(block)) {
				BlockEntity be = level.getBlockEntity(mutablePos);

				if (beTest.test(be)) {
					ItemStack stack = lensGetter.apply(be);

					if (stack.has(DataComponents.DYED_COLOR))
						return stack.get(DataComponents.DYED_COLOR).rgb();

					break;
				}
			}

			mutablePos.move(direction);
		}

		return -1;
	}

	public static void onRegisterColorHandlersItem() {
		blocksWithReinforcedTint.forEach((item, tint) -> ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (tintIndex == 0)
				return mixWithReinforcedTintIfEnabled(tint, null);
			else
				return 0xFFFFFFFF;
		}, item));
		blocksWithCustomTint.forEach((item, tint) -> ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (tintIndex == 0)
				return tint;
			else
				return 0xFFFFFFFF;
		}, item));
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex > 0 ? -1 : DyedItemColor.getOrDefault(stack, 0xFF333333), SCContent.BRIEFCASE.get());
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex > 0 ? -1 : DyedItemColor.getOrDefault(stack, 0xFFFFFFFF), SCContent.LENS.get());
		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
			if (tintIndex == 1) {
				int grassTint = GrassColor.get(0.5D, 1.0D);

				return mixWithReinforcedTintIfEnabled(grassTint, null);
			}

			return ConfigHandler.CLIENT.reinforcedBlockTintColor.get();
		}, SCContent.REINFORCED_GRASS_BLOCK.get());
		blocksWithReinforcedTint = null;
		blocksWithCustomTint = null;
	}

	public static int mixWithReinforcedTintIfEnabled(int tint, IOwnable ownable) {
		if (ownable == null)
			return FastColor.ARGB32.multiply(tint, 0xFF000000 | TintMode.color());

		return TintMode.tint(Minecraft.getInstance().player, tint, ownable);
	}

	public static Player getClientPlayer() {
		return Minecraft.getInstance().player;
	}

	public static Level getClientLevel() {
		return Minecraft.getInstance().level;
	}

	public static void displayMRATScreen(ItemStack stack) {
		Minecraft.getInstance().setScreen(new MineRemoteAccessToolScreen(stack));
	}

	public static void displaySRATScreen(ItemStack stack) {
		Minecraft.getInstance().setScreen(new SentryRemoteAccessToolScreen(stack));
	}

	public static void displayEditModuleScreen(ItemStack stack) {
		Minecraft.getInstance().setScreen(new EditModuleScreen(stack));
	}

	public static void displayCameraMonitorScreen(ItemStack stack) {
		Minecraft.getInstance().setScreen(new CameraMonitorScreen(stack));
	}

	public static void displayFrameScreen(FrameBlockEntity be, boolean readOnly) {
		Minecraft.getInstance().setScreen(new FrameScreen(readOnly, be));
	}

	public static void displaySCManualScreen() {
		Minecraft.getInstance().setScreen(new SCManualScreen());
	}

	public static void displaySonicSecuritySystemScreen(SonicSecuritySystemBlockEntity be) {
		Minecraft.getInstance().setScreen(new SonicSecuritySystemScreen(be));
	}

	public static void displayBriefcasePasscodeScreen(Component title) {
		Minecraft.getInstance().setScreen(new BriefcasePasscodeScreen(title, false));
	}

	public static void displayBriefcaseSetupScreen(Component title) {
		Minecraft.getInstance().setScreen(new BriefcasePasscodeScreen(title, true));
	}

	public static void displayUsernameLoggerScreen(UsernameLoggerBlockEntity be) {
		Minecraft.getInstance().setScreen(new UsernameLoggerScreen(be));
	}

	public static void displayUniversalKeyChangerScreen(BlockEntity be) {
		Minecraft.getInstance().setScreen(new KeyChangerScreen((IPasscodeProtected) be));
	}

	public static void displayUniversalKeyChangerScreen(Entity entity) {
		Minecraft.getInstance().setScreen(new KeyChangerScreen((IPasscodeProtected) entity));
	}

	public static void displayCheckPasscodeScreen(BlockEntity be) {
		Component displayName = be instanceof Nameable nameable ? nameable.getDisplayName() : Component.translatable(be.getBlockState().getBlock().getDescriptionId());

		Minecraft.getInstance().setScreen(new CheckPasscodeScreen((IPasscodeProtected) be, displayName));
	}

	public static void displayCheckPasscodeScreen(Entity entity) {
		Minecraft.getInstance().setScreen(new CheckPasscodeScreen((IPasscodeProtected) entity, entity.getDisplayName()));
	}

	public static void displaySetPasscodeScreen(BlockEntity be) {
		Component displayName = be instanceof Nameable nameable ? nameable.getDisplayName() : Component.translatable(be.getBlockState().getBlock().getDescriptionId());

		Minecraft.getInstance().setScreen(new SetPasscodeScreen((IPasscodeProtected) be, displayName));
	}

	public static void displaySetPasscodeScreen(Entity entity) {
		Minecraft.getInstance().setScreen(new SetPasscodeScreen((IPasscodeProtected) entity, entity.getDisplayName()));
	}

	public static void displaySSSItemScreen(ItemStack stack) {
		Minecraft.getInstance().setScreen(new SSSItemScreen(stack));
	}

	public static void displayRiftStabilizerScreen(RiftStabilizerBlockEntity be) {
		Minecraft.getInstance().setScreen(new RiftStabilizerScreen(be));
	}

	public static void displaySecureRedstoneInterfaceScreen(SecureRedstoneInterfaceBlockEntity be) {
		Minecraft.getInstance().setScreen(new SecureRedstoneInterfaceScreen(be));
	}

	public static void displayAlarmScreen(AlarmBlockEntity be) {
		Minecraft.getInstance().setScreen(new AlarmScreen(be, be.getSound().getLocation()));
	}

	public static void refreshModelData(BlockEntity be) {
		BlockPos pos = be.getBlockPos();

		//PORT-NOTE: NeoForge caches model data per position and needs it to be requested and recalculated here. Fabric's renderer
		//reads the block entity's render data whenever the section is rebuilt, so recompiling the section is enough
		if (be instanceof IBlockEntityExtension blockEntityExtension)
			blockEntityExtension.requestModelDataUpdate();

		ClientUtils.recompileChunk(pos); //Recompiles the render chunk at the changed position
	}

	public static boolean isPlayerMountedOnCamera() {
		return Minecraft.getInstance().cameraEntity instanceof SecurityCamera;
	}

	public static void putDisguisedBeRenderer(BlockEntity disguisableBlockEntity, ItemStack stack) {
		DISGUISED_BLOCK_RENDER_DELEGATE.putDelegateFor(disguisableBlockEntity, stack.getOrDefault(SCContent.SAVED_BLOCK_STATE, SavedBlockState.EMPTY).state(), stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne());
	}

	public static void updateBlockColorAroundPosition(BlockPos pos) {
		Minecraft.getInstance().levelRenderer.blockChanged(Minecraft.getInstance().level, pos, null, null, 0);
	}

	public static ShaderInstance getFrameFeedShader() {
		return frameFeedShader;
	}

	/**
	 * PORT-NOTE: On NeoForge, the captured texture getter was needed to bake the powered secure redstone interface models with a
	 * rotation. On Fabric, they are baked with the baker of Fabric's model loading API instead
	 * ({@link #onModelModifyBakingResult}), so this is unused. It is kept for mixin.sri.ModelManagerMixin, which still calls it.
	 */
	public static ModelBakery.TextureGetter getTextureGetter() {
		return textureGetter;
	}

	public static void setTextureGetter(Map<ResourceLocation, StitchResult> stitchResults) {
		textureGetter = (debugName, material) -> {
			AtlasSet.StitchResult stitchResult = stitchResults.get(material.atlasLocation());
			TextureAtlasSprite sprite = stitchResult.getSprite(material.texture());

			if (sprite != null)
				return sprite;
			else
				return stitchResult.missing();
		};
	}

	private static BlockModelRotation modelRotationFromDirection(Direction direction) {
		return switch (direction) {
			case DOWN -> BlockModelRotation.X180_Y0;
			case UP -> BlockModelRotation.X0_Y0;
			case NORTH -> BlockModelRotation.X90_Y0;
			case SOUTH -> BlockModelRotation.X90_Y180;
			case WEST -> BlockModelRotation.X90_Y270;
			case EAST -> BlockModelRotation.X90_Y90;
		};
	}

	private static BlockEntity safeGetBlockEntity(BlockAndTintGetter level, BlockPos pos) {
		if (level != null && pos != null)
			return level.getBlockEntity(pos);
		else
			return null;
	}
}
