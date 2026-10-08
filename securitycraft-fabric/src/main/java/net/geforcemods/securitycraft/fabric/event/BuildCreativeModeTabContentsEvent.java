package net.geforcemods.securitycraft.fabric.event;

import java.util.Collection;
import java.util.List;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's BuildCreativeModeTabContentsEvent, wrapping Fabric's item group entries.
 */
public class BuildCreativeModeTabContentsEvent {
	private final CreativeModeTab tab;
	private final FabricItemGroupEntries entries;

	public BuildCreativeModeTabContentsEvent(CreativeModeTab tab, FabricItemGroupEntries entries) {
		this.tab = tab;
		this.entries = entries;
	}

	public ResourceKey<CreativeModeTab> getTabKey() {
		return BuiltInRegistries.CREATIVE_MODE_TAB.getResourceKey(tab).orElseThrow();
	}

	public CreativeModeTab getTab() {
		return tab;
	}

	public CreativeModeTab.ItemDisplayParameters getParameters() {
		return entries.getContext();
	}

	public void insertAfter(ItemStack existing, ItemStack toInsert, TabVisibility visibility) {
		entries.addAfter(existing, List.of(toInsert), visibility);
	}

	public void insertBefore(ItemStack existing, ItemStack toInsert, TabVisibility visibility) {
		entries.addBefore(existing, List.of(toInsert), visibility);
	}

	public void accept(ItemStack stack, TabVisibility visibility) {
		entries.accept(stack, visibility);
	}

	public void accept(ItemStack stack) {
		entries.accept(stack);
	}

	public void acceptAll(Collection<ItemStack> stacks, TabVisibility visibility) {
		entries.acceptAll(stacks, visibility);
	}

	public void acceptAll(Collection<ItemStack> stacks) {
		entries.acceptAll(stacks);
	}
}
