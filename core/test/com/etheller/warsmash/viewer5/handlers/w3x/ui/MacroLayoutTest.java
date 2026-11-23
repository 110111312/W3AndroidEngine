package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * The macro sheet's arithmetic: how many icons fit across it, how tall its rows are, and where its scroll stops. The
 * panel draws and hit-tests with these numbers, so a mistake here is a cell nobody can tap -- which is exactly the kind
 * of bug a screenshot cannot show.
 */
class MacroLayoutTest {
	@Test
	void theSheetHoldsSixIconsAcross() {
		// The sketch's 360dp: the margins come off, and the gaps go between the cells rather than around them.
		assertEquals(6, MacroLayout.columns(360f));
	}

	@Test
	void aNarrowSheetStillHoldsOneIconAndNeverNone() {
		assertEquals(1, MacroLayout.columns(80f));
		assertEquals(1, MacroLayout.columns(0f));
	}

	@Test
	void rowsStackByTheirOwnHeightPlusTheGap() {
		assertEquals(0f, MacroLayout.rowTopDp(0), 0.001f);
		assertEquals(MacroLayout.STRIP + MacroLayout.GAP, MacroLayout.rowTopDp(1), 0.001f);
		assertEquals(0f, MacroLayout.rowsHeightDp(0), 0.001f);
		// Two rows are two cells and the gap between them, not three.
		assertEquals((2f * MacroLayout.STRIP) + MacroLayout.GAP, MacroLayout.rowsHeightDp(2), 0.001f);
	}

	@Test
	void theScrollStopsAtTheTopAndAtTheEnd() {
		assertEquals(0f, MacroLayout.clampScroll(-40f, 300f, 120f), 0.001f);
		assertEquals(180f, MacroLayout.clampScroll(500f, 300f, 120f), 0.001f);
	}

	@Test
	void contentThatFitsDoesNotScrollAtAll() {
		assertEquals(0f, MacroLayout.clampScroll(60f, 100f, 120f), 0.001f);
	}
}
