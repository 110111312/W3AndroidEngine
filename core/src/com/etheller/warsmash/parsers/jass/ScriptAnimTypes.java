package com.etheller.warsmash.parsers.jass;

import com.etheller.warsmash.viewer5.handlers.w3x.AnimationTokens.SecondaryTag;

/** The animtype and subanimtype constants of common.j as the names the model sequences use. */
final class ScriptAnimTypes {
	private static final String[] ANIM_NAMES = { "birth", "death", "decay", "dissipate", "stand", "walk", "attack",
			"morph", "sleep", "spell", "portrait" };
	// SUBANIM_TYPE_ROOTED is 11; the tags no sequence name of this engine's models uses are null.
	private static final SecondaryTag[] SUB_ANIMS = { null, SecondaryTag.ALTERNATEEX, SecondaryTag.LOOPING,
			SecondaryTag.SLAM, SecondaryTag.THROW, SecondaryTag.SPIKED, SecondaryTag.FAST, SecondaryTag.SPIN,
			SecondaryTag.READY, SecondaryTag.CHANNEL, SecondaryTag.DEFEND, SecondaryTag.VICTORY, SecondaryTag.TURN,
			SecondaryTag.LEFT, SecondaryTag.RIGHT, SecondaryTag.FIRE, SecondaryTag.FLESH, SecondaryTag.HIT,
			SecondaryTag.WOUNDED, SecondaryTag.LIGHT, SecondaryTag.MODERATE, SecondaryTag.SEVERE,
			SecondaryTag.CRITICAL, SecondaryTag.COMPLETE, SecondaryTag.GOLD, SecondaryTag.LUMBER, SecondaryTag.WORK,
			SecondaryTag.TALK, SecondaryTag.FIRST, SecondaryTag.SECOND, SecondaryTag.THIRD, SecondaryTag.FOURTH,
			SecondaryTag.FIFTH, SecondaryTag.ONE, SecondaryTag.TWO, SecondaryTag.THREE, SecondaryTag.FOUR,
			SecondaryTag.FIVE, SecondaryTag.SMALL, SecondaryTag.MEDIUM, SecondaryTag.LARGE, SecondaryTag.UPGRADE,
			SecondaryTag.DRAIN, SecondaryTag.FILL, SecondaryTag.CHAIN, SecondaryTag.EATTREE, SecondaryTag.PUKE,
			SecondaryTag.FLAIL, SecondaryTag.OFF, SecondaryTag.SWIM, null, null };
	private static final int FIRST_SUB_ANIM = 11;

	private ScriptAnimTypes() {
	}

	/** The name of ANIM_TYPE_* (0 to 10), or null for a number that is none. */
	static String animName(final int animType) {
		return (animType >= 0) && (animType < ANIM_NAMES.length) ? ANIM_NAMES[animType] : null;
	}

	/** The tag of SUBANIM_TYPE_*, or null when no sequence name of the models uses it. */
	static SecondaryTag subAnim(final int subAnimType) {
		final int index = subAnimType - FIRST_SUB_ANIM;
		return (index >= 0) && (index < SUB_ANIMS.length) ? SUB_ANIMS[index] : null;
	}
}
