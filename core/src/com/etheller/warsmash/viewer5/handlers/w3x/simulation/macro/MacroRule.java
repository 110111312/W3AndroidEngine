package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayList;
import java.util.List;

/**
 * The spending order, as one rule: the earliest number with something ready, one purchase per pass.
 * <p>
 * Order is a preference, not a lock. A ticket waits only for itself -- its price, or an order of its own that is
 * already out -- and never for the tickets before it, because what it actually *requires* is the game's answer, not
 * the list's: a step whose prerequisite is missing comes back {@code STUCK MISSING_REQUIREMENT} and is skipped, and
 * that is the whole of the ordering constraint. So a hall upgrade that takes four minutes no longer freezes the base
 * for four minutes: the tickets behind it that can be paid for are bought meanwhile.
 * <p>
 * Pure by construction -- it is handed {@link TicketState}s and returns one of them -- so the numbering semantics are
 * provable without a simulation (see {@code MacroRuleTest}). Stuck tickets are never returned and never considered;
 * among the ready ones the lowest number wins, and among equals the first in the order the plans are given, then in the
 * order of the plan, which is stable across passes and so makes the choice reproducible rather than dependent on hash
 * order.
 */
public final class MacroRule {
	private MacroRule() {
	}

	/**
	 * The one ticket to buy this pass, or null when nothing can be bought.
	 *
	 * @param plans every plan, building plan first: the numbering is shared across all of them
	 */
	public static TicketState next(final List<? extends List<TicketState>> plans) {
		final List<TicketState> all = new ArrayList<>();
		for (final List<TicketState> plan : plans) {
			all.addAll(plan);
		}
		TicketState best = null;
		for (final TicketState state : all) {
			if (state.isStuck() || !state.isReady()) {
				continue;
			}
			// Strictly lower, so the first ticket of the lowest number wins and the choice is stable across passes.
			if ((best == null) || (state.getNumber() < best.getNumber())) {
				best = state;
			}
		}
		return best;
	}
}
