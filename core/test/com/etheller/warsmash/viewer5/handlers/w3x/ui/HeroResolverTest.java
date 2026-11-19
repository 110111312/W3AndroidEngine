package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Which unit hero mode plays. The rule is one rule for both kinds of game: the first hero of the local player. A
 * campaign mission and a custom game differ in which units the script gave the player, not in how one is picked, so
 * there is no game kind to pass here — that is the property these tests hold down, and the fourth asserts the map
 * with no hero falls back to the console rather than to a broken screen.
 */
class HeroResolverTest {
	@Test
	void aCampaignMissionPlaysItsFirstHero() {
		assertEquals("malFurion", HeroResolver.firstHero(Arrays.asList("malFurion", "tyrande", "illidan"),
				(hero) -> true));
	}

	@Test
	void aCustomGamePlaysItsFirstHeroByTheSameRule() {
		// The same list, resolved the same way: nothing in the resolver knows or asks what kind of game this is.
		final List<String> heroes = Arrays.asList("bladeMaster", "farSeer");
		assertEquals("bladeMaster", HeroResolver.firstHero(heroes, (hero) -> true));
		assertEquals("bladeMaster", HeroResolver.firstHero(heroes, (hero) -> true));
	}

	@Test
	void aDeadFirstHeroLeavesTheNextLivingOne() {
		assertEquals("tyrande", HeroResolver.firstHero(Arrays.asList("malFurion", "tyrande"),
				(hero) -> !"malFurion".equals(hero)));
	}

	@Test
	void everyHeroDeadStillGivesThePlayersFirstHero() {
		assertEquals("malFurion", HeroResolver.firstHero(Arrays.asList("malFurion", "tyrande"), (hero) -> false));
	}

	@Test
	void noHeroAtAllMeansNoHeroMode() {
		assertNull(HeroResolver.firstHero(Collections.emptyList(), (hero) -> true));
		assertNull(HeroResolver.firstHero(null, (hero) -> true));
	}
}
