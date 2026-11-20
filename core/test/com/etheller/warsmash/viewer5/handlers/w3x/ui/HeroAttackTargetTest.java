package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Who the big attack button may point at. The reported failure is a campaign gate nobody could hit, so the
 * interesting case is the Neutral Passive building that a smart order walks to and an attack order breaks — and the
 * cases around it, because a button that picks the nearest thing must never pick the player's own army.
 */
class HeroAttackTargetTest {
	private static final int PLAYERS = 8;
	private static final int HUMAN = 0;
	private static final int ALLY = 1;
	private static final int ENEMY = 2;
	/** {@link CSimulation#isNeutralPassive} is the last slot, so this has to be the last player in the list. */
	private static final int NEUTRAL_PASSIVE = PLAYERS - 1;
	private static final int NEUTRAL_HOSTILE = PLAYERS - 4;

	// ---------------------------------------------------------------- what the button is allowed to pick

	@Test
	void aNeutralPassiveBuildingIsATarget() {
		// The bug: Neutral Passive is PASSIVE-allied to everyone so that a right click walks instead of attacking,
		// and the smart order the button used to send was refused for exactly that alliance. The button exists to
		// break these.
		assertTrue(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), unit(NEUTRAL_PASSIVE)));
	}

	@Test
	void anEnemyIsATarget() {
		assertTrue(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), unit(ENEMY)));
	}

	@Test
	void aNeutralHostileCreatureIsATarget() {
		assertTrue(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), unit(NEUTRAL_HOSTILE)));
	}

	@Test
	void aDestructableIsATarget() {
		// A destructable has no owner, so no alliance can ever exclude it; the weapon check is what decides.
		assertTrue(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), destructable()));
	}

	@Test
	void theHerosOwnUnitIsNotATarget() {
		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), unit(HUMAN)));
	}

	@Test
	void anAllyIsNotATarget() {
		// Allied and a real player: a button that picks the nearest thing must not pick the player's own side.
		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), unit(ALLY)));
	}

	@Test
	void theHeroItselfIsNotATarget() {
		final CUnit hero = unit(HUMAN);
		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, hero, hero));
	}

	@Test
	void aDeadWidgetIsNotATarget() {
		final CUnit corpse = unit(ENEMY);
		dead(corpse);
		final CDestructable rubble = destructable();
		dead(rubble);

		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), corpse));
		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), rubble));
	}

	@Test
	void aHiddenUnitIsNotATarget() {
		final CUnit hidden = unit(ENEMY);
		TestObjects.set(hidden, "hidden", true);

		assertFalse(HeroAttackTarget.isPickable(game(), HUMAN, unit(HUMAN), hidden));
	}

	// ---------------------------------------------------------------- the list the button looks at

	@Test
	void theCandidatesAreTheUnitsAndTheDestructables() {
		final CSimulation game = game();
		final CUnit footman = unit(ENEMY);
		final CDestructable gate = destructable();
		units(game, footman);
		destructables(game, gate);

		// A destructable gate is not in getUnits(), which is why the button could never see one.
		final List<CWidget> expected = new ArrayList<>();
		expected.add(footman);
		expected.add(gate);
		assertEquals(expected, HeroAttackTarget.candidates(game));
	}

	@Test
	void aGameWithNothingInItHasNoCandidates() {
		assertTrue(HeroAttackTarget.candidates(game()).isEmpty());
	}

	// ---------------------------------------------------------------- picking the nearest

	@Test
	void theNearestAcceptedCandidateWins() {
		final CUnit far = unit(ENEMY);
		final CUnit nearest = unit(ENEMY);
		final CUnit near = unit(ENEMY);
		// Deliberately out of order: the answer must not be the first one listed.
		final List<CWidget> candidates = Arrays.asList(far, nearest, near);
		final Map<CWidget, Double> distances = distances(far, 900.0, nearest, 100.0, near, 300.0);

		assertSame(nearest, HeroAttackTarget.nearest(candidates, 1000f, distances::get, acceptAll()));
	}

	@Test
	void aCandidateBeyondTheRangeIsNotATarget() {
		final CUnit beyond = unit(ENEMY);
		final List<CWidget> candidates = Collections.singletonList(beyond);
		final Map<CWidget, Double> distances = distances(beyond, 900.0);

		assertNull(HeroAttackTarget.nearest(candidates, 400f, distances::get, acceptAll()));
		assertSame(beyond, HeroAttackTarget.nearest(candidates, 900f, distances::get, acceptAll()));
	}

	@Test
	void aCandidateAtTheRangeIsTaken() {
		final CUnit atTheEdge = unit(ENEMY);
		final Map<CWidget, Double> distances = distances(atTheEdge, 400.0);

		assertSame(atTheEdge,
				HeroAttackTarget.nearest(Collections.singletonList(atTheEdge), 400f, distances::get, acceptAll()));
	}

	@Test
	void aRefusedCandidateLetsTheNextNearestWin() {
		final CSimulation game = game();
		final CUnit refused = unit(ALLY);
		final CUnit accepted = unit(ENEMY);
		final List<CWidget> candidates = Arrays.asList(refused, accepted);
		final Map<CWidget, Double> distances = distances(refused, 100.0, accepted, 500.0);

		assertSame(accepted, HeroAttackTarget.nearest(candidates, 1000f, distances::get,
				candidate -> HeroAttackTarget.isPickable(game, HUMAN, unit(HUMAN), candidate)));
	}

	@Test
	void nothingAcceptedIsNoTarget() {
		final CSimulation game = game();
		final CUnit only = unit(ALLY);
		final Map<CWidget, Double> distances = distances(only, 100.0);

		assertNull(HeroAttackTarget.nearest(Collections.singletonList(only), 1000f, distances::get,
				candidate -> HeroAttackTarget.isPickable(game, HUMAN, unit(HUMAN), candidate)));
		assertNull(HeroAttackTarget.nearest(Collections.<CWidget>emptyList(), 1000f, distances::get, acceptAll()));
	}

	// ---------------------------------------------------------------- the range knob

	@Test
	void theRangeIsTheHerosAcquisitionRangeFlooredAtTheMinimum() {
		assertEquals(HeroAttackTarget.MINIMUM_RANGE, HeroAttackTarget.rangeOf(0f));
		assertEquals(HeroAttackTarget.MINIMUM_RANGE, HeroAttackTarget.rangeOf(HeroAttackTarget.MINIMUM_RANGE));
		assertEquals(800f, HeroAttackTarget.rangeOf(800f));
	}

	// ---------------------------------------------------------------- the harness

	private static Predicate<CWidget> acceptAll() {
		return candidate -> true;
	}

	private static Map<CWidget, Double> distances(final CWidget first, final double firstDistance,
			final Object... more) {
		final Map<CWidget, Double> distances = new HashMap<>();
		distances.put(first, Double.valueOf(firstDistance));
		for (int i = 0; i < more.length; i += 2) {
			distances.put((CWidget) more[i], (Double) more[i + 1]);
		}
		return distances;
	}

	/**
	 * A simulation with real player slots and real alliances: the human fully allied with itself, {@link #ALLY}
	 * PASSIVE-allied to it, Neutral Passive allied the way the game allies it to everybody, and the rest strangers.
	 */
	private static CSimulation game() {
		final List<CPlayer> players = new ArrayList<>();
		for (int i = 0; i < PLAYERS; i++) {
			final CPlayer player = TestObjects.allocate(CPlayer.class);
			TestObjects.set(player, "alliances", alliances(i));
			players.add(player);
		}
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "players", players);
		units(game);
		destructables(game);
		return game;
	}

	@SuppressWarnings("unchecked")
	private static EnumSet<CAllianceType>[] alliances(final int playerId) {
		final EnumSet<CAllianceType>[] alliances = new EnumSet[PLAYERS];
		for (int i = 0; i < PLAYERS; i++) {
			final boolean allied = (i == playerId)
					|| ((playerId == HUMAN) && ((i == ALLY) || (i == NEUTRAL_PASSIVE)))
					|| ((i == HUMAN) && ((playerId == ALLY) || (playerId == NEUTRAL_PASSIVE)));
			alliances[i] = allied ? EnumSet.of(CAllianceType.PASSIVE) : EnumSet.noneOf(CAllianceType.class);
		}
		return alliances;
	}

	private static CUnit unit(final int owner) {
		final CUnit unit = TestObjects.allocate(CUnit.class);
		TestObjects.set(unit, "life", Float.valueOf(100f));
		TestObjects.set(unit, "playerIndex", Integer.valueOf(owner));
		return unit;
	}

	private static CDestructable destructable() {
		final CDestructable destructable = TestObjects.allocate(CDestructable.class);
		TestObjects.set(destructable, "life", Float.valueOf(100f));
		return destructable;
	}

	private static void dead(final CWidget widget) {
		TestObjects.set(widget, "life", Float.valueOf(0f));
	}

	private static void units(final CSimulation game, final CUnit... units) {
		TestObjects.set(game, "units", new ArrayList<>(Arrays.asList(units)));
	}

	private static void destructables(final CSimulation game, final CDestructable... destructables) {
		TestObjects.set(game, "destructables", new ArrayList<>(Arrays.asList(destructables)));
	}
}
