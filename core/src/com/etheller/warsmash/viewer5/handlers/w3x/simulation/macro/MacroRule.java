package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The spending order, as one rule: supply first, then the lowest number across every plan, one purchase per pass.
 * <p>
 * Pure by construction -- it is handed {@link TicketState}s and returns one of them -- so the numbering semantics are
 * provable without a simulation (see {@code MacroRuleTest}). The three cases of a number, in the order they matter:
 * <ul>
 * <li>every ticket at it is stuck: the number is passed over, because requirement-stuck tickets block nobody;</li>
 * <li>some ticket at it is ready: the first of those is bought, and only one, so two same-number tickets compete;</li>
 * <li>otherwise the number holds: an unaffordable ticket waits visibly, and a pending one waits for its own order, and
 * either way the numbers behind it do not start, because a number must be done before the next starts.</li>
 * </ul>
 * "First" means the first in the order the plans are given, then in the order of the plan, which is stable across
 * passes and so makes the choice reproducible rather than dependent on hash order.
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
		final TreeSet<Integer> numbers = new TreeSet<>();
		for (final List<TicketState> plan : plans) {
			for (final TicketState state : plan) {
				all.add(state);
				numbers.add(state.getNumber());
			}
		}
		for (final Integer number : numbers) {
			boolean anyLive = false;
			for (final TicketState state : all) {
				if ((state.getNumber() != number) || state.isStuck()) {
					continue;
				}
				anyLive = true;
				if (state.isReady()) {
					return state;
				}
			}
			if (anyLive) {
				// Unaffordable or already ordered: the number is open and nothing behind it may start.
				return null;
			}
			// Every ticket at this number is stuck, so the number is passed over and the next one decides.
		}
		return null;
	}
}
