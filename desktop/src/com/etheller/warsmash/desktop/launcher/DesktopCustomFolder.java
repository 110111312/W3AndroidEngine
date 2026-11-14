package com.etheller.warsmash.desktop.launcher;

import java.io.File;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.etheller.warsmash.launcher.CustomContent;
import com.etheller.warsmash.launcher.CustomFolderChooser;
import com.etheller.warsmash.launcher.EngineSettings;

/** Chooses the content folder from the game's own Options menu with the system's folder dialog. */
public final class DesktopCustomFolder implements CustomFolderChooser {
	private final File ini;

	public DesktopCustomFolder(final File ini) {
		this.ini = ini;
	}

	private EngineSettings settings() {
		return new EngineSettings(new File("Save"), "", this.ini);
	}

	@Override
	public File current() {
		return settings().customFolder();
	}

	@Override
	public void choose(final Consumer<File> kept) {
		GameDataFolders.chooseFolder(current(), chosen -> Gdx.app.postRunnable(() -> {
			if ((chosen == null) || (!chosen.isDirectory() && !chosen.mkdirs()) || !CustomContent.ensureStructure(chosen)) {
				kept.accept(null);
				return;
			}
			settings().setCustomFolder(chosen.getAbsoluteFile());
			kept.accept(chosen.getAbsoluteFile());
		}));
	}
}
