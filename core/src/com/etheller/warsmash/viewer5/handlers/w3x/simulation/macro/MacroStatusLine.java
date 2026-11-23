package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.function.Function;

import com.etheller.warsmash.util.War3ID;

/**
 * What a ticket's state says in the panel, in as few words as fit under an icon.
 * <p>
 * The words are a decision, not a drawing, so they live here with a test: "wait", the requirement's name, "max". The
 * game's own answer decides which -- an unaffordable ticket is waiting for its price, a stuck one is greyed and names
 * what it lacks, and a ready one says nothing at all, because a ticket with no words is a ticket that is about to be
 * bought.
 */
public final class MacroStatusLine {
	/** Answers a name for a rawcode, so that "needs etoa" can be "needs Tree of Ages". */
	private static final Function<War3ID, String> RAWCODES = War3ID::asStringValue;

	private MacroStatusLine() {
	}

	/** The line under a ticket, empty when there is nothing to say. */
	public static String of(final TicketState state) {
		return of(state, RAWCODES);
	}

	public static String of(final TicketState state, final Function<War3ID, String> nameOf) {
		if (state.isStuck()) {
			final Stuck stuck = state.getStuck();
			switch (stuck.getReason()) {
				case MISSING_REQUIREMENT:
					final War3ID missing = stuck.getMissing();
					return (missing == null) ? "needs tech" : ("needs " + nameOf.apply(missing));
				case AT_MAXIMUM:
					return "max";
				case NO_BUILDER:
					return "no builder";
				case NO_PRODUCER:
					return "no trainer";
				case NO_HALL:
					return "no hall";
				case NO_SITE:
					return "no room";
				default:
					return "unreadable";
			}
		}
		if (state.isPending()) {
			// An order is out for it: the ticket is not waiting for gold, it is waiting for the thing it ordered.
			return "ordered";
		}
		if (!state.isAffordable()) {
			return "wait";
		}
		return "";
	}
}
