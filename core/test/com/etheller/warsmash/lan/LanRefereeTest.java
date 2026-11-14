package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanProtocol;

/** What a referee may give a game: the pause, and nothing a player plays with. */
class LanRefereeTest {
	private static final int REFEREE = LanProtocol.WATCHER_SLOT_BASE + 2;

	private final LanCommandValidator validator = new LanCommandValidator();
	private final List<byte[]> sent = new ArrayList<byte[]>();

	@Test
	void theValidatorTakesOnlyThePauseFromAReferee() {
		final LanCommandSender sender = new LanCommandSender(this.sent::add);
		sender.setGamePaused(true);
		sender.issuePointOrder(1, 2, 3, 4f, 5f, false);
		sender.selectUnits(new int[0], new int[] { 1 });
		sender.chatMessage(0, -1, "hi");
		sender.minimapSignal(1f, 2f);
		assertEquals(5, this.sent.size());
		assertTrue(this.validator.accept(REFEREE, this.sent.get(0)));
		for (int i = 1; i < this.sent.size(); i++) {
			assertFalse(this.validator.accept(REFEREE, this.sent.get(i)), "command " + i);
			assertTrue(this.validator.accept(3, this.sent.get(i)), "a player gives command " + i);
		}
		assertTrue(this.validator.accept(3, this.sent.get(0)));
	}
}
