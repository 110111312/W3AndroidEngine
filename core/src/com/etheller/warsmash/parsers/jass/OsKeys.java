package com.etheller.warsmash.parsers.jass;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Input.Keys;

/**
 * The oskeytype constants of common.j are Windows virtual key codes; the interface works with libGDX key codes.
 * The meta key masks of BlzTriggerRegisterPlayerKeyEvent are 1 shift, 2 control, 4 alt and 8 meta.
 */
public final class OsKeys {
	public static final int META_SHIFT = 1;
	public static final int META_CONTROL = 2;
	public static final int META_ALT = 4;
	public static final int META_META = 8;

	private static final Map<Integer, Integer> VIRTUAL_TO_GDX = new HashMap<>();
	private static final Map<Integer, Integer> GDX_TO_VIRTUAL = new HashMap<>();

	private static void map(final int virtualKey, final int gdxKey) {
		VIRTUAL_TO_GDX.put(virtualKey, gdxKey);
		GDX_TO_VIRTUAL.put(gdxKey, virtualKey);
	}

	static {
		map(0x08, Keys.BACKSPACE);
		map(0x09, Keys.TAB);
		map(0x0D, Keys.ENTER);
		map(0x10, Keys.SHIFT_LEFT);
		map(0xA0, Keys.SHIFT_LEFT);
		map(0xA1, Keys.SHIFT_RIGHT);
		map(0x11, Keys.CONTROL_LEFT);
		map(0xA2, Keys.CONTROL_LEFT);
		map(0xA3, Keys.CONTROL_RIGHT);
		map(0x12, Keys.ALT_LEFT);
		map(0xA4, Keys.ALT_LEFT);
		map(0xA5, Keys.ALT_RIGHT);
		map(0x13, Keys.PAUSE);
		map(0x14, Keys.CAPS_LOCK);
		map(0x1B, Keys.ESCAPE);
		map(0x20, Keys.SPACE);
		map(0x21, Keys.PAGE_UP);
		map(0x22, Keys.PAGE_DOWN);
		map(0x23, Keys.END);
		map(0x24, Keys.HOME);
		map(0x25, Keys.LEFT);
		map(0x26, Keys.UP);
		map(0x27, Keys.RIGHT);
		map(0x28, Keys.DOWN);
		map(0x2C, Keys.PRINT_SCREEN);
		map(0x2D, Keys.INSERT);
		map(0x2E, Keys.FORWARD_DEL);
		for (int i = 0; i < 10; i++) {
			map(0x30 + i, Keys.NUM_0 + i);
			map(0x60 + i, Keys.NUMPAD_0 + i);
		}
		for (int i = 0; i < 26; i++) {
			map(0x41 + i, Keys.A + i);
		}
		map(0x5B, Keys.SYM);
		map(0x5C, Keys.SYM);
		map(0x6A, Keys.STAR);
		map(0x6B, Keys.PLUS);
		map(0x6D, Keys.MINUS);
		for (int i = 0; i < 12; i++) {
			map(0x70 + i, Keys.F1 + i);
		}
		map(0x90, Keys.NUM_LOCK);
		map(0x91, Keys.SCROLL_LOCK);
		map(0xBA, Keys.SEMICOLON);
		map(0xBB, Keys.EQUALS);
		map(0xBC, Keys.COMMA);
		map(0xBD, Keys.MINUS);
		map(0xBE, Keys.PERIOD);
		map(0xBF, Keys.SLASH);
		map(0xC0, Keys.GRAVE);
		map(0xDB, Keys.LEFT_BRACKET);
		map(0xDC, Keys.BACKSLASH);
		map(0xDD, Keys.RIGHT_BRACKET);
		map(0xDE, Keys.APOSTROPHE);
	}

	private OsKeys() {
	}

	/** The libGDX key of a Windows virtual key code, or -1 when the interface has none. */
	public static int toGdx(final int virtualKey) {
		final Integer gdx = VIRTUAL_TO_GDX.get(virtualKey);
		return gdx == null ? -1 : gdx;
	}

	/** The Windows virtual key code of a libGDX key, or -1 when the script has no constant for it. */
	public static int toVirtual(final int gdxKey) {
		final Integer virtual = GDX_TO_VIRTUAL.get(gdxKey);
		return virtual == null ? -1 : virtual;
	}

	/** The meta keys held now, as the mask the key event natives use. */
	public static int heldMetaKeys(final com.badlogic.gdx.Input input) {
		int mask = 0;
		if (input.isKeyPressed(Keys.SHIFT_LEFT) || input.isKeyPressed(Keys.SHIFT_RIGHT)) {
			mask |= META_SHIFT;
		}
		if (input.isKeyPressed(Keys.CONTROL_LEFT) || input.isKeyPressed(Keys.CONTROL_RIGHT)) {
			mask |= META_CONTROL;
		}
		if (input.isKeyPressed(Keys.ALT_LEFT) || input.isKeyPressed(Keys.ALT_RIGHT)) {
			mask |= META_ALT;
		}
		if (input.isKeyPressed(Keys.SYM)) {
			mask |= META_META;
		}
		return mask;
	}
}
