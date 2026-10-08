package net.geforcemods.securitycraft.fabric.util;

/**
 * Fabric stand-in for NeoForge's TriState.
 */
public enum TriState {
	TRUE,
	DEFAULT,
	FALSE;

	public boolean isTrue() {
		return this == TRUE;
	}

	public boolean isDefault() {
		return this == DEFAULT;
	}

	public boolean isFalse() {
		return this == FALSE;
	}
}
