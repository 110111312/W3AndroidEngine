package com.etheller.warsmash.input.touch;

import com.etheller.warsmash.units.Element;

/**
 * Gesture thresholds from the [Touch] section of warsmash.ini. Distances are kept in pixels, converted once from dp
 * with the screen density, so the recognizer itself never deals with units.
 */
public final class TouchSettings {
	public final float tapSlopPx;
	public final float penSlopPx;
	public final long tapMaxMs;
	public final long longPressMs;
	public final long twoFingerWindowMs;
	public final long twoFingerTapMs;
	public final float pinchStartRatio;
	public final float pinchStepRatio;
	public final float buildAimOffsetPx;
	public final float ghostTapRadiusPx;
	public final float palmTouchMajorPx;
	public final PalmFilter.PenFingerPolicy penFingerPolicy;
	public final PalmFilter.SizeRejection palmRejection;
	public final ControlScheme controlScheme;
	public final TouchStyle touchStyle;
	public final long doubleTapMs;
	public final float doubleTapSlopPx;
	public final PenButton penButton;

	public TouchSettings(final float tapSlopPx, final float penSlopPx, final long tapMaxMs, final long longPressMs,
			final long twoFingerWindowMs, final long twoFingerTapMs, final float pinchStartRatio,
			final float pinchStepRatio, final float buildAimOffsetPx, final float ghostTapRadiusPx,
			final float palmTouchMajorPx, final PalmFilter.PenFingerPolicy penFingerPolicy,
			final PalmFilter.SizeRejection palmRejection, final ControlScheme controlScheme,
			final TouchStyle touchStyle, final long doubleTapMs, final float doubleTapSlopPx,
			final PenButton penButton) {
		this.tapSlopPx = tapSlopPx;
		this.penSlopPx = penSlopPx;
		this.tapMaxMs = tapMaxMs;
		this.longPressMs = longPressMs;
		this.twoFingerWindowMs = twoFingerWindowMs;
		this.twoFingerTapMs = twoFingerTapMs;
		this.pinchStartRatio = pinchStartRatio;
		this.pinchStepRatio = pinchStepRatio;
		this.buildAimOffsetPx = buildAimOffsetPx;
		this.ghostTapRadiusPx = ghostTapRadiusPx;
		this.palmTouchMajorPx = palmTouchMajorPx;
		this.penFingerPolicy = penFingerPolicy;
		this.palmRejection = palmRejection;
		this.controlScheme = controlScheme;
		this.touchStyle = touchStyle;
		this.doubleTapMs = doubleTapMs;
		this.doubleTapSlopPx = doubleTapSlopPx;
		this.penButton = penButton;
	}

	/** @param platformScheme the scheme used when the configuration names none: touch on a phone, mouse on a PC */
	public static TouchSettings defaults(final float density, final ControlScheme platformScheme) {
		return new TouchSettings(10 * density, 4 * density, 300, 300, 150, 250, 1.3f, 1.4f, 56 * density,
				48 * density, 96 * density, PalmFilter.PenFingerPolicy.IGNORE, PalmFilter.SizeRejection.AUTO,
				platformScheme, TouchStyle.DOUBLE_TAP, 250, 32 * density, PenButton.SHIFT);
	}

	public static TouchSettings fromIni(final Element touch, final float density, final ControlScheme platformScheme) {
		final TouchSettings defaults = defaults(density, platformScheme);
		if (touch == null) {
			return defaults;
		}
		return new TouchSettings(dp(touch, "TapSlopDp", defaults.tapSlopPx, density),
				dp(touch, "PenSlopDp", defaults.penSlopPx, density), ms(touch, "TapMaxMs", defaults.tapMaxMs),
				ms(touch, "LongPressMs", defaults.longPressMs),
				ms(touch, "TwoFingerWindowMs", defaults.twoFingerWindowMs),
				ms(touch, "TwoFingerTapMs", defaults.twoFingerTapMs),
				ratio(touch, "PinchStartRatio", defaults.pinchStartRatio),
				ratio(touch, "PinchStepRatio", defaults.pinchStepRatio),
				dp(touch, "BuildAimOffsetDp", defaults.buildAimOffsetPx, density),
				dp(touch, "GhostTapRadiusDp", defaults.ghostTapRadiusPx, density),
				dp(touch, "PalmTouchMajorDp", defaults.palmTouchMajorPx, density),
				"Allow".equalsIgnoreCase(touch.getField("PenFingerPolicy")) ? PalmFilter.PenFingerPolicy.ALLOW
						: PalmFilter.PenFingerPolicy.IGNORE,
				sizeRejection(touch.getField("PalmRejection"), defaults.palmRejection),
				controlScheme(touch, defaults.controlScheme),
				TouchStyle.parse(touch.getField("TouchScheme"), defaults.touchStyle),
				ms(touch, "DoubleTapMs", defaults.doubleTapMs),
				dp(touch, "DoubleTapSlopDp", defaults.doubleTapSlopPx, density),
				PenButton.parse(touch.getField("PenButton"), defaults.penButton));
	}

	/** The older switch TouchControls=Off stays a way to ask for the mouse scheme. */
	static ControlScheme controlScheme(final Element touch, final ControlScheme fallback) {
		final String value = touch.getField("ControlScheme");
		if (value.isEmpty() && "Off".equalsIgnoreCase(touch.getField("TouchControls"))) {
			return ControlScheme.MOUSE_KEYBOARD;
		}
		return ControlScheme.parse(value, fallback);
	}

	static PalmFilter.SizeRejection sizeRejection(final String value, final PalmFilter.SizeRejection fallback) {
		if ("Off".equalsIgnoreCase(value)) {
			return PalmFilter.SizeRejection.OFF;
		}
		if ("On".equalsIgnoreCase(value)) {
			return PalmFilter.SizeRejection.ON;
		}
		if ("Auto".equalsIgnoreCase(value)) {
			return PalmFilter.SizeRejection.AUTO;
		}
		return fallback;
	}

	private static float dp(final Element touch, final String key, final float fallbackPx, final float density) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallbackPx : Float.parseFloat(value) * density;
	}

	private static long ms(final Element touch, final String key, final long fallback) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallback : Long.parseLong(value);
	}

	private static float ratio(final Element touch, final String key, final float fallback) {
		final String value = touch.getField(key);
		return value.isEmpty() ? fallback : Float.parseFloat(value);
	}
}
