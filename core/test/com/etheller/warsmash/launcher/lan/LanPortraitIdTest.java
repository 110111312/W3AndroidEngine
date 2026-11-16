package com.etheller.warsmash.launcher.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.launcher.SkinArt;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

class LanPortraitIdTest {
	@Test
	void aClassicPortraitIsNamedByItsButton() {
		final String id = LanPortraitId.encode(1, false);
		assertEquals("c:BTNArthas", id);
		assertEquals(1, LanPortraitId.decode(id, false));
	}

	@Test
	void everyClassicPortraitRoundTripsAndFitsTheWire() {
		for (int i = 0; i < SkinArt.portraitCount(false); i++) {
			final String id = LanPortraitId.encode(i, false);
			assertTrue(id.length() <= LanProtocol.MAX_PORTRAIT, id);
			assertEquals(i, LanPortraitId.decode(id, false));
		}
	}

	@Test
	void aReforgedPortraitIsNamedByItsNumberFromOne() {
		assertEquals("r:17", LanPortraitId.encode(16, true));
		assertEquals(16, LanPortraitId.decode("r:17", true));
		assertEquals(0, LanPortraitId.decode("r:1", true));
		assertEquals(-1, LanPortraitId.decode("r:0", true));
		assertEquals(-1, LanPortraitId.decode("r:" + (SkinArt.portraitCount(true) + 1), true));
	}

	@Test
	void theOtherSkinsPortraitIsNotShownAsSomethingElse() {
		assertEquals(-1, LanPortraitId.decode(LanPortraitId.encode(3, false), true));
		assertEquals(-1, LanPortraitId.decode(LanPortraitId.encode(3, true), false));
		assertNotEquals("", LanPortraitId.encode(3, false));
	}

	@Test
	void numbersOfEarlierBuildsStayReadable() {
		assertEquals(7, LanPortraitId.decode("7", false));
		assertEquals(7, LanPortraitId.decode(" 7 ", true));
		assertEquals(0, LanPortraitId.decode("0", false));
		assertEquals(-1, LanPortraitId.decode("-4", false));
		assertEquals(-1, LanPortraitId.decode("", false));
		assertEquals(-1, LanPortraitId.decode(null, false));
		assertEquals(-1, LanPortraitId.decode("another launcher name", false));
		assertEquals(-1, LanPortraitId.decode("c:NoSuchButton", false));
	}

	@Test
	void noPortraitIsSentForNone() {
		assertEquals("", LanPortraitId.encode(-1, false));
		assertEquals("", LanPortraitId.encode(-1, true));
		assertEquals("", LanPortraitId.encode(SkinArt.portraitCount(true), true));
		assertEquals("", LanPortraitId.encode(10_000, false));
	}

	@Test
	void theLobbyShowsTheIdentityInTheViewersSkin() {
		final LanMessages.LobbyState s = new LanMessages.LobbyState();
		s.ownerId = 0;
		s.map.sha256 = new byte[32];
		for (int i = 0; i < 3; i++) {
			final LanData.SlotInfo slot = new LanData.SlotInfo();
			slot.index = i;
			slot.team = i;
			slot.kind = LanProtocol.SLOT_HUMAN;
			slot.participantId = i;
			slot.name = "P" + i;
			s.slots.add(slot);
		}
		s.portraits.put(0, LanPortraitId.encode(1, false));
		s.portraits.put(1, LanPortraitId.encode(40, true));
		s.portraits.put(2, "5");
		final LanLobbyView classic = LanLobbyView.of(s, 0, null, -1, false);
		assertEquals(1, classic.rows.get(0).portrait);
		assertEquals(-1, classic.rows.get(1).portrait);
		assertEquals(5, classic.rows.get(2).portrait);
		final LanLobbyView reforged = LanLobbyView.of(s, 0, null, -1, true);
		assertEquals(-1, reforged.rows.get(0).portrait);
		assertEquals(40, reforged.rows.get(1).portrait);
		assertEquals(5, reforged.rows.get(2).portrait);
	}
}
