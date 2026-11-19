package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActivePointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveUnitTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.archmage.CAbilityBlizzard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.paladin.CAbilityHolyLight;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.warden.CAbilityBlink;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.nightelf.warden.CAbilityFanOfKnives;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.undead.dreadlord.CAbilityCarrionSwarmDummy;

/**
 * The parts of the hero tray that are decisions rather than drawing: which four skills it shows, when a level can be
 * bought, which items get a button, how far an aim is allowed to go, and which of the four cast types a spell is.
 */
class HeroTrayLogicTest {
	private static War3ID id(final String rawcode) {
		return War3ID.fromString(rawcode);
	}

	// ------------------------------------------------------------------ slots

	@Test
	void theTrayShowsTheHeroesFirstFourSkillsInDataOrder() {
		final List<War3ID> skills = new ArrayList<>(List.of(id("AEfk"), id("AEmb"), id("AEbl"), id("AEsh"),
				id("AEsf")));

		assertEquals(List.of(id("AEfk"), id("AEmb"), id("AEbl"), id("AEsh")), HeroSkillSlots.slots(skills));
	}

	@Test
	void aHeroWithFewerSkillsGetsFewerSlotsAndNoRepeats() {
		assertEquals(List.of(id("AEfk"), id("AEbl")),
				HeroSkillSlots.slots(List.of(id("AEfk"), id("AEbl"), id("AEfk"))));
		assertEquals(List.of(), HeroSkillSlots.slots(List.of()));
	}

	@Test
	void aBadgeIsShownOnlyWhenThePointCanBeSpentOnThatSlot() {
		// A point to spend, a level to gain, and the hero's level is enough.
		assertTrue(HeroSkillSlots.showsLearnBadge(0, 3, 1, 2, 1));
		assertTrue(HeroSkillSlots.showsLearnBadge(1, 3, 2, 3, 3));
		// No point.
		assertFalse(HeroSkillSlots.showsLearnBadge(0, 3, 0, 5, 1));
		// Already at the last level.
		assertFalse(HeroSkillSlots.showsLearnBadge(3, 3, 1, 9, 1));
		// The hero is not high enough for the next level.
		assertFalse(HeroSkillSlots.showsLearnBadge(0, 3, 1, 2, 3));
	}

	@Test
	void aCooldownFillsTheButtonAndGivesItBack() {
		assertEquals(1f, HeroSkillSlots.cooldownFraction(6f, 6f));
		assertEquals(0.5f, HeroSkillSlots.cooldownFraction(3f, 6f));
		assertEquals(0f, HeroSkillSlots.cooldownFraction(0f, 6f));
		assertEquals(0f, HeroSkillSlots.cooldownFraction(5f, 0f));
		assertEquals(1f, HeroSkillSlots.cooldownFraction(9f, 6f));
	}

	// ------------------------------------------------------------------ items

	@Test
	void itemButtonsAreTheUsableSlotsCompactedInOrder() {
		assertArrayEquals(new int[] { 1, 3, 4 },
				HeroItemSlots.buttons(new boolean[] { false, true, false, true, true, false }));
		assertArrayEquals(new int[] { 0 }, HeroItemSlots.buttons(new boolean[] { true, false, false }));
		assertArrayEquals(new int[] {}, HeroItemSlots.buttons(new boolean[] { false, false }));
	}

	@Test
	void onlyFourItemsGetButtonsAndTheFirstOnesWin() {
		assertArrayEquals(new int[] { 0, 1, 2, 3 },
				HeroItemSlots.buttons(new boolean[] { true, true, true, true, true, true }));
		assertArrayEquals(new int[] { 2, 4, 5, 8 },
				HeroItemSlots.buttons(new boolean[] { false, false, true, false, true, true, false, false, true, false }));
	}

	// ------------------------------------------------------------------ the aim

	@Test
	void anAimInsideTheRangeIsLeftWhereTheThumbPutIt() {
		final float[] out = new float[2];
		HeroAim.clamp(100f, 100f, 150f, 100f, 200f, out);

		assertEquals(150f, out[0], 0.001f);
		assertEquals(100f, out[1], 0.001f);
	}

	@Test
	void anAimPastTheRangeComesBackAlongItsOwnRay() {
		final float[] out = new float[2];
		HeroAim.clamp(0f, 0f, 300f, 400f, 500f, out);

		// The point is 500 away from the hero on a 3-4-5 ray.
		assertEquals(300f, out[0], 0.001f);
		assertEquals(400f, out[1], 0.001f);

		HeroAim.clamp(0f, 0f, 60f, 80f, 50f, out);
		assertEquals(30f, out[0], 0.001f);
		assertEquals(40f, out[1], 0.001f);
	}

	@Test
	void aSpellWithNoRangeAndAnAimOnTheHeroAreBothLeftAlone() {
		final float[] out = new float[2];
		HeroAim.clamp(10f, 10f, 900f, 900f, 0f, out);
		assertEquals(900f, out[0], 0.001f);

		HeroAim.clamp(10f, 10f, 10f, 10f, 100f, out);
		assertEquals(10f, out[0], 0.001f);
		assertEquals(10f, out[1], 0.001f);
	}

	@Test
	void theDirectionOfADragIsNotChangedByTheClamp() {
		final float[] out = new float[2];
		HeroAim.clamp(0f, 0f, 100f, 0f, 40f, out);
		assertEquals(40f, out[0], 0.001f);
		assertEquals(0f, HeroAim.angle(0f, 0f, out[0], out[1]), 0.001f);
	}

	// ------------------------------------------------------------------ the cast types

	@Test
	void theFourTargetShapesComeFromTheEngineBases() {
		assertEquals(HeroCastType.NO_TARGET, HeroCastTypes.ofClass(CAbilityFanOfKnives.class, id("AEfk")));
		assertEquals(HeroCastType.SINGLE_TARGET, HeroCastTypes.ofClass(CAbilityHolyLight.class, id("AHhb")));
		assertEquals(HeroCastType.GROUND, HeroCastTypes.ofClass(CAbilityBlizzard.class, id("AHbz")));
		assertEquals(HeroCastType.GROUND, HeroCastTypes.ofClass(CAbilityBlink.class, id("AEbl")));
	}

	@Test
	void aDirectionSpellIsNamedByRawcodeBecauseTheDataCannotSayIt() {
		// Breath of Fire accepts a point like any other ground spell; only the table knows the cone leaves the hero.
		assertEquals(HeroCastType.DIRECTION,
				HeroCastTypes.ofClass(CAbilityBlizzard.class, id("ANbf")));
		assertEquals(HeroCastType.DIRECTION,
				HeroCastTypes.ofClass(CAbilityCarrionSwarmDummy.class, id("AUcs")));
	}

	@Test
	void aJassBuiltAbilityIsKnownByItsBehavioursClass() {
		// Shadow Strike has no class of its own: the ability builder makes it a unit target, and says so by the class.
		assertEquals(HeroCastType.SINGLE_TARGET,
				HeroCastTypes.ofClass(CAbilityAbilityBuilderActiveUnitTarget.class, id("AEsh")));
		assertEquals(HeroCastType.GROUND,
				HeroCastTypes.ofClass(CAbilityAbilityBuilderActivePointTarget.class, id("AEmb")));
	}

	@Test
	void anAbilityTheTableDoesNotKnowIsLeftToTheProbe() {
		assertNull(HeroCastTypes.ofClass(Object.class, id("AHer")));
	}
}
