package com.etheller.warsmash.input.touch;

/**
 * The cadence of the point orders a held stick produces: the caller asks every frame, and this says when to actually
 * issue one. A turn sharper than {@link TouchSettings#stickTurnDegrees} is answered at once so the hero does not run
 * on in the old direction, and a held stick that keeps its direction is re-issued every
 * {@link TouchSettings#stickReissueMs} so the target point stays ahead of a hero who has walked.
 */
public final class HeroStickOrderEmitter {
	private final TouchSettings settings;
	private float lastDirectionX;
	private float lastDirectionY;
	private long lastIssuedMs;
	private boolean issued;

	public HeroStickOrderEmitter(final TouchSettings settings) {
		this.settings = settings;
	}

	/** True when the caller must now issue a point order somewhere ahead of the hero. */
	public boolean tick(final float directionX, final float directionY, final long nowMs) {
		final float length = (float) Math.sqrt(directionX * directionX + directionY * directionY);
		if (!(length > 0f)) {
			return false;
		}
		final float dx = directionX / length;
		final float dy = directionY / length;
		if (!this.issued) {
			record(dx, dy, nowMs);
			return true;
		}
		if (degreesBetween(dx, dy) > this.settings.stickTurnDegrees) {
			record(dx, dy, nowMs);
			return true;
		}
		if (nowMs - this.lastIssuedMs >= this.settings.stickReissueMs) {
			record(dx, dy, nowMs);
			return true;
		}
		return false;
	}

	public void reset() {
		this.issued = false;
		this.lastDirectionX = 0f;
		this.lastDirectionY = 0f;
		this.lastIssuedMs = 0L;
	}

	public float getLastDirectionX() {
		return this.lastDirectionX;
	}

	public float getLastDirectionY() {
		return this.lastDirectionY;
	}

	public boolean hasIssued() {
		return this.issued;
	}

	private void record(final float dx, final float dy, final long nowMs) {
		this.lastDirectionX = dx;
		this.lastDirectionY = dy;
		this.lastIssuedMs = nowMs;
		this.issued = true;
	}

	/** The unsigned turn between the last issued direction and a unit direction, in degrees, never more than 180. */
	private float degreesBetween(final float dx, final float dy) {
		final double dot = (double) this.lastDirectionX * dx + (double) this.lastDirectionY * dy;
		final double cross = (double) this.lastDirectionX * dy - (double) this.lastDirectionY * dx;
		return Math.abs((float) Math.toDegrees(Math.atan2(cross, dot)));
	}
}
