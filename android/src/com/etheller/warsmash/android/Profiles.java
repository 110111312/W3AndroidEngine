package com.etheller.warsmash.android;

import java.io.File;

import android.content.Context;
import android.content.SharedPreferences;

/** The profiles as the launcher keeps them on Android: the engine's preferences, and Save beside the copy of the list. */
final class Profiles extends com.etheller.warsmash.launcher.Profiles {
	Profiles(final Context context, final SharedPreferences launcher) {
		super(new AndroidStore(context.getSharedPreferences(ENGINE_PREFERENCES, Context.MODE_PRIVATE)),
				new AndroidStore(launcher), saves(context));
	}

	/** Save where the profiles are kept: the shared storage, or the private files, which Gdx.files.local is. */
	static File saves(final Context context) {
		return ProfileStorageAndroid.saves(context);
	}

	@Override
	protected void changed() {
		ProfileStorageAndroid.mirror(this);
	}
}
