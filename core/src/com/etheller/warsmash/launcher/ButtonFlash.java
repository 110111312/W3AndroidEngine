package com.etheller.warsmash.launcher;

/**
 * The flash of a button that asks to be looked at: lit for half a second, dark for half a second, starting lit. The
 * game's Quests button and the launcher's LAN button share it, so they flash alike.
 */
public final class ButtonFlash {
	/** Seconds of a lit or a dark phase. */
	public static final float PERIOD = 0.5f;
	/** The time of a flash that is not running. */
	public static final float OFF = -1;

	private ButtonFlash() {
	}

	/** @param time seconds since the flash began, or {@link #OFF} */
	public static boolean isLit(final float time) {
		return ((int) (time / PERIOD) % 2) == 0;
	}

	public static boolean isRunning(final float time) {
		return time >= 0;
	}
}
