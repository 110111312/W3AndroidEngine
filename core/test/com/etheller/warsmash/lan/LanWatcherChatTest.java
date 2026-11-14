package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.ParticipantInfo;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;

/** What a game knows of its watchers: who they are, and the lines they say outside the turns. */
class LanWatcherChatTest {
	private static LanGameSetup setup() {
		final LanGameSetup setup = new LanGameSetup();
		final int[] roles = { LanProtocol.ROLE_PLAYER, LanProtocol.ROLE_REFEREE, LanProtocol.ROLE_OBSERVER };
		final String[] names = { "Host", "Ref", "Obs" };
		for (int i = 0; i < roles.length; i++) {
			final ParticipantInfo p = new ParticipantInfo();
			p.id = i + 1;
			p.name = names[i];
			p.role = roles[i];
			p.slot = i == 0 ? 0 : LanProtocol.NO_SLOT;
			setup.participants.add(p);
		}
		setup.hostOrder = new int[] { 1, 2, 3 };
		return setup;
	}

	private static LanPlay play(final int me) {
		return new LanPlay(setup(), me, new LanTimings(), vote -> {
		}, () -> 0L);
	}

	@Test
	void roles() {
		assertFalse(play(1).isWatcher());
		assertTrue(play(2).isWatcher());
		assertFalse(play(2).isObserver());
		assertTrue(play(3).isWatcher());
		assertTrue(play(3).isObserver());
		assertEquals("Obs", play(3).localName());
	}

	@Test
	void linesOfWatchersAreQueuedUnderTheirNames() {
		final LanPlay play = play(1);
		play.onWatcherChat(3, LanProtocol.CHAT_OBSERVERS, "hello");
		final LanPlay.WatcherChat chat = play.pollChat();
		assertEquals("Obs", chat.name);
		assertEquals(LanProtocol.CHAT_OBSERVERS, chat.channel);
		assertEquals("hello", chat.text);
		assertNull(play.pollChat());
	}

	@Test
	void ownEchoAndPlayersLinesAreDropped() {
		final LanPlay play = play(2);
		play.onWatcherChat(2, LanProtocol.CHAT_ALL, "mine");
		play.onWatcherChat(1, LanProtocol.CHAT_ALL, "a player's line comes as a command");
		assertNull(play.pollChat());
	}
}
