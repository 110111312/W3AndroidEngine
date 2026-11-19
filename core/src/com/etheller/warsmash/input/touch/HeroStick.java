package com.etheller.warsmash.input.touch;

/**
 * Geometry of the floating movement stick, with no drawing and no input plumbing around it. The stick has no fixed
 * base: it appears under the thumb that lands in the left zone, keeps its origin for as long as the thumb stays down,
 * and vanishes on release. The drag vector from that origin gives the direction, and its length only has to clear the
 * dead zone — how far the thumb is dragged never sets the hero's speed.
 */
public final class HeroStick {
	private final TouchSettings settings;
	private boolean active;
	private float originX;
	private float originY;
	private float fingerX;
	private float fingerY;

	public HeroStick(final TouchSettings settings) {
		this.settings = settings;
	}

	public void begin(final float x, final float y) {
		this.active = true;
		this.originX = x;
		this.originY = y;
		this.fingerX = x;
		this.fingerY = y;
	}

	public void move(final float x, final float y) {
		this.fingerX = x;
		this.fingerY = y;
	}

	public void end() {
		this.active = false;
		this.originX = 0f;
		this.originY = 0f;
		this.fingerX = 0f;
		this.fingerY = 0f;
	}

	public boolean isActive() {
		return this.active;
	}

	public float getOriginX() {
		return this.originX;
	}

	public float getOriginY() {
		return this.originY;
	}

	public boolean hasDirection() {
		if (!this.active) {
			return false;
		}
		final float dragX = this.fingerX - this.originX;
		final float dragY = this.fingerY - this.originY;
		return dragX * dragX + dragY * dragY >= this.settings.stickDeadZonePx * this.settings.stickDeadZonePx;
	}

	public float getDirectionX() {
		if (!hasDirection()) {
			return 0f;
		}
		return (this.fingerX - this.originX) / dragLength();
	}

	public float getDirectionY() {
		if (!hasDirection()) {
			return 0f;
		}
		return (this.fingerY - this.originY) / dragLength();
	}

	public float getKnobOffsetX() {
		return this.active ? knobOffset(this.fingerX - this.originX) : 0f;
	}

	public float getKnobOffsetY() {
		return this.active ? knobOffset(this.fingerY - this.originY) : 0f;
	}

	private float dragLength() {
		final float dragX = this.fingerX - this.originX;
		final float dragY = this.fingerY - this.originY;
		return (float) Math.sqrt(dragX * dragX + dragY * dragY);
	}

	/** One axis of the drag, shortened so that the knob drawn on it stays inside the outer ring. */
	private float knobOffset(final float dragAxis) {
		final float maxOffset = this.settings.stickRadiusPx - this.settings.stickKnobRadiusPx * 0.5f;
		final float length = dragLength();
		return length > maxOffset ? dragAxis * maxOffset / length : dragAxis;
	}
}
