package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The plan a mission starts with is the race's whole order ({@link RaceBuildOrder}) minus what that base already has, so
 * a campaign mission and a custom map differ only because their bases differ. These are the ways a base differs -- it
 * already has the building, its hall is already past a tier, it has no workers at all -- and none of them needs a table
 * of campaigns.
 */
class LiveBaseDefaultsTest {
	private static final War3ID FARM = War3ID.fromString("hhou");
	private static final War3ID BARRACKS = War3ID.fromString("hbar");
	private static final War3ID TOWN_HALL = War3ID.fromString("htow");
	private static final War3ID KEEP = War3ID.fromString("hkee");
	private static final War3ID CASTLE = War3ID.fromString("hcas");
	private static final War3ID ALTAR = War3ID.fromString("halt");
	private static final War3ID FOOTMAN = War3ID.fromString("hfoo");
	private static final War3ID PEASANT = War3ID.fromString("hpea");
	private static final War3ID HERO = War3ID.fromString("Hpal");

	private static PlanTypeFacts type(final War3ID id, final boolean building, final int goldCost, final int foodMade,
			final List<War3ID> trains, final boolean hero) {
		final boolean worker = PEASANT.equals(id);
		return new PlanTypeFacts(id, building, hero, worker, goldCost, 0, 0, foodMade, List.of(), trains, List.of(),
				List.of());
	}

	/** A hall that upgrades into another hall, which is how the executor knows what the player's tier already is. */
	private static PlanTypeFacts hallThatUpgradesTo(final War3ID id, final War3ID... upgradesTo) {
		return new PlanTypeFacts(id, true, false, false, 385, 0, 0, 0, List.of(), List.of(PEASANT), List.of(),
				List.of(upgradesTo));
	}

	/** A human base: a hall that trains peasants, a barracks that trains footmen, a farm, an altar, a footman. */
	private static Map<War3ID, PlanTypeFacts> humanTypes() {
		final Map<War3ID, PlanTypeFacts> types = new LinkedHashMap<>();
		types.put(PEASANT, type(PEASANT, false, 75, 0, List.of(), false));
		types.put(FOOTMAN, type(FOOTMAN, false, 135, 0, List.of(), false));
		types.put(HERO, type(HERO, false, 425, 0, List.of(), true));
		return types;
	}

	/** The hall chain {@code htow -> hkee -> hcas}, as the game data has it. */
	private static Function<War3ID, PlanTypeFacts> humanHallChain() {
		final Map<War3ID, PlanTypeFacts> types = new LinkedHashMap<>();
		types.put(TOWN_HALL, hallThatUpgradesTo(TOWN_HALL, KEEP));
		types.put(KEEP, hallThatUpgradesTo(KEEP, CASTLE));
		types.put(CASTLE, hallThatUpgradesTo(CASTLE));
		return types::get;
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

	private static List<MacroTicket> humanOrder() {
		return RaceBuildOrder.forRace(CUnitRace.HUMAN);
	}

	/** The human order with some tickets taken out, for the cases where the base already did them. */
	private static List<MacroTicket> humanOrderWithout(final War3ID... done) {
		final Set<War3ID> skip = Set.of(done);
		final List<MacroTicket> kept = new ArrayList<>();
		for (final MacroTicket ticket : humanOrder()) {
			if (!skip.contains(ticket.getRawcode())) {
				kept.add(ticket);
			}
		}
		return kept;
	}

	@Test
	void theSupplyBuildingIsTheBuildersFoodBuildingByFoodMadeThenCost() {
		assertEquals(FARM, LiveBaseDefaults.supplyType(List.of(barracks(), farm(), townHall())));
	}

	@Test
	void thePlanIsTheRacesWholeOrder() {
		// A custom map: a hall and a peasant, nothing else. The plan is the whole order, altar to aviary.
		assertEquals(humanOrder(), LiveBaseDefaults.seed(true, true, CUnitRace.HUMAN, Set.of(TOWN_HALL, PEASANT),
				humanHallChain(), null));
		assertFalse(humanOrder().isEmpty());
	}

	@Test
	void whatTheMissionAlreadyBuiltIsNotAskedFor() {
		// The campaign starts with a barracks: that one line of the order goes, the rest stays.
		assertEquals(humanOrderWithout(BARRACKS),
				LiveBaseDefaults.seed(true, true, CUnitRace.HUMAN, Set.of(TOWN_HALL, PEASANT, BARRACKS),
						humanHallChain(), null));
	}

	@Test
	void aHallAlreadyAtAKeepIsNotAskedToBecomeOneButIsAskedForTheCastle() {
		assertEquals(humanOrderWithout(KEEP),
				LiveBaseDefaults.seed(true, true, CUnitRace.HUMAN, Set.of(KEEP, PEASANT), humanHallChain(), null));
	}

	@Test
	void aMissionThatStartsWithACastleIsNotAskedForTheKeepBeyondIt() {
		// The hall is past both upgrades, so neither the keep nor the castle is worth a ticket.
		assertEquals(humanOrderWithout(KEEP, CASTLE),
				LiveBaseDefaults.seed(true, true, CUnitRace.HUMAN, Set.of(CASTLE, PEASANT), humanHallChain(), null));
	}

	@Test
	void aBaseThatHasOutBuiltTheOrderHasNothingToDo() {
		final War3ID[] all = new War3ID[humanOrder().size()];
		for (int i = 0; i < all.length; i++) {
			all[i] = humanOrder().get(i).getRawcode();
		}
		assertTrue(LiveBaseDefaults.seed(true, true, CUnitRace.HUMAN, Set.of(all), humanHallChain(), BARRACKS)
				.isEmpty());
	}

	@Test
	void aRaceWithoutAnOrderFallsBackToOneMilitaryProducer() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final War3ID military = LiveBaseDefaults.militaryType(List.of(barracks(), farm()), types);
		assertEquals(List.of(MacroTicket.build(BARRACKS)),
				LiveBaseDefaults.seed(true, true, CUnitRace.NAGA, Set.of(TOWN_HALL, PEASANT), humanHallChain(), military));
	}

	@Test
	void aBaseWithNoWorkersGetsNoPlan() {
		assertTrue(LiveBaseDefaults.seed(false, true, CUnitRace.HUMAN, Set.of(TOWN_HALL), humanHallChain(), BARRACKS)
				.isEmpty());
	}

	@Test
	void anUnreadableTechtreeSeedsNothing() {
		assertTrue(LiveBaseDefaults.seed(true, false, CUnitRace.HUMAN, Set.of(TOWN_HALL, PEASANT), humanHallChain(),
				BARRACKS).isEmpty());
		assertTrue(LiveBaseDefaults.seed(true, true, null, Set.of(TOWN_HALL, PEASANT), humanHallChain(), null).isEmpty());
	}

	@Test
	void noFoodMadeMeansNoSupplyBuilding() {
		assertNull(LiveBaseDefaults.supplyType(List.of(barracks(), townHall())));
	}

	@Test
	void aProducerThatOnlyTrainsWorkersOrHeroesIsNotAMilitaryProducer() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		// The hall trains peasants and the altar trains heroes, so neither is a military producer.
		assertNull(LiveBaseDefaults.militaryType(List.of(townHall(), altar()), types));
	}

	@Test
	void aFactoryThatAlsoTrainsWorkersIsStillAMilitaryProducer() {
		// The hall trains a peasant and nothing else, so a base that only has a hall needs a barracks for its soldiers.
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final PlanTypeFacts works = type(War3ID.fromString("hwtw"), true, 120, 0, List.of(FOOTMAN, PEASANT), false);
		assertEquals(works.id(), LiveBaseDefaults.militaryType(List.of(works, townHall()), types));
	}

	@Test
	void theFactsAreCopiedOutOfTheUnitType() {
		assertTrue(LiveBaseDefaults.trainsASoldier(barracks(), humanTypes()));
		assertFalse(LiveBaseDefaults.trainsASoldier(altar(), humanTypes()));
		assertFalse(LiveBaseDefaults.trainsASoldier(townHall(), humanTypes()));
	}

	@Test
	void theSameBaseGivesTheSameAnswers() {
		final Map<War3ID, PlanTypeFacts> types = humanTypes();
		final List<PlanTypeFacts> buildable = new ArrayList<>(List.of(farm(), barracks(), altar(), townHall()));
		assertEquals(LiveBaseDefaults.militaryType(buildable, types), LiveBaseDefaults.militaryType(buildable, types));
	}

	@Test
	void theRaceArmyIsSeededMinusWhatTheBaseAlreadyHas() {
		final LiveBaseDefaults.UnitSeed seed = LiveBaseDefaults.seedUnits(CUnitRace.HUMAN,
				Map.of(PEASANT, 13, FOOTMAN, 2));
		// The bot's thirteen peasants are already there; four of its six footmen are not.
		assertTrue(seed.workers().isEmpty());
		assertEquals(4, count(seed.army(), FOOTMAN));
		assertEquals(2, count(seed.army(), War3ID.fromString("hgyr")));
	}

	@Test
	void workersAreSeededApartFromTheArmy() {
		final LiveBaseDefaults.UnitSeed seed = LiveBaseDefaults.seedUnits(CUnitRace.HUMAN, Map.of());
		assertEquals(13, seed.workers().size());
		assertEquals(25, seed.army().size());
		for (final MacroTicket ticket : seed.workers()) {
			assertEquals(PEASANT, ticket.getRawcode());
			assertEquals(MacroTicketKind.TRAIN, ticket.getKind());
		}
	}

	@Test
	void aBaseWithTheBotsWholeArmyGetsNoUnitTickets() {
		final Map<War3ID, Integer> owned = new LinkedHashMap<>();
		for (final RaceUnitOrder.Line line : RaceUnitOrder.forRace(CUnitRace.NIGHTELF)) {
			owned.put(line.rawcode(), line.count());
		}
		final LiveBaseDefaults.UnitSeed seed = LiveBaseDefaults.seedUnits(CUnitRace.NIGHTELF, owned);
		assertTrue(seed.workers().isEmpty());
		assertTrue(seed.army().isEmpty());
	}

	@Test
	void aRaceWithNoArmySeedsNoUnits() {
		final LiveBaseDefaults.UnitSeed seed = LiveBaseDefaults.seedUnits(CUnitRace.OTHER, Map.of());
		assertTrue(seed.workers().isEmpty());
		assertTrue(seed.army().isEmpty());
	}

	private static int count(final List<MacroTicket> tickets, final War3ID rawcode) {
		int count = 0;
		for (final MacroTicket ticket : tickets) {
			if (ticket.getRawcode().equals(rawcode)) {
				count++;
			}
		}
		return count;
	}
}
