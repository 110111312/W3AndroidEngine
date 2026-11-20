package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;

/**
 * How long the walk stick keeps its move orders to itself after a skill is cast, so the cast survives.
 * <p>
 * A move order interrupts a spell in this engine, as it does in the game it comes from, and the stick of a thumb that
 * is held re-issues its move order every few frames. Without a hold, every skill cast while walking was cancelled
 * before it fired — including the instant ones, because a cast that has an animation is still a cast.
 * <p>
 * The length is per skill, because the animations are: the number is how long the cast needs before a move order may
 * interrupt it, with a margin. It is a table and not a data field because nothing in the ability data describes a
 * cast's length — the unit's cast point is a multiplier on a model's spell sequence, which is animation data this
 * screen never sees. Skill classes that are not named here fall back to {@link #DEFAULT_MS}.
 */
final class HeroCastHold {
	/** Enough for a cast whose animation nobody measured; Blink alone is around a third of a second. */
	static final long DEFAULT_MS = 400L;

	private HeroCastHold() {
	}

	/** How long the stick must wait before it may order another move, for the skill behind this ability. */
	static long holdMs(final CAbility ability) {
		if (ability == null) {
			return 0L;
		}
		switch (ability.getAlias().getValue()) {
		case 0x4145666b: // AEfk, Fan of Knives, the Warden's own spin
		case 0x41556373: // AUcs, Carrion Swarm
			return 300L;
		case 0x4145626c: // AEbl, Blink: a short wind-up, then the body is elsewhere
			return 320L;
		case 0x41457368: // AEsh, Shadow Strike
			return 380L;
		case 0x4148627a: // AHbz, Blizzard: the caster holds the storm up before it falls
			return 500L;
		case 0x41457665: // AEve, Vengeance: a transformation, the longest of her four
			return 450L;
		default:
			return DEFAULT_MS;
		}
	}
}
