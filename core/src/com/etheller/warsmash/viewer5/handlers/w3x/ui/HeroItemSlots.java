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
	/** Positions along the bottom edge, where the rest of the items and then the hero's extra skills go. */
	static final int ROW_SLOTS = 3;

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

	/** How many of the item buttons sit in the row rather than beside the attack button. */
	static int rowItemCount(final int itemButtonCount) {
		return Math.max(0, Math.min(MAX_BUTTONS, itemButtonCount) - INNER_ARC_BUTTONS);
	}

	/**
	 * How many positions the row has left for the hero's extra skills once the items have theirs. The extra skills are
	 * appended after the items, so a hero carrying four usable items has one position left and a hero carrying none
	 * has the whole row. The four arc slots never move: the extra skills are what the arc has no room for.
	 */
	static int freeRowSlots(final int itemButtonCount) {
		return Math.max(0, ROW_SLOTS - rowItemCount(itemButtonCount));
	}
}
