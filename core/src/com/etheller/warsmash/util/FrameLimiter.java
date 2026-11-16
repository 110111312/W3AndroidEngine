package com.etheller.warsmash.util;

import java.util.concurrent.TimeUnit;

/**
 * A cap on the frames a second, for devices that cannot turn vertical sync off (every Android one) and would
 * otherwise draw at the full refresh rate of the screen. It waits at the end of a frame, so the frame takes at least
 * its share of the second; a frame that is already late is not made up for.
 */
public final class FrameLimiter {
	private static volatile int maxFps;
	private static long deadline;

	private FrameLimiter() {
	}

	/** @param fps frames a second, 0 for no cap */
	public static void setMaxFps(final int fps) {
		maxFps = Math.max(0, fps);
		deadline = 0;
	}

	public static int getMaxFps() {
		return maxFps;
	}

	/** Called once at the end of every frame drawn. */
	public static void limit() {
		final int fps = maxFps;
		if (fps <= 0) {
			return;
		}
		final long now = System.nanoTime();
		final long next = deadline + (TimeUnit.SECONDS.toNanos(1) / fps);
		if ((deadline == 0) || (now >= next)) {
			deadline = now;
			return;
		}
		final long wait = next - now;
		try {
			Thread.sleep(TimeUnit.NANOSECONDS.toMillis(wait), (int) (wait % 1_000_000L));
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		deadline = next;
	}
}
