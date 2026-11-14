package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** When a rejoining player is counted in again, and what its progress says. */
class LanRejoinRulesTest {
	@Test
	void caughtUpNeedsTheWholeArchivePlayedAndTheLiveTurnsNear() {
		// 100 archived turns (0..99); the host is at turn 130
		assertFalse(LanRejoin.caughtUp(60, 100, 130, 500, 499));
		assertFalse(LanRejoin.caughtUp(99, 100, 130, 500, 499));
		assertTrue(LanRejoin.caughtUp(128, 100, 130, 500, 499));
		assertTrue(LanRejoin.caughtUp(130, 100, 130, 500, 499));
		// steps of the last applied turn are still to be made
		assertFalse(LanRejoin.caughtUp(130, 100, 130, 500, 503));
		// nothing archived: the first turns are the live ones
		assertTrue(LanRejoin.caughtUp(-1, 0, 1, 0, -1));
	}

	@Test
	void progressIsAPercentOfTheArchiveAndNeverShowsHundred() {
		assertEquals(0, LanRejoin.percent(500, 0));
		assertEquals(42, LanRejoin.percent(420, 1000));
		assertEquals(99, LanRejoin.percent(1000, 1000));
		assertEquals(99, LanRejoin.percent(1200, 1000));
		assertEquals("12:40", LanRejoin.clock(12 * 60 * 20 + 40 * 20));
		assertEquals("0:05", LanRejoin.clock(100));
	}

	@Test
	void theProgressIsBroughtUpToDateEveryTwentySteps() {
		assertTrue(LanRejoin.progressDue(0));
		assertFalse(LanRejoin.progressDue(7));
		assertTrue(LanRejoin.progressDue(240));
	}
}
