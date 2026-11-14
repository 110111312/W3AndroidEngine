package net.warsmash.lan.protocol;

import java.util.ArrayList;
import java.util.List;

import net.warsmash.lan.protocol.LanData.GameInfo;

/**
 * Framing and datagram coding.
 *
 * TCP frame: u8 type, i32 body length, body (big-endian, length at most {@link LanProtocol#MAX_FRAME_BODY}).
 * Datagram: i32 magic, u8 type, body (at most {@link LanProtocol#MAX_DATAGRAM} bytes, foreign magic is ignored).
 */
public final class LanCodec {
	private LanCodec() {
	}

	public static byte[] encodeFrame(final LanMessage m) throws LanProtocolException {
		final int hint = m.sizeHint();
		final ByteWriter w = new ByteWriter(LanProtocol.FRAME_HEADER + LanProtocol.MAX_FRAME_BODY,
				hint > 0 ? LanProtocol.FRAME_HEADER + hint : 64);
		w.u8(m.type()).i32(0);
		m.write(w);
		final byte[] frame = w.toByteArray();
		final int body = frame.length - LanProtocol.FRAME_HEADER;
		frame[1] = (byte) (body >>> 24);
		frame[2] = (byte) (body >>> 16);
		frame[3] = (byte) (body >>> 8);
		frame[4] = (byte) body;
		return frame;
	}

	/** Decodes a body of the given type that is already framed. */
	public static LanMessage decodeBody(final int type, final byte[] body, final int off, final int len)
			throws LanProtocolException {
		try {
			return LanMessages.read(type, new ByteReader(body, off, len));
		}
		catch (final RuntimeException e) {
			throw new LanProtocolException("malformed message: " + e);
		}
	}

	public interface MessageSink {
		void accept(LanMessage message);
	}

	/**
	 * Incremental TCP decoder. Memory is bounded: a body buffer is allocated only after its announced length passed
	 * the limit check. After a {@link LanProtocolException} the decoder must be discarded with its connection.
	 */
	public static final class FrameDecoder {
		private final byte[] header = new byte[LanProtocol.FRAME_HEADER];
		private int headerFill;
		private byte[] body;
		private int bodyFill;
		private int type;
		private boolean failed;

		public void feed(final byte[] data, final int off, final int len, final MessageSink sink)
				throws LanProtocolException {
			if (this.failed) {
				throw new LanProtocolException("decoder failed earlier");
			}
			int pos = off;
			final int end = off + len;
			try {
				while (pos < end) {
					if (this.body == null) {
						final int n = Math.min(LanProtocol.FRAME_HEADER - this.headerFill, end - pos);
						System.arraycopy(data, pos, this.header, this.headerFill, n);
						this.headerFill += n;
						pos += n;
						if (this.headerFill < LanProtocol.FRAME_HEADER) {
							break;
						}
						this.type = this.header[0] & 0xFF;
						final int length = ((this.header[1] & 0xFF) << 24) | ((this.header[2] & 0xFF) << 16)
								| ((this.header[3] & 0xFF) << 8) | (this.header[4] & 0xFF);
						if (length < 0 || length > LanProtocol.MAX_FRAME_BODY) {
							throw new LanProtocolException("frame length out of range");
						}
						this.bodyFill = 0;
						if (length == 0) {
							this.headerFill = 0;
							sink.accept(decodeBody(this.type, new byte[0], 0, 0));
							continue;
						}
						this.body = new byte[length];
					}
					final int n = Math.min(this.body.length - this.bodyFill, end - pos);
					System.arraycopy(data, pos, this.body, this.bodyFill, n);
					this.bodyFill += n;
					pos += n;
					if (this.bodyFill == this.body.length) {
						final byte[] done = this.body;
						this.body = null;
						this.headerFill = 0;
						sink.accept(decodeBody(this.type, done, 0, done.length));
					}
				}
			}
			catch (final LanProtocolException e) {
				this.failed = true;
				throw e;
			}
		}
	}

	// ----------------------------------------------------------------- datagrams

	public abstract static class Datagram {
		public abstract int type();
	}

	public static final class Search extends Datagram {
		public final int protocolVersion;

		public Search(final int protocolVersion) {
			this.protocolVersion = protocolVersion;
		}

		@Override
		public int type() {
			return LanProtocol.UDP_SEARCH;
		}
	}

	public static final class GameAnnounce extends Datagram {
		public final GameInfo info;

		public GameAnnounce(final GameInfo info) {
			this.info = info;
		}

		@Override
		public int type() {
			return LanProtocol.UDP_GAME_INFO;
		}
	}

	public static final class GameGone extends Datagram {
		public final long gameId;

		public GameGone(final long gameId) {
			this.gameId = gameId;
		}

		@Override
		public int type() {
			return LanProtocol.UDP_GAME_GONE;
		}
	}

	public static byte[] encodeDatagram(final Datagram d) throws LanProtocolException {
		final ByteWriter w = new ByteWriter(LanProtocol.MAX_DATAGRAM);
		w.i32(LanProtocol.MAGIC).u8(d.type());
		if (d instanceof Search) {
			w.u16(((Search) d).protocolVersion);
		}
		else if (d instanceof GameAnnounce) {
			((GameAnnounce) d).info.write(w);
		}
		else if (d instanceof GameGone) {
			w.i64(((GameGone) d).gameId);
		}
		else {
			throw new LanProtocolException("unknown datagram");
		}
		return w.toByteArray();
	}

	/** Returns null for datagrams that are not ours (wrong magic or too short to carry one). */
	public static Datagram decodeDatagram(final byte[] data, final int off, final int len)
			throws LanProtocolException {
		if (len < 5 || len > LanProtocol.MAX_DATAGRAM) {
			if (len >= 4 && len <= 65535 && magicAt(data, off)) {
				throw new LanProtocolException("datagram length out of range");
			}
			return null;
		}
		if (!magicAt(data, off)) {
			return null;
		}
		try {
			final ByteReader r = new ByteReader(data, off + 4, len - 4);
			final int type = r.u8();
			final Datagram d;
			switch (type) {
			case LanProtocol.UDP_SEARCH:
				d = new Search(r.u16());
				break;
			case LanProtocol.UDP_GAME_INFO:
				d = new GameAnnounce(GameInfo.read(r));
				break;
			case LanProtocol.UDP_GAME_GONE:
				d = new GameGone(r.i64());
				break;
			default:
				throw new LanProtocolException("unknown datagram type " + type);
			}
			r.expectEnd();
			return d;
		}
		catch (final RuntimeException e) {
			throw new LanProtocolException("malformed datagram: " + e);
		}
	}

	private static boolean magicAt(final byte[] data, final int off) {
		if (data.length < off + 4) {
			return false;
		}
		final int m = ((data[off] & 0xFF) << 24) | ((data[off + 1] & 0xFF) << 16) | ((data[off + 2] & 0xFF) << 8)
				| (data[off + 3] & 0xFF);
		return m == LanProtocol.MAGIC;
	}

	/** Every message type the codec knows, for tests. */
	public static List<Integer> knownTypes() {
		final int[] t = { LanProtocol.HELLO, LanProtocol.WELCOME, LanProtocol.REJECT, LanProtocol.PING,
				LanProtocol.PONG, LanProtocol.PLAYER_STATE, LanProtocol.LOBBY_STATE, LanProtocol.SLOT_REQUEST,
				LanProtocol.HOST_SLOT, LanProtocol.HOST_SETUP, LanProtocol.HOST_OPTIONS, LanProtocol.READY, LanProtocol.CHAT,
				LanProtocol.MAP_REQUEST, LanProtocol.MAP_CHUNK, LanProtocol.MAP_STATUS, LanProtocol.START_REQUEST,
				LanProtocol.COUNTDOWN, LanProtocol.COUNTDOWN_CANCEL, LanProtocol.START, LanProtocol.LOAD_PROGRESS,
				LanProtocol.LOADED, LanProtocol.GO, LanProtocol.LEAVE, LanProtocol.KICKED, LanProtocol.CMD,
				LanProtocol.TURN_DONE, LanProtocol.CHECKSUM, LanProtocol.STATE, LanProtocol.VOTE, LanProtocol.TURN,
				LanProtocol.LAG, LanProtocol.LAG_END, LanProtocol.PRESENCE, LanProtocol.DESYNC,
				LanProtocol.HOST_CHANGED, LanProtocol.GAME_END, LanProtocol.RESYNC, LanProtocol.REJOIN, LanProtocol.CAUGHT_UP,
				LanProtocol.TURN_BLOCK,
				LanProtocol.MIGRATE_HELLO, LanProtocol.MIGRATE_WELCOME, LanProtocol.REDIRECT,
				LanProtocol.TURN_FILL_REQUEST, LanProtocol.TURN_FILL };
		final List<Integer> list = new ArrayList<Integer>();
		for (final int i : t) {
			list.add(i);
		}
		return list;
	}
}
