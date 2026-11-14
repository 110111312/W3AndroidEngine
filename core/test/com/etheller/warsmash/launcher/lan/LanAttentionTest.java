package com.etheller.warsmash.launcher.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.launcher.ButtonFlash;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

class LanAttentionTest {
	private static final int ME = 1;

	/** A lobby of four slots: the host (0) and this player (1), the rest open. */
	private static LanMessages.LobbyState lobby() {
		final LanMessages.LobbyState s = new LanMessages.LobbyState();
		s.ownerId = 0;
		s.options.gameName = "Game";
		s.map.name = "Map.w3x";
		s.map.sha256 = new byte[32];
		for (int i = 0; i < 4; i++) {
			final LanData.SlotInfo slot = new LanData.SlotInfo();
			slot.index = i;
			slot.team = i;
			slot.color = i;
			if (i < 2) {
				slot.kind = LanProtocol.SLOT_HUMAN;
				slot.participantId = i;
				slot.name = i == 0 ? "Thrall" : "Player";
				slot.ready = true;
			}
			s.slots.add(slot);
		}
		return s;
	}

	private static LanMessages.LobbyState copy(final LanMessages.LobbyState a) {
		final LanMessages.LobbyState s = new LanMessages.LobbyState();
		s.rev = a.rev + 1;
		s.ownerId = a.ownerId;
		s.options = a.options.copy();
		s.map = a.map;
		s.syncWarning = a.syncWarning;
		for (final LanData.SlotInfo x : a.slots) {
			s.slots.add(x.copy());
		}
		s.observers.addAll(a.observers);
		return s;
	}

	private static List<LanAttention.Event> diff(final LanMessages.LobbyState before,
			final LanMessages.LobbyState after) {
		return LanAttention.diff(before, after, ME, false);
	}

	@Test
	void theFirstLobbyIsNotAChange() {
		assertTrue(LanAttention.diff(null, lobby(), ME, false).isEmpty());
	}

	@Test
	void aPlayerJoining() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		b.slots.get(2).kind = LanProtocol.SLOT_HUMAN;
		b.slots.get(2).participantId = 5;
		assertTrue(diff(a, b).contains(LanAttention.Event.JOINED));
	}

	@Test
	void anObserverJoiningCounts() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		final LanData.ObserverInfo o = new LanData.ObserverInfo();
		o.participantId = 7;
		b.observers.add(o);
		assertTrue(diff(a, b).contains(LanAttention.Event.JOINED));
	}

	@Test
	void aPlayerLeavingAndTheHostChanging() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		b.slots.get(0).kind = LanProtocol.SLOT_OPEN;
		b.ownerId = ME;
		final List<LanAttention.Event> events = diff(a, b);
		assertTrue(events.contains(LanAttention.Event.LEFT));
		assertTrue(events.contains(LanAttention.Event.HOST_CHANGED));
		assertTrue(events.contains(LanAttention.Event.SLOTS_CHANGED));
	}

	@Test
	void theMapAndTheSettings() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState map = copy(a);
		map.map = new LanData.MapInfo();
		map.map.name = "Other.w3x";
		assertTrue(diff(a, map).contains(LanAttention.Event.MAP_CHANGED));
		final LanMessages.LobbyState speed = copy(a);
		speed.options.speed = 3;
		assertTrue(diff(a, speed).contains(LanAttention.Event.OPTIONS_CHANGED));
		final LanMessages.LobbyState observers = copy(a);
		observers.options.observers = LanProtocol.OBSERVERS_REFEREES;
		assertTrue(diff(a, observers).contains(LanAttention.Event.OPTIONS_CHANGED));
		final LanMessages.LobbyState sync = copy(a);
		sync.syncWarning = true;
		assertTrue(diff(a, sync).contains(LanAttention.Event.OPTIONS_CHANGED));
	}

	@Test
	void theSlots() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState computer = copy(a);
		computer.slots.get(2).kind = LanProtocol.SLOT_COMPUTER;
		assertTrue(diff(a, computer).contains(LanAttention.Event.SLOTS_CHANGED));
		final LanMessages.LobbyState closed = copy(a);
		closed.slots.get(3).kind = LanProtocol.SLOT_CLOSED;
		assertTrue(diff(a, closed).contains(LanAttention.Event.SLOTS_CHANGED));
		final LanMessages.LobbyState team = copy(a);
		team.slots.get(0).team = 1;
		assertTrue(diff(a, team).contains(LanAttention.Event.SLOTS_CHANGED));
		final LanMessages.LobbyState race = copy(a);
		race.slots.get(0).race = 2;
		assertTrue(diff(a, race).contains(LanAttention.Event.SLOTS_CHANGED));
	}

	@Test
	void ownChoicesAreNotAChange() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		b.slots.get(1).race = 3;
		b.slots.get(1).color = 5;
		assertTrue(diff(a, b).isEmpty());
	}

	@Test
	void ownReadyClearedByTheSettings() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		b.slots.get(1).ready = false;
		assertTrue(LanAttention.diff(a, b, ME, false).contains(LanAttention.Event.OWN_READY_CLEARED));
		assertFalse(LanAttention.diff(a, b, ME, true).contains(LanAttention.Event.OWN_READY_CLEARED));
	}

	@Test
	void anotherPlayersReadyPingAndSchemeAreNoReason() {
		final LanMessages.LobbyState a = lobby();
		final LanMessages.LobbyState b = copy(a);
		b.slots.get(0).ready = false;
		b.slots.get(0).pingMs = 40;
		b.slots.get(0).controlScheme = LanProtocol.CONTROL_KBM;
		assertEquals(Arrays.asList(LanAttention.Event.READY_OR_PING), diff(a, b));
		assertFalse(LanAttention.Event.READY_OR_PING.flashes);
	}

	@Test
	void whichEventsFlash() {
		for (final LanAttention.Event e : LanAttention.Event.values()) {
			final boolean silent = (e == LanAttention.Event.CHAT) || (e == LanAttention.Event.READY_OR_PING);
			assertEquals(!silent, e.flashes, e.name());
		}
	}

	@Test
	void anEventOnAnotherPageStartsTheFlash() {
		final LanAttention attention = new LanAttention();
		attention.setPageShown(false);
		assertFalse(attention.isUnseen());
		attention.onEvent(LanAttention.Event.JOINED);
		assertTrue(attention.isUnseen());
		assertTrue(attention.isFlashing());
		assertEquals("Lobby changed", attention.description());
	}

	@Test
	void anEventOnTheLanPageIsSeenAtOnce() {
		final LanAttention attention = new LanAttention();
		attention.setPageShown(true);
		attention.onEvent(LanAttention.Event.JOINED);
		assertFalse(attention.isUnseen());
		assertNull(attention.description());
	}

	@Test
	void openingThePageEndsTheFlashAndTheDot() {
		final LanAttention attention = new LanAttention();
		attention.onEvent(LanAttention.Event.MAP_CHANGED);
		attention.onEvent(LanAttention.Event.CHAT);
		assertTrue(attention.isUnseen());
		assertTrue(attention.hasChatDot());
		attention.setPageShown(true);
		assertFalse(attention.isUnseen());
		assertFalse(attention.hasChatDot());
		assertFalse(attention.isLit());
	}

	@Test
	void chatOnlyPutsADot() {
		final LanAttention attention = new LanAttention();
		attention.onEvent(LanAttention.Event.CHAT);
		assertFalse(attention.isUnseen());
		assertFalse(attention.isFlashing());
		assertTrue(attention.hasChatDot());
		attention.onEvent(LanAttention.Event.READY_OR_PING);
		assertFalse(attention.isUnseen());
	}

	@Test
	void theFlashLightsHalfASecondThenDarkAsTheQuestsButtonDoes() {
		final LanAttention attention = new LanAttention();
		attention.onEvent(LanAttention.Event.JOINED);
		assertTrue(attention.isLit());
		attention.update(0.4f);
		assertTrue(attention.isLit());
		attention.update(0.2f);
		assertFalse(attention.isLit());
		attention.update(0.5f);
		assertTrue(attention.isLit());
		attention.update(0.5f);
		assertFalse(attention.isLit());
	}

	@Test
	void aSecondEventDoesNotRestartTheFlash() {
		final LanAttention attention = new LanAttention();
		attention.onEvent(LanAttention.Event.JOINED);
		attention.update(0.6f);
		assertFalse(attention.isLit());
		attention.onEvent(LanAttention.Event.LEFT);
		assertFalse(attention.isLit());
	}

	@Test
	void reducedMotionKeepsTheHighlightSteady() {
		final LanAttention attention = new LanAttention();
		attention.setReducedMotion(true);
		attention.onEvent(LanAttention.Event.JOINED);
		assertTrue(attention.isUnseen());
		assertFalse(attention.isFlashing());
		for (int i = 0; i < 8; i++) {
			assertTrue(attention.isLit(), "step " + i);
			attention.update(0.3f);
		}
		assertEquals("Lobby changed", attention.description());
		attention.setPageShown(true);
		assertFalse(attention.isLit());
	}

	@Test
	void buttonFlashTimingMatchesTheGamesQuestButton() {
		// The formula MeleeUI had before the flash was shared, with its period.
		final float period = 0.5f;
		for (float t = 0; t < 12; t += 0.013f) {
			assertEquals(((int) (t / period) % 2) == 0, ButtonFlash.isLit(t), "t=" + t);
		}
		assertEquals(0.5f, ButtonFlash.PERIOD);
		assertFalse(ButtonFlash.isRunning(ButtonFlash.OFF));
		assertTrue(ButtonFlash.isRunning(0));
		assertTrue(ButtonFlash.isLit(0));
		assertFalse(ButtonFlash.isLit(0.5f));
		assertTrue(ButtonFlash.isLit(1.0f));
	}
}
