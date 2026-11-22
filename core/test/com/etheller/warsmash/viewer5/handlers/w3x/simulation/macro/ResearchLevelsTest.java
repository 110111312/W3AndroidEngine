package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * How long a research ticket lives, as arithmetic: it asks for the next level until the levels run out, and the two
 * things that can stop it are the data's own level count and the mission's cap.
 * <p>
 * This mattered on The Broken Isles, where the campaign caps Strength of the Moon and Moon Armor at 2 by
 * {@code SetPlayerTechMaxAllowed}, and the plan used to buy level 1 and retire, leaving 2000 gold in the bank.
 */
class ResearchLevelsTest {
	private static final int THREE_LEVELS = 3;
	private static final int UNLIMITED = -1;

	@Test
	void aResearchWithLevelsLeftIsBoughtAgain() {
		assertTrue(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, UNLIMITED, 0));
		assertTrue(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, UNLIMITED, 2));
	}

	@Test
	void theDataStopsItWhenTheMissionDoesNot() {
		// -1 means the mission sets no cap, so the third level is the last one.
		assertFalse(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, UNLIMITED, 3));
	}

	@Test
	void theMissionCanStopItEarly() {
		// SetPlayerTechMaxAllowed(Player(1), 'Resm', 2): the second level is the last one, whatever the data allows.
		assertTrue(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, 2, 1));
		assertFalse(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, 2, 2));
	}

	@Test
	void aCapOfZeroWasNeverSeededButIsStillNo() {
		// The seed drops a ticket the mission allows none of, so this cannot reach a plan; the sum agrees anyway.
		assertFalse(BuildingPlanExecutor.anotherLevel(THREE_LEVELS, 0, 0));
	}

	@Test
	void aRawcodeTheDataDoesNotKnowHasNothingToBuy() {
		assertFalse(BuildingPlanExecutor.anotherLevel(0, UNLIMITED, 0));
		assertFalse(BuildingPlanExecutor.anotherLevel(0, 2, 0));
	}
}
