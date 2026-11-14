package com.etheller.warsmash.input.touch;

/** What the pen's side button does while it is held; as right click the tip tap that goes with it is a right click. */
public enum PenButton {
	SHIFT("Shift"), CTRL("Ctrl"), DRAG("Drag"), RIGHT_CLICK("RightClick");

	public final String iniValue;

	PenButton(final String iniValue) {
		this.iniValue = iniValue;
	}

	public static PenButton parse(final String value, final PenButton fallback) {
		if (value != null) {
			for (final PenButton button : values()) {
				if (button.iniValue.equalsIgnoreCase(value.trim())) {
					return button;
				}
			}
		}
		return fallback;
	}
}
