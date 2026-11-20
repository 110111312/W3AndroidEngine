package com.etheller.warsmash.input.touch;

/**
 * Gestures recognized from raw pointers, with no meaning attached yet; an adapter decides what each one does on the
 * current screen. Coordinates are screen pixels, top left origin.
 */
public interface GestureListener {
	/** The first pointer of a gesture went down; what the gesture becomes is not known yet. */
	default void pressed(final float x, final float y, final long timeMillis) {
	}

	/** The finger or pen moved while still undecided, so hover effects can follow it. */
	void pointerAt(float x, float y);

	void tap(float x, float y, boolean pen);

	/** An order at the point, as a right mouse click gives it: what a double tap or a pen hold turns into. */
	void secondaryTap(float x, float y);

	/** Fires as soon as the press has been held long enough, not on release. */
	void longPress(float x, float y);

	void longPressEnd(float x, float y);

	/** A second finger tapped while the first one holds a long press; reported at the held finger. */
	void holdTap(float heldX, float heldY);

	void dragStart(float x, float y, boolean pen);

	void drag(float x, float y);

	void dragEnd(float x, float y);

	/** A finger held long enough for a long press may still start a drag here, if it is on a slider. */
	default boolean canDragAfterLongPress(final float x, final float y) {
		return false;
	}

	/**
	 * How many times the usual movement a press at this point may drift before it counts as a drag; a point whose drag
	 * means more than its tap, such as an item slot, asks for more room so that an unsteady tap stays a tap.
	 */
	default float dragSlopFactor(final float x, final float y) {
		return 1;
	}

	void panStart(float x, float y);

	void pan(float x, float y);

	void panEnd(float x, float y);

	/** One zoom step: positive when the fingers spread apart, negative when they pinch together. */
	void zoomStep(int direction);

	void twoFingerTap(float x, float y);

	/** The gesture in progress was aborted, for example by the system or a palm; nothing should be carried out. */
	void cancel();

	/**
	 * A second finger landed while the first one's gesture is still running. A screen that has something for it — a
	 * hero-mode skill button, where the finger aims while the other thumb walks — answers true and then gets this
	 * finger's movements and its lift through {@link #secondaryMove} and {@link #secondaryUp}, while the first
	 * finger's gesture carries on untouched. Answering false leaves everything as it was: two fingers are a pan.
	 */
	default boolean secondaryDown(final float x, final float y, final int pointerId) {
		return false;
	}

	default void secondaryMove(final float x, final float y) {
	}

	default void secondaryUp(final float x, final float y) {
	}
}
