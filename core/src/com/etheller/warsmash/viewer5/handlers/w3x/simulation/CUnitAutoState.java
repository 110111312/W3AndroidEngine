package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

final class CUnitAutoState {
	boolean autoAttack = true;
	/**
	 * Whether the unit looks for targets of its own accord. Separate from {@link #autoAttack} because that one is
	 * recomputed from the unit state buffs, so a flag put there by hand would be lost the next time those are
	 * recomputed; hero mode turns this one off for the hero so that she fights only when she is told to.
	 */
	boolean autoAcquire = true;
	boolean autoCast = true;
	boolean autoTargetable = true;
	boolean assistAlly = true;
}
