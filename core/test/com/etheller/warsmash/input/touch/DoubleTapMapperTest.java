package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DoubleTapMapperTest {
	private static final float DENSITY = 1f;

	private static final class FakeTarget implements TouchTarget {
		final Map<Integer, PointKind> kinds = new HashMap<>();
		boolean orderable = true;
		boolean activeCommand;
		boolean placing;

		void kindAt(final int x, final PointKind kind) {
			this.kinds.put(x, kind);
		}

		@Override
		public PointKind pointKindAt(final int screenX, final int screenY) {
			return this.kinds.getOrDefault(screenX, PointKind.GROUND);
		}

		@Override
		public boolean hasOrderableSelection() {
			return this.orderable;
		}

		@Override
		public boolean hasActiveCommand() {
			return this.activeCommand;
		}

		@Override
		public boolean isPlacingBuilding() {
			return this.placing;
		}

		@Override
		public boolean isOverClickableUi(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public boolean isOverInventorySlot(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public boolean isOverAutocastButton(final int screenX, final int screenY) {
			return false;
		}

		@Override
		public void screenToWorld(final int screenX, final int screenY, final float[] out) {
		}

		@Override
		public void worldToScreen(final float[] world, final float[] out) {
		}

		@Override
		public int touchOverlaySlotAt(final int screenX, final int screenY) {
			return -1;
		}

		@Override
		public void selectControlGroupSlot(final int slot) {
		}

		@Override
		public void assignControlGroupSlot(final int slot, final boolean add) {
		}

		@Override
		public void cancelPointerDrag() {
		}

		@Override
		public void endTouchHover() {
		}

		@Override
		public boolean skipCinematic() {
			return false;
		}
	}

	/** Records the calls that get through, as "tap 100,50" and "order 100,50". */
	private static final class Recorder implements GestureListener {
		final List<String> calls = new ArrayList<>();

		private static String at(final float x, final float y) {
			return (int) x + "," + (int) y;
		}

		@Override
		public void pointerAt(final float x, final float y) {
		}

		@Override
		public void tap(final float x, final float y, final boolean pen) {
			this.calls.add("tap " + at(x, y));
		}

		@Override
		public void secondaryTap(final float x, final float y) {
			this.calls.add("order " + at(x, y));
		}

		@Override
		public void longPress(final float x, final float y) {
			this.calls.add("long " + at(x, y));
		}

		@Override
		public void longPressEnd(final float x, final float y) {
		}

		@Override
		public void holdTap(final float heldX, final float heldY) {
		}

		@Override
		public void dragStart(final float x, final float y, final boolean pen) {
			this.calls.add("dragStart " + at(x, y));
		}

		@Override
		public void drag(final float x, final float y) {
		}

		@Override
		public void dragEnd(final float x, final float y) {
		}

		@Override
		public void panStart(final float x, final float y) {
			this.calls.add("panStart");
		}

		@Override
		public void pan(final float x, final float y) {
		}

		@Override
		public void panEnd(final float x, final float y) {
		}

		@Override
		public void zoomStep(final int direction) {
		}

		@Override
		public void twoFingerTap(final float x, final float y) {
			this.calls.add("twoFingerTap");
		}

		@Override
		public void cancel() {
			this.calls.add("cancel");
		}
	}

	private final AtomicLong clock = new AtomicLong();
	private FakeTarget target;
	private Recorder recorder;
	private GestureRecognizer recognizer;
	private DoubleTapMapper mapper;

	@BeforeEach
	void setUp() {
		this.target = new FakeTarget();
		this.recorder = new Recorder();
		final TouchSettings settings = doubleTapSettings(TouchStyle.DOUBLE_TAP);
		this.mapper = new DoubleTapMapper(this.recorder, settings, () -> this.target, this.clock::get, false);
		this.recognizer = new GestureRecognizer(settings, this.mapper);
	}

	private static TouchSettings doubleTapSettings(final TouchStyle style) {
		final TouchSettings d = TouchSettings.defaults(DENSITY, ControlScheme.TOUCH);
		return new TouchSettings(d.tapSlopPx, d.penSlopPx, d.tapMaxMs, d.longPressMs, d.twoFingerWindowMs,
				d.twoFingerTapMs, d.pinchStartRatio, d.pinchStepRatio, d.buildAimOffsetPx, d.ghostTapRadiusPx,
				d.palmTouchMajorPx, d.penFingerPolicy, d.palmRejection, ControlScheme.TOUCH, style, 250, 32, d.penButton,
				d.stickDeadZonePx, d.stickRadiusPx, d.stickKnobRadiusPx, d.stickOrderAhead, d.stickTurnDegrees,
				d.stickReissueMs);
	}

	private void event(final PointerEvent.Action action, final float x, final float y, final long time) {
		this.clock.set(time);
		this.recognizer.onEvent(new PointerEvent(0, PointerEvent.Tool.FINGER, action, x, y, 0, 1, 10, 0, time));
		this.recognizer.update(time);
		this.mapper.update(time);
	}

	private void tap(final float x, final float y, final long downTime) {
		event(PointerEvent.Action.DOWN, x, y, downTime);
		event(PointerEvent.Action.UP, x, y, downTime + 60);
	}

	private void idle(final long untilTime) {
		this.clock.set(untilTime);
		this.recognizer.update(untilTime);
		this.mapper.update(untilTime);
	}

	@Test
	void doubleTapOnGroundMoves() {
		tap(100, 100, 0);
		tap(110, 105, 200);
		assertEquals(List.of("tap 100,100", "order 100,100"), this.recorder.calls);
	}

	@Test
	void secondTapOnGroundTooLateIsAnotherTap() {
		tap(100, 100, 0);
		tap(100, 100, 400);
		assertEquals(List.of("tap 100,100", "tap 100,100"), this.recorder.calls);
	}

	@Test
	void windowIsMeasuredFromReleaseToNextPress() {
		tap(100, 100, 0);
		tap(100, 100, 60 + 250);
		assertEquals(List.of("tap 100,100", "order 100,100"), this.recorder.calls);
		this.recorder.calls.clear();
		tap(100, 100, 2000);
		tap(100, 100, 2000 + 60 + 251);
		assertEquals(List.of("tap 100,100", "tap 100,100"), this.recorder.calls);
	}

	@Test
	void secondTapBeyondSlopIsAnotherTap() {
		tap(100, 100, 0);
		tap(100 + 33, 100, 150);
		assertEquals(List.of("tap 100,100", "tap 133,100"), this.recorder.calls);
		this.recorder.calls.clear();
		tap(500, 100, 1000);
		tap(500 + 32, 100, 1150);
		assertEquals(List.of("tap 500,100", "order 500,100"), this.recorder.calls);
	}

	@Test
	void groundDoubleTapNeedsAnOrderableSelection() {
		this.target.orderable = false;
		tap(100, 100, 0);
		tap(100, 100, 150);
		assertEquals(List.of("tap 100,100", "tap 100,100"), this.recorder.calls);
	}

	@Test
	void doubleTapOnEnemyAttacksAndDropsTheHeldClick() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		assertEquals(List.of(), this.recorder.calls);
		tap(300, 200, 150);
		assertEquals(List.of("order 300,200"), this.recorder.calls);
		idle(2000);
		assertEquals(List.of("order 300,200"), this.recorder.calls);
	}

	@Test
	void singleTapOnEnemyIsClickedWhenTheWindowPasses() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		idle(60 + 249);
		assertEquals(List.of(), this.recorder.calls);
		idle(60 + 250);
		assertEquals(List.of("tap 300,200"), this.recorder.calls);
	}

	@Test
	void resourceAndItemAreOrderedToo() {
		this.target.kindAt(300, PointKind.RESOURCE);
		this.target.kindAt(400, PointKind.ITEM);
		tap(300, 200, 0);
		tap(300, 200, 150);
		tap(400, 200, 1000);
		tap(400, 200, 1150);
		assertEquals(List.of("order 300,200", "order 400,200"), this.recorder.calls);
	}

	@Test
	void tapOnEnemyWithoutSelectionIsAPlainClick() {
		this.target.kindAt(300, PointKind.ENEMY);
		this.target.orderable = false;
		tap(300, 200, 0);
		assertEquals(List.of("tap 300,200"), this.recorder.calls);
	}

	@Test
	void doubleTapOnOwnUnitStaysTwoClicks() {
		this.target.kindAt(300, PointKind.OWN_UNIT);
		tap(300, 200, 0);
		tap(300, 200, 150);
		assertEquals(List.of("tap 300,200", "tap 300,200"), this.recorder.calls);
	}

	@Test
	void interfaceAndTargetingTapsAreNotTouched() {
		this.target.kindAt(50, PointKind.INTERFACE);
		tap(50, 50, 0);
		tap(50, 50, 150);
		assertEquals(List.of("tap 50,50", "tap 50,50"), this.recorder.calls);
		this.recorder.calls.clear();
		this.target.activeCommand = true;
		tap(100, 100, 1000);
		tap(100, 100, 1150);
		assertEquals(List.of("tap 100,100", "tap 100,100"), this.recorder.calls);
		this.recorder.calls.clear();
		this.target.activeCommand = false;
		this.target.placing = true;
		tap(100, 100, 2000);
		tap(100, 100, 2150);
		assertEquals(List.of("tap 100,100", "tap 100,100"), this.recorder.calls);
	}

	@Test
	void tapHoldStyleIsAPassThrough() {
		this.mapper.setStyle(TouchStyle.TAP_HOLD);
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		assertEquals(List.of("tap 300,200"), this.recorder.calls);
		tap(300, 200, 150);
		assertEquals(List.of("tap 300,200", "tap 300,200"), this.recorder.calls);
	}

	@Test
	void switchingToTapHoldReleasesTheHeldClick() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		this.mapper.setStyle(TouchStyle.TAP_HOLD);
		assertEquals(List.of("tap 300,200"), this.recorder.calls);
	}

	@Test
	void secondPressThatBecomesADragReleasesTheHeldClickFirst() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		event(PointerEvent.Action.DOWN, 300, 200, 150);
		event(PointerEvent.Action.MOVE, 300 + 40, 200, 170);
		assertEquals(List.of("tap 300,200", "dragStart 300,200"), this.recorder.calls);
	}

	@Test
	void secondPressThatBecomesALongPressReleasesTheHeldClickFirst() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		event(PointerEvent.Action.DOWN, 300, 200, 150);
		idle(150 + 300);
		assertEquals(List.of("tap 300,200", "long 300,200"), this.recorder.calls);
	}

	@Test
	void heldClickWaitsWhileTheSecondPressIsDown() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		event(PointerEvent.Action.DOWN, 300, 200, 240);
		idle(60 + 280);
		assertEquals(List.of(), this.recorder.calls);
		event(PointerEvent.Action.UP, 300, 200, 60 + 285);
		assertEquals(List.of("order 300,200"), this.recorder.calls);
	}

	@Test
	void cancelOfTheSecondPressStillClicksTheFirstTap() {
		this.target.kindAt(300, PointKind.ENEMY);
		tap(300, 200, 0);
		event(PointerEvent.Action.DOWN, 300, 200, 150);
		event(PointerEvent.Action.CANCEL, 0, 0, 160);
		idle(1000);
		assertEquals(List.of("tap 300,200", "cancel"), this.recorder.calls);
	}

	@Test
	void groundOrderIsGivenOnceNotThrice() {
		tap(100, 100, 0);
		tap(100, 100, 150);
		tap(100, 100, 300);
		assertEquals(List.of("tap 100,100", "order 100,100", "tap 100,100"), this.recorder.calls);
	}

	@Test
	void penTapsCountToo() {
		this.target.kindAt(300, PointKind.ENEMY);
		this.clock.set(0);
		pen(PointerEvent.Action.DOWN, 300, 0);
		pen(PointerEvent.Action.UP, 300, 60);
		pen(PointerEvent.Action.DOWN, 301, 150);
		pen(PointerEvent.Action.UP, 301, 200);
		assertEquals(List.of("order 300,200"), this.recorder.calls);
	}

	private void penAt(final PointerEvent.Action action, final float x, final float y, final int buttons,
			final long time) {
		this.clock.set(time);
		this.recognizer.onEvent(new PointerEvent(0, PointerEvent.Tool.STYLUS, action, x, y, buttons, 1, 0, 0, time));
	}

	@Test
	void penButtonDragPansTheCameraOnlyInDragMode() {
		this.recognizer.setPenButton(PenButton.DRAG);
		penAt(PointerEvent.Action.DOWN, 100, 100, 32, 0);
		penAt(PointerEvent.Action.MOVE, 100, 130, 32, 20);
		assertEquals(List.of("panStart"), this.recorder.calls);
	}

	@Test
	void penButtonDragSetLaterInTheStrokeAlsoPans() {
		this.recognizer.setPenButton(PenButton.DRAG);
		penAt(PointerEvent.Action.DOWN, 100, 100, 0, 0);
		penAt(PointerEvent.Action.MOVE, 100, 102, 32, 10);
		penAt(PointerEvent.Action.MOVE, 100, 130, 32, 20);
		assertEquals(List.of("panStart"), this.recorder.calls);
	}

	@Test
	void penButtonDragStrokeWithoutButtonStillBoxSelects() {
		this.recognizer.setPenButton(PenButton.DRAG);
		penAt(PointerEvent.Action.DOWN, 100, 100, 0, 0);
		penAt(PointerEvent.Action.MOVE, 100, 130, 0, 20);
		assertEquals(List.of("dragStart 100,100"), this.recorder.calls);
	}

	@Test
	void penButtonAsShiftOrCtrlDoesNotPan() {
		penAt(PointerEvent.Action.DOWN, 100, 100, 32, 0);
		penAt(PointerEvent.Action.MOVE, 100, 130, 32, 20);
		assertEquals(List.of("dragStart 100,100"), this.recorder.calls);
		this.recorder.calls.clear();
		penAt(PointerEvent.Action.UP, 100, 130, 32, 30);
		this.recognizer.setPenButton(PenButton.CTRL);
		penAt(PointerEvent.Action.DOWN, 100, 100, 32, 100);
		penAt(PointerEvent.Action.MOVE, 100, 130, 32, 120);
		assertEquals(List.of("dragStart 100,100"), this.recorder.calls);
	}

	@Test
	void penLongPressIsReportedForTheAdapterToDecide() {
		penAt(PointerEvent.Action.DOWN, 100, 100, 0, 0);
		this.recognizer.update(300);
		assertEquals(List.of("long 100,100"), this.recorder.calls);
	}

	private void pen(final PointerEvent.Action action, final float x, final long time) {
		this.clock.set(time);
		this.recognizer.onEvent(new PointerEvent(0, PointerEvent.Tool.STYLUS, action, x, 200, 0, 1, 0, 0, time));
	}
}
