package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

/**
 * The plan a mission starts with comes from the base that is there, not from a table of campaigns. These are the two
 * ways a mission differs -- it already has the building, or it has no workers at all -- and neither needs a table.
 */
class LiveBaseDefaultsTest {
	private static final War3ID FARM = War3ID.fromString("hhou");
	private static final War3ID BARRACKS = War3ID.fromString("hbar");
	private static final War3ID TOWN_HALL = War3ID.fromString("htow");
	private static final War3ID ALTAR = War3ID.fromString("halt");
	private static final War3ID FOOTMAN = War3ID.fromString("hfoo");
	private static final War3ID PEASANT = War3ID.fromString("hpea");
	private static final War3ID HERO = War3ID.fromString("Hpal");
	private static final War3ID HUMAN_BUILD = War3ID.fromString("AHbu");

	private static PlanTypeFacts type(final War3ID id, final boolean building, final int goldCost, final int foodMade,
			final List<War3ID> trains, final boolean hero) {
		final boolean worker = PEASANT.equals(id);
		return new PlanTypeFacts(id, building, hero, worker, goldCost, 0, 0, foodMade, List.of(), trains,
				List.of(), List.of());
	}

	/** A human base: a hall that trains peasants, a barracks that trains footmen, a farm, an altar, a footman. */
	private static Map<War3ID, PlanTypeFacts> humanTypes() {
		final Map<War3ID, PlanTypeFacts> types = new LinkedHashMap<>();
		types.put(PEASANT, type(PEASANT, false, 75, 0, List.of(), false));
		types.put(FOOTMAN, type(FOOTMAN, false, 135, 0, List.of(), false));
		types.put(HERO, type(HERO, false, 425, 0, List.of(), true));
		return types;
	}

	private static PlanTypeFacts farm() {
		return type(FARM, true, 80, 6, List.of(), false);
	}

	private static PlanTypeFacts barracks() {
		return type(BARRACKS, true, 180, 0, List.of(FOOTMAN), false);
	}

	private static PlanTypeFacts altar() {
		return type(ALTAR, true, 180, 0, List.of(HERO), false);
	}

	private static PlanTypeFacts townHall() {
		return type(TOWN_HALL, true, 385, 0, List.of(PEASANT), false);
	}

	@Test
	void theSupplyBuildingIsTheBuildersFoodBuildingByFoodMadeThenCost() {
		assertEquals(FARM, LiveBaseDefaults.supplyType(List.of(barracks(), farm(), townHall())));
	}

	@Test
	void asksForOneMilitaryProducerWhenThereIsNone() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final List<PlanTypeFacts> buildable = List.of(farm(), barracks(), altar(), townHall());
		final War3ID military = LiveBaseDefaults.militaryType(buildable, types);
		assertEquals(BARRACKS, military);
		assertEquals(List.of(MacroTicket.build(BARRACKS)),
				LiveBaseDefaults.seed(true, true, military, Set.of(TOWN_HALL, PEASANT, FARM)));
	}

	@Test
	void doesNotAskForABuildingTheMissionAlreadyGave() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final War3ID military = LiveBaseDefaults.militaryType(List.of(barracks(), farm()), types);
		// The mission starts with the barracks, and its ticket is still being built: either answer means "we have one".
		assertTrue(LiveBaseDefaults.seed(true, true, military, Set.of(TOWN_HALL, PEASANT, BARRACKS)).isEmpty());
	}

	@Test
	void aProducerThatOnlyTrainsWorkersOrHeroesIsNotAMilitaryProducer() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		// The hall trains peasants and the altar trains heroes, so neither is a military producer.
		assertTrue(LiveBaseDefaults.seed(true, true, LiveBaseDefaults.militaryType(List.of(townHall(), altar()), types),
				Set.of(TOWN_HALL, PEASANT)).isEmpty());
	}

	@Test
	void aFactoryThatAlsoTrainsWorkersIsStillAMilitaryProducer() {
		// The hall trains a peasant and nothing else, so a base that only has a hall needs a barracks for its soldiers.
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		types.put(PEASANT, type(PEASANT, false, 75, 0, List.of(), false));
		final PlanTypeFacts works = type(War3ID.fromString("hwtw"), true, 120, 0, List.of(FOOTMAN, PEASANT), false);
		assertEquals(works.id(), LiveBaseDefaults.militaryType(List.of(works, townHall()), types));
	}

	@Test
	void aBaseWithNoWorkersGetsNoPlan() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final War3ID military = LiveBaseDefaults.militaryType(List.of(barracks(), farm()), types);
		assertTrue(LiveBaseDefaults.seed(false, true, military, Set.of(TOWN_HALL)).isEmpty());
	}

	@Test
	void anUnreadableTechtreeSeedsNothing() {
		assertTrue(LiveBaseDefaults.seed(true, false, BARRACKS, Set.of(TOWN_HALL, PEASANT)).isEmpty());
		assertTrue(LiveBaseDefaults.seed(true, true, null, Set.of(TOWN_HALL, PEASANT)).isEmpty());
	}

	@Test
	void noFoodMadeMeansNoSupplyBuilding() {
		assertNull(LiveBaseDefaults.supplyType(List.of(barracks(), townHall())));
	}

	@Test
	void theFactsAreCopiedOutOfTheUnitType() {
		assertTrue(LiveBaseDefaults.trainsASoldier(barracks(), humanTypes()));
		assertTrue(!LiveBaseDefaults.trainsASoldier(altar(), humanTypes()));
		assertTrue(!LiveBaseDefaults.trainsASoldier(townHall(), humanTypes()));
	}

	@Test
	void theSameBaseGivesTheSameAnswers() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final List<PlanTypeFacts> buildable = new ArrayList<>(List.of(farm(), barracks(), altar(), townHall()));
		assertEquals(LiveBaseDefaults.militaryType(buildable, types), LiveBaseDefaults.militaryType(buildable, types));
	}
}
