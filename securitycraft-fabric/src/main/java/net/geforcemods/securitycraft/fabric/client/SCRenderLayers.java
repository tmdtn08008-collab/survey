package net.geforcemods.securitycraft.fabric.client;

import java.util.List;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.geforcemods.securitycraft.SecurityCraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * NeoForge reads a block's render layer from the "render_type" field of its block models, which Fabric ignores. This
 * registers the same layers through Fabric API. The lists were generated from the "render_type" fields of the models that
 * each blockstate file references; regenerate them if models change.
 */
public final class SCRenderLayers {
	private static final List<String> CUTOUT = List.of(
			"cage_trap", "iron_door_reinforced", "keypad_blast_furnace", "keypad_door", "keypad_furnace", "keypad_smoker", "keypad_trapdoor",
			"reinforced_cobweb", "reinforced_copper_grate", "reinforced_exposed_copper_grate", "reinforced_glass", "reinforced_iron_trapdoor",
			"reinforced_ladder", "reinforced_lantern", "reinforced_lightning_rod", "reinforced_oxidized_copper_grate", "reinforced_scaffolding",
			"reinforced_soul_lantern", "reinforced_weathered_copper_grate", "retinal_scanner", "scanner_door", "scanner_trapdoor",
			"secure_trading_station", "track_mine");
	private static final List<String> CUTOUT_MIPPED = List.of(
			"block_pocket_manager", "horizontal_reinforced_iron_bars", "reinforced_chain", "reinforced_glass_pane", "reinforced_grass_block",
			"reinforced_hopper", "reinforced_iron_bars", "reinforced_mycelium", "reinforced_podzol");
	private static final List<String> TRANSLUCENT = List.of(
			"block_pocket_wall", "floor_trap", "inventory_scanner_field", "laser", "reinforced_black_stained_glass",
			"reinforced_black_stained_glass_pane", "reinforced_blue_stained_glass", "reinforced_blue_stained_glass_pane",
			"reinforced_brown_stained_glass", "reinforced_brown_stained_glass_pane", "reinforced_cyan_stained_glass",
			"reinforced_cyan_stained_glass_pane", "reinforced_gray_stained_glass", "reinforced_gray_stained_glass_pane",
			"reinforced_green_stained_glass", "reinforced_green_stained_glass_pane", "reinforced_ice", "reinforced_light_blue_stained_glass",
			"reinforced_light_blue_stained_glass_pane", "reinforced_light_gray_stained_glass", "reinforced_light_gray_stained_glass_pane",
			"reinforced_lime_stained_glass", "reinforced_lime_stained_glass_pane", "reinforced_magenta_stained_glass",
			"reinforced_magenta_stained_glass_pane", "reinforced_orange_stained_glass", "reinforced_orange_stained_glass_pane",
			"reinforced_pink_stained_glass", "reinforced_pink_stained_glass_pane", "reinforced_purple_stained_glass",
			"reinforced_purple_stained_glass_pane", "reinforced_red_stained_glass", "reinforced_red_stained_glass_pane", "reinforced_tinted_glass",
			"reinforced_white_stained_glass", "reinforced_white_stained_glass_pane", "reinforced_yellow_stained_glass",
			"reinforced_yellow_stained_glass_pane");

	private SCRenderLayers() {}

	public static void register() {
		register(CUTOUT, RenderType.cutout());
		register(CUTOUT_MIPPED, RenderType.cutoutMipped());
		register(TRANSLUCENT, RenderType.translucent());
	}

	private static void register(List<String> blockNames, RenderType renderType) {
		for (String name : blockNames) {
			BlockRenderLayerMap.INSTANCE.putBlock(BuiltInRegistries.BLOCK.get(SecurityCraft.resLoc(name)), renderType);
		}
	}
}
