package net.geforcemods.securitycraft.fabric.items;

import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Fabric stand-in for NeoForge's VanillaHopperItemHandler.
 */
public class VanillaHopperItemHandler extends InvWrapper {
	// PORT-NOTE: NeoForge's version also starts the hopper's 8 tick transfer cooldown when an item is inserted into an
	// empty hopper. That is not done here, so items move into reinforced hoppers through other mods' pipes slightly faster.
	public VanillaHopperItemHandler(HopperBlockEntity hopper) {
		super(hopper);
	}
}
