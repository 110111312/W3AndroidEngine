package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.Arrays;

/**
 * Which item slots get a button on the tray.
 * <p>
 * A hero has six inventory slots — nine when the data asks for nine — and most items are never used by hand: a sword
 * has nothing to click. So a button is drawn only for an item the data says is actively used, and the buttons are
 * compacted in slot order, because the player cares that the potion is one tap away and not that it is in slot four.
 * Four is the cap: the two small buttons of the inner arc and one row of two under them.
 */
final class HeroItemSlots {
	static final int MAX_BUTTONS = 4;
	/** The first two buttons sit in the inner arc, beside the attack button. */
	static final int INNER_ARC_BUTTONS = 2;

	private HeroItemSlots() {
	}

	/**
	 * @param usable one entry per inventory slot, true when that slot holds an item that is actively used
	 * @return the inventory slot behind each button, in slot order, at most {@link #MAX_BUTTONS} of them
	 */
	static int[] buttons(final boolean[] usable) {
		final int[] slots = new int[Math.min(MAX_BUTTONS, usable.length)];
		int button = 0;
		for (int slot = 0; (slot < usable.length) && (button < slots.length); slot++) {
			if (usable[slot]) {
				slots[button++] = slot;
			}
		}
		return (button == slots.length) ? slots : Arrays.copyOf(slots, button);
	}
}
