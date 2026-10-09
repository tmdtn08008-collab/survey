package net.geforcemods.securitycraft.fabric.client.gui;

import java.text.DecimalFormat;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A slider with a real value range, an optional step size and a "prefix value suffix" label. Fabric replacement for the
 * slider widget NeoForge ships, with the same public and protected API. {@link #value} (inherited) stays the position of the
 * handle between 0 and 1, while {@link #getValue()} is the value in the range from {@link #minValue} to {@link #maxValue}.
 */
public class ExtendedSlider extends AbstractSliderButton {
	protected Component prefix;
	protected Component suffix;
	protected double minValue;
	protected double maxValue;
	protected double stepSize;
	protected boolean drawString;
	private final DecimalFormat format;

	public ExtendedSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, boolean drawString) {
		this(x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, 1.0D, 0, drawString);
	}

	/**
	 * @param stepSize The distance between two selectable values, or 0 for a continuous slider
	 * @param precision The amount of decimal places shown for a continuous slider (at most 4)
	 */
	public ExtendedSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, double stepSize, int precision, boolean drawString) {
		super(x, y, width, height, Component.empty(), 0.0D);
		this.prefix = prefix;
		this.suffix = suffix;
		this.minValue = minValue;
		this.maxValue = maxValue;
		this.stepSize = Math.abs(stepSize);
		this.drawString = drawString;
		format = createFormat(this.stepSize, precision);
		value = toSnappedFraction((currentValue - minValue) / (maxValue - minValue));
		updateMessage();
	}

	private static DecimalFormat createFormat(double stepSize, int precision) {
		if (stepSize == 0.0D) {
			int decimals = Math.min(precision, 4);

			return new DecimalFormat(decimals > 0 ? "0." + "0".repeat(decimals) : "0");
		}
		else if (Mth.equal(stepSize, Math.floor(stepSize)))
			return new DecimalFormat("0");
		else //as many decimal places as the step size has
			return new DecimalFormat(Double.toString(stepSize).replaceAll("\\d", "0"));
	}

	/**
	 * @return The current value, between {@link #minValue} and {@link #maxValue}
	 */
	public double getValue() {
		return minValue + value * (maxValue - minValue);
	}

	public long getValueLong() {
		return Math.round(getValue());
	}

	public int getValueInt() {
		return (int) getValueLong();
	}

	/**
	 * Sets the value of this slider. The value is snapped to the step size and clamped to the slider's range.
	 */
	public void setValue(double value) {
		setFraction((value - minValue) / (maxValue - minValue));
	}

	public String getValueString() {
		return format.format(getValue());
	}

	/**
	 * Stores a text color for this slider. Like on NeoForge, the slider's own rendering does not use it.
	 */
	public void setFGColor(int color) {
		WidgetColors.setFGColor(this, color);
	}

	public int getFGColor() {
		return WidgetColors.getFGColor(this);
	}

	public void clearFGColor() {
		WidgetColors.clearFGColor(this);
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setFractionFromMouse(mouseX);
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
		setFractionFromMouse(mouseX);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
			boolean towardsMin = keyCode == GLFW.GLFW_KEY_LEFT;

			if (minValue > maxValue)
				towardsMin = !towardsMin;

			double direction = towardsMin ? -1.0D : 1.0D;

			if (stepSize <= 0.0D)
				setFraction(value + direction / (width - 8));
			else
				setValue(getValue() + direction * stepSize);
		}

		return false;
	}

	@Override
	protected void updateMessage() {
		if (drawString)
			setMessage(Component.literal("").append(prefix).append(getValueString()).append(suffix));
		else
			setMessage(Component.empty());
	}

	@Override
	protected void applyValue() {}

	private void setFractionFromMouse(double mouseX) {
		setFraction((mouseX - (getX() + 4)) / (width - 8));
	}

	private void setFraction(double fraction) {
		double previous = value;

		value = toSnappedFraction(fraction);

		if (!Mth.equal(previous, value))
			applyValue();

		updateMessage();
	}

	private double toSnappedFraction(double fraction) {
		fraction = Mth.clamp(fraction, 0.0D, 1.0D);

		if (stepSize <= 0.0D)
			return fraction;

		double actualValue = stepSize * Math.round(Mth.lerp(fraction, minValue, maxValue) / stepSize);

		actualValue = Mth.clamp(actualValue, Math.min(minValue, maxValue), Math.max(minValue, maxValue));
		return Mth.inverseLerp(actualValue, minValue, maxValue);
	}
}
