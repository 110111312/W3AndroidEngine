package com.etheller.warsmash.android;

import static com.etheller.warsmash.launcher.LauncherKeys.KEY_PROFILES_SHARED;
import static com.etheller.warsmash.launcher.LauncherKeys.PREFERENCES;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.etheller.warsmash.launcher.ProfileMirror;
import com.etheller.warsmash.launcher.ProfileStorage;
import com.etheller.warsmash.launcher.StorageRoot;

/**
 * Where the profiles are on Android: in WC3-Android/Profiles of the shared storage, which an uninstall leaves, when
 * the player wants that (the default) and the app has all files access; else in the private files, as before. Decided
 * once when the process starts, because the launcher and the engine hold the folders they were given.
 */
final class ProfileStorageAndroid {
	private static final String TAG = "ProfileStorage";
	/** Set when the private Save and Replay have been copied to the shared folder; cleared when the choice changes. */
	private static final String KEY_MOVED_IN = "profilesMovedIn";
	/** Set when the shared folder has been copied to the private files after the player turned the choice off. */
	private static final String KEY_MOVED_OUT = "profilesMovedOut";

	private static File active;
	private static String state = "";

	private ProfileStorageAndroid() {
	}

	/** Called by the application before anything reads or writes a profile. A failure leaves the private storage. */
	static synchronized void init(final Context context) {
		final SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
		final boolean wanted = preferences.getBoolean(KEY_PROFILES_SHARED, true);
		final File folder = ProfileStorage.sharedFolder(Environment.getExternalStorageDirectory());
		active = null;
		StorageRoot.set(null);
		try {
			final boolean access = allFilesAccess(context);
			if (!wanted) {
				state = "private";
				if (access && !preferences.getBoolean(KEY_MOVED_OUT, false)) {
					moveOut(context, preferences, folder);
				}
				return;
			}
			if (!access || !ProfileStorage.writable(folder)) {
				state = access ? "unwritable" : "noaccess";
				return;
			}
			if (!preferences.getBoolean(KEY_MOVED_IN, false) && !moveIn(context, preferences, folder)) {
				// The private copy stays the one in use until every file is in the shared folder.
				state = "failed";
				return;
			}
			active = folder;
			StorageRoot.set(folder);
			state = "shared";
			final Profiles profiles = new Profiles(context, preferences);
			ProfileMirror.restore(profiles, folder);
			ProfileMirror.write(profiles, folder);
		}
		catch (final RuntimeException e) {
			Log.e(TAG, "Profiles stay in the private storage", e);
			active = null;
			StorageRoot.set(null);
			state = "failed";
		}
	}

	/** Private Save and Replay into the shared folder; nothing is overwritten or deleted. */
	private static boolean moveIn(final Context context, final SharedPreferences preferences, final File folder) {
		if (!copyFolders(context.getFilesDir(), folder)) {
			return false;
		}
		preferences.edit().putBoolean(KEY_MOVED_IN, true).commit();
		return true;
	}

	/** The shared folder into the private storage, for a player who turned the choice off; the shared copy stays. */
	private static void moveOut(final Context context, final SharedPreferences preferences, final File folder) {
		if (!folder.isDirectory()) {
			preferences.edit().putBoolean(KEY_MOVED_OUT, true).commit();
			return;
		}
		active = null;
		if (copyFolders(folder, context.getFilesDir())) {
			final Profiles profiles = new Profiles(context, preferences);
			ProfileMirror.restore(profiles, folder);
			preferences.edit().putBoolean(KEY_MOVED_OUT, true).commit();
		}
	}

	private static boolean copyFolders(final File from, final File to) {
		final File conflicts = new File(new File(to, ProfileStorage.CONFLICTS),
				new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
		boolean ok = true;
		for (final String name : ProfileStorage.FOLDERS) {
			final ProfileStorage.Result result = ProfileStorage.copyTree(new File(from, name), new File(to, name),
					new File(conflicts, name));
			Log.i(TAG, name + " " + from + " -> " + to + ": copied " + result.copied + ", same " + result.skipped
					+ ", kept apart " + result.conflicts + ", failed " + result.failed.size() + ", unverified "
					+ result.unverified.size());
			ok &= result.ok();
		}
		return ok;
	}

	static boolean allFilesAccess(final Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			return Environment.isExternalStorageManager();
		}
		return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	/** Save in the folder the profiles are kept in. */
	static File saves(final Context context) {
		return new File(active != null ? active : context.getFilesDir(), "Save");
	}

	/** The shared folder the profiles are in now, or null while they are in the private storage. */
	static File active() {
		return active;
	}

	/** Why the profiles are where they are: shared, private (the player's choice), noaccess, unwritable or failed. */
	static String state() {
		return state;
	}

	/** The player's choice, which takes effect when the app starts next. */
	static boolean wanted(final Context context) {
		return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(KEY_PROFILES_SHARED, true);
	}

	static void setWanted(final Context context, final boolean wanted) {
		context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(KEY_PROFILES_SHARED, wanted)
				.putBoolean(KEY_MOVED_IN, false).putBoolean(KEY_MOVED_OUT, false).commit();
	}

	/** Writes the copy of the profile list beside the profiles (after a change, and for what the engine's menu did). */
	static void mirror(final Profiles profiles) {
		final File folder = active;
		if ((folder != null) && (profiles != null)) {
			ProfileMirror.write(profiles, folder);
		}
	}
}
