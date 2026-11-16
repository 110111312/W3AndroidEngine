package com.etheller.warsmash.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small capitals as the game's style sheets set them (font-variant: small-caps): a capital stays a capital, a small
 * letter becomes a capital drawn smaller.
 */
public final class SmallCaps {
	/** The size of the small capitals against the capitals. */
	public static final float SCALE = 0.8f;

	/** A piece of the text in capitals; small when it was written in small letters. */
	public static final class Run {
		public final String text;
		public final boolean small;

		Run(final String text, final boolean small) {
			this.text = text;
			this.small = small;
		}
	}

	private SmallCaps() {
	}

	/** The text as runs of capitals and small capitals; signs that have no case belong to the run before them. */
	public static List<Run> runs(final String text) {
		final List<Run> runs = new ArrayList<>();
		final StringBuilder run = new StringBuilder();
		boolean small = false;
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			final boolean lower = Character.isLowerCase(c);
			final boolean upper = Character.isUpperCase(c);
			if ((lower || upper) && (run.length() > 0) && (lower != small)) {
				runs.add(new Run(run.toString(), small));
				run.setLength(0);
			}
			if (lower || upper) {
				small = lower;
			}
			run.append(lower ? String.valueOf(c).toUpperCase(Locale.ROOT) : String.valueOf(c));
		}
		if (run.length() > 0) {
			runs.add(new Run(run.toString(), small));
		}
		return runs;
	}
}
