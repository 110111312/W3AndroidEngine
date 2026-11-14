package net.warsmash.lan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;

import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * Game id plus a 128-bit slot token: whoever holds it may take the seat back. Kept on disk across app restarts, with
 * the address of the host the seat was last held at, so the game can be looked for there without a broadcast.
 */
public final class RejoinKey {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	public final long gameId;
	private final byte[] token;
	private final String host;

	public RejoinKey(final long gameId, final byte[] token) {
		this(gameId, token, null);
	}

	public RejoinKey(final long gameId, final byte[] token, final String host) {
		if (token.length != LanProtocol.TOKEN_BYTES) {
			throw new IllegalArgumentException("token must be " + LanProtocol.TOKEN_BYTES + " bytes");
		}
		this.gameId = gameId;
		this.token = token.clone();
		this.host = host == null || host.isEmpty() || host.length() > 64 ? null : host;
	}

	/** The host's "address:port" this key was last used at, or null. */
	public String host() {
		return this.host;
	}

	public RejoinKey withHost(final String newHost) {
		return new RejoinKey(this.gameId, this.token, newHost);
	}

	public static RejoinKey create(final long gameId, final SecureRandom random) {
		final byte[] t = new byte[LanProtocol.TOKEN_BYTES];
		random.nextBytes(t);
		return new RejoinKey(gameId, t);
	}

	public byte[] token() {
		return this.token.clone();
	}

	/** Constant-time comparison. */
	public static boolean tokenEquals(final byte[] a, final byte[] b) {
		if (a.length != b.length) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < a.length; i++) {
			diff |= a[i] ^ b[i];
		}
		return diff == 0;
	}

	/** What START tells everybody of a token: enough to check it, not to use it. */
	public static byte[] hash(final byte[] token) {
		try {
			return java.security.MessageDigest.getInstance("SHA-256").digest(token);
		}
		catch (final java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean matches(final long otherGameId, final byte[] otherToken) {
		return this.gameId == otherGameId && tokenEquals(this.token, otherToken);
	}

	public LanMessages.RejoinData toRejoinData(final int haveTurns) {
		final LanMessages.RejoinData d = new LanMessages.RejoinData();
		d.gameId = this.gameId;
		d.token = this.token.clone();
		d.haveTurns = haveTurns;
		return d;
	}

	public String serialize() {
		final StringBuilder sb = new StringBuilder();
		sb.append(Long.toHexString(this.gameId)).append(':');
		for (final byte b : this.token) {
			sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
		}
		if (this.host != null) {
			sb.append('@').append(this.host);
		}
		return sb.toString();
	}

	/** Returns null when the text is not a well-formed key. */
	public static RejoinKey parse(final String text) {
		String s = text.trim();
		String host = null;
		final int at = s.indexOf('@');
		if (at >= 0) {
			host = s.substring(at + 1);
			s = s.substring(0, at);
		}
		final int colon = s.indexOf(':');
		if (colon < 1 || s.length() - colon - 1 != LanProtocol.TOKEN_BYTES * 2) {
			return null;
		}
		try {
			final long id = Long.parseUnsignedLong(s.substring(0, colon), 16);
			final byte[] t = new byte[LanProtocol.TOKEN_BYTES];
			for (int i = 0; i < t.length; i++) {
				final int hi = Character.digit(s.charAt(colon + 1 + i * 2), 16);
				final int lo = Character.digit(s.charAt(colon + 2 + i * 2), 16);
				if (hi < 0 || lo < 0) {
					return null;
				}
				t[i] = (byte) ((hi << 4) | lo);
			}
			return new RejoinKey(id, t, host);
		}
		catch (final NumberFormatException e) {
			return null;
		}
	}

	/** Writes through a temporary file so a crash never leaves a half-written key. */
	public void save(final File file) throws IOException {
		final File tmp = new File(file.getPath() + ".tmp");
		Files.write(tmp.toPath(), serialize().getBytes(UTF8));
		Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	/** Returns null when the file is missing or damaged. */
	public static RejoinKey load(final File file) {
		try {
			if (!file.isFile() || file.length() > 256) {
				return null;
			}
			return parse(new String(Files.readAllBytes(file.toPath()), UTF8));
		}
		catch (final IOException e) {
			return null;
		}
	}

	public static void delete(final File file) {
		if (file.exists() && !file.delete()) {
			file.deleteOnExit();
		}
	}
}
