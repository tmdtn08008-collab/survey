package net.geforcemods.securitycraft.network.client;

import net.geforcemods.securitycraft.ClientHandler;
import net.geforcemods.securitycraft.SCClientEventHandler;
import net.geforcemods.securitycraft.SecurityCraft;
import net.geforcemods.securitycraft.entity.camera.CameraController;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.geforcemods.securitycraft.misc.LayerToggleHandler;
import net.geforcemods.securitycraft.util.Utils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.geforcemods.securitycraft.fabric.network.IPayloadContext;

public record SetCameraView(int id) implements CustomPacketPayload {
	public static final Type<SetCameraView> TYPE = new Type<>(SecurityCraft.resLoc("set_camera_view"));
	//@formatter:off
	public static final StreamCodec<RegistryFriendlyByteBuf, SetCameraView> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetCameraView::id,
			SetCameraView::new);
	//@formatter:on

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(IPayloadContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		Entity entity = mc.level.getEntity(id);
		boolean isMountingCamera = entity instanceof SecurityCamera;

		if (isMountingCamera || entity instanceof Player) {
			mc.setCameraEntity(entity);

			if (isMountingCamera) {
				CameraController.setCameraMountedTimestamp();
				CameraController.previousCameraType = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.gui.setOverlayMessage(Utils.localize("mount.onboard", mc.options.keyShift.getTranslatedKeyMessage()), false);
				LayerToggleHandler.disable(VanillaGuiLayers.JUMP_METER);
				LayerToggleHandler.disable(VanillaGuiLayers.EXPERIENCE_BAR);
				LayerToggleHandler.disable(VanillaGuiLayers.EXPERIENCE_LEVEL);
				LayerToggleHandler.disable(VanillaGuiLayers.EFFECTS);
				LayerToggleHandler.enable(ClientHandler.CAMERA_LAYER);
				SCClientEventHandler.resetCameraInfoMessageTime();
			}
			else {
				if (CameraController.previousCameraType != null)
					mc.options.setCameraType(CameraController.previousCameraType);

				CameraController.resetOverlaysAfterDismount = true;
			}

			mc.levelRenderer.allChanged();
		}
	}

	// PORT-NOTE: NeoForge's VanillaGuiLayers does not exist on Fabric. These are the same ids NeoForge gives the vanilla HUD
	// layers. LayerToggleHandler only stores them; the Gui mixins of the HUD port must check them for these layers to actually
	// be hidden. Replace this class with a shared shim (and use it in CameraController too) once one exists.
	private static final class VanillaGuiLayers {
		private static final ResourceLocation JUMP_METER = ResourceLocation.withDefaultNamespace("jump_meter");
		private static final ResourceLocation EXPERIENCE_BAR = ResourceLocation.withDefaultNamespace("experience_bar");
		private static final ResourceLocation EXPERIENCE_LEVEL = ResourceLocation.withDefaultNamespace("experience_level");
		private static final ResourceLocation EFFECTS = ResourceLocation.withDefaultNamespace("effects");

		private VanillaGuiLayers() {}
	}
}
