package com.etheller.warsmash.input.touch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.launcher.EngineSettings;

class ControlSchemeTest {
	@AfterEach
	void reset() {
		TouchInputRouter.install(null);
		VirtualModifiers.setPenButtonHeld(false, PenButton.SHIFT);
	}

	private static PointerEvent pen(final PointerEvent.Action action, final int buttons) {
		return new PointerEvent(0, PointerEvent.Tool.STYLUS, action, 10, 10, buttons, 1, 0, 0, 0);
	}

	@Test
	void parsesNamesAndKeepsTheFallback() {
		assertEquals(ControlScheme.TOUCH, ControlScheme.parse("touch", ControlScheme.MOUSE_KEYBOARD));
		assertEquals(ControlScheme.MOUSE_KEYBOARD, ControlScheme.parse(" Mouse ", ControlScheme.TOUCH));
		assertEquals(ControlScheme.TOUCH, ControlScheme.parse("", ControlScheme.TOUCH));
		assertEquals(ControlScheme.MOUSE_KEYBOARD, ControlScheme.parse("bogus", ControlScheme.MOUSE_KEYBOARD));
		assertEquals(TouchStyle.DOUBLE_TAP, TouchStyle.parse("doubletap", TouchStyle.TAP_HOLD));
		assertEquals(TouchStyle.TAP_HOLD, TouchStyle.parse(null, TouchStyle.TAP_HOLD));
	}

	@Test
	void defaultsFollowThePlatform() {
		assertEquals(ControlScheme.TOUCH, TouchSettings.defaults(2f, ControlScheme.TOUCH).controlScheme);
		assertEquals(ControlScheme.MOUSE_KEYBOARD,
				TouchSettings.defaults(1f, ControlScheme.MOUSE_KEYBOARD).controlScheme);
		assertEquals(TouchStyle.DOUBLE_TAP, TouchSettings.defaults(1f, ControlScheme.TOUCH).touchStyle);
		assertEquals(64f, TouchSettings.defaults(2f, ControlScheme.TOUCH).doubleTapSlopPx);
	}

	@Test
	void switchingTheSchemeChangesWhatTheInterfaceSees() {
		final TouchInputRouter router = new TouchInputRouter(
				TouchSettings.defaults(1f, ControlScheme.MOUSE_KEYBOARD), () -> 0, false);
		TouchInputRouter.install(router);
		assertFalse(TouchInputRouter.isActive());
		assertFalse(VirtualCursor.isCursorHidden());
		router.setScheme(ControlScheme.TOUCH);
		assertTrue(TouchInputRouter.isActive());
		assertTrue(VirtualCursor.isCursorHidden());
		router.setScheme(ControlScheme.MOUSE_KEYBOARD);
		assertFalse(TouchInputRouter.isActive());
		assertFalse(VirtualCursor.isCursorHidden());
	}

	@Test
	void touchPlatformStartsActive() {
		TouchInputRouter
				.install(new TouchInputRouter(TouchSettings.defaults(1f, ControlScheme.TOUCH), () -> 0, false));
		assertTrue(TouchInputRouter.isActive());
	}

	@Test
	void penButtonIsShiftWhileHeld() {
		assertFalse(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.HOVER_MOVE, 32), PenButton.SHIFT);
		assertTrue(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.DOWN, 32), PenButton.SHIFT);
		assertTrue(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.MOVE, 0), PenButton.SHIFT);
		assertFalse(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.MOVE, 2), PenButton.SHIFT);
		assertTrue(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.UP, 0), PenButton.SHIFT);
		assertFalse(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.HOVER_MOVE, 32), PenButton.SHIFT);
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.HOVER_EXIT, 0), PenButton.SHIFT);
		assertFalse(VirtualModifiers.isShiftDown());
	}

	@Test
	void penButtonCanBeCtrlOrNoKeyAtAll() {
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.HOVER_MOVE, 32), PenButton.CTRL);
		assertTrue(VirtualModifiers.isControlDown());
		assertFalse(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.UP, 0), PenButton.CTRL);
		assertFalse(VirtualModifiers.isControlDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.DOWN, 32), PenButton.DRAG);
		assertFalse(VirtualModifiers.isControlDown());
		assertFalse(VirtualModifiers.isShiftDown());
		TouchInputRouter.followPenButton(pen(PointerEvent.Action.DOWN, 32), PenButton.SHIFT);
		assertTrue(VirtualModifiers.isShiftDown());
		assertFalse(VirtualModifiers.isControlDown());
	}

	@Test
	void parsesPenButtonAndKeepsShiftAsDefault() {
		assertEquals(PenButton.DRAG, PenButton.parse("drag", PenButton.SHIFT));
		assertEquals(PenButton.CTRL, PenButton.parse(" Ctrl ", PenButton.SHIFT));
		assertEquals(PenButton.SHIFT, PenButton.parse("", PenButton.SHIFT));
		assertEquals(PenButton.SHIFT, PenButton.parse("bogus", PenButton.SHIFT));
		assertEquals(PenButton.SHIFT, TouchSettings.defaults(1f, ControlScheme.TOUCH).penButton);
	}

	@Test
	void oldAndNewConfigurationValuesAreRead(@TempDir final File folder) throws IOException {
		final File ini = new File(folder, "warsmash.ini");
		Files.write(ini.toPath(), "[Touch]\nTouchControls=Off\nTapSlopDp=10\n".getBytes(StandardCharsets.UTF_8));
		final EngineSettings engine = new EngineSettings(folder, "p", ini);
		assertEquals(ControlScheme.MOUSE_KEYBOARD, engine.controlScheme(ControlScheme.TOUCH));
		assertEquals(TouchStyle.DOUBLE_TAP, engine.touchStyle());
		assertEquals(PenButton.SHIFT, engine.penButton());
		engine.setControlScheme(ControlScheme.TOUCH);
		engine.setTouchStyle(TouchStyle.TAP_HOLD);
		engine.setPenButton(PenButton.DRAG);
		final EngineSettings again = new EngineSettings(folder, "p", ini);
		assertEquals(ControlScheme.TOUCH, again.controlScheme(ControlScheme.MOUSE_KEYBOARD));
		assertEquals(TouchStyle.TAP_HOLD, again.touchStyle());
		assertEquals(PenButton.DRAG, again.penButton());
		assertEquals("10", again.touch("TapSlopDp", ""));
	}
}
