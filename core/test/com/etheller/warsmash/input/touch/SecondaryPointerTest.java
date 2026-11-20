package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A second finger while the first one is still down: the hero-mode tray takes it and the first gesture keeps running,
 * which is what lets the player aim a skill without lifting the thumb that walks. A screen that wants nothing — the
 * RTS console — must keep the two finger pan it always had.
 */
class SecondaryPointerTest {
	private static final class Recorder implements GestureListener {
		final List<String> calls = new ArrayList<>();
		boolean claimSecond = true;

		@Override
		public void pressed(final float x, final float y, final long timeMillis) {
			this.calls.add("pressed");
		}

		@Override
		public void pointerAt(final float x, final float y) {
		}

		@Override
		public void tap(final float x, final float y, final boolean pen) {
			this.calls.add("tap");
		}

		@Override
		public void secondaryTap(final float x, final float y) {
			this.calls.add("secondaryTap");
		}

		@Override
		public void longPress(final float x, final float y) {
			this.calls.add("longPress");
		}

		@Override
		public void longPressEnd(final float x, final float y) {
			this.calls.add("longPressEnd");
		}

		@Override
		public void holdTap(final float heldX, final float heldY) {
		}

		@Override
		public void dragStart(final float x, final float y, final boolean pen) {
			this.calls.add("dragStart");
		}

		@Override
		public void drag(final float x, final float y) {
			this.calls.add("drag");
		}

		@Override
		public void dragEnd(final float x, final float y) {
			this.calls.add("dragEnd");
		}

		@Override
		public void panStart(final float x, final float y) {
			this.calls.add("panStart");
		}

		@Override
		public void pan(final float x, final float y) {
			this.calls.add("pan");
		}

		@Override
		public void panEnd(final float x, final float y) {
			this.calls.add("panEnd");
		}

		@Override
		public void zoomStep(final int direction) {
			this.calls.add("zoom");
		}

		@Override
		public void twoFingerTap(final float x, final float y) {
			this.calls.add("twoFingerTap");
		}

		@Override
		public void cancel() {
			this.calls.add("cancel");
		}

		@Override
		public boolean secondaryDown(final float x, final float y, final int pointerId) {
			this.calls.add("secondaryDown");
			return this.claimSecond;
		}

		@Override
		public void secondaryMove(final float x, final float y) {
			this.calls.add("secondaryMove");
		}

		@Override
		public void secondaryUp(final float x, final float y) {
			this.calls.add("secondaryUp");
		}
	}

	private final Recorder recorder = new Recorder();
	private final GestureRecognizer recognizer = new GestureRecognizer(
			TouchSettings.defaults(1f, ControlScheme.TOUCH), this.recorder);

	private static PointerEvent event(final int pointerId, final PointerEvent.Action action, final float x,
			final float y, final long time) {
		return new PointerEvent(pointerId, PointerEvent.Tool.FINGER, action, x, y, 0, 1f, 10f, 0f, time);
	}

	private void send(final int pointerId, final PointerEvent.Action action, final float x, final float y,
			final long time) {
		this.recognizer.onEvent(event(pointerId, action, x, y, time));
		this.recognizer.update(time);
	}

	/** The first finger has walked far enough to be a drag. */
	private void walking(final long time) {
		send(0, PointerEvent.Action.DOWN, 200, 700, time);
		send(0, PointerEvent.Action.MOVE, 240, 700, time + 30);
	}

	/**
	 * A tap is a press and a release with nothing between them: the tray has to treat that as a tap and not as a drag
	 * that came back to its button, which would cancel the cast. This is the exact event stream a tap on the tray sends
	 * while the other thumb walks, and it is the case that was silently cancelling.
	 */
	@Test
	void aTapOfTheSecondFingerIsOnePressAndOneReleaseWithNoDrag() {
		walking(1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1100);
		send(1, PointerEvent.Action.UP, 902, 801, 1180);
		send(0, PointerEvent.Action.MOVE, 280, 700, 1240);
		send(0, PointerEvent.Action.UP, 280, 700, 1300);

		assertEquals(List.of("pressed", "dragStart", "drag", "secondaryDown", "secondaryUp", "drag", "dragEnd"),
				this.recorder.calls);
	}

	@Test
	void aTakenSecondFingerAimsWhileTheFirstOneKeepsDragging() {
		walking(1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1100);
		send(0, PointerEvent.Action.MOVE, 280, 700, 1140);
		send(1, PointerEvent.Action.MOVE, 860, 760, 1160);
		send(1, PointerEvent.Action.UP, 860, 760, 1200);
		send(0, PointerEvent.Action.MOVE, 300, 700, 1240);
		send(0, PointerEvent.Action.UP, 300, 700, 1300);

		assertEquals(List.of("pressed", "dragStart", "drag", "secondaryDown", "drag", "secondaryMove", "secondaryUp",
				"drag", "dragEnd"), this.recorder.calls);
		assertTrue(!this.recorder.calls.contains("cancel"));
		assertTrue(!this.recorder.calls.contains("panStart"));
	}

	@Test
	void aSecondFingerThatTheScreenDoesNotWantIsTheTwoFingerPanItAlwaysWas() {
		this.recorder.claimSecond = false;
		walking(1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1050);
		send(0, PointerEvent.Action.UP, 240, 700, 1100);

		assertEquals(List.of("pressed", "dragStart", "drag", "secondaryDown", "cancel", "twoFingerTap"),
				this.recorder.calls);
	}

	@Test
	void theSecondFingerIsTakenEvenBeforeTheFirstOneHasDecidedWhatItIs() {
		send(0, PointerEvent.Action.DOWN, 200, 700, 1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1030);
		send(0, PointerEvent.Action.MOVE, 240, 700, 1060);
		send(1, PointerEvent.Action.UP, 900, 800, 1100);
		send(0, PointerEvent.Action.UP, 240, 700, 1150);

		assertEquals(List.of("pressed", "secondaryDown", "dragStart", "drag", "secondaryUp", "dragEnd"),
				this.recorder.calls);
	}

	@Test
	void aThirdFingerIsNotTakenAndDoesNotStealTheAim() {
		walking(1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1030);
		send(2, PointerEvent.Action.DOWN, 950, 850, 1040);
		send(1, PointerEvent.Action.MOVE, 890, 790, 1060);

		assertEquals(List.of("pressed", "dragStart", "drag", "secondaryDown", "cancel", "panStart", "pan"),
				this.recorder.calls);
	}

	@Test
	void aCancelledGestureEndsTheAimToo() {
		walking(1000);
		send(1, PointerEvent.Action.DOWN, 900, 800, 1030);
		send(1, PointerEvent.Action.CANCEL, 900, 800, 1040);

		assertEquals(List.of("pressed", "dragStart", "drag", "secondaryDown", "cancel"), this.recorder.calls);
	}
}
