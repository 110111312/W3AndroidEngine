package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * The macro sheet's geometry, in dp: how many cells fit across it, which cell a point lands on, how far the body has
 * scrolled.
 * <p>
 * These are the parts of the panel that are arithmetic rather than art, so they are static and a test can walk the
 * corners of the grid without a phone. The panel draws with the same numbers, which is the whole point: a hit test that
 * disagrees with the drawing is a tap that does nothing, and it is invisible on a screenshot.
 */
final class MacroLayout {
	/**
	 * One icon and the cell it sits in, the gap between cells and the margin at the sheet's edge. The cell is small
	 * because the sheet is: a 4:3 interface on a wide phone leaves the right-middle band about 230dp, which is four
	 * icons across -- and the hero owns everything left of it.
	 */
	static final float ICON = 36f;
	static final float CELL = 46f;
	static final float GAP = 6f;
	static final float MARGIN = 8f;
	/**
	 * One row of cells: the icon, the caption under it and the status line under that, plus the air between two rows.
	 * The tall cell is what makes a ticket's words readable, so the strip is sized to hold all three.
	 */
	static final float STRIP = 64f;

	private MacroLayout() {
	}

	/** How many cells fit across a sheet this wide, at least one whatever the width says. */
	static int columns(final float widthDp) {
		return Math.max(1, (int) Math.floor((widthDp - (2f * MARGIN) + GAP) / (CELL + GAP)));
	}

	/** The top of a row, measured down from the first one: the row's cells start here. */
	static float rowTopDp(final int row) {
		return row * (STRIP + GAP);
	}

	/** How tall a list of rows is, gaps included. */
	static float rowsHeightDp(final int rows) {
		return (rows <= 0) ? 0f : ((rows * STRIP) + ((rows - 1) * GAP));
	}

	/** The scroll the body can have: never negative, never past the end of its content. */
	static float clampScroll(final float scrollDp, final float contentDp, final float bodyDp) {
		if (contentDp <= bodyDp) {
			return 0f;
		}
		return Math.max(0f, Math.min(scrollDp, contentDp - bodyDp));
	}
}
