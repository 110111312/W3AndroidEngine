package net.warsmash.lan.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanData.GameInfo;

class LanCodecTest {
	private static List<LanMessage> decodeAll(final byte[] data, final int chunk) throws LanProtocolException {
		final List<LanMessage> out = new ArrayList<LanMessage>();
		final LanCodec.FrameDecoder d = new LanCodec.FrameDecoder();
		for (int i = 0; i < data.length; i += chunk) {
			d.feed(data, i, Math.min(chunk, data.length - i), new LanCodec.MessageSink() {
				@Override
				public void accept(final LanMessage m) {
					out.add(m);
				}
			});
		}
		return out;
	}

	@Test
	void everyMessageRoundTripsWithCanonicalBytes() throws Exception {
		final Set<Integer> seen = new HashSet<Integer>();
		for (final LanMessage m : SampleMessages.all()) {
			final byte[] frame = LanCodec.encodeFrame(m);
			final List<LanMessage> back = decodeAll(frame, frame.length);
			assertEquals(1, back.size(), "type " + m.type());
			assertEquals(m.type(), back.get(0).type());
			assertEquals(m.getClass(), back.get(0).getClass());
			assertArrayEquals(frame, LanCodec.encodeFrame(back.get(0)), "re-encoding of type " + m.type());
			seen.add(m.type());
		}
		assertEquals(new HashSet<Integer>(LanCodec.knownTypes()), seen, "samples must cover every message type");
	}

	@Test
	void aBigMessageIsEncodedInOneArrayOfItsOwnSize() throws Exception {
		final byte[] data = new byte[LanProtocol.MAX_MAP_CHUNK];
		java.util.Arrays.fill(data, (byte) 7);
		final LanMessages.MapChunk chunk = new LanMessages.MapChunk(1024, 1 << 20, data);
		final byte[] frame = LanCodec.encodeFrame(chunk);
		assertEquals(LanProtocol.FRAME_HEADER + chunk.sizeHint(), frame.length);
		final LanMessages.MapChunk back = (LanMessages.MapChunk) decodeAll(frame, frame.length).get(0);
		assertEquals(1024, back.offset);
		assertArrayEquals(data, back.data);
	}

	@Test
	void byteByByteDeliveryGivesTheSameMessages() throws Exception {
		final List<LanMessage> all = SampleMessages.all();
		int total = 0;
		final List<byte[]> frames = new ArrayList<byte[]>();
		for (final LanMessage m : all) {
			final byte[] f = LanCodec.encodeFrame(m);
			frames.add(f);
			total += f.length;
		}
		final byte[] stream = new byte[total];
		int pos = 0;
		for (final byte[] f : frames) {
			System.arraycopy(f, 0, stream, pos, f.length);
			pos += f.length;
		}
		for (final int chunk : new int[] { 1, 2, 3, 7, 64, 5000 }) {
			final List<LanMessage> back = decodeAll(stream, chunk);
			assertEquals(all.size(), back.size(), "chunk " + chunk);
			for (int i = 0; i < all.size(); i++) {
				assertArrayEquals(frames.get(i), LanCodec.encodeFrame(back.get(i)));
			}
		}
	}

	@Test
	void emptyMessageEndingTheChunkIsDeliveredImmediately() throws Exception {
		final byte[] frame = LanCodec.encodeFrame(new LanMessages.Go());
		assertEquals(LanProtocol.FRAME_HEADER, frame.length);
		assertEquals(1, decodeAll(frame, frame.length).size());
	}

	@Test
	void decodedFieldsKeepTheirValues() throws Exception {
		final LanMessages.Chat chat = (LanMessages.Chat) decodeAll(
				LanCodec.encodeFrame(new LanMessages.Chat(LanProtocol.CHAT_OBSERVERS, 4, "héllo")), 100).get(0);
		assertEquals(LanProtocol.CHAT_OBSERVERS, chat.channel);
		assertEquals(4, chat.from);
		assertEquals("héllo", chat.text);
		final LanMessages.Turn turn = (LanMessages.Turn) decodeAll(
				LanCodec.encodeFrame(new LanMessages.Turn(SampleMessages.turn(9))), 100).get(0);
		assertEquals(9, turn.turn.n);
		assertEquals(2, turn.turn.cmds.size());
		assertEquals(7, turn.turn.cmds.get(1).seq);
		assertEquals(LanProtocol.PRESENCE_ABSENT, turn.turn.presence.get(0).state);
	}

	@Test
	void helloOfAnotherProtocolVersionIsStillReadable() throws Exception {
		final ByteWriter w = new ByteWriter(100);
		w.u8(LanProtocol.HELLO).i32(0).i32(LanProtocol.MAGIC).u16(99).raw(new byte[] { 1, 2, 3, 4, 5 }, 0, 5);
		final byte[] frame = w.toByteArray();
		final int body = frame.length - LanProtocol.FRAME_HEADER;
		frame[4] = (byte) body;
		final LanMessages.Hello h = (LanMessages.Hello) decodeAll(frame, frame.length).get(0);
		assertEquals(99, h.protocolVersion);
	}

	@Test
	void helloWithBadMagicIsRejected() throws Exception {
		final ByteWriter w = new ByteWriter(100);
		w.u8(LanProtocol.HELLO).i32(6).i32(0x12345678).u16(1);
		final byte[] frame = w.toByteArray();
		assertThrows(LanProtocolException.class, () -> decodeAll(frame, frame.length));
	}

	@Test
	void unknownTypeAndTrailingBytesAreProtocolErrors() throws Exception {
		assertThrows(LanProtocolException.class,
				() -> decodeAll(new byte[] { (byte) 200, 0, 0, 0, 0 }, 5));
		final byte[] ok = LanCodec.encodeFrame(new LanMessages.Ready(true));
		final byte[] longer = new byte[ok.length + 1];
		System.arraycopy(ok, 0, longer, 0, ok.length);
		longer[4] = 2;
		assertThrows(LanProtocolException.class, () -> decodeAll(longer, longer.length));
	}

	@Test
	void oversizedAndNegativeLengthsAreRejectedBeforeAnyAllocation() {
		for (final int length : new int[] { LanProtocol.MAX_FRAME_BODY + 1, Integer.MAX_VALUE, -1, Integer.MIN_VALUE }) {
			final byte[] header = { (byte) LanProtocol.CMD, (byte) (length >>> 24), (byte) (length >>> 16),
					(byte) (length >>> 8), (byte) length };
			assertThrows(LanProtocolException.class, () -> decodeAll(header, header.length), "length " + length);
		}
	}

	@Test
	void decoderStaysFailedAfterAnError() throws Exception {
		final LanCodec.FrameDecoder d = new LanCodec.FrameDecoder();
		final LanCodec.MessageSink sink = new LanCodec.MessageSink() {
			@Override
			public void accept(final LanMessage m) {
				throw new AssertionError("nothing should be delivered");
			}
		};
		assertThrows(LanProtocolException.class, () -> d.feed(new byte[] { (byte) 250, 0, 0, 0, 0 }, 0, 5, sink));
		final byte[] good = LanCodec.encodeFrame(new LanMessages.Go());
		assertThrows(LanProtocolException.class, () -> d.feed(good, 0, good.length, sink));
	}

	@Test
	void limitsAreEnforcedOnEncoding() {
		assertThrows(LanProtocolException.class,
				() -> LanCodec.encodeFrame(new LanMessages.Cmd(1, new byte[LanProtocol.MAX_COMMAND_BYTES + 1])));
		assertThrows(LanProtocolException.class, () -> LanCodec.encodeFrame(
				new LanMessages.Chat(0, 0, new String(new char[LanProtocol.MAX_CHAT + 1]).replace('\0', 'x'))));
		assertThrows(LanProtocolException.class, () -> LanCodec.encodeFrame(
				new LanMessages.MapChunk(0, 0, new byte[LanProtocol.MAX_MAP_CHUNK + 1])));
		final LanMessages.Hello h = new LanMessages.Hello();
		h.playerName = "ThisNameIsFarTooLongForTheLimit";
		assertThrows(LanProtocolException.class, () -> LanCodec.encodeFrame(h));
	}

	@Test
	void nameWithControlCharactersIsRejectedOnDecoding() throws Exception {
		final ByteWriter w = new ByteWriter(200);
		w.i32(LanProtocol.MAGIC).u16(LanProtocol.PROTOCOL_VERSION).string("", 32).string("", 48).bool(false).i64(0)
				.string("bad\nname", 15).string("", 64).u16(0).u8(0).u8(0).bool(false);
		final byte[] body = w.toByteArray();
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeBody(LanProtocol.HELLO, body, 0, body.length));
	}

	@Test
	void hugeCountsCannotMakeTheDecoderAllocate() throws Exception {
		// LOBBY_STATE claiming 65535 slots in a 10 byte body
		final ByteWriter w = new ByteWriter(100);
		w.i32(1).u8(1).u16(0xFFFF).u16(0xFFFF).u8(0);
		final byte[] body = w.toByteArray();
		assertThrows(LanProtocolException.class,
				() -> LanCodec.decodeBody(LanProtocol.LOBBY_STATE, body, 0, body.length));
	}

	@Test
	void datagramsRoundTrip() throws Exception {
		final GameInfo g = new GameInfo();
		g.gameId = -5;
		g.engineVersion = "0.4.0";
		g.buildCommit = "abc";
		g.hostName = "Thrall";
		g.gameName = "Game";
		g.mapName = "Maps\\X.w3x";
		g.mapTitle = "The X Map";
		g.mapSize = 70000;
		g.mapWidth = 64;
		g.mapHeight = 96;
		g.mapSha256 = SampleMessages.bytes(32, 4);
		g.mapCrc32 = 0x1234ABCD;
		g.observers = LanProtocol.OBSERVERS_REFEREES;
		g.lockTeams = true;
		g.randomRaces = true;
		g.players = 2;
		g.maxPlayers = 4;
		g.state = LanProtocol.STATE_IN_GAME;
		g.hostId = 1;
		g.tcpPort = 6113;
		g.speed = 2;
		final LanCodec.Datagram[] ds = { new LanCodec.Search(1), new LanCodec.GameAnnounce(g),
				new LanCodec.GameGone(77) };
		for (final LanCodec.Datagram d : ds) {
			final byte[] b = LanCodec.encodeDatagram(d);
			assertTrue(b.length <= LanProtocol.MAX_DATAGRAM);
			final LanCodec.Datagram back = LanCodec.decodeDatagram(b, 0, b.length);
			assertNotNull(back);
			assertArrayEquals(b, LanCodec.encodeDatagram(back));
			if (d instanceof LanCodec.GameAnnounce) {
				final GameInfo got = ((LanCodec.GameAnnounce) back).info;
				assertEquals("The X Map", got.mapTitle);
				assertEquals(96, got.mapHeight);
				assertArrayEquals(g.mapSha256, got.mapSha256);
				assertEquals(LanProtocol.OBSERVERS_REFEREES, got.observers);
				assertTrue(got.lockTeams && got.randomRaces && !got.teamsTogether);
			}
		}
	}

	@Test
	void longestAnnouncementStillFitsADatagram() throws Exception {
		final GameInfo g = new GameInfo();
		final String long64 = "\u00e4".repeat(64);
		g.engineVersion = "v".repeat(32);
		g.buildCommit = "c".repeat(48);
		g.hostName = "\u00e4".repeat(LanProtocol.MAX_PLAYER_NAME);
		g.gameName = "\u00e4".repeat(LanProtocol.MAX_GAME_NAME);
		g.mapName = long64;
		g.mapTitle = long64;
		g.mapSha256 = SampleMessages.bytes(32, 1);
		assertTrue(LanCodec.encodeDatagram(new LanCodec.GameAnnounce(g)).length <= LanProtocol.MAX_DATAGRAM);
	}

	@Test
	void foreignDatagramsAreIgnoredAndBrokenOnesAreErrors() throws Exception {
		assertNull(LanCodec.decodeDatagram(new byte[] { 1, 2, 3, 4, 5, 6 }, 0, 6));
		assertNull(LanCodec.decodeDatagram(new byte[] { 1 }, 0, 1));
		final byte[] good = LanCodec.encodeDatagram(new LanCodec.GameGone(1));
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeDatagram(good, 0, good.length - 1));
		final byte[] unknown = good.clone();
		unknown[4] = 99;
		assertThrows(LanProtocolException.class, () -> LanCodec.decodeDatagram(unknown, 0, unknown.length));
	}
}
