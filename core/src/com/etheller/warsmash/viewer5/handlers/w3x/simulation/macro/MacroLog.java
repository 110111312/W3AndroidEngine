package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;

/**
 * The plan's line in the log, under the tag {@code MACRO}.
 * <p>
 * A plan that does nothing and a plan that cannot do anything look identical from the phone: the base just sits there.
 * The only way to tell them apart is to say what was decided, which is what this is for -- one line when the plan is
 * seeded, one when a ticket changes state, one when an order goes out, and a heartbeat so "the executor is not running
 * at all" is a visible answer too.
 * <p>
 * Two throttles keep it readable. {@link #transition} speaks only when a value changes, so a ticket that waits for gold
 * for a minute says so once. {@link #say} repeats a key at most every {@value #REPEAT_TICKS} ticks, for things that are
 * true on every pass. Neither is a substitute for a real log level; both exist so the log stays short enough to read on
 * a phone.
 */
final class MacroLog {
	static final String TAG = "MACRO";
	private static final int REPEAT_TICKS = 60;

	private final Map<String, String> lastValue = new HashMap<>();
	private final Map<String, Integer> lastTick = new HashMap<>();

	/** One line, unconditionally. */
	static void line(final String message) {
		Gdx.app.log(TAG, message);
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
}
