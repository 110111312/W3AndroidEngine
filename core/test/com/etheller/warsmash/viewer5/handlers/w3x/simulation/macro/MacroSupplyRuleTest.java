package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Supply is a status line that outranks every number: built a headroom before the army hits the cap, one at a time,
 * never past the map's ceiling, and silent while paused. A dead farm needs no branch of its own -- the cap is the sum of
 * the living farms, so the same test fires.
 */
class MacroSupplyRuleTest {
	private static final int FARM_FOOD = 6;
	private static final int CEILING = 100;

	/** The ordinary case: a base at 10 of 10 food, a ceiling of 100, one farm's worth of room, nothing paused. */
	private static boolean wanted(final int foodUsed, final int incoming, final int foodCap, final int foodMade,
			final boolean paused, final int inFlight) {
		return MacroSupplyRule.wanted(true, false, foodUsed, incoming, foodCap, foodCap, CEILING, foodMade, paused,
				inFlight);
	}

	@Test
	void supplyIsWantedWhenTheArmyIsAtTheCap() {
		assertTrue(wanted(10, 0, 10, FARM_FOOD, false, 0));
	}

	@Test
	void supplyIsWantedAheadOfTheCapWhenUnitsAreComing() {
		// Nine of ten used and a unit of two food on its way: the farm starts before the train is refused.
		assertTrue(MacroSupplyRule.wanted(true, false, 9, 2, 10, 10, CEILING, FARM_FOOD, false, 0));
	}

	@Test
	void aDeadFarmIsRebuilt() {
		// The farm died, so the cap dropped under what is already used: the same rule, no rebuild branch.
		assertTrue(wanted(10, 0, 4, FARM_FOOD, false, 0));
	}

	@Test
	void atMostOneIsInFlight() {
		assertFalse(wanted(10, 0, 10, FARM_FOOD, false, 1));
	}

	@Test
	void supplyStopsAtTheFoodCeiling() {
		assertFalse(wanted(100, 0, CEILING, FARM_FOOD, false, 0));
		assertFalse(wanted(95, 0, 95, FARM_FOOD, false, 0));
	}

	@Test
	void supplyIsNotWantedWhenPaused() {
		assertFalse(wanted(10, 0, 10, FARM_FOOD, true, 0));
	}

	@Test
	void supplyIsNotWantedWithoutAFoodBuilding() {
		assertFalse(MacroSupplyRule.wanted(false, false, 10, 0, 10, 10, CEILING, FARM_FOOD, false, 0));
		assertFalse(MacroSupplyRule.wanted(true, false, 10, 0, 10, 10, CEILING, 0, false, 0));
	}

	@Test
	void aStuckFoodBuildingIsNotWanted() {
		// Missing tech or no builder left: supply must not hold the whole plan hostage for something it can never do.
		assertFalse(MacroSupplyRule.wanted(true, true, 10, 0, 10, 10, CEILING, FARM_FOOD, false, 0));
	}

	@Test
	void theLineStartsAHeadroomBeforeTheCap() {
		// The melee AI's own number: at 4 of 10 food there are 6 left, which is less than the headroom of 7, so the
		// next farm is already worth starting while the base still has room to train.
		assertEquals(7, MacroSupplyRule.SUPPLY_HEADROOM);
		assertTrue(wanted(4, 0, 10, FARM_FOOD, false, 0));
	}

	@Test
	void roomBeyondTheHeadroomIsStillRoom() {
		assertFalse(wanted(3, 0, 10, FARM_FOOD, false, 0));
		assertFalse(wanted(0, 0, 10, FARM_FOOD, false, 0));
	}
}
