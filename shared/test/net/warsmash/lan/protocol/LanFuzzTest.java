package net.warsmash.lan.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** Whatever the peer sends, the only thing the codec may throw is a LanProtocolException. */
class LanFuzzTest {
	private static final LanCodec.MessageSink DISCARD = new LanCodec.MessageSink() {
		@Override
		public void accept(final LanMessage m) {
		}
	};

	/** Returns true when the data decoded without an error. */
	private static boolean feed(final byte[] data, final int chunk) {
		final LanCodec.FrameDecoder d = new LanCodec.FrameDecoder();
		try {
			for (int i = 0; i < data.length; i += chunk) {
				d.feed(data, i, Math.min(chunk, data.length - i), DISCARD);
			}
			return true;
		}
		catch (final LanProtocolException e) {
			return false;
		}
	}

	@Test
	void randomBytesNeverThrowAnythingElse() {
		final Random r = new Random(1);
		for (int i = 0; i < 3000; i++) {
			final byte[] data = new byte[r.nextInt(300)];
			r.nextBytes(data);
			feed(data, 1 + r.nextInt(40));
		}
	}

	@Test
	void randomBodiesOfEveryTypeNeverThrowAnythingElse() {
		final Random r = new Random(2);
		for (final int type : LanCodec.knownTypes()) {
			for (int i = 0; i < 600; i++) {
				final byte[] body = new byte[r.nextInt(120)];
				r.nextBytes(body);
				if (r.nextBoolean() && body.length > 3) {
					// plausible small counts and magics get deeper into the parsers
					for (int j = 0; j < body.length; j += 2 + r.nextInt(6)) {
						body[j] = (byte) r.nextInt(4);
					}
				}
				try {
					LanCodec.decodeBody(type, body, 0, body.length);
				}
				catch (final LanProtocolException expected) {
					// fine
				}
			}
		}
	}

	@Test
	void everyTruncationOfEveryValidFrameIsHandled() throws Exception {
		for (final LanMessage m : SampleMessages.all()) {
			final byte[] frame = LanCodec.encodeFrame(m);
			// cutting inside the stream is not an error for a streaming decoder, cutting the body is
			for (int cut = 0; cut < frame.length; cut++) {
				final byte[] part = new byte[cut];
				System.arraycopy(frame, 0, part, 0, cut);
				feed(part, 3);
			}
			final byte[] body = new byte[frame.length - LanProtocol.FRAME_HEADER];
			System.arraycopy(frame, LanProtocol.FRAME_HEADER, body, 0, body.length);
			for (int cut = 0; cut < body.length; cut++) {
				boolean failed = false;
				try {
					LanCodec.decodeBody(m.type(), body, 0, cut);
				}
				catch (final LanProtocolException e) {
					failed = true;
				}
				if (m.type() == LanProtocol.HELLO) {
					continue; // a shortened HELLO may be a valid older-version prefix
				}
				assertTrue(failed, "type " + m.type() + " accepted a body cut to " + cut + " of " + body.length);
			}
		}
	}

	@Test
	void bitFlipsInValidFramesAreHandled() throws Exception {
		final Random r = new Random(3);
		final List<LanMessage> all = SampleMessages.all();
		for (int round = 0; round < 12; round++) {
			for (final LanMessage m : all) {
				final byte[] frame = LanCodec.encodeFrame(m);
				final int flips = 1 + r.nextInt(4);
				for (int i = 0; i < flips; i++) {
					frame[r.nextInt(frame.length)] ^= (byte) (1 << r.nextInt(8));
				}
				feed(frame, frame.length);
				feed(frame, 1);
			}
		}
	}

	@Test
	void hugeAnnouncedLengthsAreRefusedWithoutBuffering() {
		final LanCodec.FrameDecoder d = new LanCodec.FrameDecoder();
		final byte[] header = { (byte) LanProtocol.TURN, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
		try {
			d.feed(header, 0, header.length, DISCARD);
			throw new AssertionError("must refuse");
		}
		catch (final LanProtocolException expected) {
			assertEquals("frame length out of range", expected.getMessage());
		}
	}

	@Test
	void garbageDatagramsNeverThrowAnythingElse() throws Exception {
		final Random r = new Random(4);
		final byte[] valid = LanCodec.encodeDatagram(new LanCodec.GameGone(5));
		for (int i = 0; i < 4000; i++) {
			final byte[] data;
			if (i % 2 == 0) {
				data = new byte[r.nextInt(1400)];
				r.nextBytes(data);
				if (data.length >= 4 && r.nextBoolean()) {
					data[0] = 'W';
					data[1] = '3';
					data[2] = 'A';
					data[3] = 'L';
				}
			}
			else {
				data = valid.clone();
				data[r.nextInt(data.length)] ^= (byte) (1 + r.nextInt(255));
			}
			try {
				LanCodec.decodeDatagram(data, 0, data.length);
			}
			catch (final LanProtocolException expected) {
				// fine
			}
		}
	}
}
