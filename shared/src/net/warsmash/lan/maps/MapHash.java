package net.warsmash.lan.maps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

import net.warsmash.lan.protocol.LanData;

/** SHA-256 and CRC-32 of map files; blocking, so never call it on the network thread. */
public final class MapHash {
	public static final class Result {
		public final long size;
		public final byte[] sha256;
		public final int crc32;

		public Result(final long size, final byte[] sha256, final int crc32) {
			this.size = size;
			this.sha256 = sha256;
			this.crc32 = crc32;
		}
	}

	/** Lets a long hash be abandoned. */
	public interface Cancel {
		boolean isCancelled();
	}

	private MapHash() {
	}

	/** Returns null when cancelled. */
	public static Result compute(final File file, final Cancel cancel) throws IOException {
		final MessageDigest sha;
		try {
			sha = MessageDigest.getInstance("SHA-256");
		}
		catch (final NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 unavailable", e);
		}
		final CRC32 crc = new CRC32();
		final byte[] buf = new byte[64 * 1024];
		long total = 0;
		try (InputStream in = new FileInputStream(file)) {
			int n;
			while ((n = in.read(buf)) > 0) {
				if (cancel != null && cancel.isCancelled()) {
					return null;
				}
				sha.update(buf, 0, n);
				crc.update(buf, 0, n);
				total += n;
			}
		}
		return new Result(total, sha.digest(), (int) crc.getValue());
	}

	public static Result compute(final File file) throws IOException {
		return compute(file, null);
	}

	/** What the host announces for a map file: name, size and both hashes. Throws if the file may not be shared. */
	public static LanData.MapInfo describe(final File file, final Result hash) throws IOException {
		final LanData.MapInfo info = new LanData.MapInfo();
		info.name = file.getName();
		info.size = (int) Math.min(hash.size, Integer.MAX_VALUE);
		info.sha256 = hash.sha256;
		info.crc32 = hash.crc32;
		final String why = MapRules.checkInfo(info);
		if (why != null) {
			throw new IOException("map cannot be shared: " + why);
		}
		final byte[] head = new byte[MapRules.HEADER_BYTES];
		int len = 0;
		try (InputStream in = new FileInputStream(file)) {
			int n;
			while (len < head.length && (n = in.read(head, len, head.length - len)) > 0) {
				len += n;
			}
		}
		final String header = MapRules.checkHeader(head, len);
		if (header != null) {
			throw new IOException("map cannot be shared: " + header);
		}
		return info;
	}

	public static String hex(final byte[] bytes) {
		final StringBuilder b = new StringBuilder(bytes.length * 2);
		for (final byte x : bytes) {
			b.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
		}
		return b.toString();
	}

	public static byte[] fromHex(final String s) {
		if (s.length() % 2 != 0) {
			return null;
		}
		final byte[] out = new byte[s.length() / 2];
		for (int i = 0; i < out.length; i++) {
			final int hi = Character.digit(s.charAt(2 * i), 16);
			final int lo = Character.digit(s.charAt(2 * i + 1), 16);
			if (hi < 0 || lo < 0) {
				return null;
			}
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}
}
