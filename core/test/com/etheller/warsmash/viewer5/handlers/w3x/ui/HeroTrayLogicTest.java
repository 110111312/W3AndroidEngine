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

	/**
	 * The aim is scaled, not mapped one to one: the same sweep of the thumb reaches the range ring whatever the spell's
	 * range is, because a spell reaches hundreds of units and a thumb moves centimetres.
	 */
	@Test
	void aSweepOfTheThumbReachesTheRangeRingWhateverTheSpellReaches() {
		final float[] out = new float[2];
		final float fullDragPx = 240f;
		// A 900 unit spell: 240 px of drag is exactly its range, and more drag does not pass it.
		final float perPx = 900f / fullDragPx;
		HeroAim.fromScaledDrag(0f, 0f, 0f, 0f, 100f, 0f, 100f, perPx, 900f, out);
		assertEquals(375f, out[0], 0.01f);
		assertEquals(0f, out[1], 0.01f);
		HeroAim.fromScaledDrag(0f, 0f, 0f, 0f, 240f, 0f, 240f, perPx, 900f, out);
		assertEquals(900f, out[0], 0.01f);
		HeroAim.fromScaledDrag(0f, 0f, 0f, 0f, 900f, 0f, 900f, perPx, 900f, out);
		assertEquals(900f, out[0], 0.01f);

		// A 300 unit spell: the same 240 px still fills its ring, and it is clamped there.
		final float shortPerPx = 300f / fullDragPx;
		HeroAim.fromScaledDrag(0f, 0f, 0f, 0f, 240f, 0f, 240f, shortPerPx, 300f, out);
		assertEquals(300f, out[0], 0.01f);
	}

	/** The direction comes from the world points, the distance from how far the thumb went, even up and left. */
	@Test
	void aScaledAimKeepsTheThumbsDirectionAndStopsShortOfNothingElse() {
		final float[] out = new float[2];
		// Thumb went 300 left and 300 up on the ground, 200 px of drag, 2 units per px, range 800.
		HeroAim.fromScaledDrag(10f, 20f, 310f, 320f, 10f, 20f, 200f, 2f, 800f, out);
		assertEquals(10f - (400f / (float) Math.sqrt(2f)), out[0], 0.01f);
		assertEquals(20f - (400f / (float) Math.sqrt(2f)), out[1], 0.01f);
		// A thumb that never moved points nowhere: the aim is on the hero, not somewhere with a direction from nothing.
		HeroAim.fromScaledDrag(10f, 20f, 500f, 500f, 500f, 500f, 0f, 2f, 800f, out);
		assertEquals(10f, out[0], 0.01f);
		assertEquals(20f, out[1], 0.01f);
	}

	/** A skill the table does not name still gets a hold, because a cast that has an animation is still a cast. */
	@Test
	void everySkillGetsACastHoldEvenWhenItIsNotNamed() {
		assertEquals(HeroCastHold.DEFAULT_MS, 400L);
	}

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
	void anAimThatHasNotMovedIsTheHero() {
		final float[] out = new float[2];
		HeroAim.fromDrag(1000f, 500f, 2300f, 900f, 2300f, 900f, out);

		assertEquals(1000f, out[0], 0.001f);
		assertEquals(500f, out[1], 0.001f);
	}

	@Test
	void theAimMovesAsFarAsTheThumbDid() {
		final float[] out = new float[2];
		// A press on the tray and 200 to the left and 150 up: the circle is that far from the hero, not at the thumb.
		HeroAim.fromDrag(1000f, 500f, 2300f, 900f, 2100f, 750f, out);

		assertEquals(800f, out[0], 0.001f);
		assertEquals(350f, out[1], 0.001f);
	}

	@Test
	void aLongDragGoesPastTheCastRangeBecauseTheEngineWalksTheCasterIn() {
		final float[] out = new float[2];
		HeroAim.fromDrag(0f, 0f, 0f, 0f, 0f, 3000f, out);

		assertEquals(3000f, out[1], 0.001f);
		assertEquals((float) (Math.PI / 2d), HeroAim.angle(0f, 0f, out[0], out[1]), 0.001f);
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

	// ------------------------------------------------------------------ the tray's corners

	@Test
	void everyButtonOfTheTraySitsInsideTheInterface() {
		// The corner is the interface's bottom-right, so a button is left of it and above it. Getting this backwards
		// puts the whole cluster off the screen edge, which no amount of drawing makes up for.
		final float right = 1920f;
		final float bottom = 0f;
		final float unit = 3f;
		final float[][] rings = { { 175.5f, 100f }, { 175.5f, 124f }, { 175.5f, 148f }, { 175.5f, 172f },
				{ 97f, 133.7f }, { 118f, 104f }, { 118f, 165.7f } };
		for (final float[] ring : rings) {
			final float[] point = HeroTray.onRing(right, bottom, unit, ring[0], ring[1]);
			assertTrue(point[0] < right, "x " + point[0] + " of the button at " + ring[1] + " degrees is off the edge");
			assertTrue(point[1] > bottom, "y " + point[1] + " of the button at " + ring[1] + " degrees is below");
			final float reached = (float) Math.hypot(right - point[0], point[1] - bottom);
			assertEquals(ring[0] * unit, reached, 0.01f);
		}
	}

	@Test
	void theUltimateIsTheLowestAngleAndSoTheTopRightOne() {
		final float[] first = HeroTray.onRing(0f, 0f, 1f, 100f, 172f);
		final float[] ult = HeroTray.onRing(0f, 0f, 1f, 100f, 100f);
		assertTrue(ult[0] > first[0], "the ultimate is further right than the first skill");
		assertTrue(ult[1] > first[1], "the ultimate is higher than the first skill");
	}

	@Test
	void noButtonIsEverCutByTheScreenEdge() {
		// The dp of the drawing are not the dp of every phone: at 480 dpi the lowest skill of the arc hangs a few
		// pixels over the bottom edge, so the cluster is moved in by whatever overflows, and by nothing otherwise.
		final float right = 1920f;
		final float bottom = 0f;
		final float[][] buttons = { { 149f, 172f, 22f }, { 149f, 148f, 22f }, { 149f, 124f, 22f }, { 149f, 100f, 22f },
				{ 82.5f, 133.7f, 29f }, { 100f, 104f, 15f }, { 100f, 165.7f, 15f } };
		for (final float unit : new float[] { 1f, 2.625f, 3f, 3.5f }) {
			final float[] shift = HeroTray.fitShift(right, bottom, unit);
			assertTrue(shift[0] >= 0f, "the shift never moves the cluster further out");
			assertTrue(shift[1] >= 0f, "the shift never moves the cluster further out");
			assertTrue(shift[0] < 6f * unit, "the shift stays small: " + shift[0] + " at " + unit);
			assertTrue(shift[1] < 6f * unit, "the shift stays small: " + shift[1] + " at " + unit);
			final float inRight = right - shift[0];
			final float inBottom = bottom + shift[1];
			for (final float[] button : buttons) {
				final float[] point = HeroTray.onRing(inRight, inBottom, unit, button[0], button[1]);
				assertTrue(point[0] + (button[2] * unit) <= right + 0.001f, "a button crosses the right edge at " + unit);
				assertTrue(point[1] - (button[2] * unit) >= bottom - 0.001f,
						"a button crosses the bottom edge at " + unit);
			}
		}
	}
}