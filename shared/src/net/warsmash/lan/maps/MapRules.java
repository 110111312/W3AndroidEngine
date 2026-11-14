package net.warsmash.lan.maps;

import java.util.Locale;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * What a map that arrives over the network must look like before it is allowed to touch the disk or the game: a
 * plain file name with a map extension, a sane size, a hash to check against and an MPQ archive at the front.
 */
public final class MapRules {
	public static final int MAX_SIZE = LanData.MapInfo.MAX_SIZE;
	/** Bytes of a file that {@link #checkHeader} needs to decide. */
	public static final int HEADER_BYTES = 1024;

	private static final String[] RESERVED = { "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5",
			"COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9" };

	private MapRules() {
	}

	/** Returns null for a usable file name, otherwise why not. */
	public static String checkName(final String name) {
		if (name == null || name.isEmpty()) {
			return "empty name";
		}
		if (name.length() > LanData.MapInfo.MAX_NAME) {
			return "name too long";
		}
		for (int i = 0; i < name.length(); i++) {
			final char c = name.charAt(i);
			if (c < 32 || c == 127 || "/\\:*?\"<>|".indexOf(c) >= 0) {
				return "illegal character in name";
			}
		}
		if (name.contains("..") || name.startsWith(".") || name.startsWith(" ") || name.endsWith(".")
				|| name.endsWith(" ")) {
			return "illegal name";
		}
		final String lower = name.toLowerCase(Locale.ROOT);
		if (!lower.endsWith(".w3x") && !lower.endsWith(".w3m")) {
			return "not a map file";
		}
		final String stem = name.substring(0, name.lastIndexOf('.')).toUpperCase(Locale.ROOT);
		for (final String r : RESERVED) {
			if (stem.equals(r)) {
				return "reserved name";
			}
		}
		return null;
	}

	/** Returns null when the announced map can be downloaded and verified, otherwise why not. */
	public static String checkInfo(final LanData.MapInfo info) {
		final String name = checkName(info.name);
		if (name != null) {
			return name;
		}
		if (info.size <= 0 || info.size > MAX_SIZE) {
			return "bad size";
		}
		if (info.sha256.length != LanProtocol.SHA256_BYTES) {
			return "no hash";
		}
		return null;
	}

	/**
	 * Checks the first bytes of a map: a bare MPQ archive, or the 512 byte map header "HM3W" followed by one. The MPQ
	 * header must announce a plausible header size. Returns null when fine, otherwise why not; {@code len} below
	 * {@link #HEADER_BYTES} is judged as a complete small file.
	 */
	public static String checkHeader(final byte[] head, final int len) {
		if (len >= 4 && isMpq(head, 0, len)) {
			return null;
		}
		if (len >= 4 && head[0] == 'H' && head[1] == 'M' && head[2] == '3' && head[3] == 'W') {
			return isMpq(head, 512, len) ? null : "no MPQ archive after the map header";
		}
		return "not an MPQ archive";
	}

	private static boolean isMpq(final byte[] b, final int at, final int len) {
		if (len < at + 8 || b[at] != 'M' || b[at + 1] != 'P' || b[at + 2] != 'Q' || b[at + 3] != 0x1A) {
			return false;
		}
		final long headerSize = (b[at + 4] & 0xFFL) | ((b[at + 5] & 0xFFL) << 8) | ((b[at + 6] & 0xFFL) << 16)
				| ((b[at + 7] & 0xFFL) << 24);
		return headerSize >= 32 && headerSize <= 4096;
	}
}
