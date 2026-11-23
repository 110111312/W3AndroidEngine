package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import com.etheller.warsmash.util.War3ID;

/**
 * Why a ticket cannot run at all, as opposed to why it is not running yet.
 * <p>
 * The distinction is the load-bearing one of the whole plan: an unaffordable ticket waits for its price, while a stuck
 * one is greyed and says why. Neither holds the numbers behind it: order is a preference, and the only thing that truly
 * orders two tickets is a requirement, which the game reports as MISSING_REQUIREMENT until it is met. The reason travels as a
 * value so that the panel can word it and so that a test can assert it; the words themselves belong to E3's panel.
 */
public final class Stuck {
	public enum Reason {
		/** A level, a structure or a tech the player has not got. {@link #getMissing()} says which. */
		MISSING_REQUIREMENT,
		/** The maximum the player may ever have of it, so waiting will never help. */
		AT_MAXIMUM,
		/** Nothing of the player's can build it: no worker left, or a mission that starts with none. */
		NO_BUILDER,
		/** Nothing of the player's can make or research it, so there is nowhere for the order to go. */
		NO_PRODUCER,
		/** The kind needs a hall, and there is no hall of ours left. */
		NO_HALL,
		/** The site search walked out of rings without a free footprint near the base. */
		NO_SITE,
		/** The rules could not read this part of the techtree, so the plan leaves it alone. */
		UNREADABLE
	}

	private final Reason reason;
	private final War3ID missing;
	private final int level;

	private Stuck(final Reason reason, final War3ID missing, final int level) {
		this.reason = reason;
		this.missing = missing;
		this.level = level;
	}

	public static Stuck of(final Reason reason) {
		return new Stuck(reason, null, 0);
	}

	public static Stuck missing(final War3ID missing, final int level) {
		return new Stuck(Reason.MISSING_REQUIREMENT, missing, level);
	}

	public Reason getReason() {
		return this.reason;
	}

	/** The rawcode the player is missing, or null when the reason names none. */
	public War3ID getMissing() {
		return this.missing;
	}

	public int getLevel() {
		return this.level;
	}

	@Override
	public String toString() {
		if (this.missing != null) {
			return this.reason + " " + this.missing.asStringValue() + " level " + this.level;
		}
		return this.reason.toString();
	}
}
