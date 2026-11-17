package com.etheller.warsmash.launcher.lan;

import java.util.HashMap;
import java.util.Map;

/**
 * Which Reforged profile portrait shows the same character as which classic command button, so that players of the
 * two skins see each other's portrait as the art of their own skin. A pair is the Reforged number (from 1, as in
 * {@code r:17}) and the classic button's name (as in {@code c:BTNArthas}); a portrait without a pair stays a colored
 * initial on the other skin. Pairs are confirmed against the two skins' pictures, never assumed.
 */
final class LanPortraitTable {
	/** {@code "r:<number>", "<button name>"}, one pair after another. */
	private static final String[] PAIRS = {};

	private static final Map<Integer, String> CLASSIC_FOR_REFORGED = new HashMap<>();
	private static final Map<String, Integer> REFORGED_FOR_CLASSIC = new HashMap<>();

	static {
		add(PAIRS);
	}

	private LanPortraitTable() {
	}

	private static void add(final String[] pairs) {
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			final int number = Integer.parseInt(pairs[i].substring(2));
			CLASSIC_FOR_REFORGED.putIfAbsent(number, pairs[i + 1]);
			REFORGED_FOR_CLASSIC.putIfAbsent(pairs[i + 1], number);
		}
	}

	static String[] pairs() {
		return PAIRS.clone();
	}

	/** The classic button that shows the Reforged portrait, or null. */
	static String classicFor(final int reforgedNumber) {
		return CLASSIC_FOR_REFORGED.get(reforgedNumber);
	}

	/** The Reforged number (from 1) of the portrait that shows the classic button, or 0. */
	static int reforgedFor(final String classicKey) {
		final Integer number = REFORGED_FOR_CLASSIC.get(classicKey);
		return number == null ? 0 : number;
	}
}
