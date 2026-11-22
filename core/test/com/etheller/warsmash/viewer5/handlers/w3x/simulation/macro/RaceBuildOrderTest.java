package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The order each race builds in, read off the melee AI's opening and cut down to what a building plan owns: no units
 * (E2 trains those) and no supply buildings (the supply rule does that). These are the invariants that keep a list from
 * quietly going wrong -- a farm in the list is a second supply decision, a unit in the list is a ticket nothing can
 * execute, and an upgrade before the hall that performs it is a ticket that waits forever.
 */
class RaceBuildOrderTest {
	private static final CUnitRace[] RACES = { CUnitRace.HUMAN, CUnitRace.ORC, CUnitRace.UNDEAD, CUnitRace.NIGHTELF };

	/** The four supply buildings: a farm, a burrow, a ziggurat and a moon well. */
	private static final Set<War3ID> SUPPLY = setOf("hhou", "otrb", "uzig", "emow");

	/** A worker and a soldier per race, none of which belongs in a building plan. */
	private static final Set<War3ID> UNITS = setOf("hpea", "opeo", "uaco", "ewsp", "hfoo", "ogru", "ugho", "earc");

	private static Set<War3ID> setOf(final String... rawcodes) {
		final Set<War3ID> ids = new HashSet<>();
		for (final String rawcode : rawcodes) {
			ids.add(War3ID.fromString(rawcode));
		}
		return ids;
	}

	private static int indexOf(final List<MacroTicket> order, final String rawcode) {
		for (int i = 0; i < order.size(); i++) {
			if (order.get(i).getRawcode().equals(War3ID.fromString(rawcode))) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void everyRaceHasAnOrderToWorkThrough() {
		for (final CUnitRace race : RACES) {
			assertTrue(RaceBuildOrder.forRace(race).size() >= 10, "short order for " + race);
		}
	}

	@Test
	void aRaceWithNoOrderGetsNone() {
		assertTrue(RaceBuildOrder.forRace(CUnitRace.NAGA).isEmpty());
		assertTrue(RaceBuildOrder.forRace(CUnitRace.CREEPS).isEmpty());
		assertTrue(RaceBuildOrder.forRace(null).isEmpty());
	}

	@Test
	void anOrderStartsWithSomethingToBuild() {
		// A hall cannot upgrade itself into existence, so the first line is always a building.
		for (final CUnitRace race : RACES) {
			assertEquals(MacroTicketKind.BUILD, RaceBuildOrder.forRace(race).get(0).getKind(), "first line of " + race);
		}
	}

	@Test
	void supplyIsNeverATicket() {
		for (final CUnitRace race : RACES) {
			for (final MacroTicket ticket : RaceBuildOrder.forRace(race)) {
				assertFalse(SUPPLY.contains(ticket.getRawcode()),
						"the supply rule owns " + ticket.getRawcode() + ", so the " + race + " order must not ask for it");
			}
		}
	}

	@Test
	void unitsAreNeverATicket() {
		for (final CUnitRace race : RACES) {
			for (final MacroTicket ticket : RaceBuildOrder.forRace(race)) {
				assertFalse(UNITS.contains(ticket.getRawcode()), "the unit plan trains " + ticket.getRawcode());
			}
		}
	}

	@Test
	void noOrderAsksForTheSameThingTwice() {
		for (final CUnitRace race : RACES) {
			final List<MacroTicket> order = RaceBuildOrder.forRace(race);
			assertEquals(order.size(), new HashSet<>(order).size(), "duplicate ticket in the " + race + " order");
		}
	}

	@Test
	void theSecondTierComesBeforeTheThird() {
		// Each hall chain is two upgrades in order: a keep before a castle, and the same on the other three races.
		final String[][] chains = { { "hkee", "hcas" }, { "ostr", "ofrt" }, { "unp1", "unp2" }, { "etoa", "etoe" } };
		for (int i = 0; i < RACES.length; i++) {
			final List<MacroTicket> order = RaceBuildOrder.forRace(RACES[i]);
			final int second = indexOf(order, chains[i][0]);
			final int third = indexOf(order, chains[i][1]);
			assertTrue(second >= 0, "no " + chains[i][0] + " in the " + RACES[i] + " order");
			assertTrue(third > second, chains[i][1] + " must come after " + chains[i][0] + " for " + RACES[i]);
		}
	}

	@Test
	void everyTicketNamesATypeOnce() {
		for (final CUnitRace race : RACES) {
			final List<War3ID> seen = new ArrayList<>();
			for (final MacroTicket ticket : RaceBuildOrder.forRace(race)) {
				assertFalse(seen.contains(ticket.getRawcode()), "repeated rawcode " + ticket.getRawcode());
				seen.add(ticket.getRawcode());
			}
		}
	}
}
