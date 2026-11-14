package com.etheller.warsmash.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The custom game list in sections, one for each folder the maps lie in: the game data's own Maps folders first, then
 * the player's own content folder (Maps/Melee, Maps/Custom and the folders below them).
 */
public final class MapSections {
	/** A section: its title, and the items that lie in its folder in the order they were given. */
	public static final class Section<T> {
		public final String title;
		public final List<T> items = new ArrayList<>();

		Section(final String title) {
			this.title = title;
		}
	}

	private MapSections() {
	}

	/** The folder of a map, below Maps, as the section is named: "" for the top of Maps. */
	private static String folder(final String path) {
		return CampaignChoice.mapFolder(path);
	}

	/** Whether the map lies in the player's own content folder rather than the game data. */
	public static boolean isOwn(final String path) {
		return MapListCache.isOwnMap(path);
	}

	/** The title of the section a map belongs to. */
	public static String title(final String path) {
		final String folder = folder(path);
		final StringBuilder title = new StringBuilder(isOwn(path) ? "Custom Maps" : "Maps");
		if (!folder.isEmpty()) {
			for (final String part : folder.split("/")) {
				title.append('\\').append(CampaignChoice.folderName(part));
			}
		}
		return title.toString();
	}

	/** Order of the sections: the game data before the player's folders, then by folder name, the top first. */
	private static int compareFolders(final String a, final String b) {
		final boolean ownA = isOwn(a);
		final boolean ownB = isOwn(b);
		if (ownA != ownB) {
			return ownA ? 1 : -1;
		}
		return folder(a).toLowerCase(Locale.ROOT).compareTo(folder(b).toLowerCase(Locale.ROOT));
	}

	/**
	 * Groups items by the folder of their path. The sections come in their order; the items of a section keep the
	 * order they had in the collection.
	 */
	public static <T> List<Section<T>> group(final Collection<T> items, final Function<T, String> pathOf) {
		final List<T> ordered = new ArrayList<>(items);
		// A stable sort by folder keeps the order the items were given within a folder.
		ordered.sort(Comparator.comparing(pathOf, MapSections::compareFolders));
		final List<Section<T>> sections = new ArrayList<>();
		Section<T> current = null;
		for (final T item : ordered) {
			final String path = pathOf.apply(item);
			final String title = title(path);
			if ((current == null) || !current.title.equalsIgnoreCase(title)) {
				current = new Section<>(title);
				sections.add(current);
			}
			current.items.add(item);
		}
		return sections;
	}

	/** A line of the sectioned launcher list: a heading, a map, or a file of the player's folders that is not a map. */
	public static final class Line {
		/** The section title for a heading line, else null. */
		public final String heading;
		public final GameCatalog.MapEntry map;
		public final MapListCache.Skipped skipped;

		private Line(final String heading, final GameCatalog.MapEntry map, final MapListCache.Skipped skipped) {
			this.heading = heading;
			this.map = map;
			this.skipped = skipped;
		}

		String path() {
			return this.map != null ? this.map.path : this.skipped.path;
		}
	}

	/**
	 * The launcher's custom game list as the engine menu has it: a heading per section, the maps of a section in the
	 * order given, then the files of that section that cannot be read.
	 */
	public static List<Line> lines(final List<GameCatalog.MapEntry> maps, final List<MapListCache.Skipped> skipped) {
		final List<Line> all = new ArrayList<>();
		for (final GameCatalog.MapEntry entry : maps) {
			all.add(new Line(null, entry, null));
		}
		if (skipped != null) {
			for (final MapListCache.Skipped left : skipped) {
				all.add(new Line(null, null, left));
			}
		}
		final List<Line> lines = new ArrayList<>();
		for (final Section<Line> section : group(all, Line::path)) {
			lines.add(new Line(section.title, null, null));
			lines.addAll(section.items);
		}
		return lines;
	}

	/** The file name of a path. */
	public static String fileName(final String path) {
		return path.substring(Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/')) + 1);
	}
}
