package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * The one ratio of the map loading bar, kept by the two passes that fill it. While a launcher's menu is still being
 * built, its preview of the map's own loading screen fills the first {@link #PREVIEW_BAR_SHARE} of the bar; the map
 * loader's own completion fills the rest. Each pass reports only its own progress, and the bar's ratio is their sum,
 * which may not go back while a load is under way — without that, a pass reporting out of turn (or a screen filled
 * from zero after the bar was already up) is seen as the bar filling twice.
 */
public final class LoadingBarProgress {
	/** The share of the bar the preview of the loading screen fills while the rest of the menu is built. */
	public static final float PREVIEW_BAR_SHARE = 0.25f;

	/** The part of the bar the preview of this map's loading screen filled. */
	private float previewed;
	/** The bar's ratio as last drawn, from 0 to 1. */
	private float ratio;

	/**
	 * The screen of a map whose loading starts. A previewed screen goes on from the part its preview filled; any other
	 * starts the bar at the beginning and leaves the previous map's preview behind.
	 *
	 * @param previewed whether this map's own loading screen was previewed while the menu was built
	 */
	public void startLoad(final boolean previewed) {
		if (!previewed) {
			this.previewed = 0;
		}
		this.ratio = this.previewed;
	}

	/** The screen went without being filled: the next map starts the bar at the beginning. */
	public void reset() {
		this.previewed = 0;
		this.ratio = 0;
	}

	/**
	 * The menu has built enough of the map's loading screen to draw it, so the preview fills the first share of the
	 * bar.
	 *
	 * @param menuRatio how much of the menu is built, 0 to 1
	 */
	public void setPreview(final float menuRatio) {
		this.previewed = PREVIEW_BAR_SHARE * clamp(menuRatio);
		advanceTo(this.previewed);
	}

	/**
	 * The map loader's own completion, drawn after the part the preview filled.
	 *
	 * @param completionRatio how much of the map is loaded, 0 to 1
	 */
	public void setCompletion(final float completionRatio) {
		advanceTo(this.previewed + ((1 - this.previewed) * clamp(completionRatio)));
	}

	/** The map is up: the bar reads as filled while the screen waits for a tap. */
	public void complete() {
		advanceTo(1);
	}

	/** @return the bar's ratio, from 0 to 1 */
	public float getRatio() {
		return this.ratio;
	}

	private void advanceTo(final float target) {
		this.ratio = Math.max(this.ratio, clamp(target));
	}

	private static float clamp(final float value) {
		return (value < 0 || Float.isNaN(value)) ? 0 : Math.min(value, 1);
	}
}
