package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The stick's geometry and the cadence of the orders it produces. Both are pure Java, so the dead zone, the
 * direction and "re-issue on a turn or on the clock" are checked here rather than on a phone.
 */
class HeroStickTest {
	private static final float DEAD_ZONE_PX = 18f;
	private static final float RADIUS_PX = 110f;
	private static final float KNOB_RADIUS_PX = 42f;
	private static final float TURN_DEGREES = 20f;
	private static final long REISSUE_MS = 250L;

	private static TouchSettings settings() {
		return new TouchSettings(10f, 4f, 300L, 300L, 150L, 250L, 1.3f, 1.4f, 56f, 48f, 96f,
				PalmFilter.PenFingerPolicy.IGNORE, PalmFilter.SizeRejection.AUTO, ControlScheme.TOUCH,
				TouchStyle.DOUBLE_TAP, 250L, 32f, PenButton.SHIFT, DEAD_ZONE_PX, RADIUS_PX, KNOB_RADIUS_PX, 300f,
				TURN_DEGREES, REISSUE_MS);
	}

	@Test
	void reportsNoDirectionUntilTheDragLeavesTheDeadZone() {
		final HeroStick stick = new HeroStick(settings());
		stick.begin(100f, 100f);
		assertTrue(stick.isActive());
		assertFalse(stick.hasDirection());

		stick.move(105f, 105f);
		assertFalse(stick.hasDirection());
		assertEquals(0f, stick.getDirectionX());
		assertEquals(0f, stick.getDirectionY());

		stick.move(140f, 100f);
		assertTrue(stick.hasDirection());
		assertEquals(1f, stick.getDirectionX());
		assertEquals(0f, stick.getDirectionY());
	}

	@Test
	void givesTheSameDirectionForAShortDragAndALongOne() {
		final HeroStick shortDrag = new HeroStick(settings());
		shortDrag.begin(100f, 100f);
		shortDrag.move(140f, 100f);

		final HeroStick longDrag = new HeroStick(settings());
		longDrag.begin(100f, 100f);
		longDrag.move(300f, 100f);

		assertEquals(shortDrag.getDirectionX(), longDrag.getDirectionX());
		assertEquals(shortDrag.getDirectionY(), longDrag.getDirectionY());
		assertEquals(1f, longDrag.getDirectionX());
	}

	@Test
	void normalisesADiagonalDragToAUnitVector() {
		final HeroStick stick = new HeroStick(settings());
		stick.begin(100f, 100f);
		stick.move(130f, 140f);

		assertTrue(stick.hasDirection());
		assertEquals(0.6f, stick.getDirectionX(), 1e-6f);
		assertEquals(0.8f, stick.getDirectionY(), 1e-6f);
	}

	@Test
	void drawsTheKnobInsideTheRingHoweverFarTheFingerGoes() {
		final HeroStick stick = new HeroStick(settings());
		stick.begin(100f, 100f);

		stick.move(110f, 100f);
		assertEquals(10f, stick.getKnobOffsetX(), 1e-6f);
		assertEquals(0f, stick.getKnobOffsetY(), 1e-6f);

		final float maxOffset = RADIUS_PX - KNOB_RADIUS_PX * 0.5f;
		stick.move(1100f, 100f);
		assertEquals(maxOffset, stick.getKnobOffsetX(), 1e-6f);
		assertEquals(0f, stick.getKnobOffsetY(), 1e-6f);

		stick.move(100f, 1100f);
		assertEquals(0f, stick.getKnobOffsetX(), 1e-6f);
		assertEquals(maxOffset, stick.getKnobOffsetY(), 1e-6f);
	}

	@Test
	void releaseClearsTheStick() {
		final HeroStick stick = new HeroStick(settings());
		stick.begin(100f, 100f);
		stick.move(200f, 100f);
		assertTrue(stick.hasDirection());

		stick.end();
		assertFalse(stick.isActive());
		assertFalse(stick.hasDirection());
		assertEquals(0f, stick.getDirectionX());
		assertEquals(0f, stick.getDirectionY());
		assertEquals(0f, stick.getKnobOffsetX());
		assertEquals(0f, stick.getKnobOffsetY());
	}

	@Test
	void issuesOnTheFirstDirection() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());

		assertTrue(emitter.tick(1f, 0f, 0L));
		assertTrue(emitter.hasIssued());
		assertEquals(1f, emitter.getLastDirectionX());
		assertEquals(0f, emitter.getLastDirectionY());
	}

	@Test
	void keepsTheSameDirectionQuietUntilTheReissueCadence() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());
		assertTrue(emitter.tick(1f, 0f, 0L));

		assertFalse(emitter.tick(1f, 0f, 100L));
		assertTrue(emitter.tick(1f, 0f, 100L + REISSUE_MS));
	}

	@Test
	void reissuesASmallTurnOnlyOnTheCadenceButALargeTurnAtOnce() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());
		assertTrue(emitter.tick(1f, 0f, 0L));

		final double smallTurn = Math.toRadians(5);
		assertFalse(emitter.tick((float) Math.cos(smallTurn), (float) Math.sin(smallTurn), 100L));

		final double largeTurn = Math.toRadians(25);
		assertTrue(emitter.tick((float) Math.cos(largeTurn), (float) Math.sin(largeTurn), 100L));
	}

	@Test
	void treatsOppositeDirectionsAsAFullTurn() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());
		assertTrue(emitter.tick(1f, 0f, 0L));

		assertTrue(emitter.tick(-1f, 0f, 100L));
		assertEquals(-1f, emitter.getLastDirectionX());
	}

	@Test
	void neverIssuesWithoutADirection() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());

		assertFalse(emitter.tick(0f, 0f, 0L));
		assertFalse(emitter.hasIssued());

		assertTrue(emitter.tick(1f, 0f, 0L));
		assertFalse(emitter.tick(0f, 0f, 10000L));
		assertEquals(1f, emitter.getLastDirectionX());
		assertEquals(0f, emitter.getLastDirectionY());
	}

	@Test
	void resetMakesTheNextDirectionIssueAgain() {
		final HeroStickOrderEmitter emitter = new HeroStickOrderEmitter(settings());
		assertTrue(emitter.tick(1f, 0f, 0L));
		assertFalse(emitter.tick(1f, 0f, 100L));

		emitter.reset();
		assertFalse(emitter.hasIssued());
		assertTrue(emitter.tick(1f, 0f, 100L));
	}
}
