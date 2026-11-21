package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

/**
 * The numbering is the position, so most of the design's edit rules are list operations. These are the four edits the
 * panel offers (E3): a new ticket at the end, tap = sooner, long-press = later, minus = remove.
 */
class MacroPlanTest {
	private static War3ID id(final String rawcode) {
		return War3ID.fromString(rawcode);
	}

	@Test
	void appendedTicketsTakeTheNextNumber() {
		final MacroPlan plan = new MacroPlan();
		plan.append(MacroTicket.build(id("hfoo")));
		plan.append(MacroTicket.research(id("Rhme")));
		assertEquals(0, plan.indexOf(plan.get(0)));
		assertEquals(MacroTicketKind.BUILD, plan.get(0).getKind());
		assertEquals(MacroTicketKind.RESEARCH, plan.get(1).getKind());
	}

	@Test
	void soonerSwapsWithItsNeighbour() {
		final MacroPlan plan = new MacroPlan();
		plan.append(MacroTicket.build(id("hfoo")));
		plan.append(MacroTicket.build(id("hbar")));
		final MacroTicket farm = plan.get(0);
		final MacroTicket barracks = plan.get(1);
		assertTrue(plan.sooner(1));
		assertEquals(barracks, plan.get(0));
		// The other one slid down behind it: the numbers are the positions, so this is the same statement.
		assertEquals(1, plan.indexOf(farm));
	}

	@Test
	void laterSwapsWithItsNeighbour() {
		final MacroPlan plan = new MacroPlan();
		plan.append(MacroTicket.build(id("hfoo")));
		plan.append(MacroTicket.build(id("hbar")));
		final MacroTicket farm = plan.get(0);
		assertTrue(plan.later(0));
		assertEquals(1, plan.indexOf(farm));
	}

	@Test
	void removeSlidesTheNumbersDown() {
		final MacroPlan plan = new MacroPlan();
		plan.append(MacroTicket.build(id("hfoo")));
		plan.append(MacroTicket.build(id("hbar")));
		plan.append(MacroTicket.research(id("Rhme")));
		final MacroTicket research = plan.get(2);
		assertTrue(plan.remove(plan.get(0)));
		// The number behind slid down by itself: that is the whole reason the number is the position.
		assertEquals(1, plan.indexOf(research));
		assertEquals(2, plan.size());
	}

	@Test
	void aTicketAtTheEndCannotGoLaterAndAFrontTicketCannotGoSooner() {
		final MacroPlan plan = new MacroPlan();
		plan.append(MacroTicket.build(id("hfoo")));
		plan.append(MacroTicket.build(id("hbar")));
		assertFalse(plan.sooner(0));
		assertFalse(plan.later(plan.size() - 1));
	}

	@Test
	void removingATicketThatIsNotThereChangesNothing() {
		final MacroPlan plan = new MacroPlan();
		final MacroTicket farm = MacroTicket.build(id("hfoo"));
		plan.append(farm);
		assertFalse(plan.remove(MacroTicket.build(id("hfoo"))));
		assertEquals(1, plan.size());
	}
}
