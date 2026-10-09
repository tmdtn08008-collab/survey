package net.geforcemods.securitycraft.fabric.block;

/**
 * Fabric stand-in for NeoForge's BubbleColumnDirection: the direction of the bubble column a block creates in the water
 * above it. See {@link BubbleColumnBlockHook}.
 */
public enum BubbleColumnDirection {
	/**
	 * Pushes entities upwards, like soul sand
	 */
	UPWARD,
	/**
	 * Pulls entities downwards, like a magma block
	 */
	DOWNWARD,
	/**
	 * Does not create a bubble column
	 */
	NONE
}
