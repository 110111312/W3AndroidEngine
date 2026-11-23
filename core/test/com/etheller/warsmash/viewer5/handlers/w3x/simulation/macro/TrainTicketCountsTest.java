package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Two grunt tickets are two grunts.
 * <p>
 * The executor records, per ticket, the count that means "my unit arrived". With one order for the same unit already
 * out, the second ticket has to expect the fourth grunt rather than the third, or it would retire on the first ticket's
 * grunt and leave the base one short -- the same way a research ticket expects its own level and not "any level".
 */
class TrainTicketCountsTest {
	@Test
	void theFirstTicketExpectsOneMoreThanYouHave() {
		assertEquals(3, MacroPlanExecutor.expectedCount(2, 0));
		assertEquals(1, MacroPlanExecutor.expectedCount(0, 0));
	}

	@Test
	void theSecondTicketOfTheSameUnitExpectsTheOneAfterThat() {
		assertEquals(4, MacroPlanExecutor.expectedCount(2, 1));
		assertEquals(6, MacroPlanExecutor.expectedCount(2, 3));
	}

	@Test
	void aTicketIsDoneOnlyWhenItsOwnUnitHasArrived() {
		// The comparison the executor makes: owned >= target.
		assertFalse(3 >= MacroPlanExecutor.expectedCount(2, 1));
		assertTrue(4 >= MacroPlanExecutor.expectedCount(2, 1));
	}
}
