package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.etheller.warsmash.util.War3ID;

/**
 * The four slots of the hero-mode skill tray, and what each of them says about itself.
 * <p>
 * The slots are the hero's own skill list in the order the unit data gives it, so the ultimate — always the last entry
 * of that list — lands in the last slot, which is the top-right of the arc. Passives take a slot like actives: the
 * point is that the tray is the same four buttons for the whole game, so the thumb learns where a skill is once.
 */
final class HeroSkillSlots {
	static final int SLOT_COUNT = 4;

	private HeroSkillSlots() {
	}

	/** The four slots, in data order; a hero with fewer skills gets fewer slots, and extras are dropped. */
	static List<War3ID> slots(final Collection<War3ID> skills) {
		final List<War3ID> slots = new ArrayList<>(SLOT_COUNT);
		for (final War3ID skill : skills) {
			if (slots.size() >= SLOT_COUNT) {
				break;
			}
			if (!slots.contains(skill)) {
				slots.add(skill);
			}
		}
		return slots;
	}

	/**
	 * Whether the "+" badge is drawn on a slot: there is a point to spend and this skill can take it. The engine's own
	 * answer is preferred where it can be had (it knows the hero level a level needs and the techtree maximum), and
	 * this is the same rule for a caller that only has the numbers.
	 */
	static boolean showsLearnBadge(final int level, final int maxLevel, final int skillPoints, final int heroLevel,
			final int requiredLevel) {
		if (skillPoints <= 0) {
			return false;
		}
		if ((maxLevel > 0) && (level >= maxLevel)) {
			return false;
		}
		return heroLevel >= requiredLevel;
	}

	/** How much of the icon a cooldown covers, from 1 at the moment of the cast to 0 when it is ready. */
	static float cooldownFraction(final float remaining, final float max) {
		if ((max <= 0f) || (remaining <= 0f)) {
			return 0f;
		}
		return Math.min(1f, remaining / max);
	}
}
