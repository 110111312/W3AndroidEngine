package com.etheller.warsmash.input.touch;

import java.util.function.LongSupplier;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;

/**
 * Lets a PC play with the touch scheme: a touch screen reaches a desktop window as left mouse button events, which
 * this stands between libGDX and the screen to turn into finger pointer events for the touch router. The right and
 * middle buttons, the wheel, hover and the keyboard go through untouched. Windows reports a touch screen as one mouse,
 * so gestures that need two fingers are not available there.
 */
public final class MouseTouchBridge implements InputProcessor {
	private final LongSupplier clock;
	private InputProcessor target;
	private boolean leftDown;

	/** @param clock the same time base the router was built with */
	public MouseTouchBridge(final LongSupplier clock) {
		this.clock = clock;
	}

	public void setTarget(final InputProcessor target) {
		this.target = target;
	}

	private void post(final PointerEvent.Action action, final int x, final int y) {
		final TouchInputRouter router = TouchInputRouter.installedRouter();
		if (router != null) {
			router.post(new PointerEvent(0, PointerEvent.Tool.FINGER, action, x, y, 0, 1, 0, 0,
					this.clock.getAsLong()));
		}
	}

	@Override
	public boolean touchDown(final int screenX, final int screenY, final int pointer, final int button) {
		if ((button == Input.Buttons.LEFT) && (pointer == 0)) {
			this.leftDown = true;
			post(PointerEvent.Action.DOWN, screenX, screenY);
			return true;
		}
		return this.target.touchDown(screenX, screenY, pointer, button);
	}

	@Override
	public boolean touchUp(final int screenX, final int screenY, final int pointer, final int button) {
		if (this.leftDown && (button == Input.Buttons.LEFT) && (pointer == 0)) {
			this.leftDown = false;
			post(PointerEvent.Action.UP, screenX, screenY);
			return true;
		}
		return this.target.touchUp(screenX, screenY, pointer, button);
	}

	@Override
	public boolean touchDragged(final int screenX, final int screenY, final int pointer) {
		if (this.leftDown && (pointer == 0)) {
			post(PointerEvent.Action.MOVE, screenX, screenY);
			return true;
		}
		return this.target.touchDragged(screenX, screenY, pointer);
	}

	@Override
	public boolean mouseMoved(final int screenX, final int screenY) {
		TouchInputRouter.mouseUsed();
		return this.target.mouseMoved(screenX, screenY);
	}

	@Override
	public boolean keyDown(final int keycode) {
		return this.target.keyDown(keycode);
	}

	@Override
	public boolean keyUp(final int keycode) {
		return this.target.keyUp(keycode);
	}

	@Override
	public boolean keyTyped(final char character) {
		return this.target.keyTyped(character);
	}

	@Override
	public boolean scrolled(final float amountX, final float amountY) {
		return this.target.scrolled(amountX, amountY);
	}

	@Override
	public boolean touchCancelled(final int screenX, final int screenY, final int pointer, final int button) {
		return this.target.touchCancelled(screenX, screenY, pointer, button);
	}
}
