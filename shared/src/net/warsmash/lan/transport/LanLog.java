package net.warsmash.lan.transport;

import com.etheller.warsmash.util.Log;

/** The network code's log lines, in the engine's log so every platform keeps them with the rest (Android: stderr). */
public final class LanLog {
	private LanLog() {
	}

	public static void info(final String message) {
		Log.info("LAN: " + message);
	}

	public static void warn(final String message, final Throwable t) {
		Log.warn("LAN: " + message, t);
	}

	public static void warn(final String message) {
		Log.warn("LAN: " + message);
	}
}
