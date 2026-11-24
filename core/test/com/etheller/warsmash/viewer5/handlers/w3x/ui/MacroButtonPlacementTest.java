package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The two round macro buttons hang above the hero tray, and the first build of them sat on the ultimate's learn badge
 * because the gap was tuned by eye against a screenshot. These pin the arithmetic that replaced the eye: the pair
 * starts a clear {@link MacroLayout#BUTTON_AIR} above the topmost point the tray draws at, and the whole pair still
 * fits in the interface.
 */
class MacroButtonPlacementTest {
	/** The interface is 360dp tall on every phone this runs on; the panel's buttons must live inside it. */
	private static final float INTERFACE_HEIGHT = 360f;
	private static final float RADIUS = 20f;

	@Test
	public void theTrayTopIsItsUltimatesLearnBadge() {
		// 149dp ring at 100 degrees, plus the 15dp badge offset and its 11dp radius: the circles are lower than this
		final float top = HeroTray.clusterTopDp();
		assertTrue((top > 170f) && (top < 175f), "tray top was " + top);
	}

	@Test
	public void theLowerButtonClearsTheTrayBadgeByTheDocumentedAir() {
		final float centre = MacroLayout.buttonCentreDp(0, HeroTray.clusterTopDp(), RADIUS);
		final float bottom = centre - RADIUS;
		assertEquals(HeroTray.clusterTopDp() + MacroLayout.BUTTON_AIR, bottom);
		assertTrue(bottom > (HeroTray.clusterTopDp() + 40f), "the lower button would sit on the tray");
	}

	@Test
	public void theTwoButtonsDoNotTouchEachOther() {
		final float lower = MacroLayout.buttonCentreDp(0, HeroTray.clusterTopDp(), RADIUS);
		final float upper = MacroLayout.buttonCentreDp(1, HeroTray.clusterTopDp(), RADIUS);
		assertEquals((2f * RADIUS) + MacroLayout.BUTTON_GAP, upper - lower, 0.001f);
	}

	@Test
	public void thePairStillFitsUnderTheResourceBar() {
		final float top = MacroLayout.buttonCentreDp(1, HeroTray.clusterTopDp(), RADIUS) + RADIUS;
		assertTrue(top < (INTERFACE_HEIGHT - 26f), "the upper button poked through the top at " + top);
	}
}
