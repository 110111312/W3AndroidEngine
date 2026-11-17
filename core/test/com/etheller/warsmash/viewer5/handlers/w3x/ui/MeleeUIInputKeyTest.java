package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.Input;

class MeleeUIInputKeyTest {
	@Test
	void aButtonAnswersToItsOwnKeyInAnyLayout() {
		// Classic layout: Water Elemental on the second place of the top row keeps the data's key W.
		assertTrue(MeleeUIInput.slotMatches(false, 'W', 'W', Input.Keys.W, Input.Keys.W));
		// Stop in the same layout is on the third row; its key is S, so the grid's W does not press it.
		assertFalse(MeleeUIInput.slotMatches(false, 'S', 'W', Input.Keys.W, Input.Keys.W));
		// The grid layout gives the button its place's key; a key the player set on it wins over the place.
		assertTrue(MeleeUIInput.slotMatches(true, 'G', 'G', Input.Keys.G, Input.Keys.Q));
		assertFalse(MeleeUIInput.slotMatches(true, 'G', 'Q', Input.Keys.Q, Input.Keys.Q));
	}

	@Test
	void aButtonWithoutKeyAnswersToItsPlaceOnlyInTheGridLayout() {
		assertTrue(MeleeUIInput.slotMatches(true, '\0', 'X', Input.Keys.X, Input.Keys.X));
		assertFalse(MeleeUIInput.slotMatches(false, '\0', 'X', Input.Keys.X, Input.Keys.X));
		assertFalse(MeleeUIInput.slotMatches(true, '\0', 'Q', Input.Keys.Q, Input.Keys.X));
	}

	@Test
	void escapeAnswersToTheTildeKey() {
		assertTrue(MeleeUIInput.slotMatches(false, '~', ' ', Input.Keys.ESCAPE, Input.Keys.C));
	}
}
