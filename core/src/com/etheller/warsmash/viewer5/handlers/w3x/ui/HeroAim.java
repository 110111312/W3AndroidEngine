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

	/** Above this the ability data is saying "as far as it likes", as Blink's 99999 does, not naming a distance. */
	static final float UNLIMITED_RANGE = 5000f;
	/** What a spell with no reach of its own is laid out against, so its aim still moves like the others. */
	static final float DEFAULT_RANGE = 1200f;

	/**
	 * The reach a spell's aim is scaled and stopped by. A spell that declares no reach, or the data's unlimited one,
	 * gets {@link #DEFAULT_RANGE} instead: scaling by 99999 put the aim ten thousand units past the camera for a
	 * thumb's width of drag.
	 */
	static float scaleRange(final float declaredRange) {
		return ((declaredRange > 0f) && (declaredRange < UNLIMITED_RANGE)) ? declaredRange : DEFAULT_RANGE;
	}

	/**
	 * Where a scaled aim points: from the hero the way the thumb went, as far as {@code screenDragPx * worldPerPx},
	 * and never past {@code maxDistance}.
	 * <p>
	 * Laying the world point the finger covers straight onto the map is honest but useless here: a spell's reach is
	 * hundreds of units and a thumb's comfortable sweep is a few centimetres, so the far end of the range was
	 * unreachable. Scaling the drag instead makes the range ring the mark to reach: {@code worldPerPx} is the cast
	 * range divided by the drag that is meant to span it, so the same flick fills the ring for a short spell and a
	 * long one alike.
	 *
	 * @param worldPerPx cast range per screen pixel dragged, or a non-positive value to keep the plain 1:1 drag
	 * @param maxDistance the cast range, which the aim is not allowed past; ignored when not positive
	 */
	static void fromScaledDrag(final float heroX, final float heroY, final float pressX, final float pressY,
			final float dragX, final float dragY, final float screenDragPx, final float worldPerPx,
			final float maxDistance, final float[] out) {
		final float deltaX = dragX - pressX;
		final float deltaY = dragY - pressY;
		final float length = (float) Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
		if (!(length > 0f)) {
			out[0] = heroX;
			out[1] = heroY;
			return;
		}
		float distance = length;
		if (worldPerPx > 0f) {
			distance = screenDragPx * worldPerPx;
		}
		if (maxDistance > 0f) {
			distance = Math.min(distance, maxDistance);
		}
		out[0] = heroX + ((deltaX / length) * distance);
		out[1] = heroY + ((deltaY / length) * distance);
	}

	/**
	 * The angle of the aim, in radians, from the hero to the aim point: the thumb's own direction, which is what a
	 * direction cast needs.
	 */
	static float angle(final float heroX, final float heroY, final float aimX, final float aimY) {
		return (float) Math.atan2(aimY - heroY, aimX - heroX);
	}
}
