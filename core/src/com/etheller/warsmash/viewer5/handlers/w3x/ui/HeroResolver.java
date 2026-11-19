package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.List;
import java.util.function.Predicate;

/**
 * Which unit hero mode plays: the first hero of the local player, resolved once when the map starts. Campaign missions
 * and custom games ask the same question of the same list, so there is deliberately no branch on the game's kind here
 * — the difference between them is which units the map's script gave the player, not how the hero is chosen.
 * <p>
 * A map where the local player owns no hero resolves to null, and there hero mode stays inert and the RTS console is
 * played instead, rather than handing the player a screen with nothing to control.
 */
public final class HeroResolver {
	private HeroResolver() {
	}

	/**
	 * @param heroes the local player's heroes, in the order the game keeps them
	 * @param usable whether a hero can be played at all, normally that it is not dead
	 * @return the first usable hero, the first hero when none is usable, or null when there is no hero
	 */
	public static <T> T firstHero(final List<T> heroes, final Predicate<T> usable) {
		if ((heroes == null) || heroes.isEmpty()) {
			return null;
		}
		for (final T hero : heroes) {
			if (usable.test(hero)) {
				return hero;
			}
		}
		// Every hero is dead (a scripted start, a mission about to end): the player still has a hero to be shown.
		return heroes.get(0);
	}
}
