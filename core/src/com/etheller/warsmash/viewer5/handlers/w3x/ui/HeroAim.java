package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * Where an aimed skill lands: the point the thumb drew, pulled back onto the spell's own cast range.
 * <p>
 * The engine does not do this — an out-of-range point order is legal for a caster who can walk, and the hero simply
 * walks into range — but a phone mis-tap would send her halfway across the map, and the player cannot see the range
 * while the thumb covers the screen. So the tray clamps, and the order it sends is the clamped point, which is still an
 * ordinary point order that a replay or a LAN peer applies as such.
 */
final class HeroAim {
	private HeroAim() {
	}

	/**
	 * @param range    the spell's cast range, or anything <= 0 for "no clamp"
	 * @param out      receives the point, two floats
	 */
	static void clamp(final float heroX, final float heroY, final float aimX, final float aimY, final float range,
			final float[] out) {
		final float dx = aimX - heroX;
		final float dy = aimY - heroY;
		final float length = (float) Math.sqrt((dx * dx) + (dy * dy));
		if ((range <= 0f) || (length <= range) || (length == 0f)) {
			out[0] = aimX;
			out[1] = aimY;
			return;
		}
		final float scale = range / length;
		out[0] = heroX + (dx * scale);
		out[1] = heroY + (dy * scale);
	}

	/**
	 * The angle of the aim, in radians, for a line drawn from the hero: it is the thumb's own direction, which is what
	 * a direction cast needs, and it is unaffected by the clamp since the clamp only shortens the ray.
	 */
	static float angle(final float heroX, final float heroY, final float aimX, final float aimY) {
		return (float) Math.atan2(aimY - heroY, aimX - heroX);
	}
}
