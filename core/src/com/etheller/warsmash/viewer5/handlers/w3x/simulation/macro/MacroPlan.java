package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered list of tickets. The number of a ticket is its position, starting at zero.
 * <p>
 * That one choice is the whole numbering rule: a finished ticket is removed and every number behind it slides down by
 * itself, a new ticket enters at the end, and "sooner" and "later" are a swap. Nothing here knows what a ticket costs
 * or whether it can be done -- {@link MacroRule} decides what runs, and it is handed every plan at once so that the
 * numbering is shared across the building plan and the unit plan.
 */
public final class MacroPlan {
	private final List<MacroTicket> tickets = new ArrayList<>();

	/** The tickets, in order, for reading only. */
	public List<MacroTicket> getTickets() {
		return Collections.unmodifiableList(this.tickets);
	}

	public int size() {
		return this.tickets.size();
	}

	public MacroTicket get(final int number) {
		return this.tickets.get(number);
	}

	/** A new ticket enters at the end of the numbering, however far down that is. */
	public int append(final MacroTicket ticket) {
		this.tickets.add(ticket);
		return this.tickets.size() - 1;
	}

	public int indexOf(final MacroTicket ticket) {
		for (int i = 0; i < this.tickets.size(); i++) {
			if (this.tickets.get(i) == ticket) {
				return i;
			}
		}
		return -1;
	}

	/** Tapping a ticket makes it one number sooner; the front ticket cannot go sooner. */
	public boolean sooner(final int number) {
		if ((number <= 0) || (number >= this.tickets.size())) {
			return false;
		}
		Collections.swap(this.tickets, number, number - 1);
		return true;
	}

	/** A long press makes it one number later; the last ticket cannot go later. */
	public boolean later(final int number) {
		if ((number < 0) || (number >= (this.tickets.size() - 1))) {
			return false;
		}
		Collections.swap(this.tickets, number, number + 1);
		return true;
	}

	public boolean remove(final MacroTicket ticket) {
		final int index = indexOf(ticket);
		if (index < 0) {
			return false;
		}
		this.tickets.remove(index);
		return true;
	}

	public void clear() {
		this.tickets.clear();
	}

	@Override
	public String toString() {
		return this.tickets.toString();
	}
}
