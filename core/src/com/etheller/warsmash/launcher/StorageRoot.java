package com.etheller.warsmash.launcher;

import java.io.File;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Where the engine keeps what belongs to the player (Save and Replay): the application's own storage, as
 * Gdx.files.local has it, unless the platform names a folder of its own, as Android does to keep profiles where an
 * uninstall does not take them.
 */
public final class StorageRoot {
	private static volatile File root;

	private StorageRoot() {
	}

	/** @param folder the folder that stands for the working folder of the player's files; null for the default */
	public static void set(final File folder) {
		root = folder;
	}

	public static File root() {
		return root;
	}

	public static FileHandle local(final String path) {
		final File folder = root;
		return folder == null ? Gdx.files.local(path) : Gdx.files.absolute(new File(folder, path).getPath());
	}
}
