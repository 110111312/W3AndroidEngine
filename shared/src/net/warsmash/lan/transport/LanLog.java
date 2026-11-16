package net.warsmash.lan.transport;

import com.etheller.warsmash.util.Log;

/**
 * The network code's log lines, in the engine's log so every platform keeps them with the rest; a platform with a log
 * of its own (Android's logcat) takes them through {@link #setSink}.
 */
public final class LanLog {
	public interface Sink {
		void log(boolean warning, String message, Throwable error);
	}

	private static volatile Sink sink;

	private LanLog() {
	}

	public static void setSink(final Sink sink) {
		LanLog.sink = sink;
	}

	public static void info(final String message) {
		final Sink s = sink;
		if (s != null) {
			s.log(false, message, null);
			return;
		}
		Log.info("LAN: " + message);
	}

	public static void warn(final String message, final Throwable t) {
		final Sink s = sink;
		if (s != null) {
			s.log(true, message, t);
			return;
		}
		Log.warn("LAN: " + message, t);
	}

	public static void warn(final String message) {
		warn(message, null);
	}
}
