package net.geforcemods.securitycraft.misc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Keeps track of the HUD layers SecurityCraft hides (vanilla ones while viewing a camera) or shows (its camera overlay).
 * PORT-NOTE: On NeoForge, this class subscribed to RenderGuiLayerEvent.Pre and canceled disabled layers. Fabric has no named HUD
 * layers, so the layers check {@link #isDisabled} themselves: the vanilla ones in fabricmixin.camera.GuiMixin, and
 * SecurityCraft's camera overlay in its HUD callback (ClientHandler#registerGuiLayers).
 */
public class LayerToggleHandler {
	private static final List<ResourceLocation> DISABLED_LAYERS = new ArrayList<>();

	private LayerToggleHandler() {}

	public static boolean isDisabled(ResourceLocation layer) {
		return DISABLED_LAYERS.contains(layer);
	}

	public static void enable(ResourceLocation layer) {
		DISABLED_LAYERS.remove(layer);
	}

	public static void disable(ResourceLocation layer) {
		if (!isDisabled(layer))
			DISABLED_LAYERS.add(layer);
	}
}
