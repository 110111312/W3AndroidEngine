package com.etheller.warsmash.datasources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;

/**
 * The [Graphics] section of the engine configuration, which both launchers write from their Options page: the in-game
 * graphics set of custom games and the texture size limit. A campaign mission is played with the graphics of the
 * campaign version it was started in, whatever the set. The menu skin (Classic or Reforged) is the launcher's own
 * choice and changes only the menus.
 */
public final class GraphicsSettings {
	public static final String SECTION = "Graphics";
	public static final String KEY_SET = "Set";
	public static final String KEY_TEXTURE_SIZE = "TextureSize";
	/** "Auto" (the default: compressed on phones and tablets), "On" or "Off"; see {@link #textureCompression}. */
	public static final String KEY_TEXTURE_COMPRESSION = "TextureCompression";
	/** The texture size limits offered, in the order the launchers list them; 0 is no limit. */
	public static final int[] TEXTURE_SIZES = { 512, 1024, 0 };
	/** Below this much memory a tablet is treated as a phone. */
	private static final long TABLET_MEMORY_MB = 6 * 1024;
	private static final String LOCALES = "\\_locales\\";

	private GraphicsSettings() {
	}

	public static GraphicsSet graphicsSet(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		return GraphicsSet.fromIniName(section == null ? null : section.getField(KEY_SET));
	}

	/** The texture size limit the configuration sets, or -1 when it sets none. */
	public static int configuredTextureSize(final DataTable ini) {
		final Element section = ini == null ? null : ini.get(SECTION);
		final String value = section == null ? "" : section.getField(KEY_TEXTURE_SIZE).trim();
		if (value.isEmpty()) {
			return -1;
		}
		try {
			return Math.max(0, Integer.parseInt(value));
		}
		catch (final NumberFormatException e) {
			return -1;
		}
	}

	/** The texture size limit to play with; without a setting the desktop's default, no limit. */
	public static int textureSize(final DataTable ini) {
		return Math.max(0, configuredTextureSize(ini));
	}

	/**
	 * The default limit of a device: 512 on phones, 1024 on tablets with enough memory, none on desktops. The game
	 * data are block compressed in formats Android GPUs lack, so a texture is decoded to four bytes a pixel unless
	 * {@link #textureCompression} re-encodes it to ETC2.
	 */
	public static int defaultTextureSize(final boolean mobile, final boolean tablet, final long memoryMb) {
		if (!mobile) {
			return 0;
		}
		return tablet && (memoryMb >= TABLET_MEMORY_MB) ? 1024 : 512;
	}

	/**
	 * Whether the model textures of a game are uploaded as ETC2 blocks. Only the HD and DE graphics are, Classic keeps
	 * its textures as they are. Mobile GPUs read the blocks directly, so "Auto" turns it on there; desktop drivers
	 * mostly expand them again, which saves nothing, so it needs "On".
	 *
	 * @param etc2Supported whether the GPU reads ETC2 (every OpenGL ES 3.0 one does)
	 */
	public static boolean textureCompression(final DataTable ini, final GraphicsSet graphics, final boolean mobile,
			final boolean etc2Supported) {
		if ((graphics == GraphicsSet.CLASSIC) || !etc2Supported) {
			return false;
		}
		final Element section = ini == null ? null : ini.get(SECTION);
		final String value = section == null ? "" : section.getField(KEY_TEXTURE_COMPRESSION).trim();
		if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false") || value.equals("0")) {
			return false;
		}
		if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || value.equals("1")) {
			return true;
		}
		return mobile;
	}

	/** Puts a default texture size limit into a configuration that sets none, for this run only. */
	public static void applyDefaultTextureSize(final DataTable ini, final int textureSize) {
		if ((ini == null) || (configuredTextureSize(ini) >= 0)) {
			return;
		}
		section(ini).setField(KEY_TEXTURE_SIZE, Integer.toString(textureSize));
	}

	/** Sets the graphics of a configuration, for this run only. */
	public static void apply(final DataTable ini, final GraphicsSet graphics, final int textureSize) {
		final Element section = section(ini);
		section.setField(KEY_SET, graphics.getIniName());
		section.setField(KEY_TEXTURE_SIZE, Integer.toString(Math.max(0, textureSize)));
	}

	private static Element section(final DataTable ini) {
		Element section = ini.get(SECTION);
		if (section == null) {
			section = new Element(SECTION, ini);
			ini.put(SECTION, section);
		}
		return section;
	}

	/** The language folder of the configured data ("enus" in "war3.w3mod\_locales\enus.w3mod"). */
	public static String locale(final List<String> prefixes) {
		for (final String prefix : prefixes) {
			final String lower = prefix.trim().toLowerCase(Locale.US).replace('/', '\\');
			final int at = lower.indexOf(LOCALES);
			if ((at >= 0) && lower.endsWith(".w3mod")) {
				return lower.substring(at + LOCALES.length(), lower.length() - ".w3mod".length());
			}
		}
		return "enus";
	}

	/** The game data as a game with these graphics reads it, its models, icons and interface alike. */
	public static DataSource gameData(final DataSource data, final DataTable ini, final GraphicsSet graphics) {
		return LayeredDataSource.wrap(data, graphics, locale(ini));
	}

	private static String locale(final DataTable ini) {
		final Element sources = ini == null ? null : ini.get("DataSources");
		if (sources == null) {
			return "enus";
		}
		final List<String> prefixes = new ArrayList<>();
		for (int i = 0; i < sources.size(); i++) {
			final String prefixField = sources.getField(String.format(Locale.ROOT, "Prefixes%02d", i));
			if (!prefixField.isEmpty()) {
				prefixes.addAll(Arrays.asList(prefixField.split(",")));
			}
		}
		return locale(prefixes);
	}
}
