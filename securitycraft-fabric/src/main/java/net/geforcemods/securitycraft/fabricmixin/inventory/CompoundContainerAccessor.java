package net.geforcemods.securitycraft.fabricmixin.inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;

/**
 * Lets SecurityCraft's item storages expose the two halves of a double keypad chest separately, the way Fabric's own
 * double chest storage does, so that every container slot has exactly one Transfer API wrapper.
 */
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {
	@Accessor("container1")
	Container securitycraft$getContainer1();

	@Accessor("container2")
	Container securitycraft$getContainer2();
}
