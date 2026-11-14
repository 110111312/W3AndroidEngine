package com.etheller.warsmash.input.touch;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * The pen under the mouse and keyboard scheme: a mouse with hover. The tip is the left button from the moment it
 * touches, so a click happens at once and a drag draws the selection box; there is no tap delay and no gesture
 * recognition. A mouse has a right button and a pen has none (its side button is Shift), so a tip held still for the
 * long press time gives a right click instead (not when the pen button is the camera drag or the right click, which press the
 * middle or right button instead); the left press already sent is abandoned first so that it selects nothing.
 */
final class PenMouse {
	private final TouchSettings settings;
	private boolean down;
	private boolean held;
	private int button = Input.Buttons.LEFT;
	private volatile PenButton penButton = PenButton.SHIFT;
	private boolean dragged;
	private float downX;
	private float downY;
	private long downTime;
	private int pointerId;

	PenMouse(final TouchSettings settings) {
		this.settings = settings;
	}

	void setPenButton(final PenButton penButton) {
		this.penButton = penButton;
	}

	void hover(final InputProcessor processor, final float x, final float y) {
		VirtualCursor.moveTo(Math.round(x), Math.round(y));
		processor.mouseMoved(Math.round(x), Math.round(y));
	}

	void handle(final PointerEvent event, final InputProcessor processor) {
		if (processor == null) {
			return;
		}
		final int x = Math.round(event.x);
		final int y = Math.round(event.y);
		switch (event.action) {
		case DOWN:
			if (this.down) {
				return;
			}
			this.down = true;
			this.held = false;
			final boolean buttonHeld = (event.buttons & PointerEvent.PEN_BUTTON_MASK) != 0;
			final boolean panning = (this.penButton == PenButton.DRAG) && buttonHeld;
			this.button = panning ? Input.Buttons.MIDDLE
					: ((this.penButton == PenButton.RIGHT_CLICK) && buttonHeld) ? Input.Buttons.RIGHT
							: Input.Buttons.LEFT;
			this.dragged = false;
			this.pointerId = event.pointerId;
			this.downX = event.x;
			this.downY = event.y;
			this.downTime = event.timeMillis;
			hover(processor, event.x, event.y);
			processor.touchDown(x, y, 0, this.button);
			break;
		case MOVE:
			if (this.down && (event.pointerId == this.pointerId) && !this.held) {
				if (Math.hypot(event.x - this.downX, event.y - this.downY) > this.settings.penSlopPx) {
					this.dragged = true;
				}
				VirtualCursor.moveTo(x, y);
				processor.touchDragged(x, y, 0);
			}
			break;
		case UP:
			if (this.down && (event.pointerId == this.pointerId)) {
				this.down = false;
				if (!this.held) {
					VirtualCursor.moveTo(x, y);
					processor.touchUp(x, y, 0, this.button);
				}
			}
			break;
		case CANCEL:
			if (this.down) {
				this.down = false;
				if (processor instanceof TouchTarget) {
					((TouchTarget) processor).cancelPointerDrag();
				}
			}
			break;
		default:
			break;
		}
	}

	/** Called once a frame. A tip that has moved is a drag, which never turns into a click. */
	void update(final long nowMillis) {
		if (!this.down || this.held || this.dragged || (this.button != Input.Buttons.LEFT) || ((nowMillis - this.downTime) < this.settings.longPressMs)) {
			return;
		}
		final InputProcessor processor = Gdx.input.getInputProcessor();
		if (processor == null) {
			return;
		}
		this.held = true;
		if (processor instanceof TouchTarget) {
			((TouchTarget) processor).cancelPointerDrag();
		}
		final int x = Math.round(this.downX);
		final int y = Math.round(this.downY);
		processor.touchDown(x, y, 0, Input.Buttons.RIGHT);
		processor.touchUp(x, y, 0, Input.Buttons.RIGHT);
	}

	boolean isDown() {
		return this.down;
	}
}
