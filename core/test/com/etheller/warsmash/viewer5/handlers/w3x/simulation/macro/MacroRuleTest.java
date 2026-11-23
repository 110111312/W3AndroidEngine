package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

/**
 * The spending order, proved without a simulation: the earliest number with something ready runs, same-number tickets
 * compete first affordable, one purchase per pass, and a ticket that is only waiting -- for gold, or for its own order
 * -- never holds back a ticket behind it that can be bought now.
 */
class MacroRuleTest {
	private static final War3ID FOOTMAN = War3ID.fromString("hfoo");
	private static final War3ID BARRACKS = War3ID.fromString("hbar");
	private static final War3ID RIFLEMAN = War3ID.fromString("hrif");
	private static final War3ID TECH = War3ID.fromString("Rhme");

	private static MacroTicket build(final War3ID rawcode) {
		return MacroTicket.build(rawcode);
	}

	@Test
	void theLowestNumberRunsFirst() {
		final MacroTicket first = build(FOOTMAN);
		final MacroTicket second = build(BARRACKS);
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.ready(0, first),
				TicketState.ready(1, second))));
		assertSame(first, next.getTicket());
	}

	@Test
	void onePurchasePerPassReturnsASingleTicket() {
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.ready(0, build(FOOTMAN)),
				TicketState.ready(0, build(BARRACKS)))));
		// Not a list: the shape of the answer is the rule.
		assertEquals(0, next.getNumber());
	}

	@Test
	void twoSameNumberTicketsAcrossBothPlansCompeteForTheGold() {
		final MacroTicket mine = build(FOOTMAN);
		final MacroTicket army = build(RIFLEMAN);
		final TicketState next = MacroRule.next(
				List.of(List.of(TicketState.waiting(0, mine)), List.of(TicketState.ready(0, army))));
		assertSame(army, next.getTicket());
	}

	@Test
	void aTicketRunsWhileAnotherPlansEarlierNumberIsOnlyWaiting() {
		// The building plan's zero cannot be paid for yet; the army plan's ticket can. Waiting is not a dependency.
		final MacroTicket armyOne = build(RIFLEMAN);
		final TicketState next = MacroRule.next(
				List.of(List.of(TicketState.waiting(0, build(FOOTMAN))), List.of(TicketState.ready(1, armyOne))));
		assertSame(armyOne, next.getTicket());
	}

	@Test
	void completingTheLastZeroSlidesTheOnesDown() {
		final MacroPlan plan = new MacroPlan();
		plan.append(build(FOOTMAN));
		plan.append(build(BARRACKS));
		final MacroTicket ones = plan.get(1);
		plan.remove(plan.get(0));
		assertEquals(0, plan.indexOf(ones));
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.ready(plan.indexOf(ones), ones))));
		assertSame(ones, next.getTicket());
	}

	@Test
	void anUnaffordableTicketWaitsVisiblyButHoldsNothing() {
		final TicketState waiting = TicketState.waiting(0, build(BARRACKS));
		final MacroTicket footman = build(FOOTMAN);
		final TicketState next = MacroRule.next(List.of(List.of(waiting, TicketState.ready(1, footman))));
		// It stays in the plan for the pass its price arrives, and the ticket behind it is bought meanwhile.
		assertSame(footman, next.getTicket());
		assertFalse(waiting.isReady());
		assertFalse(waiting.isStuck());
	}

	@Test
	void aRequirementStuckTicketIsNeverReturned() {
		final TicketState stuck = TicketState.stuck(0, MacroTicket.research(TECH),
				Stuck.of(Stuck.Reason.MISSING_REQUIREMENT));
		final TicketState next = MacroRule.next(List.of(List.of(stuck)));
		assertNull(next);
		assertTrue(stuck.isStuck());
		assertEquals(Stuck.Reason.MISSING_REQUIREMENT, stuck.getStuck().getReason());
	}

	@Test
	void aStuckTicketBlocksNothing() {
		final MacroTicket next = build(BARRACKS);
		final TicketState state = MacroRule.next(List.of(List.of(TicketState.stuck(0, MacroTicket.research(TECH),
				Stuck.of(Stuck.Reason.NO_PRODUCER)), TicketState.ready(1, next))));
		assertSame(next, state.getTicket());
	}

	@Test
	void aNumberWhoseOnlyTicketsAreStuckIsPassedOver() {
		final MacroTicket next = build(BARRACKS);
		final TicketState state = MacroRule.next(List.of(List.of(
				TicketState.stuck(0, MacroTicket.research(TECH), Stuck.of(Stuck.Reason.MISSING_REQUIREMENT)),
				TicketState.stuck(0, MacroTicket.upgrade(BARRACKS), Stuck.of(Stuck.Reason.NO_HALL)),
				TicketState.ready(1, next))));
		assertSame(next, state.getTicket());
	}

	@Test
	void aPendingTicketWaitsForItsOwnOrderAndNothingElse() {
		// A hall upgrade or a research takes minutes; the base spends those minutes on what it can still pay for.
		final MacroTicket footman = build(FOOTMAN);
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.pending(0, build(BARRACKS)),
				TicketState.ready(1, footman))));
		assertSame(footman, next.getTicket());
	}

	@Test
	void anEmptyPlanReturnsNothing() {
		assertNull(MacroRule.next(List.of(List.of())));
		assertNull(MacroRule.next(List.of()));
	}

	@Test
	void theRuleIsTheSameForEveryKind() {
		// A number is a number whatever the ticket asks for: the rule never looks at the kind.
		final MacroTicket upgrade = MacroTicket.upgrade(BARRACKS);
		final MacroTicket research = MacroTicket.research(TECH);
		final MacroTicket building = MacroTicket.build(FOOTMAN);
		final TicketState withAPending = MacroRule.next(List.of(List.of(TicketState.stuck(0, building,
				Stuck.of(Stuck.Reason.NO_BUILDER)), TicketState.pending(0, upgrade), TicketState.ready(1, research))));
		assertSame(research, withAPending.getTicket());

		final TicketState withoutThePending = MacroRule.next(List.of(List.of(
				TicketState.stuck(0, building, Stuck.of(Stuck.Reason.NO_BUILDER)), TicketState.ready(1, research))));
		assertSame(research, withoutThePending.getTicket());
	}

	@Test
	void aUnitTicketAtOneRunsWhileTheBuildingAtZeroWaits() {
		// The army does not wait on a building it does not need: a waiting zero holds nothing. This is the pair the two
		// plans actually meet on -- same gold, one numbering.
		final MacroTicket grunt = MacroTicket.train(War3ID.fromString("ogru"));
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.waiting(0, build(BARRACKS)),
				TicketState.ready(1, grunt))));
		assertSame(grunt, next.getTicket());
	}

	@Test
	void aUnitTicketKeepsItsNumberWhenItsOwnOrderIsOut() {
		final MacroTicket grunt = MacroTicket.train(War3ID.fromString("ogru"));
		final MacroTicket rifleman = MacroTicket.train(RIFLEMAN);
		final TicketState next = MacroRule.next(List.of(List.of(TicketState.pending(0, grunt),
				TicketState.ready(1, rifleman))));
		// Two units means two orders, and the first one being in flight is a reason for the second to wait for its own
		// gold, not for the first one's unit.
		assertSame(rifleman, next.getTicket());
	}
}
