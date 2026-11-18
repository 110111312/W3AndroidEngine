package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.EffectAttachmentUI;

/**
 * Which art a caster effect draws. Classic 1.29 leaves Casterart empty and declares the burst in Effectart — Fan of
 * Knives' whole section is `Casterart=` empty and
 * `Effectart=Abilities\Spells\NightElf\FanOfKnives\FanOfKnivesCaster.mdl`, and nothing else — while the data the
 * engine was written against declares Casterart instead. Requesting only the caster art drew nothing on 1.29 data.
 * <p>
 * Effectart only counts as the caster's when the ability declares no other art: Flame Strike's section declares
 * `Specialart` too, and that effect belongs at the target point.
 */
class CasterArtFallbackTest {
	private static final String BURST = "Abilities\\Spells\\NightElf\\FanOfKnives\\FanOfKnivesCaster.mdl";
	private static final String CASTER_ART = "Abilities\\Spells\\NightElf\\Taunt\\TauntCaster.mdl";

	@Test
	void theDatasCasterArtIsUsedWhenItHasOne() {
		final EffectAttachmentUI caster = art(CASTER_ART);
		assertSame(caster, MapViewerEffects.casterArt(caster, art(BURST), false));
		// Even for an ability that also draws something at its target, a declared caster art wins.
		assertSame(caster, MapViewerEffects.casterArt(caster, art(BURST), true));
	}

	@Test
	void anEmptyCasterArtFallsBackToTheEffectArt() {
		assertEquals(BURST, MapViewerEffects.casterArt(art(""), art(BURST), false).getModelPath());
		assertEquals(BURST, MapViewerEffects.casterArt(null, art(BURST), false).getModelPath());
		assertEquals(BURST,
				MapViewerEffects.casterArt(new EffectAttachmentUI(null, null), art(BURST), false).getModelPath());
		assertEquals(BURST, MapViewerEffects.casterArt(art("_"), art(BURST), false).getModelPath());
	}

	@Test
	void anEffectArtThatBelongsToATargetIsNotDrawnOnTheCaster() {
		// Flame Strike: Effectart is the fire, Specialart says where it goes; nothing is drawn on the caster.
		final EffectAttachmentUI fire = art("Abilities\\Spells\\Human\\FlameStrike\\FlameStrikeTarget.mdl");
		final EffectAttachmentUI drawn = MapViewerEffects.casterArt(art(""), fire, true);
		assertTrue((drawn == null) || !MapViewerEffects.namesModel(drawn.getModelPath()));
	}

	@Test
	void theNoModelPlaceholderIsNotAModel() {
		assertFalse(MapViewerEffects.namesModel("_"));
		assertFalse(MapViewerEffects.namesModel("_.mdl"));
		assertFalse(MapViewerEffects.namesModel("_.mdx"));
		assertFalse(MapViewerEffects.namesModel(""));
		assertTrue(MapViewerEffects.namesModel(BURST));
	}

	@Test
	void aListOfEmptyCasterArtFallsBackToTheEffectArt() {
		// AbilityDataUI builds one entry per attach point, so an empty Casterart cell becomes a list holding "".
		final List<EffectAttachmentUI> empty = Collections.singletonList(art(""));
		final List<EffectAttachmentUI> effect = Collections.singletonList(art(BURST));
		assertSame(effect, MapViewerEffects.casterArt(empty, effect, false));
		assertSame(empty, MapViewerEffects.casterArt(empty, effect, true));

		final List<EffectAttachmentUI> casters = Collections.singletonList(art(CASTER_ART));
		assertSame(casters, MapViewerEffects.casterArt(casters, effect, true));
	}

	private static EffectAttachmentUI art(final String modelPath) {
		return new EffectAttachmentUI(modelPath, Collections.emptyList());
	}
}
