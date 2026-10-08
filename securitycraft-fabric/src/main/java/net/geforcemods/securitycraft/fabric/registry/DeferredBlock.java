package net.geforcemods.securitycraft.fabric.registry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/**
 * Fabric stand-in for NeoForge's DeferredBlock.
 */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
	protected DeferredBlock(ResourceKey<Block> key) {
		super(key);
	}

	public ItemStack toStack() {
		return toStack(1);
	}

	public ItemStack toStack(int count) {
		return new ItemStack(this, count);
	}

	@Override
	public Item asItem() {
		return get().asItem();
	}
}
