package net.warsmash.lan.protocol;

import java.nio.charset.Charset;

/** Bounds-checked reader: every failure is a {@link LanProtocolException}, counts are validated before allocation. */
public final class ByteReader {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private final byte[] buf;
	private int pos;
	private final int end;

	public ByteReader(final byte[] buf) {
		this(buf, 0, buf.length);
	}

	public ByteReader(final byte[] buf, final int off, final int len) {
		this.buf = buf;
		this.pos = off;
		this.end = off + len;
	}

	public int remaining() {
		return this.end - this.pos;
	}

	private void need(final int n) throws LanProtocolException {
		if (n < 0 || this.end - this.pos < n) {
			throw new LanProtocolException("truncated message");
		}
	}

	public int u8() throws LanProtocolException {
		need(1);
		return this.buf[this.pos++] & 0xFF;
	}

	/** Enum-like byte in 0..max. */
	public int u8(final int max) throws LanProtocolException {
		final int v = u8();
		if (v > max) {
			throw new LanProtocolException("value out of range");
		}
		return v;
	}

	public int u16() throws LanProtocolException {
		need(2);
		final int v = ((this.buf[this.pos] & 0xFF) << 8) | (this.buf[this.pos + 1] & 0xFF);
		this.pos += 2;
		return v;
	}

	public int i32() throws LanProtocolException {
		need(4);
		final int v = ((this.buf[this.pos] & 0xFF) << 24) | ((this.buf[this.pos + 1] & 0xFF) << 16)
				| ((this.buf[this.pos + 2] & 0xFF) << 8) | (this.buf[this.pos + 3] & 0xFF);
		this.pos += 4;
		return v;
	}

	public int i32(final int min, final int max) throws LanProtocolException {
		final int v = i32();
		if (v < min || v > max) {
			throw new LanProtocolException("value out of range");
		}
		return v;
	}

	public long i64() throws LanProtocolException {
		final long hi = i32();
		final long lo = i32() & 0xFFFFFFFFL;
		return (hi << 32) | lo;
	}

	public boolean bool() throws LanProtocolException {
		final int v = u8();
		if (v > 1) {
			throw new LanProtocolException("bad boolean");
		}
		return v == 1;
	}

	public byte[] bytes(final int maxLen) throws LanProtocolException {
		final int n = u16();
		if (n > maxLen) {
			throw new LanProtocolException("byte array too long");
		}
		need(n);
		final byte[] b = new byte[n];
		System.arraycopy(this.buf, this.pos, b, 0, n);
		this.pos += n;
		return b;
	}

	public byte[] fixed(final int n) throws LanProtocolException {
		final byte[] b = bytes(n);
		if (b.length != n) {
			throw new LanProtocolException("bad fixed length");
		}
		return b;
	}

	public String string(final int maxChars) throws LanProtocolException {
		final String s = new String(bytes(maxChars * 4), UTF8);
		if (s.length() > maxChars) {
			throw new LanProtocolException("string too long");
		}
		return s;
	}

	/** Name-like string: no control characters. */
	public String name(final int maxChars) throws LanProtocolException {
		final String s = string(maxChars);
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) < 0x20 || s.charAt(i) == 0x7F) {
				throw new LanProtocolException("control character in name");
			}
		}
		return s;
	}

	/** Reads an element count and checks that the remaining bytes can hold that many elements. */
	public int count(final int max, final int minElementBytes) throws LanProtocolException {
		final int n = u16();
		if (n > max || (long) n * minElementBytes > remaining()) {
			throw new LanProtocolException("bad element count");
		}
		return n;
	}

	public void skipRest() {
		this.pos = this.end;
	}

	public void expectEnd() throws LanProtocolException {
		if (this.pos != this.end) {
			throw new LanProtocolException("trailing bytes");
		}
	}
}
