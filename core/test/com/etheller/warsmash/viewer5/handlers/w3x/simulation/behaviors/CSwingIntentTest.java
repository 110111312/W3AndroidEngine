package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * One tap, one swing. The rule the mobile grammars use: a sustained order swings until the target dies, a one-tap
 * intent swings once and then waits for the player — and a tap that arrives while a swing is still running is
 * remembered rather than lost.
 */
class CSwingIntentTest {
	@Test
	void aSustainedOrderAlwaysMaySwingAgain() {
		final CSwingIntent sustained = new CSwingIntent(false);

		assertTrue(sustained.nextSwingAllowed());
		assertTrue(sustained.nextSwingAllowed());
		assertFalse(sustained.isOneSwing());
	}

	@Test
	void aOneTapIntentSwingsOnceAndThenWaits() {
		final CSwingIntent intent = new CSwingIntent(true);

		assertTrue(intent.isOneSwing());
		assertFalse(intent.nextSwingAllowed());
	}

	@Test
	void oneTapBuysOneSwing() {
		final CSwingIntent intent = new CSwingIntent(true);
		intent.arm();

		assertTrue(intent.nextSwingAllowed());
		assertFalse(intent.nextSwingAllowed());
	}

	@Test
	void aTapSpamsIntoNoMoreThanOneSwing() {
		final CSwingIntent intent = new CSwingIntent(true);
		intent.arm();
		intent.arm();
		intent.arm();

		assertTrue(intent.nextSwingAllowed());
		// The surge of taps bought the one swing it was worth, and the next question is the player's again.
		assertFalse(intent.nextSwingAllowed());
		assertFalse(intent.isArmed());
	}

	@Test
	void aTapArrivingDuringASwingIsRememberedUntilTheSwingIsOver() {
		final CSwingIntent intent = new CSwingIntent(true);
		intent.arm();

		// Nothing asks "may I swing" while the swing in flight runs; the arm simply sits there.
		assertTrue(intent.isArmed());
		assertTrue(intent.nextSwingAllowed());
	}

	@Test
	void aQuickDoubleTapIsOneAttack() {
		final CSwingIntent intent = new CSwingIntent(true);

		// The second tap lands while the swing the first one bought is still in its windup, before the hit.
		assertFalse(intent.canArm(true, false));
		// Nothing was armed, so the swing that was already coming is the only one.
		assertFalse(intent.nextSwingAllowed());
	}

	@Test
	void aTapDuringTheBackswingBuysTheNextSwing() {
		final CSwingIntent intent = new CSwingIntent(true);

		// The hit has landed and the backswing is still playing: this tap means "again".
		assertTrue(intent.canArm(true, true));
	}

	@Test
	void aSustainedOrderIsNeverArmed() {
		final CSwingIntent sustained = new CSwingIntent(false);

		assertFalse(sustained.canArm(true, true));
		assertTrue(sustained.nextSwingAllowed());
	}

	@Test
	void armingASustainedOrderChangesNothing() {
		final CSwingIntent sustained = new CSwingIntent(false);
		sustained.arm();

		assertTrue(sustained.nextSwingAllowed());
		assertTrue(sustained.nextSwingAllowed());
	}
}
