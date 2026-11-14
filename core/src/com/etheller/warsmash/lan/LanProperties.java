package com.etheller.warsmash.lan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.replay.GameReplays;

/**
 * Where an Android test run gets the -Dwarsmash.lan.* settings {@link LanHeadless} reads: from the [Emulator] section
 * of warsmash.ini, under the same names (warsmash.lan.host=6112, warsmash.lan.steps=3000, ...), plus the replay file
 * switch. They become system properties of the process. A value cannot be empty in the ini, so a host on the default
 * port names it.
 */
public final class LanProperties {
	private static final String PREFIX = "warsmash.lan.";
	/** The properties an earlier ini put there: the process outlives the activity, so a later start must not inherit them. */
	private static final List<String> applied = new ArrayList<String>();

	private LanProperties() {
	}

	public static synchronized void apply(final GameObject emulator) {
		for (final String name : applied) {
			System.clearProperty(name);
		}
		applied.clear();
		if (emulator == null) {
			return;
		}
		for (final String key : emulator.keySet()) {
			final String name = key.toLowerCase(Locale.ROOT);
			if (name.startsWith(PREFIX) || name.equals(GameReplays.RECORD_PROPERTY)) {
				System.setProperty(name, emulator.getField(key));
				applied.add(name);
				Log.warn("LAN test setting " + name + "=" + emulator.getField(key));
			}
		}
	}
}
