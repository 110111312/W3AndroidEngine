package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * The macro sheet's arithmetic, in dp: how many icons fit across a sheet of a given width, how tall a grid of rows is,
 * and where a scroll stops. The panel draws and hit-tests with these numbers, so a mistake here is a cell nobody can
 * tap -- which is exactly the kind of bug a screenshot cannot show.
 */
final class MacroLayout {
	/**
	 * A small icon in a tight cell: the sheet is nearly the whole screen, so it is the count across that matters, and a
	 * 42dp cell puts fifteen or more on a phone's landscape row.
	 */
	static final float ICON = 36f;
	static final float CELL = 42f;
	static final float GAP = 4f;
	static final float MARGIN = 8f;
	/** The square a ticket's number sits in, and the one its minus sits in: both inside the cell's own bounds. */
	static final float CHIP = 15f;

	private MacroLayout() {
	}

	/** How many cells fit across a sheet of this width: whole cells only, and never fewer than one. */
	static int columns(final float widthDp) {
		final int fit = (int) Math.floor((widthDp - (2f * MARGIN) + GAP) / (CELL + GAP));
		return Math.max(1, fit);
	}

	/** The top of a row, measured down from the first one: the row's cells start here. */
	static float rowTopDp(final int row) {
		return row * (CELL + GAP);
	}

	/** How tall a grid of these rows is, gaps between rows included and none after the last. */
	static float rowsHeightDp(final int rows) {
		return (rows <= 0) ? 0f : ((rows * CELL) + ((rows - 1) * GAP));
	}

	/** A scroll kept between the top of the content and its last screenful, so a page can never scroll into nothing. */
	static float clampScroll(final float scrollDp, final float contentDp, final float bodyDp) {
		final float furthest = Math.max(0f, contentDp - bodyDp);
		return Math.min(Math.max(0f, scrollDp), furthest);
	}
}
