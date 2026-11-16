package net.warsmash.lan.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanData.GameInfo;

/** The host's portrait has its own datagram, so the announcement keeps the layout older builds accept. */
class LanAnnouncePortraitTest {
	private static GameInfo info(final String portrait) {
		final GameInfo g = new GameInfo();
		g.gameId = 9;
		g.hostName = "Thrall";
		g.gameName = "Game";
		g.mapName = "Maps\\X.w3x";
		g.mapSha256 = SampleMessages.bytes(32, 4);
		g.players = 1;
		g.maxPlayers = 4;
		g.tcpPort = 6112;
		g.speed = 3;
		g.portrait = portrait;
		return g;
	}

	@Test
	void theAnnouncementDoesNotCarryThePortrait() throws Exception {
		final byte[] with = LanCodec.encodeDatagram(new LanCodec.GameAnnounce(info("c:BTNArthas")));
		final byte[] without = LanCodec.encodeDatagram(new LanCodec.GameAnnounce(info("")));
		assertEquals(without.length, with.length);
		final GameInfo got = ((LanCodec.GameAnnounce) LanCodec.decodeDatagram(with, 0, with.length)).info;
		assertEquals("", got.portrait);
		assertEquals("Thrall", got.hostName);
	}

	@Test
	void thePortraitDatagramRoundTrips() throws Exception {
		final byte[] b = LanCodec.encodeDatagram(new LanCodec.GamePortrait(9, "c:BTNArthas"));
		final LanCodec.GamePortrait p = (LanCodec.GamePortrait) LanCodec.decodeDatagram(b, 0, b.length);
		assertEquals(9, p.gameId);
		assertEquals("c:BTNArthas", p.portrait);
	}

	@Test
	void aCutOrOverlongPortraitDatagramIsAnError() throws Exception {
		final byte[] b = LanCodec.encodeDatagram(new LanCodec.GamePortrait(9, "r:17"));
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeDatagram(b, 0, b.length - 1));
		final byte[] extra = java.util.Arrays.copyOf(b, b.length + 1);
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeDatagram(extra, 0, extra.length));
	}

	@Test
	void theLongestPortraitFitsADatagram() throws Exception {
		final String p = "\u00e4".repeat(LanProtocol.MAX_PORTRAIT);
		final byte[] b = LanCodec.encodeDatagram(new LanCodec.GamePortrait(9, p));
		assertTrue(b.length <= LanProtocol.MAX_DATAGRAM);
		assertEquals(p, ((LanCodec.GamePortrait) LanCodec.decodeDatagram(b, 0, b.length)).portrait);
	}

	@Test
	void aPortraitWithControlCharactersIsRefused() throws Exception {
		final byte[] b = LanCodec.encodeDatagram(new LanCodec.GamePortrait(9, "c:\u0001"));
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeDatagram(b, 0, b.length));
	}
}
