package net.geforcemods.securitycraft.fabric.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Text ("foreground") colors for widgets, replacing the methods NeoForge adds to AbstractWidget
 * (setFGColor/getFGColor/clearFGColor). The color is stored on the widget by a mixin, and buttons
 * (AbstractButton#renderWidget) draw their message with it when one is set, like on NeoForge.
 */
public final class WidgetColors {
	public static final int UNSET_FG_COLOR = -1;

	private WidgetColors() {}

	public static void setFGColor(AbstractWidget widget, int color) {
		((Holder) widget).securitycraft$setPackedFGColor(color);
	}

	/**
	 * @return The color that was set for this widget, or the default text color (white if active, light gray otherwise)
	 */
	public static int getFGColor(AbstractWidget widget) {
		int color = ((Holder) widget).securitycraft$getPackedFGColor();

		if (color != UNSET_FG_COLOR)
			return color;

		return widget.active ? 0xFFFFFF : 0xA0A0A0;
	}

	public static void clearFGColor(AbstractWidget widget) {
		setFGColor(widget, UNSET_FG_COLOR);
	}

	/**
	 * Implemented by every {@link AbstractWidget} through a SecurityCraft mixin.
	 */
	public interface Holder {
		int securitycraft$getPackedFGColor();

		void securitycraft$setPackedFGColor(int color);
	}
}
