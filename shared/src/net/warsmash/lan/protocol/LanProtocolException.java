package net.warsmash.lan.protocol;

/** Thrown for any malformed, oversized or unknown wire data; the only exception the codec throws. */
public final class LanProtocolException extends Exception {
	private static final long serialVersionUID = 1L;

	public LanProtocolException(final String message) {
		super(message);
	}
}
