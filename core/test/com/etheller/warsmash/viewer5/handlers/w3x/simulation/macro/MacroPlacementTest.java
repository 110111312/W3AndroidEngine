package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Placement is the AI's ring walk, and it is the one part of the executor that could be got wrong without the phone
 * saying so -- a building that cannot be placed simply never appears. So the walk is proved here, against a probe that
 * is a lambda, rather than by watching a base fill up.
 */
class MacroPlacementTest {
	private static final int CELLS = 4;
	private static final float MIN_RADIUS = 256f;
	private static final float MAX_RADIUS = 1024f;
	private static final float GAP = 96f;

	private static float[] find(final float angle, final SiteProbe probe) {
		return MacroPlacement.findSite(0f, 0f, MIN_RADIUS, MAX_RADIUS, angle, CELLS, CELLS, GAP, false, probe);
	}

	private static SiteProbe nothingObstructed() {
		return (x, y) -> false;
	}

	@Test
	void theSearchStartsAtTheMinimumRadiusAtThePreferredAngle() {
		final List<float[]> probed = new ArrayList<>();
		final float[] site = MacroPlacement.findSite(0f, 0f, MIN_RADIUS, MAX_RADIUS, 0f, CELLS, CELLS, GAP, false,
				(x, y) -> {
					probed.add(new float[] { x, y });
					return false;
				});
		assertNotNull(site);
		// The first candidate is on the preferred angle, one minimum radius out: (256, 0) walking east.
		assertArrayEquals(new float[] { 256f, 0f }, probed.get(0));
		assertArrayEquals(new float[] { 256f, 0f }, site);
	}

	@Test
	void theFirstFreeSiteWins() {
		final float[] site = find(0f, (x, y) -> x < 300f);
		assertNotNull(site);
		assertTrue(site[0] >= 300f, "the search must settle on the first candidate that is free: " + site[0]);
	}

	@Test
	void aBlockedRingIsSkippedForTheNextRadius() {
		final float[] site = find(0f, (x, y) -> Math.hypot(x, y) <= 320f);
		assertNotNull(site);
		assertTrue(Math.hypot(site[0], site[1]) > 320f, "a fully blocked ring must be walked past");
	}

	@Test
	void everyCandidateIsOnTheBuildGrid() {
		final List<float[]> probed = new ArrayList<>();
		MacroPlacement.findSite(0f, 0f, MIN_RADIUS, MAX_RADIUS, 0.7f, CELLS, CELLS, GAP, false, (x, y) -> {
			probed.add(new float[] { x, y });
			return true;
		});
		for (final float[] point : probed) {
			// A four-cell footprint cuts to the 64 grid, exactly as the console's own placement cursor does.
			assertEquals(0f, point[0] % 64f, 0.001f, "x " + point[0] + " is off the build grid");
			assertEquals(0f, point[1] % 64f, 0.001f, "y " + point[1] + " is off the build grid");
		}
	}

	@Test
	void noSiteWithinTheMaximumRadiusReturnsNull() {
		assertNull(find(0f, (x, y) -> true));
	}

	@Test
	void theSameInputsGiveTheSameSiteTwice() {
		final SiteProbe probe = (x, y) -> (x > 400f) && (y > 100f);
		assertArrayEquals(find(1.1f, probe), find(1.1f, probe));
	}

	@Test
	void aBuildingThatIsMeantToBeBuiltOntoNeedsNoRoomAroundIt() {
		// A haunted gold mine goes onto a mine: the neighbourhood test would refuse every site, so it is skipped.
		final float[] withRoom = MacroPlacement.findSite(0f, 0f, MIN_RADIUS, MAX_RADIUS, 0f, CELLS, CELLS, GAP, true,
				(x, y) -> (!nothingObstructed().obstructed(x, y)) && (Math.abs(y) >= GAP));
		final float[] without = MacroPlacement.findSite(0f, 0f, MIN_RADIUS, MAX_RADIUS, 0f, CELLS, CELLS, GAP, false,
				(x, y) -> Math.abs(y) >= GAP);
		assertNull(withRoom, "with the gap test on, nothing on the east axis has room");
		assertNotNull(without, "with it off, the same point is accepted");
	}
}
