package com.etheller.warsmash.launcher;

/**
 * How the Reforged campaign result comes in, from the game's glue style sheet: the banner (and its title) falls from
 * above the screen, overshoots a little and settles, then the numbers fade in. Time runs from the moment the page is
 * shown; both launchers draw from the same numbers.
 */
public final class ResultMotion {
	/**
	 * The glue waits a second before the banner moves, for the scene behind it to fade in; the launcher's page already
	 * has the scene, so it starts at once. A Reforged screenshot sequence has to confirm the choice.
	 */
	public static final long BANNER_DELAY_MS = 0;
	public static final long BANNER_SLIDE_MS = 600;
	/** The glue's content starts 0.1 s after the banner has settled (1.7 s against 1.0 + 0.6 s). */
	public static final long CONTENT_DELAY_MS = BANNER_DELAY_MS + BANNER_SLIDE_MS + 100;
	public static final long CONTENT_FADE_MS = 150;
	/** The banner's keyframes: the share of the slide where it is nearly down, and how far above its place it is then. */
	private static final float KEY_TIME = 0.6f;
	private static final float KEY_OFFSET = -0.01f;
	/** The slide's easing, the glue's cubic-bezier(.47, .01, 0, .99). */
	private static final float[] EASE = { 0.47f, 0.01f, 0f, 0.99f };

	private ResultMotion() {
	}

	/** The time after which nothing moves any more. */
	public static long totalMillis() {
		return CONTENT_DELAY_MS + CONTENT_FADE_MS;
	}

	/** Where the banner is, as a share of its height above its place: -1 outside the screen, 0 settled. */
	public static float bannerOffset(final long elapsed) {
		final float progress = (elapsed - BANNER_DELAY_MS) / (float) BANNER_SLIDE_MS;
		if (progress <= 0) {
			return -1;
		}
		if (progress >= 1) {
			return 0;
		}
		if (progress < KEY_TIME) {
			return -1 + ((KEY_OFFSET + 1) * ease(progress / KEY_TIME));
		}
		return KEY_OFFSET * (1 - ease((progress - KEY_TIME) / (1 - KEY_TIME)));
	}

	/** The opacity of what is not in the banner: the numbers, the mission and the difficulty. */
	public static float contentAlpha(final long elapsed) {
		final float progress = (elapsed - CONTENT_DELAY_MS) / (float) CONTENT_FADE_MS;
		return Math.max(0, Math.min(1, progress));
	}

	/** The cubic bezier timing function through (0, 0), (x1, y1), (x2, y2), (1, 1), as CSS has it. */
	static float ease(final float x) {
		if (x <= 0) {
			return 0;
		}
		if (x >= 1) {
			return 1;
		}
		float low = 0;
		float high = 1;
		float t = x;
		for (int i = 0; i < 24; i++) {
			final float at = bezier(t, EASE[0], EASE[2]);
			if (Math.abs(at - x) < 1e-5f) {
				break;
			}
			if (at < x) {
				low = t;
			}
			else {
				high = t;
			}
			t = (low + high) / 2;
		}
		return bezier(t, EASE[1], EASE[3]);
	}

	private static float bezier(final float t, final float p1, final float p2) {
		final float u = 1 - t;
		return (3 * u * u * t * p1) + (3 * u * t * t * p2) + (t * t * t);
	}
}
