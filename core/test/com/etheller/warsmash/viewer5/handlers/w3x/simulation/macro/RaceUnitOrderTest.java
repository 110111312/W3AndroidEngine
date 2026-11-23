package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The default army is data, so these are the invariants of the table rather than the bot's balance: every race's list
 * starts with its worker, names each type once, asks for a positive count of it, and does not name a hero -- the hero is
 * never a ticket.
 */
class RaceUnitOrderTest {
	private static final List<CUnitRace> PLAYABLE = List.of(CUnitRace.HUMAN, CUnitRace.ORC, CUnitRace.UNDEAD,
			CUnitRace.NIGHTELF);

	private static int indexOf(final List<RaceUnitOrder.Line> lines, final String rawcode) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).rawcode().equals(War3ID.fromString(rawcode))) {
				return i;
			}
		}
		return -1;
	}

	@Test
	void everyRaceStartsWithItsWorkerAndOnlyOneOfThem() {
		for (final CUnitRace race : PLAYABLE) {
			final List<RaceUnitOrder.Line> lines = RaceUnitOrder.forRace(race);
			assertTrue(lines.get(0).worker(), race + " must start with its worker");
			int workers = 0;
			for (final RaceUnitOrder.Line line : lines) {
				if (line.worker()) {
					workers++;
				}
			}
			// A second worker line would mean the seed split its workers across two entries, which is not what the
			// bot scripts do: they ask for more of the same type.
			assertEquals(1, workers, race + " has one worker line");
		}
	}

	@Test
	void aTypeAppearsOnceWithAPositiveCount() {
		for (final CUnitRace race : PLAYABLE) {
			final Set<War3ID> seen = new HashSet<>();
			for (final RaceUnitOrder.Line line : RaceUnitOrder.forRace(race)) {
				assertTrue(line.count() > 0, race + " asks for a positive number of " + line.rawcode());
				assertTrue(seen.add(line.rawcode()), race + " names " + line.rawcode() + " twice");
			}
		}
	}

	@Test
	void theNightElfArmyIsTheBotsNumbers() {
		final List<RaceUnitOrder.Line> lines = RaceUnitOrder.forRace(CUnitRace.NIGHTELF);
		assertEquals(11, lines.get(0).count()); // the bot's first-mine wisp total
		assertEquals(6, lines.get(indexOf(lines, "earc")).count());
		assertEquals(6, lines.get(indexOf(lines, "esen")).count());
		assertEquals(4, lines.get(indexOf(lines, "edry")).count());
		assertEquals(3, lines.get(indexOf(lines, "edoc")).count());
		assertEquals(2, lines.get(indexOf(lines, "ebal")).count());
		assertEquals(2, lines.get(indexOf(lines, "echm")).count());
	}

	@Test
	void theHeroIsNotInAnyArmy() {
		// The script asks for hero_id, which is a rawcode picked at runtime; the hero is never a ticket.
		for (final CUnitRace race : PLAYABLE) {
			for (final RaceUnitOrder.Line line : RaceUnitOrder.forRace(race)) {
				assertNotEquals("hero_id", line.rawcode().asStringValue());
			}
		}
	}

	@Test
	void aRaceWithNoTableHasNoArmy() {
		assertTrue(RaceUnitOrder.forRace(CUnitRace.OTHER).isEmpty());
		assertTrue(RaceUnitOrder.forRace(null).isEmpty());
	}
}
