package net.geforcemods.securitycraft;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.geforcemods.securitycraft.blockentities.BlockChangeDetectorBlockEntity;
import net.geforcemods.securitycraft.blockentities.BlockChangeDetectorBlockEntity.ChangeEntry;
import net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity;
import net.geforcemods.securitycraft.blocks.SecurityCameraBlock;
import net.geforcemods.securitycraft.misc.BlockEntityTracker;
import net.geforcemods.securitycraft.misc.CameraRedstoneModuleState;
import net.geforcemods.securitycraft.misc.KeyBindings;
import net.geforcemods.securitycraft.misc.ModuleType;
import net.geforcemods.securitycraft.misc.StillValid;
import net.geforcemods.securitycraft.util.Utils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SCClientEventHandler {
	public static final ResourceLocation BACKGROUND_SPRITE = SecurityCraft.resLoc("hud/camera/background");
	public static final ResourceLocation LIVE_SPRITE = SecurityCraft.resLoc("hud/camera/live");
	public static final ResourceLocation NIGHT_VISION_INACTIVE_SPRITE = SecurityCraft.resLoc("hud/camera/night_vision_inactive");
	public static final ResourceLocation REDSTONE_MODULE_NOT_PRESENT_SPRITE = SecurityCraft.resLoc("hud/camera/redstone_module_not_present");
	public static final ResourceLocation REDSTONE_MODULE_PRESENT_SPRITE = SecurityCraft.resLoc("hud/camera/redstone_module_present");
	public static final ResourceLocation NIGHT_VISION = SecurityCraft.mcResLoc("textures/mob_effect/night_vision.png");
	public static final ItemStack REDSTONE = new ItemStack(Items.REDSTONE);
	private static final Component REDSTONE_NOTE = Utils.localize("gui.securitycraft:camera.toggleRedstoneNote");
	private static final Component SMART_MODULE_NOTE = Utils.localize("gui.securitycraft:camera.smartModuleNote");
	//@formatter:off
	private static final CameraKeyInfoEntry[] CAMERA_KEY_INFO_LIST = {
			new CameraKeyInfoEntry(() -> true, options -> Utils.localize("gui.securitycraft:camera.lookAround", options.keyUp.getTranslatedKeyMessage(), options.keyLeft.getTranslatedKeyMessage(), options.keyDown.getTranslatedKeyMessage(), options.keyRight.getTranslatedKeyMessage()), $ -> true),
			new CameraKeyInfoEntry(() -> true, options -> Utils.localize("gui.securitycraft:camera.exit", options.keyShift.getTranslatedKeyMessage()), $ -> true),
			new CameraKeyInfoEntry(() -> true, $ -> Utils.localize("gui.securitycraft:camera.zoom", KeyBindings.cameraZoomIn.getTranslatedKeyMessage(), KeyBindings.cameraZoomOut.getTranslatedKeyMessage()), $ -> true),
			new CameraKeyInfoEntry(ConfigHandler.SERVER.allowCameraNightVision::get, $ -> Utils.localize("gui.securitycraft:camera.activateNightVision", KeyBindings.cameraActivateNightVision.getTranslatedKeyMessage()), $ -> true),
			new CameraKeyInfoEntry(() -> true, $ -> Utils.localize("gui.securitycraft:camera.toggleRedstone", KeyBindings.cameraEmitRedstone.getTranslatedKeyMessage()), be -> be.isModuleEnabled(ModuleType.REDSTONE)),
			new CameraKeyInfoEntry(() -> true, $ -> REDSTONE_NOTE, be -> be.isModuleEnabled(ModuleType.REDSTONE)),
			new CameraKeyInfoEntry(() -> true, $ -> Utils.localize("gui.securitycraft:camera.setDefaultViewingDirection", KeyBindings.setDefaultViewingDirection.getTranslatedKeyMessage()), be -> be.isModuleEnabled(ModuleType.SMART)),
			new CameraKeyInfoEntry(() -> true, $ -> SMART_MODULE_NOTE, be -> be.isModuleEnabled(ModuleType.SMART))
	};
	//@formatter:on
	private static final List<DataComponentType<? extends TooltipProvider>> COMPONENTS_WITH_GLOBAL_TOOLTIP = List.of(SCContent.OWNER_DATA, SCContent.NOTES);
	private static float cameraInfoMessageTime;

	private SCClientEventHandler() {}

	/**
	 * Registers the Fabric API listeners that replace NeoForge's @EventBusSubscriber registration of this class. Called once
	 * from {@link ClientHandler#init()}.
	 * <p>
	 * PORT-NOTE: The camera parts of this handler (ChunkEvent.Unload, LevelEvent.Unload and InputEvent.InteractionKeyMappingTriggered
	 * while viewing a camera) moved to entity.camera.CameraClientEvents and the camera mixins, which call them where NeoForge
	 * fires those events. RenderHandEvent is replaced by fabricmixin.client.ItemInHandRendererMixin, which asks
	 * {@link #shouldRenderHand()}.
	 */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> onClientTickPost());
		//PORT-NOTE: NeoForge's AFTER_TRIPWIRE_BLOCKS stage has no Fabric equivalent. AFTER_TRANSLUCENT runs a bit later (after
		//particles), which does not matter for these lines, as they are drawn into the outline target
		WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> onRenderLevelStage(context.camera()));
		ItemTooltipCallback.EVENT.register(SCClientEventHandler::onItemTooltip);
	}

	public static void onClientTickPost() {
		Minecraft mc = Minecraft.getInstance();

		if (cameraInfoMessageTime >= 0)
			cameraInfoMessageTime--;

		if (mc != null && mc.screen instanceof StillValid screen && !screen.stillValid(mc.player))
			mc.setScreen(null);
	}

	public static void onRenderLevelStage(Camera camera) {
		if (camera != null) {
			Vec3 camPos = camera.getPosition();
			PoseStack pose = new PoseStack();
			Minecraft mc = Minecraft.getInstance();
			Level level = mc.level;
			VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(ClientHandler.OVERLAY_LINES);

			for (BlockPos bcdPos : BlockEntityTracker.BLOCK_CHANGE_DETECTOR.getTrackedBlockEntities(level)) {
				BlockEntity be = level.getBlockEntity(bcdPos);

				if (be instanceof BlockChangeDetectorBlockEntity bcd && bcd.isShowingHighlights() && bcd.isOwnedBy(mc.player)) {
					int packedColor = bcd.getColor();
					float r = FastColor.ARGB32.red(packedColor) / 255.0F;
					float g = FastColor.ARGB32.green(packedColor) / 255.0F;
					float b = FastColor.ARGB32.blue(packedColor) / 255.0F;

					for (ChangeEntry changeEntry : bcd.getFilteredEntries()) {
						BlockPos pos = changeEntry.pos();

						pose.pushPose();
						pose.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
						LevelRenderer.renderLineBox(pose, consumer, 0, 0, 0, 1, 1, 1, r, g, b, 1.0F);
						pose.popPose();
					}
				}
			}

			mc.renderBuffers().bufferSource().endBatch();
		}
	}

	/**
	 * Replaces the RenderHandEvent subscription, which canceled the event while viewing through a security camera
	 *
	 * @return false if the first person hands should not be rendered
	 */
	public static boolean shouldRenderHand() {
		return !ClientHandler.isPlayerMountedOnCamera();
	}

	public static void onItemTooltip(ItemStack stack, TooltipContext ctx, TooltipFlag flag, List<Component> tooltip) {
		int nextIndex = 0;

		for (int i = 0; i < COMPONENTS_WITH_GLOBAL_TOOLTIP.size(); i++) {
			TooltipProvider tooltipProvider = stack.get(COMPONENTS_WITH_GLOBAL_TOOLTIP.get(i));

			if (tooltipProvider != null) {
				final int index = nextIndex++ + 1; //add in order of global tooltip components list, after item name

				tooltipProvider.addToTooltip(ctx, line -> {
					if (index >= tooltip.size())
						tooltip.add(line);
					else
						tooltip.add(index, line);
				}, flag);
			}
		}
	}

	public static void cameraOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		BlockPos pos = mc.cameraEntity.blockPosition();
		Window window = mc.getWindow();
		int scaledWidth = window.getGuiScaledWidth();
		int scaledHeight = window.getGuiScaledHeight();

		if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen())
			return;

		if (!(level.getBlockEntity(pos) instanceof SecurityCameraBlockEntity be))
			return;

		Font font = Minecraft.getInstance().font;
		Options options = Minecraft.getInstance().options;
		BlockState state = level.getBlockState(pos);
		long dayTime = Minecraft.getInstance().level.getDayTime();
		int hours24 = (int) ((float) dayTime / 1000L + 6L) % 24;
		int hours = hours24 % 12;
		int minutes = (int) (dayTime / 16.666666F % 60.0F);
		String time = String.format("%02d:%02d %s", Integer.valueOf(hours < 1 ? 12 : hours), Integer.valueOf(minutes), hours24 < 12 ? "AM" : "PM");
		int timeY = 25;

		if (be.hasCustomName()) {
			Component cameraName = be.getCustomName();

			guiGraphics.drawString(font, cameraName, scaledWidth - font.width(cameraName) - 8, 25, 0xFFFFFF, true);
			timeY += 10;
		}

		guiGraphics.drawString(font, time, scaledWidth - font.width(time) - 4, timeY, 0xFFFFFF, true);

		if (cameraInfoMessageTime >= 0) {
			float fadeOutPartialTick = Math.max(cameraInfoMessageTime + 1.0F - deltaTracker.getGameTimeDeltaPartialTick(false), 1.0F);
			int alpha = (int) Math.ceil(255.0F * Math.min(20.0F, fadeOutPartialTick) / 20.0F);
			int heightOffset = 10;

			for (int i = CAMERA_KEY_INFO_LIST.length - 1; i >= 0; i--) {
				CameraKeyInfoEntry entry = CAMERA_KEY_INFO_LIST[i];

				if (entry.enabled().get()) {
					entry.drawString(options, guiGraphics, font, scaledWidth, scaledHeight, heightOffset, be, alpha);
					heightOffset += 10;
				}
			}
		}

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		guiGraphics.blitSprite(BACKGROUND_SPRITE, 5, 0, 90, 20);
		guiGraphics.blitSprite(LIVE_SPRITE, window.getGuiScaledWidth() - 70, 5, 65, 16);

		if (!mc.player.hasEffect(MobEffects.NIGHT_VISION))
			guiGraphics.blitSprite(NIGHT_VISION_INACTIVE_SPRITE, 28, 4, 16, 9);
		else
			guiGraphics.blit(NIGHT_VISION, 27, -1, 0, 0, 18, 18, 18, 18);

		if (state.getSignal(level, pos, state.getValue(SecurityCameraBlock.FACING)) == 0) {
			if (!be.isModuleEnabled(ModuleType.REDSTONE))
				CameraRedstoneModuleState.NOT_INSTALLED.render(guiGraphics, 12, 2);
			else
				CameraRedstoneModuleState.DEACTIVATED.render(guiGraphics, 12, 2);
		}
		else
			CameraRedstoneModuleState.ACTIVATED.render(guiGraphics, 12, 2);
	}

	public static void resetCameraInfoMessageTime() {
		cameraInfoMessageTime = 200;
	}

	public record CameraKeyInfoEntry(Supplier<Boolean> enabled, Function<Options, Component> text, Predicate<SecurityCameraBlockEntity> whiteText) {
		public void drawString(Options options, GuiGraphics guiGraphics, Font font, int scaledWidth, int scaledHeight, int heightOffset, SecurityCameraBlockEntity be, int alpha) {
			Component text = text().apply(options);
			int textColor = whiteText().test(be) ? 0xFFFFFF : 0xFF3377;

			guiGraphics.drawString(font, text, scaledWidth - font.width(text) - 8, scaledHeight - heightOffset, textColor + (alpha << 24), true);
		}
	}
}
