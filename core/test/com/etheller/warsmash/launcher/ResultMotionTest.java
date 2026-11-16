package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The motion of the Reforged campaign result and the small capitals of its text. */
class ResultMotionTest {
	@Test
	void bannerFallsFromAboveAndSettles() {
		assertEquals(-1f, ResultMotion.bannerOffset(ResultMotion.BANNER_DELAY_MS), 1e-6f);
		assertEquals(0f, ResultMotion.bannerOffset(ResultMotion.BANNER_DELAY_MS + ResultMotion.BANNER_SLIDE_MS), 1e-6f);
		assertEquals(-0.01f, ResultMotion.bannerOffset(ResultMotion.BANNER_DELAY_MS + 360), 0.005f);
		float last = -1;
		for (long t = 0; t <= 360; t += 10) {
			final float offset = ResultMotion.bannerOffset(ResultMotion.BANNER_DELAY_MS + t);
			assertTrue(offset >= last - 1e-6f, "falls without going back");
			last = offset;
		}
	}

	@Test
	void contentFadesAfterTheBanner() {
		assertEquals(0f, ResultMotion.contentAlpha(ResultMotion.CONTENT_DELAY_MS), 1e-6f);
		assertEquals(0.5f, ResultMotion.contentAlpha(ResultMotion.CONTENT_DELAY_MS + (ResultMotion.CONTENT_FADE_MS / 2)),
				1e-6f);
		assertEquals(1f, ResultMotion.contentAlpha(ResultMotion.totalMillis()), 1e-6f);
	}

	@Test
	void easingStartsSlowAndKeepsItsEnds() {
		assertEquals(0f, ResultMotion.ease(0), 1e-6f);
		assertEquals(1f, ResultMotion.ease(1), 1e-6f);
		assertTrue(ResultMotion.ease(0.5f) > 0.8f, "the glue's curve is steep at the start");
	}

	@Test
	void smallLettersBecomeSmallCapitals() {
		final List<SmallCaps.Run> runs = SmallCaps.runs("Units Killed");
		assertEquals(4, runs.size());
		assertEquals("U", runs.get(0).text);
		assertEquals(false, runs.get(0).small);
		assertEquals("NITS ", runs.get(1).text);
		assertEquals(true, runs.get(1).small);
		assertEquals("K", runs.get(2).text);
		assertEquals("ILLED", runs.get(3).text);
	}
}
