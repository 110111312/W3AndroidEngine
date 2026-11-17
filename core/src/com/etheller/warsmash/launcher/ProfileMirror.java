package com.etheller.warsmash.launcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.campaign.CSaveStorage;

/**
 * A copy of the profile list kept beside the profiles' files (in the shared storage on Android), so that a list lost
 * with the application's preferences can be had back. The files themselves are in Save/&lt;profile&gt; there; only
 * what the preferences held (the names, the current profile, the launcher's portrait, times and campaign maps) is
 * written here. A folder of the saves with no entry in the copy is a profile, too: its name is the folder's.
 */
public final class ProfileMirror {
	public static final String FILE = "profiles.txt";
	private static final String ENGINE = "e.";
	private static final String LAUNCHER = "l.";
	private static final String VERSION = "version";
	private static final String CURRENT = "CurrentProfile";

	private ProfileMirror() {
	}

	/** What the copy holds: the names in order, the current one and the launcher's values per profile. */
	static final class Entries {
		final List<String> names = new ArrayList<>();
		String current;
		final Map<String, Map<String, Object>> extras = new LinkedHashMap<>();
	}

	/**
	 * Writes the copy. A list that holds no profile of the player's own is not written, so that a fresh start does
	 * not replace the copy it has yet to read.
	 *
	 * @return whether the copy is written
	 */
	public static boolean write(final Profiles profiles, final File folder) {
		if (!profiles.any()) {
			return false;
		}
		final Properties properties = new Properties();
		properties.setProperty(VERSION, "1");
		final List<String> names = profiles.names();
		properties.setProperty(ENGINE + "ProfileCount", Integer.toString(names.size()));
		for (int i = 0; i < names.size(); i++) {
			properties.setProperty(ENGINE + "Profile" + i + "_Name", names.get(i));
		}
		properties.setProperty(ENGINE + CURRENT, profiles.current());
		final LauncherStore store = profiles.launcherStore();
		for (final String name : names) {
			for (final String field : Profiles.FIELDS) {
				final String key = Profiles.key(name, field);
				final Object value = store.get(key);
				if (value instanceof String) {
					properties.setProperty(LAUNCHER + key, "s" + value);
				}
				else if (value instanceof Integer) {
					properties.setProperty(LAUNCHER + key, "i" + value);
				}
				else if (value instanceof Long) {
					properties.setProperty(LAUNCHER + key, "l" + value);
				}
			}
		}
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			properties.store(bytes, null);
		}
		catch (final IOException e) {
			return false;
		}
		return ProfileStorage.writeAtomically(new File(folder, FILE), bytes.toByteArray());
	}

	/** The copy, or null when it is missing or unreadable. */
	static Entries read(final File folder) {
		final File file = new File(folder, FILE);
		if (!file.isFile()) {
			return null;
		}
		final Properties properties = new Properties();
		try {
			properties.load(new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
			final int count = Integer.parseInt(properties.getProperty(ENGINE + "ProfileCount", "-1"));
			if (count < 0) {
				return null;
			}
			final Entries entries = new Entries();
			for (int i = 0; i < count; i++) {
				final String name = properties.getProperty(ENGINE + "Profile" + i + "_Name");
				if ((name != null) && !name.isEmpty() && !entries.names.contains(name)) {
					entries.names.add(name);
				}
			}
			entries.current = properties.getProperty(ENGINE + CURRENT);
			for (final String name : entries.names) {
				final Map<String, Object> values = new LinkedHashMap<>();
				for (final String field : Profiles.FIELDS) {
					final String key = Profiles.key(name, field);
					final String text = properties.getProperty(LAUNCHER + key);
					if ((text != null) && !text.isEmpty()) {
						final Object value = decode(text);
						if (value != null) {
							values.put(key, value);
						}
					}
				}
				entries.extras.put(name, values);
			}
			return entries;
		}
		catch (final IOException | RuntimeException e) {
			LauncherLog.w("Unable to read the profile list copy " + file, e);
			return null;
		}
	}

	private static Object decode(final String text) {
		final String value = text.substring(1);
		try {
			switch (text.charAt(0)) {
			case 's':
				return value;
			case 'i':
				return Integer.valueOf(value);
			case 'l':
				return Long.valueOf(value);
			default:
				return null;
			}
		}
		catch (final NumberFormatException e) {
			return null;
		}
	}

	/** The profiles that have a folder of saves, by the folders' names; a folder with nothing in it is not one. */
	static List<String> foldersOf(final File folder) {
		final List<String> names = new ArrayList<>();
		final File[] children = new File(folder, "Save").listFiles();
		if (children != null) {
			for (final File child : children) {
				final String[] inside = child.isDirectory() ? child.list() : null;
				final String name = child.getName();
				if ((inside != null) && (inside.length > 0) && !name.startsWith(".")
						&& CSaveStorage.safeName(name).equals(name)) {
					names.add(name);
				}
			}
		}
		Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/**
	 * Brings the profiles of the folder into the list: all of them, with the current one, when the list holds no
	 * profile of the player's own, else only those it lacks (the player's profiles on this device are kept as they
	 * are).
	 *
	 * @return how many profiles were added
	 */
	public static int restore(final Profiles profiles, final File folder) {
		final Entries entries = read(folder);
		final List<String> found = entries == null ? new ArrayList<>() : new ArrayList<>(entries.names);
		for (final String name : foldersOf(folder)) {
			if (!containsName(found, name)) {
				found.add(name);
			}
		}
		if (found.isEmpty()) {
			return 0;
		}
		final boolean replace = !profiles.any();
		final List<String> names = replace ? new ArrayList<>() : profiles.names();
		final LauncherStore.Edit launcher = profiles.launcherStore().edit();
		int added = 0;
		for (final String name : found) {
			if (containsName(names, name)) {
				continue;
			}
			names.add(name);
			added++;
			final Map<String, Object> values = entries == null ? null : entries.extras.get(name);
			if (values != null) {
				for (final Map.Entry<String, Object> value : values.entrySet()) {
					final Object v = value.getValue();
					if (v instanceof String) {
						launcher.putString(value.getKey(), (String) v);
					}
					else if (v instanceof Integer) {
						launcher.putInt(value.getKey(), (Integer) v);
					}
					else if (v instanceof Long) {
						launcher.putLong(value.getKey(), (Long) v);
					}
				}
			}
		}
		if (added == 0) {
			return 0;
		}
		launcher.commit();
		profiles.writeNames(names);
		if (replace) {
			final String current = (entries != null) && (entries.current != null) && names.contains(entries.current)
					? entries.current
					: names.get(0);
			profiles.engineStore().edit().putString("CurrentProfile", current).commit();
		}
		return added;
	}

	private static boolean containsName(final List<String> names, final String name) {
		final String safe = CSaveStorage.safeName(name);
		for (final String other : names) {
			if (CSaveStorage.safeName(other).equalsIgnoreCase(safe)) {
				return true;
			}
		}
		return false;
	}
}
