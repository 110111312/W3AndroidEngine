package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Which games the launcher shows the result of, and which end on the score screen. */
class MeleeUIScoreFlowTest {
	@Test
	void campaignMissionResultIsTheLaunchers() {
		assertTrue(MeleeUI.resultShownByLauncher(true, 0));
		assertTrue(MeleeUI.resultShownByLauncher(true, 7));
	}

	@Test
	void customAndLoadedGamesEndOnTheScoreScreen() {
		assertFalse(MeleeUI.resultShownByLauncher(true, -1));
		assertFalse(MeleeUI.resultShownByLauncher(false, -1));
	}

	@Test
	void withoutLauncherNothingIsShownOutside() {
		assertFalse(MeleeUI.resultShownByLauncher(false, 3));
	}
}
