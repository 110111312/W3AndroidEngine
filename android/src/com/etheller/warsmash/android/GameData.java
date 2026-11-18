package com.etheller.warsmash.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.launcher.DataFolderCheck;
import com.etheller.warsmash.launcher.EngineSettings;
import com.etheller.warsmash.units.DataTable;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;

/**
 * The game data this build plays from. It lies at a fixed place in the app's own folder
 * (<code>Android/data/&lt;app&gt;/files/data</code>), which the build's <code>pushGameData</code> task fills, so the
 * player is never asked for a folder: the engine configuration is written from what is found there.
 * <p>
 * A copy of the game folder that ships inside the APK can fill the same place on first start; the folder is the only
 * thing the rest of the app knows about, so nothing else has to change for it.
 */
final class GameData {
	private static final String TAG = "Warsmash";
	/** The folder in the app's own storage that holds the game data. */
	static final String FOLDER = "data";
	private static final String TEMPLATE = "warsmash-template.ini";
	/**
	 * The archives of a 1.29 and earlier install, in the order the game loads them: the later ones override the
	 * earlier ones, so the locale archives come last.
	 */
	private static final String[] MPQ_FILES = { "War3.mpq", "War3x.mpq", "War3Local.mpq", "War3xLocal.mpq" };

	private GameData() {
	}

	/** The folder the game data is read from. */
	static File folder(final Context context) {
		return new File(context.getExternalFilesDir(null), FOLDER);
	}

	/**
	 * Makes sure the engine configuration names the game data of {@link #folder(Context)}, writing it from what is
	 * found there, and tells whether the game can start.
	 */
	static boolean ensure(final Context context) {
		final File iniFile = SkinCache.iniFile(context);
		try {
			// Both folders have to exist before the section is written: a Folder source is only listed when it does, and
			// the engine's own resources carry files the game data does not (SmashFrameDef.toc and friends).
			EngineResources.ensureCurrent(context);
			// The engine folder is listed as a data source only if it exists; scripts and maps are placed there later.
			new File(context.getExternalFilesDir(null), "engine").mkdirs();
			final String dataSources = dataSources(context, folder(context));
			if (dataSources == null) {
				Log.e(TAG, "No game data in " + folder(context));
				return false;
			}
			// Compared rather than merely tested for presence: an ini left by a build that found other data, or that
			// listed fewer folders, would otherwise keep the game on the wrong sources for good.
			if (!iniFile.isFile() || !sameSources(iniFile, dataSources)) {
				writeIni(context, iniFile, dataSources);
			}
		}
		catch (final IOException e) {
			Log.e(TAG, "Unable to write " + iniFile, e);
			return false;
		}
		return true;
	}

	/** Says where the game data is read from, then closes the screen: there is no folder to choose. */
	static void showFolder(final Activity activity) {
		new AlertDialog.Builder(activity).setTitle(R.string.game_data_title)
				.setMessage(activity.getString(R.string.game_data_folder, folder(activity).getAbsolutePath()))
				.setPositiveButton(android.R.string.ok, (dialog, which) -> activity.finish()).setCancelable(false)
				.show();
	}

	/** Says whether the ini already lists exactly these sources. */
	private static boolean sameSources(final File iniFile, final String expected) throws IOException {
		final String text = new String(Files.readAllBytes(iniFile.toPath()), StandardCharsets.UTF_8)
				.replace("\r\n", "\n");
		final int start = text.indexOf("[DataSources]");
		if (start < 0) {
			return false;
		}
		final int end = text.indexOf("\n[", start + 1);
		final String current = (end < 0 ? text.substring(start) : text.substring(start, end)).trim();
		return current.equals(expected.trim());
	}

	/** The [DataSources] section for what the folder holds, or null when it holds no game data. */
	private static String dataSources(final Context context, final File folder) {
		if (!folder.isDirectory()) {
			return null;
		}
		final List<File> archives = archives(folder);
		if (!archives.isEmpty()) {
			return archiveSection(context, folder, archives);
		}
		final DataFolderCheck.Kind kind = DataFolderCheck.detect(folder);
		if (kind == DataFolderCheck.Kind.NONE) {
			return null;
		}
		return DataFolderCheck.dataSourcesSection(kind, folder, EngineResources.folder(context),
				new File(context.getExternalFilesDir(null), "engine"));
	}

	/** The archives of an install that ships MPQ archives (1.29 and earlier), or an empty list. */
	private static List<File> archives(final File folder) {
		final List<File> archives = new ArrayList<>();
		for (final String name : MPQ_FILES) {
			final File archive = child(folder, name);
			if (archive == null) {
				return new ArrayList<>();
			}
			archives.add(archive);
		}
		return archives;
	}

	/** A child of the folder by name, in any case: a copy made on a computer keeps the archive names as they are. */
	private static File child(final File folder, final String name) {
		final File exact = new File(folder, name);
		if (exact.isFile()) {
			return exact;
		}
		final File[] children = folder.listFiles();
		if (children != null) {
			for (final File child : children) {
				if (child.isFile() && child.getName().equalsIgnoreCase(name)) {
					return child;
				}
			}
		}
		return null;
	}

	private static String archiveSection(final Context context, final File folder, final List<File> archives) {
		return DataFolderCheck.mpqDataSourcesSection(folder, archives, EngineResources.folder(context),
				new File(context.getExternalFilesDir(null), "engine"));
	}

	private static void writeIni(final Context context, final File iniFile, final String dataSources)
			throws IOException {
		// The touch thresholds and the in-game graphics are the player's own (Options) and outlive a change of game data.
		final String own = EngineSettings.ownSections(iniFile);
		try (InputStream template = context.getAssets().open(TEMPLATE);
				OutputStream out = new FileOutputStream(iniFile)) {
			out.write(dataSources.getBytes(StandardCharsets.UTF_8));
			out.write('\n');
			final byte[] buffer = new byte[4096];
			int read;
			while ((read = template.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			if (!own.isEmpty()) {
				out.write(("\n" + own).getBytes(StandardCharsets.UTF_8));
			}
		}
	}
}
