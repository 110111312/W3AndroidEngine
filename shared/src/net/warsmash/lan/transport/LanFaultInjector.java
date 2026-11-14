package net.warsmash.lan.transport;

import java.util.Random;

/**
 * Network faults for tests, applied by a {@link LanLoop} to every connection it owns: fixed delay plus random jitter
 * in each direction (order is preserved, like TCP), a freeze that holds all traffic in both directions, and a hard
 * cut of every connection.
 */
public final class LanFaultInjector {
	private final Random random;
	private volatile int delayMs;
	private volatile int jitterMs;
	private volatile long frozenUntil;
	private volatile LanLoop loop;

	public LanFaultInjector(final long seed) {
		this.random = new Random(seed);
	}

	public void setDelay(final int delayMs, final int jitterMs) {
		this.delayMs = delayMs;
		this.jitterMs = jitterMs;
	}

	/** Holds traffic for the given time, starting now. */
	public void freeze(final long ms) {
		this.frozenUntil = LanLoop.nowMs() + ms;
	}

	public void thaw() {
		this.frozenUntil = 0;
	}

	/** Abruptly closes every connection of the attached loop. */
	public void cut() {
		final LanLoop l = this.loop;
		if (l != null) {
			l.execute(new Runnable() {
				@Override
				public void run() {
					l.closeAllConnections("fault: cut");
				}
			});
		}
	}

	void attach(final LanLoop loop) {
		this.loop = loop;
	}

	boolean isActive() {
		return this.delayMs > 0 || this.jitterMs > 0 || this.frozenUntil > LanLoop.nowMs();
	}

	long frozenUntil() {
		return this.frozenUntil;
	}

	/** Time at which a message arriving now may be released; never earlier than the previous one. */
	long releaseTime(final long now, final long previous) {
		int jitter = 0;
		if (this.jitterMs > 0) {
			synchronized (this.random) {
				jitter = this.random.nextInt(this.jitterMs + 1);
			}
		}
		return Math.max(Math.max(now + this.delayMs + jitter, this.frozenUntil), previous);
	}
}
