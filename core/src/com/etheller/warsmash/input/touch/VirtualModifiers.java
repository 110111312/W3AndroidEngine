package com.etheller.warsmash.input.touch;

/**
 * The on-screen Shift for touch play. It is held like the key: down while a finger rests on it, so orders and
 * selections given with other fingers meanwhile are queued or added.
 */
public final class VirtualModifiers {
	public enum ShiftState {
		OFF, HELD
	}

	private static volatile ShiftState shift = ShiftState.OFF;
	private static volatile boolean penButtonShift;
	private static volatile boolean penButtonCtrl;

	private VirtualModifiers() {
	}

	public static boolean isShiftDown() {
		return (shift == ShiftState.HELD) || penButtonShift;
	}

	public static ShiftState getShiftState() {
		return shift;
	}

	public static boolean isControlDown() {
		return penButtonCtrl;
	}

	/** The pen's side button acts as Shift or Ctrl while it is held, as the player chose; as drag it is no key. */
	static void setPenButtonHeld(final boolean held, final PenButton mode) {
		penButtonShift = held && (mode == PenButton.SHIFT);
		penButtonCtrl = held && (mode == PenButton.CTRL);
	}

	/** What is held now, to give a click later the way it was meant when it was made. */
	static int snapshot() {
		return ((shift == ShiftState.HELD) ? 1 : 0) | (penButtonShift ? 2 : 0) | (penButtonCtrl ? 4 : 0);
	}

	static void restore(final int held) {
		shift = (held & 1) != 0 ? ShiftState.HELD : ShiftState.OFF;
		penButtonShift = (held & 2) != 0;
		penButtonCtrl = (held & 4) != 0;
	}

	static void setShiftHeld(final boolean held) {
		shift = held ? ShiftState.HELD : ShiftState.OFF;
	}
}
