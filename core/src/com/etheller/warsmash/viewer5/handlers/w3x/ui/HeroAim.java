package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * Where an aimed skill lands: the hero, plus what the thumb moved since it went down on the button.
 * <p>
 * The aim is a drag, not a point. A finger on a skill button is at the hero's back, not at the place the player wants
 * the spell: reading the thumb's absolute position would put the circle far from the hero the moment the button is
 * touched, at the corner of the screen where the tray is. So the press point is the origin, and the delta of the drag
 * is the spell's displacement, which is also how the range is reached — a longer drag goes further, past the cast range
 * if it comes to that, because the engine's own point orders walk a caster into range and the tray is not the place to
 * second-guess that.
 */
final class HeroAim {
	private HeroAim() {
	}

	/**
	 * @param out receives the point, two floats
	 */
	static void fromDrag(final float heroX, final float heroY, final float pressX, final float pressY, final float dragX,
			final float dragY, final float[] out) {
		out[0] = heroX + (dragX - pressX);
		out[1] = heroY + (dragY - pressY);
	}

	/**
	 * The angle of the aim, in radians, from the hero to the aim point: the thumb's own direction, which is what a
	 * direction cast needs.
	 */
	static float angle(final float heroX, final float heroY, final float aimX, final float aimY) {
		return (float) Math.atan2(aimY - heroY, aimX - heroX);
	}
}
