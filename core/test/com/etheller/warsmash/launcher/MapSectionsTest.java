package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Color;
import com.etheller.warsmash.util.ListItemStringProperty;

/** The custom game list is in sections by the folder the maps lie in: game data first, then the player's folders. */
class MapSectionsTest {
	@Test
	void sectionsAreNamedByFolderAndSplitGameDataFromOwnMaps() {
		assertEquals("Maps", MapSections.title("Maps\\Arena.w3x"));
		assertEquals("Maps", MapSections.title("Maps/Arena.w3m"));
		assertEquals("Maps\\FrozenThrone", MapSections.title("Maps\\frozenthrone\\Arena.w3x"));
		assertEquals("Custom Maps\\Melee", MapSections.title("Maps\\Melee\\a.w3x"));
		assertEquals("Custom Maps\\Custom", MapSections.title("Maps\\Custom\\a.w3x"));
		assertEquals("Custom Maps\\Custom\\Dota", MapSections.title("Maps\\Custom\\Dota\\a.w3x"));
		assertTrue(MapSections.isOwn("maps\\custom\\x.w3x"));
		assertFalse(MapSections.isOwn("Maps\\FrozenThrone\\x.w3x"));
	}

	@Test
	void gameDataComesBeforeOwnFoldersAndItemsKeepTheirOrder() {
		final List<String> paths = Arrays.asList("Maps\\Custom\\Dota\\z.w3x", "Maps\\Melee\\m1.w3x",
				"Maps\\Custom\\b.w3x", "Maps\\FrozenThrone\\f.w3x", "Maps\\Custom\\a.w3x", "Maps\\top.w3x",
				"Maps\\Melee\\m0.w3x");
		final List<MapSections.Section<String>> sections = MapSections.group(paths, path -> path);
		assertEquals(Arrays.asList("Maps", "Maps\\FrozenThrone", "Custom Maps\\Custom", "Custom Maps\\Custom\\Dota",
				"Custom Maps\\Melee"), titles(sections));
		// The caller has already put the items in players and name order, and the sections keep it.
		assertEquals(Arrays.asList("Maps\\Custom\\b.w3x", "Maps\\Custom\\a.w3x"), sections.get(2).items);
		assertEquals(Arrays.asList("Maps\\Melee\\m1.w3x", "Maps\\Melee\\m0.w3x"), sections.get(4).items);
	}

	@Test
	void anEmptyListHasNoSections() {
		assertTrue(MapSections.group(new ArrayList<String>(), path -> path).isEmpty());
	}

	@Test
	void aSectionHeadingCannotBeChosen() {
		assertFalse(ListItemStringProperty.heading("Maps", Color.GOLD).isSelectable());
		assertTrue(new ListItemStringProperty("Maps\\a.w3x").isSelectable());
	}

	@Test
	void launcherLinesHaveHeadingsMapsThenUnreadableFilesOfTheSection() {
		final List<GameCatalog.MapEntry> maps = Arrays.asList(entry("Maps\\Custom\\b.w3x"),
				entry("Maps\\Arena.w3x"), entry("Maps\\Custom\\a.w3x"));
		final List<MapListCache.Skipped> skipped = Arrays.asList(
				new MapListCache.Skipped("Maps\\Custom\\bad.w3x", MapListCache.Problem.UNREADABLE),
				new MapListCache.Skipped("Maps\\Custom\\Dota\\x.w3x", MapListCache.Problem.NO_INFORMATION));
		final List<String> shown = new ArrayList<>();
		for (final MapSections.Line line : MapSections.lines(maps, skipped)) {
			shown.add(line.heading != null ? "# " + line.heading
					: line.map != null ? line.map.path : "! " + line.skipped.path);
		}
		assertEquals(Arrays.asList("# Maps", "Maps\\Arena.w3x", "# Custom Maps\\Custom", "Maps\\Custom\\b.w3x",
				"Maps\\Custom\\a.w3x", "! Maps\\Custom\\bad.w3x", "# Custom Maps\\Custom\\Dota",
				"! Maps\\Custom\\Dota\\x.w3x"), shown);
		assertTrue(MapSections.lines(new ArrayList<GameCatalog.MapEntry>(), new ArrayList<>()).isEmpty());
		assertEquals("a.w3x", MapSections.fileName("Maps\\Custom\\a.w3x"));
		assertEquals("a.w3x", MapSections.fileName("a.w3x"));
	}

	private static GameCatalog.MapEntry entry(final String path) {
		return new GameCatalog.MapEntry(path, 2, path, false);
	}

	private static List<String> titles(final List<MapSections.Section<String>> sections) {
		final List<String> titles = new ArrayList<>();
		for (final MapSections.Section<String> section : sections) {
			titles.add(section.title);
		}
		return titles;
	}
}
