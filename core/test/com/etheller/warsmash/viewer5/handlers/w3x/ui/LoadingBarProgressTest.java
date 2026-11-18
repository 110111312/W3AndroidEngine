package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The one ratio of the map loading bar: the menu's preview fills the first quarter while the menu is built, the map
 * loader fills the rest, and neither pass can move the bar back — that is what made it read as filling twice.
 */
class LoadingBarProgressTest {
	private static final float EPSILON = 1.0e-6f;

	@Test
	void thePreviewFillsTheFirstShareOfTheBarWhileTheMenuIsBuilt() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);

		bar.setPreview(0.5f);
		assertEquals(LoadingBarProgress.PREVIEW_BAR_SHARE * 0.5f, bar.getRatio(), EPSILON);

		bar.setPreview(1f);
		assertEquals(LoadingBarProgress.PREVIEW_BAR_SHARE, bar.getRatio(), EPSILON);
	}

	@Test
	void theMapLoaderGoesOnFromWhereThePreviewStopped() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setPreview(1f);

		bar.setCompletion(0.5f);
		assertEquals(0.625f, bar.getRatio(), EPSILON);

		bar.setCompletion(1f);
		assertEquals(1f, bar.getRatio(), EPSILON);
	}

	@Test
	void theBarDoesNotGoBackWhenThePassesReportOutOfTurn() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setCompletion(0.9f);
		final float loaded = bar.getRatio();

		bar.setPreview(0.4f);
		assertEquals(loaded, bar.getRatio(), EPSILON);
		assertTrue(bar.getRatio() >= loaded);
	}

	@Test
	void aPreviewedMapGoesOnFromThePartItsPreviewFilled() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setPreview(1f);

		bar.startLoad(true);
		assertEquals(LoadingBarProgress.PREVIEW_BAR_SHARE, bar.getRatio(), EPSILON);
		bar.setCompletion(0f);
		assertEquals(LoadingBarProgress.PREVIEW_BAR_SHARE, bar.getRatio(), EPSILON);
	}

	@Test
	void aMapWithoutAPreviewStartsAtTheBeginningAndLeavesTheOldShareBehind() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setPreview(1f);

		bar.startLoad(false);
		assertEquals(0f, bar.getRatio(), EPSILON);
		bar.setCompletion(0.5f);
		assertEquals(0.5f, bar.getRatio(), EPSILON);
	}

	@Test
	void aScreenThatWentKeepsNothing() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setPreview(1f);
		bar.setCompletion(1f);

		bar.reset();
		assertEquals(0f, bar.getRatio(), EPSILON);
		bar.setCompletion(0.25f);
		assertEquals(0.25f, bar.getRatio(), EPSILON);
	}

	@Test
	void aLoadedMapShowsTheBarFull() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);
		bar.setPreview(0.75f);

		bar.complete();
		assertEquals(1f, bar.getRatio(), EPSILON);
	}

	@Test
	void outOfRangeReportsStayOnTheBar() {
		final LoadingBarProgress bar = new LoadingBarProgress();
		bar.startLoad(false);

		bar.setPreview(-1f);
		assertEquals(0f, bar.getRatio(), EPSILON);
		bar.setPreview(Float.NaN);
		assertEquals(0f, bar.getRatio(), EPSILON);
		bar.setCompletion(2f);
		assertEquals(1f, bar.getRatio(), EPSILON);
	}
}
