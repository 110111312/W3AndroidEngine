package com.etheller.warsmash.input.touch;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.badlogic.gdx.Gdx;

/**
 * Gives orders by double tap instead of by holding: a second tap soon after a first one, close to it, is an order
 * (move on open ground, attack on an enemy, gather from a resource, pick up an item) given where the first tap was.
 * Every other gesture reaches the listener behind unchanged, so with the tap-hold style the mapper is a plain pass
 * through.
 * <p>
 * The first tap of a pair is also a left click, which would select an enemy or a mine and leave the selection with
 * nobody to give the order. So when a unit of the player's is selected, a tap on anything but open ground or an own
 * unit is held back until the window for the second tap has passed; a second tap in time makes it an order and the
 * held click is dropped. Taps on open ground and on own units go through at once (a click there changes nothing, and
 * two quick clicks on an own unit select all of its type); a second tap on open ground is the order.
 */
public final class DoubleTapMapper implements GestureListener {
	private static final String LOG_TAG = "WarsmashTouch";

	private final GestureListener delegate;
	private final TouchSettings settings;
	private final Supplier<TouchTarget> targets;
	private final LongSupplier clock;
	private final boolean debugLog;
	private volatile boolean enabled;

	private boolean pendingTap;
	private float pendingX;
	private float pendingY;
	private boolean pendingPen;
	private int pendingModifiers;
	private long pendingUpTime;

	private boolean groundTapRecent;
	private float groundX;
	private float groundY;
	private long groundUpTime;
	private boolean groundSecondPress;

	private boolean secondPress;
	private long pressTime;
	private float pressX;
	private float pressY;

	public DoubleTapMapper(final GestureListener delegate, final TouchSettings settings,
			final Supplier<TouchTarget> targets, final LongSupplier clock, final boolean debugLog) {
		this.delegate = delegate;
		this.settings = settings;
		this.targets = targets;
		this.clock = clock;
		this.debugLog = debugLog;
		this.enabled = settings.touchStyle == TouchStyle.DOUBLE_TAP;
	}

	public void setStyle(final TouchStyle style) {
		this.enabled = style == TouchStyle.DOUBLE_TAP;
		if (!this.enabled) {
			flush();
			this.groundTapRecent = false;
			this.groundSecondPress = false;
		}
	}

	/** Called once a frame, after the frame's gestures. */
	public void update(final long nowMillis) {
		if (this.pendingTap && !this.secondPress && ((nowMillis - this.pendingUpTime) >= this.settings.doubleTapMs)) {
			flush();
		}
		// A second finger that came down in time is still on its way to lifting; the window is about when it landed.
		if (this.groundTapRecent && !this.groundSecondPress
				&& ((nowMillis - this.groundUpTime) > this.settings.doubleTapMs)) {
			this.groundTapRecent = false;
		}
	}

	private void flush() {
		if (this.pendingTap) {
			this.pendingTap = false;
			this.secondPress = false;
			log("held tap -> left click");
			// The pen's button or the on-screen Shift may have been let go while the tap waited.
			final int now = VirtualModifiers.snapshot();
			VirtualModifiers.restore(this.pendingModifiers);
			try {
				this.delegate.tap(this.pendingX, this.pendingY, this.pendingPen);
			}
			finally {
				VirtualModifiers.restore(now);
			}
		}
	}

	private void interrupted() {
		flush();
		this.groundTapRecent = false;
		this.groundSecondPress = false;
		this.secondPress = false;
	}

	private boolean near(final float ax, final float ay, final float bx, final float by) {
		return Math.hypot(ax - bx, ay - by) <= this.settings.doubleTapSlopPx;
	}

	@Override
	public void pressed(final float x, final float y, final long timeMillis) {
		this.pressTime = timeMillis;
		this.pressX = x;
		this.pressY = y;
		this.secondPress = this.enabled && this.pendingTap
				&& ((timeMillis - this.pendingUpTime) <= this.settings.doubleTapMs)
				&& near(x, y, this.pendingX, this.pendingY);
		this.groundSecondPress = this.enabled && this.groundTapRecent
				&& ((timeMillis - this.groundUpTime) <= this.settings.doubleTapMs)
				&& near(x, y, this.groundX, this.groundY);
		this.delegate.pressed(x, y, timeMillis);
	}

	@Override
	public void pointerAt(final float x, final float y) {
		this.delegate.pointerAt(x, y);
	}

	@Override
	public void tap(final float x, final float y, final boolean pen) {
		final TouchTarget target = this.targets.get();
		this.groundSecondPress = false;
		if (!this.enabled || (target == null)) {
			this.delegate.tap(x, y, pen);
			return;
		}
		final long now = this.clock.getAsLong();
		if (this.secondPress && this.pendingTap) {
			final float orderX = this.pendingX;
			final float orderY = this.pendingY;
			this.pendingTap = false;
			this.secondPress = false;
			log("double tap on a held target -> order");
			this.delegate.secondaryTap(orderX, orderY);
			return;
		}
		this.secondPress = false;
		flush();
		final int sx = Math.round(x);
		final int sy = Math.round(y);
		if (target.hasActiveCommand() || target.isPlacingBuilding()) {
			this.groundTapRecent = false;
			this.delegate.tap(x, y, pen);
			return;
		}
		final PointKind kind = target.pointKindAt(sx, sy);
		final boolean orderable = target.hasOrderableSelection();
		if (this.groundTapRecent && orderable && (kind == PointKind.GROUND)
				&& (this.pressTime - this.groundUpTime <= this.settings.doubleTapMs)
				&& near(this.pressX, this.pressY, this.groundX, this.groundY)) {
			this.groundTapRecent = false;
			log("double tap on ground -> order");
			this.delegate.secondaryTap(this.groundX, this.groundY);
			return;
		}
		if (this.groundTapRecent) {
			log("no order: kind=" + kind + " orderable=" + orderable + " gap=" + (this.pressTime - this.groundUpTime)
					+ "ms moved=" + (int) Math.hypot(this.pressX - this.groundX, this.pressY - this.groundY) + "px");
		}
		this.groundTapRecent = false;
		switch (kind) {
		case GROUND:
			this.groundTapRecent = true;
			this.groundX = x;
			this.groundY = y;
			this.groundUpTime = now;
			log("tap on ground, window open (orderable=" + orderable + ")");
			this.delegate.tap(x, y, pen);
			break;
		case ENEMY:
		case RESOURCE:
		case ITEM:
		case OTHER:
			if (orderable) {
				this.pendingTap = true;
				this.pendingX = x;
				this.pendingY = y;
				this.pendingPen = pen;
				this.pendingModifiers = VirtualModifiers.snapshot();
				this.pendingUpTime = now;
				log("tap held for a second tap");
			}
			else {
				this.delegate.tap(x, y, pen);
			}
			break;
		default:
			this.delegate.tap(x, y, pen);
			break;
		}
	}

	@Override
	public void secondaryTap(final float x, final float y) {
		interrupted();
		this.delegate.secondaryTap(x, y);
	}

	@Override
	public void longPress(final float x, final float y) {
		interrupted();
		this.delegate.longPress(x, y);
	}

	@Override
	public void longPressEnd(final float x, final float y) {
		this.delegate.longPressEnd(x, y);
	}

	@Override
	public void holdTap(final float heldX, final float heldY) {
		this.delegate.holdTap(heldX, heldY);
	}

	@Override
	public void dragStart(final float x, final float y, final boolean pen) {
		interrupted();
		this.delegate.dragStart(x, y, pen);
	}

	@Override
	public void drag(final float x, final float y) {
		this.delegate.drag(x, y);
	}

	@Override
	public void dragEnd(final float x, final float y) {
		this.delegate.dragEnd(x, y);
	}

	@Override
	public boolean canDragAfterLongPress(final float x, final float y) {
		return this.delegate.canDragAfterLongPress(x, y);
	}

	@Override
	public float dragSlopFactor(final float x, final float y) {
		return this.delegate.dragSlopFactor(x, y);
	}

	@Override
	public void panStart(final float x, final float y) {
		interrupted();
		this.delegate.panStart(x, y);
	}

	@Override
	public void pan(final float x, final float y) {
		this.delegate.pan(x, y);
	}

	@Override
	public void panEnd(final float x, final float y) {
		this.delegate.panEnd(x, y);
	}

	@Override
	public void zoomStep(final int direction) {
		interrupted();
		this.delegate.zoomStep(direction);
	}

	@Override
	public void twoFingerTap(final float x, final float y) {
		interrupted();
		this.delegate.twoFingerTap(x, y);
	}

	@Override
	public void cancel() {
		// What was cancelled is the gesture in progress; a tap already made stays a tap.
		interrupted();
		this.delegate.cancel();
	}

	/**
	 * A finger the screen takes for itself — the hero tray's aim — goes straight through: the mapper only holds taps
	 * back, and a claimed finger never becomes a tap. Dropping these three here is how the aim silently became a
	 * camera pan, because the default refusal let the recognizer fall back to a two finger gesture.
	 */
	@Override
	public boolean secondaryDown(final float x, final float y, final int pointerId) {
		return this.delegate.secondaryDown(x, y, pointerId);
	}

	@Override
	public void secondaryMove(final float x, final float y) {
		this.delegate.secondaryMove(x, y);
	}

	@Override
	public void secondaryUp(final float x, final float y) {
		this.delegate.secondaryUp(x, y);
	}

	private void log(final String message) {
		if (this.debugLog) {
			Gdx.app.log(LOG_TAG, message);
		}
	}
}
