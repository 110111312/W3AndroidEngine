package com.etheller.warsmash.input.touch;

/**
 * How the player drives the game: with a mouse and keyboard, or by touch. The choice decides what fingers and the pen
 * mean on every platform; a real mouse and keyboard keep working the same under both.
 */
public enum ControlScheme {
	MOUSE_KEYBOARD("Mouse"), TOUCH("Touch");

	public final String iniValue;

	ControlScheme(final String iniValue) {
		this.iniValue = iniValue;
	}

	/** @return the scheme the text names, or the fallback for a missing or unknown value */
	public static ControlScheme parse(final String value, final ControlScheme fallback) {
		if (value != null) {
			for (final ControlScheme scheme : values()) {
				if (scheme.iniValue.equalsIgnoreCase(value.trim())) {
					return scheme;
				}
			}
		}
		return fallback;
	}
}
