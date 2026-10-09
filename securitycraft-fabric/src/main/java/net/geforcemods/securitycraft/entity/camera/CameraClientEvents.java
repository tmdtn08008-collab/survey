package net.geforcemods.securitycraft.entity.camera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.geforcemods.securitycraft.ClientHandler;
import net.geforcemods.securitycraft.SCContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Fabric wiring for the client-side camera behavior that NeoForge delivered through its event bus (ClientTickEvent,
 * ChunkEvent.Unload, InputEvent.InteractionKeyMappingTriggered, LevelEvent.Unload, ScreenshotEvent). The Fabric API events
 * are registered by {@link #register()}, which the client entrypoint has to call once. The events Fabric API has no
 * equivalent for are fired by the client mixins in net.geforcemods.securitycraft.fabricmixin.camera, which call the hooks
 * below. (RenderHandEvent, which hides the hands while mounted, is replaced by fabricmixin.client.ItemInHandRendererMixin.)
 */
public final class CameraClientEvents {
	private CameraClientEvents() {}

	/**
	 * Registers the camera's Fabric API event listeners. Must be called exactly once, from the client entrypoint.
	 */
	public static void register() {
		//Lambdas rather than method references, so that CameraController is only initialized on the first tick: its static
		//initializer reads Minecraft#options, which does not exist yet while client entrypoints run
		ClientTickEvents.START_CLIENT_TICK.register(mc -> CameraController.onClientTickPre());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			CameraController.onClientTickPost();
			FrameFeedHandler.onClientTickPost();
		});
		ClientChunkEvents.CHUNK_UNLOAD.register(CameraClientEvents::onChunkUnload);
		//Replaces the attack part of InputEvent.InteractionKeyMappingTriggered: while mounted, neither starting nor continuing an
		//attack does anything, and the hand does not swing. Fabric checks this for both Minecraft#startAttack and #continueAttack.
		ClientPreAttackCallback.EVENT.register((mc, player, clickCount) -> ClientHandler.isPlayerMountedOnCamera());
	}

	/**
	 * Replaces SCClientEventHandler#onChunkUnload (ChunkEvent.Unload on the client): releases frame feed render sections of
	 * unloaded chunks. Also receives the unloads that {@link CameraClientChunkCacheExtension} fires for its own chunks.
	 */
	public static void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();

		CameraViewAreaExtension.onChunkUnload(pos.x, pos.z);
	}

	/**
	 * Replaces SCClientEventHandler#onLevelUnload (LevelEvent.Unload on the client). Called by
	 * fabricmixin.camera.MinecraftMixin at the three places NeoForge posts that event: Minecraft#setLevel,
	 * Minecraft#disconnect and Minecraft#clearClientLevel, each time only if there is a level to unload.
	 */
	public static void onLevelUnload() {
		FrameFeedHandler.removeAllFeeds();
		CameraClientChunkCacheExtension.clear();
		CameraViewAreaExtension.clear();
	}

	/**
	 * Replaces the use part of SCClientEventHandler#onClickInput (InputEvent.InteractionKeyMappingTriggered with the use key).
	 * Called by fabricmixin.camera.MinecraftMixin at the start of each hand's iteration in Minecraft#startUseItem, which is
	 * where NeoForge fires that event. While mounted, using items or blocks is canceled without swinging the hand, except that
	 * the Camera Monitor can be used to reopen the monitor screen. As upstream, the event is canceled for the main hand
	 * already, so the off hand is never looked at.
	 *
	 * @return true if Minecraft#startUseItem should stop
	 */
	public static boolean onUseInput(InteractionHand hand) {
		if (ClientHandler.isPlayerMountedOnCamera()) {
			Minecraft mc = Minecraft.getInstance();

			if (mc.player.getItemInHand(hand).is(SCContent.CAMERA_MONITOR.get()))
				SCContent.CAMERA_MONITOR.get().use(mc.level, mc.player, hand);

			return true;
		}

		return false;
	}

	/**
	 * Replaces the pick block part of SCClientEventHandler#onClickInput (InputEvent.InteractionKeyMappingTriggered with the
	 * pick block key). Called by fabricmixin.camera.MinecraftMixin at the start of Minecraft#pickBlock.
	 *
	 * @return true if Minecraft#pickBlock should stop
	 */
	public static boolean onPickBlockInput() {
		return ClientHandler.isPlayerMountedOnCamera();
	}

	/**
	 * Replaces CameraController#onScreenshot's ScreenshotEvent subscription. Called by fabricmixin.camera.ScreenshotMixin
	 * where NeoForge posts ScreenshotEvent.
	 */
	public static void onScreenshot() {
		CameraController.onScreenshot();
	}
}
