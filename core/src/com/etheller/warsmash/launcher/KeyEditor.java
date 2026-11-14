package com.etheller.warsmash.launcher;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * The hotkey editor's model, shared by the desktop and Android launchers: the player's own keys over the defaults
 * of the chosen layout, kept in the CustomKeys file the game reads. A key equal to a command's default is never
 * kept, so the file holds only what the player changed.
 */
public final class KeyEditor {
	private final CustomKeys keys;
	private final File file;

	/** @param file where changes are written; null keeps them in memory only */
	public KeyEditor(final CustomKeys keys, final File file) {
		this.keys = keys;
		this.file = file;
	}

	/** The player's keys as the game has them: their file and the layout their preferences name. */
	public static KeyEditor open(final File file, final LauncherStore engine) {
		return new KeyEditor(CustomKeys.read(file, CustomKeys.LAYOUT_GRID.equals(engine.getString(CustomKeys.KEY_LAYOUT, ""))),
				file);
	}

	public CustomKeys keys() {
		return this.keys;
	}

	public String keyOf(final HotkeyCatalog.Command command) {
		return this.keys.key(command.section, command.field, command.dataKey, command.x, command.y);
	}

	public String defaultKeyOf(final HotkeyCatalog.Command command) {
		return this.keys.defaultKey(command.dataKey, command.x, command.y);
	}

	/** Whether the command has a key of the player's own, which Default takes away. */
	public boolean changed(final HotkeyCatalog.Command command) {
		return this.keys.isOwn(command.section, command.field);
	}

	public boolean anyChanged() {
		return !this.keys.isEmpty();
	}

	/** Gives the command a key; the key it has by default is not kept. Returns false when the file cannot be written. */
	public boolean bind(final HotkeyCatalog.Command command, final String key) {
		this.keys.remove(command.section, command.field);
		if (!CustomKeys.shown(key).equals(CustomKeys.shown(defaultKeyOf(command)))) {
			this.keys.set(command.section, command.field, key);
		}
		return save();
	}

	/** Takes the player's key of one command away. */
	public boolean restore(final HotkeyCatalog.Command command) {
		this.keys.remove(command.section, command.field);
		return save();
	}

	/** Takes all the player's keys away; the layout stays. */
	public boolean resetAll() {
		this.keys.clear();
		return save();
	}

	/** Chooses the layout, in the engine's preferences where the game reads it. */
	public void setGrid(final boolean grid, final LauncherStore engine) {
		this.keys.setGrid(grid);
		engine.edit().putString(CustomKeys.KEY_LAYOUT, grid ? CustomKeys.LAYOUT_GRID : "classic").commit();
	}

	/** The commands of a card that share a key with another of its commands. */
	public Set<String> conflicts(final HotkeyCatalog.Card card) {
		return HotkeyCatalog.conflicts(card, this.keys);
	}

	private boolean save() {
		if (this.file == null) {
			return true;
		}
		try {
			this.keys.write(this.file);
			return true;
		}
		catch (final IOException e) {
			LauncherLog.w("Unable to write the hotkeys", e);
			return false;
		}
	}

	/** The key a typed character binds: a letter or a digit, as a capital; null for any other character. */
	public static String keyOfChar(final char c) {
		if (((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) || ((c >= '0') && (c <= '9'))) {
			return String.valueOf(c).toUpperCase(Locale.ROOT);
		}
		return null;
	}

	/**
	 * The key an Android key code stands for by its place on the keyboard (letters, digits and the number pad), so a
	 * physical keyboard and a key sent through adb bind alike; null for any other key.
	 */
	public static String keyOfAndroidCode(final int code) {
		final int keycodeA = 29, keycodeZ = 54, keycode0 = 7, keycode9 = 16, numpad0 = 144, numpad9 = 153;
		if ((code >= keycodeA) && (code <= keycodeZ)) {
			return keyOfChar((char) ('A' + (code - keycodeA)));
		}
		if ((code >= keycode0) && (code <= keycode9)) {
			return keyOfChar((char) ('0' + (code - keycode0)));
		}
		if ((code >= numpad0) && (code <= numpad9)) {
			return keyOfChar((char) ('0' + (code - numpad0)));
		}
		return null;
	}
}
