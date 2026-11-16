package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class SkinArtRevisionTest {
	@Test
	void theRevisionIsSteadyForOneSetOfArtAndDiffersBetweenSkins() {
		assertEquals(SkinArt.revision(false), SkinArt.revision(false));
		assertEquals(SkinArt.revision(true), SkinArt.revision(true));
		assertNotEquals(SkinArt.revision(false), SkinArt.revision(true));
	}
}
