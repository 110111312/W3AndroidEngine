package com.etheller.warsmash.launcher.lan;

import java.util.HashMap;
import java.util.Map;

import com.etheller.warsmash.launcher.SkinArt;

import net.warsmash.lan.protocol.LanProtocol;

/**
 * How a profile portrait travels between players. A portrait's number only means something inside the skin that
 * numbers it, so the wire carries the skin and the portrait's stable name there: {@code c:BTNArthas} for the classic
 * skin (the command button it shows), {@code r:17} for Reforged (its p017). A plain number is what earlier builds
 * sent; it is read as the number in the receiver's own skin, as before. A portrait the receiver's skin does not have
 * (the other skin's) is shown as the portrait {@link LanPortraitTable} pairs it with, and when it has no pair the
 * player's colored initial stands in.
 */
public final class LanPortraitId {
	public static final int NONE = -1;
	private static final String CLASSIC = "c:";
	private static final String REFORGED = "r:";
	private static final String FILE_PREFIX = "portrait-";
	private static final String FILE_SUFFIX = ".png";

	private static Map<Integer, String> classicKeys;
	private static Map<String, Integer> classicIndexes;

	private LanPortraitId() {
	}

	/** The identity of a portrait of the sender's skin; empty for none. */
	public static String encode(final int index, final boolean reforged) {
		if (index < 0) {
			return "";
		}
		if (reforged) {
			return index < SkinArt.portraitCount(true) ? REFORGED + (index + 1) : "";
		}
		final String key = classicKeys().get(index);
		return key == null ? "" : CLASSIC + key;
	}

	/** The portrait's number in the receiver's skin, or {@link #NONE} when it has none for it. */
	public static int decode(final String id, final boolean reforged) {
		if (id == null) {
			return NONE;
		}
		final String s = id.trim();
		if (s.startsWith(CLASSIC)) {
			final String key = s.substring(CLASSIC.length());
			if (reforged) {
				final int number = LanPortraitTable.reforgedFor(key);
				return (number >= 1) && (number <= SkinArt.portraitCount(true)) ? number - 1 : NONE;
			}
			final Integer index = classicIndexes().get(key);
			return index == null ? NONE : index;
		}
		if (s.startsWith(REFORGED)) {
			final int number = number(s.substring(REFORGED.length()), 1, SkinArt.portraitCount(true));
			if (reforged) {
				return number - 1;
			}
			final String key = number < 1 ? null : LanPortraitTable.classicFor(number);
			final Integer index = key == null ? null : classicIndexes().get(key);
			return index == null ? NONE : index;
		}
		return number(s, 0, Integer.MAX_VALUE);
	}

	private static int number(final String text, final int min, final int max) {
		try {
			final int n = Integer.parseInt(text);
			return (n >= min) && (n <= max) ? n : min - 1;
		}
		catch (final NumberFormatException e) {
			return NONE;
		}
	}

	private static synchronized Map<Integer, String> classicKeys() {
		if (classicKeys == null) {
			buildClassic();
		}
		return classicKeys;
	}

	private static synchronized Map<String, Integer> classicIndexes() {
		if (classicIndexes == null) {
			buildClassic();
		}
		return classicIndexes;
	}

	/** The classic portraits are the skin's command-button images; the button's name is the stable key. */
	private static void buildClassic() {
		final Map<Integer, String> keys = new HashMap<>();
		final Map<String, Integer> indexes = new HashMap<>();
		for (final SkinArt.Item item : SkinArt.items(false)) {
			if (item.name.startsWith(FILE_PREFIX) && item.name.endsWith(FILE_SUFFIX)) {
				try {
					final int index = Integer.parseInt(
							item.name.substring(FILE_PREFIX.length(), item.name.length() - FILE_SUFFIX.length()));
					if (item.shown.length() + CLASSIC.length() <= LanProtocol.MAX_PORTRAIT) {
						keys.put(index, item.shown);
						indexes.put(item.shown, index);
					}
				}
				catch (final NumberFormatException e) {
					// not a numbered portrait (the active frame)
				}
			}
		}
		classicKeys = keys;
		classicIndexes = indexes;
	}
}
