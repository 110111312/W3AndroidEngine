package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * The plan's line in the log, under the tag {@code MACRO} and, on the phone, in {@code macro.log} beside the game data.
 * <p>
 * A plan that does nothing and a plan that cannot do anything look identical from the phone: the base just sits there.
 * The only way to tell them apart is to say what was decided, which is what this is for -- one line when the plan is
 * seeded, one when a ticket changes state, one when an order goes out, and a heartbeat so "the executor is not running
 * at all" is a visible answer too.
 * <p>
 * The file exists because logcat does not survive the question: its ring is 256 KiB and the engine fills it in a
 * couple of minutes, so reading it after the fact is a coin toss -- twice now a "there is no log" answer was really a
 * rotated buffer. Every line goes to the file as it is made, and the file is pulled instead of the buffer.
 * <p>
 * Two throttles keep it readable. {@link #transition} speaks only when a value changes, so a ticket that waits for gold
 * for a minute says so once. {@link #say} repeats a key at most every {@value #REPEAT_TICKS} ticks, for things that are
 * true on every pass. Neither is a substitute for a real log level; both exist so the log stays short enough to read on
 * a phone.
 */
final class MacroLog {
	static final String TAG = "MACRO";
	private static final int REPEAT_TICKS = 60;
	/** Beside the game data on the phone: /sdcard/Android/data/com.etheller.warsmash.android/files/macro.log. */
	static final String FILE = "macro.log";
	/** Rewritten from scratch past this, so a long session cannot fill the player's storage. */
	private static final long MAX_BYTES = 1L << 20;

	private static FileHandle file;
	private static boolean fileBroken;
	private static boolean headerWritten;

	private final Map<String, String> lastValue = new HashMap<>();
	private final Map<String, Integer> lastTick = new HashMap<>();

	/** One line, unconditionally. */
	static void line(final String message) {
		if (Gdx.app != null) {
			Gdx.app.log(TAG, message);
		}
		append(message);
	}

	/** One line, but only when this key's value changed. */
	void transition(final String key, final String value, final String message) {
		if (value.equals(this.lastValue.put(key, value))) {
			return;
		}
		line(message);
	}

	/** One line, at most once every {@value #REPEAT_TICKS} ticks for this key. */
	void say(final String key, final int gameTurnTick, final String message) {
		final Integer last = this.lastTick.get(key);
		if ((last != null) && ((gameTurnTick - last) < REPEAT_TICKS)) {
			return;
		}
		this.lastTick.put(key, gameTurnTick);
		line(message);
	}

	private static synchronized void append(final String message) {
		final FileHandle handle = file();
		if (handle == null) {
			return;
		}
		try {
			final StringBuilder text = new StringBuilder();
			if (!headerWritten) {
				headerWritten = true;
				text.append("=== macro log ").append(new Date()).append('\n');
			}
			text.append(String.format(Locale.US, "%tT %s%n", new Date(), message));
			handle.writeString(text.toString(), true);
		}
		catch (final Throwable throwable) {
			// A log that breaks the game is worse than no log: say so once and stay quiet.
			fileBroken = true;
			if (Gdx.app != null) {
				Gdx.app.log(TAG, "macro.log is not writable: " + throwable);
			}
		}
	}

	private static FileHandle file() {
		if ((file == null) && !fileBroken) {
			try {
				if (Gdx.files == null) {
					fileBroken = true;
					return null;
				}
				final FileHandle handle = Gdx.files.external(FILE);
				if (handle.exists() && (handle.length() > MAX_BYTES)) {
					handle.writeString("", false);
				}
				file = handle;
			}
			catch (final Throwable throwable) {
				fileBroken = true;
			}
		}
		return file;
	}
}
