package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

/**
 * How many swings an attack order allows.
 *
 * <p>
 * A plain attack order is <b>sustained</b>: the unit swings until the target dies or another order arrives. A
 * one-tap intent ({@link com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds#attackonce}) is
 * <b>one swing and then wait for the next tap</b>, which is the mobile grammars' scheme — no tap, no attack.
 *
 * <p>
 * The tap is <em>armed</em> rather than counted, and that is the whole point of doing it here: an arm is a field on
 * the attack behaviour that is already running, so the swing in flight, its backswing latch, its cooldown and the
 * random wait before its first swing all survive. Issuing a fresh order instead would throw every one of those away
 * and make each tap pay up to a quarter second before the swing.
 *
 * <p>
 * One arm at most, on purpose: fifteen taps during one swing still buy one swing. A queue would let a masher stock
 * free swings and make the unit keep fighting with no current input, which is the auto-attack this exists to remove.
 */
final class CSwingIntent {
	private final boolean oneSwing;
	private boolean armed;

	CSwingIntent(final boolean oneSwing) {
		this.oneSwing = oneSwing;
	}

	/** True for a one-tap intent, false for a sustained attack order. */
	boolean isOneSwing() {
		return this.oneSwing;
	}

	/** A tap for one more swing. Harmless on a sustained order, which never needs arming. */
	void arm() {
		this.armed = true;
	}

	boolean isArmed() {
		return this.armed;
	}

	/** Whether another swing may begin now. Spends the arm: one tap buys one swing. */
	boolean nextSwingAllowed() {
		if (!this.oneSwing) {
			return true;
		}
		if (!this.armed) {
			return false;
		}
		this.armed = false;
		return true;
	}
}
