package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;

/**
 * What a ticket says under its icon. The words are the panel's, but which word a state earns is a rule, so it is a test:
 * a ready ticket says nothing, an unaffordable one says it waits without being called stuck, and a missing requirement
 * names the thing that is missing instead of leaving the player to guess.
 */
class MacroStatusLineTest {
	private static final War3ID GRUNT = War3ID.fromString("ogru");
	private static final War3ID STRONGHOLD = War3ID.fromString("ostr");

	private static String name(final War3ID rawcode) {
		return rawcode.equals(STRONGHOLD) ? "Stronghold" : rawcode.asStringValue();
	}

	@Test
	void aReadyTicketSaysNothing() {
		assertEquals("", MacroStatusLine.of(TicketState.ready(0, MacroTicket.train(GRUNT)), MacroStatusLineTest::name));
	}

	@Test
	void anUnaffordableTicketSaysItWaitsAndIsNeverStuck() {
		final TicketState waiting = TicketState.waiting(1, MacroTicket.train(GRUNT));
		assertFalse(waiting.isStuck());
		assertEquals("wait", MacroStatusLine.of(waiting));
	}

	@Test
	void anOrderAlreadyOutIsNotAWaitForGold() {
		assertEquals("ordered", MacroStatusLine.of(TicketState.pending(2, MacroTicket.train(GRUNT))));
	}

	@Test
	void aMissingRequirementNamesTheThingThatIsMissing() {
		final TicketState locked = TicketState.stuck(3, MacroTicket.build(STRONGHOLD),
				Stuck.missing(STRONGHOLD, 0));
		assertEquals("needs Stronghold", MacroStatusLine.of(locked, MacroStatusLineTest::name));
	}

	@Test
	void everyStuckReasonHasWordsOfItsOwn() {
		for (final Stuck.Reason reason : Stuck.Reason.values()) {
			final String line = MacroStatusLine.of(TicketState.stuck(0, MacroTicket.train(GRUNT), Stuck.of(reason)));
			assertFalse(line.isEmpty(), "no words for " + reason);
			assertFalse(line.contains("_"), "a raw enum leaked into the panel: " + line);
		}
	}
}
