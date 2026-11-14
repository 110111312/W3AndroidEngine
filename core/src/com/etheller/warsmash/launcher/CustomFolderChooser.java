package com.etheller.warsmash.launcher;

import java.io.File;
import java.util.function.Consumer;

/**
 * How the player picks the content folder from inside the game's own menu, for a platform that shows that menu
 * without the launcher around it. The platform installs one before the menu opens; without one the menu offers no
 * choice.
 */
public interface CustomFolderChooser {
	/** The folder in use, or null when none is chosen. */
	File current();

	/**
	 * Asks for a folder and keeps it. The answer is the folder kept, or null when the player cancelled or the folder
	 * cannot be used; it comes on the thread that draws the menu, where the game takes the folder up at once.
	 */
	void choose(Consumer<File> kept);

	final class Holder {
		private static volatile CustomFolderChooser installed;

		private Holder() {
		}

		public static void install(final CustomFolderChooser chooser) {
			installed = chooser;
		}

		/** The platform's chooser, or null. */
		public static CustomFolderChooser get() {
			return installed;
		}
	}
}
