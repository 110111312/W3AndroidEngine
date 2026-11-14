package net.warsmash.lan.protocol;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class ByteWriter {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private byte[] buf;
	private int len;
	private final int limit;

	public ByteWriter(final int limit) {
		this(limit, 64);
	}

	/** @param initial the size the message is expected to have: a writer of exactly that size copies nothing at the end */
	public ByteWriter(final int limit, final int initial) {
		this.limit = limit;
		this.buf = new byte[Math.max(1, Math.min(limit, initial))];
	}

	public int size() {
		return this.len;
	}

	private void ensure(final int n) throws LanProtocolException {
		if (this.len + n > this.limit) {
			throw new LanProtocolException("message too large");
		}
		if (this.len + n > this.buf.length) {
			this.buf = Arrays.copyOf(this.buf, Math.min(this.limit, Math.max(this.len + n, this.buf.length * 2)));
		}
	}

	public ByteWriter u8(final int v) throws LanProtocolException {
		ensure(1);
		this.buf[this.len++] = (byte) v;
		return this;
	}

	public ByteWriter u16(final int v) throws LanProtocolException {
		ensure(2);
		this.buf[this.len++] = (byte) (v >>> 8);
		this.buf[this.len++] = (byte) v;
		return this;
	}

	public ByteWriter i32(final int v) throws LanProtocolException {
		ensure(4);
		this.buf[this.len++] = (byte) (v >>> 24);
		this.buf[this.len++] = (byte) (v >>> 16);
		this.buf[this.len++] = (byte) (v >>> 8);
		this.buf[this.len++] = (byte) v;
		return this;
	}

	public ByteWriter i64(final long v) throws LanProtocolException {
		i32((int) (v >>> 32));
		return i32((int) v);
	}

	public ByteWriter bool(final boolean v) throws LanProtocolException {
		return u8(v ? 1 : 0);
	}

	/** u16 length prefix. */
	public ByteWriter bytes(final byte[] b) throws LanProtocolException {
		if (b.length > 0xFFFF) {
			throw new LanProtocolException("byte array too long");
		}
		u16(b.length);
		return raw(b, 0, b.length);
	}

	public ByteWriter raw(final byte[] b, final int off, final int n) throws LanProtocolException {
		ensure(n);
		System.arraycopy(b, off, this.buf, this.len, n);
		this.len += n;
		return this;
	}

	public ByteWriter string(final String s, final int maxChars) throws LanProtocolException {
		if (s.length() > maxChars) {
			throw new LanProtocolException("string too long");
		}
		return bytes(s.getBytes(UTF8));
	}

	/** The bytes written; the writer's own array when it is exactly full, so a writer is not used after this. */
	public byte[] toByteArray() {
		return this.len == this.buf.length ? this.buf : Arrays.copyOf(this.buf, this.len);
	}
}
