package com.etheller.warsmash.lan;

/**
 * The rules of a rejoin on the player's side. A player given the game so far (it came back, or its game started over)
 * plays the turns of the archive without drawing, as a replay skips ahead, while the others go on; it is counted in
 * again once it has played the archive and is within a few turns of the host's game. The host checks the sums the
 * player reports on the way against what the others reported when each step was played.
 */
public final class LanRejoin {
	/** The game is this close to the host's when a rejoining player is counted in again. */
	public static final int CAUGHT_UP_TURNS = 2;

	/** How often (in steps) the progress shown to the player is brought up to date. */
	public static final int PROGRESS_STEPS = 20;

	private LanRejoin() {
	}

	public static boolean progressDue(final int tick) {
		return (tick % PROGRESS_STEPS) == 0;
	}

	/**
	 * Whether the game has played every archived turn, has no step left to make of them and is within
	 * {@link #CAUGHT_UP_TURNS} turns of the last one received.
	 *
	 * @param lastApplied the number of the last turn applied, -1 for none
	 * @param archived the number of archived turns
	 * @param received the number of the last turn received
	 * @param tick the game's step, {@code allowedUntil} the last step the applied turns allow
	 */
	public static boolean caughtUp(final int lastApplied, final int archived, final int received, final int tick,
			final int allowedUntil) {
		return lastApplied >= (archived - 1) && (received - lastApplied) <= CAUGHT_UP_TURNS && tick > allowedUntil;
	}

	/** How far the game has been played through, 0 to 99; 0 while the target is not known. */
	public static int percent(final long tick, final long target) {
		return target <= 0 ? 0 : (int) Math.min(99, tick * 100L / target);
	}

	/** Game time as minutes and seconds ("12:40") of that many steps (20 a second). */
	public static String clock(final long steps) {
		final long seconds = steps / 20;
		return (seconds / 60) + ":" + (seconds % 60 < 10 ? "0" : "") + (seconds % 60);
	}
}
