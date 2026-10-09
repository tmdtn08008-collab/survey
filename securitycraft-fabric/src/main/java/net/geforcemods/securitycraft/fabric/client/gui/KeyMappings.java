package net.geforcemods.securitycraft.fabric.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Replacement for the key matching method NeoForge adds to KeyMapping (isActiveAndMatches). Fabric has no key conflict
 * contexts or key modifiers, so a mapping matches a key when it is bound to exactly that key.
 */
public final class KeyMappings {
	private KeyMappings() {}

	public static boolean isActiveAndMatches(KeyMapping keyMapping, InputConstants.Key key) {
		return key != InputConstants.UNKNOWN && key.equals(KeyBindingHelper.getBoundKeyOf(keyMapping));
	}
}
