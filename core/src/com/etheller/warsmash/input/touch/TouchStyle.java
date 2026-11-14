package com.etheller.warsmash.input.touch;

/** How a touch gives an order: by holding the finger down, or by tapping twice. */
public enum TouchStyle {
	TAP_HOLD("TapHold"), DOUBLE_TAP("DoubleTap");

	public final String iniValue;

	TouchStyle(final String iniValue) {
		this.iniValue = iniValue;
	}

	public static TouchStyle parse(final String value, final TouchStyle fallback) {
		if (value != null) {
			for (final TouchStyle style : values()) {
				if (style.iniValue.equalsIgnoreCase(value.trim())) {
					return style;
				}
			}
		}
		return fallback;
	}
}
