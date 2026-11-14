package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.datasources.CompoundDataSource;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.MapsFolderDataSource;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.StringBundle;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.mapsetup.MapListContainer;

/** The player's content folder: its structure, its place in the data sources and the import of a map. */
class CustomContentTest {
	@TempDir
	File temp;

	private static DataTable ini(final String text) throws IOException {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		ini.readTXT(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), true);
		return ini;
	}

	private static void write(final File file, final String text) throws IOException {
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void structureIsMadeInsideAnExistingFolderOnly() {
		final File root = new File(this.temp, "content");
		assertFalse(CustomContent.ensureStructure(root));
		assertFalse(root.exists());

		assertTrue(root.mkdirs());
		assertTrue(CustomContent.ensureStructure(root));
		for (final String folder : CustomContent.FOLDERS) {
			assertTrue(new File(root, folder).isDirectory(), folder);
		}
		assertTrue(CustomContent.ensureStructure(root));
	}

	@Test
	void theFolderIsAnotherDataSourceOnlyWhileItExists() throws IOException {
		final File root = new File(this.temp, "content");
		final String text = "[DataSources]\nCount=1\nType00=Folder\nPath00=" + this.temp.getPath()
				+ "\n[CustomContent]\nFolder=" + root.getPath() + "\n";
		final DataTable missing = ini(text);
		CustomContent.apply(missing);
		assertEquals("", missing.get("DataSources").getField("Type01"));
		assertFalse(root.exists());

		assertTrue(root.mkdirs());
		final DataTable present = ini(text);
		CustomContent.apply(present);
		final Element sources = present.get("DataSources");
		assertEquals("Maps", sources.getField("Type01"));
		assertEquals(root.getAbsolutePath(), sources.getField("Path01"));
		assertEquals(2, sources.getFieldValue("Count"));
		assertTrue(new File(root, "Maps/Custom").isDirectory());

		CustomContent.apply(present);
		assertEquals("", sources.getField("Type02"));
	}

	@Test
	void withoutAFolderTheConfigurationIsUnchanged() throws IOException {
		final DataTable ini = ini("[DataSources]\nCount=1\nType00=Folder\nPath00=x\n[CustomContent]\nFolder=\n");
		assertNull(CustomContent.folder(ini));
		CustomContent.apply(ini);
		assertEquals("", ini.get("DataSources").getField("Type01"));
		assertNull(CustomContent.folder(ini("[DataSources]\nCount=0\n")));
		assertEquals(new File("a b"), CustomContent.parse("\"a b\""));
	}

	@Test
	void theChosenFolderSurvivesInTheSettingsFile() throws IOException {
		final File settings = new File(this.temp, "warsmash.ini");
		write(settings, "[DataSources]\nCount=1\nType00=Folder\nPath00=x\n");
		final EngineSettings engine = new EngineSettings(new File(this.temp, "Save"), "Player", settings);
		assertNull(engine.customFolder());
		final File folder = new File(this.temp, "my content");
		engine.setCustomFolder(folder);
		assertEquals(folder.getAbsoluteFile(), engine.customFolder());
		assertTrue(EngineSettings.ownSections(settings).contains("[CustomContent]"));
		engine.setCustomFolder(null);
		assertNull(engine.customFolder());
	}

	@Test
	void importedFilesNeedAMapName() {
		assertEquals("Dota.w3x", CustomContent.mapFileName("Dota.w3x"));
		assertEquals("a.W3M", CustomContent.mapFileName("downloads/sub\\a.W3M"));
		assertEquals("a_b.w3x", CustomContent.mapFileName("a:b.w3x"));
		assertNull(CustomContent.mapFileName("map.zip"));
		assertNull(CustomContent.mapFileName(".w3x"));
		assertNull(CustomContent.mapFileName("map.w3x.txt"));
		assertNull(CustomContent.mapFileName(null));
	}

	@Test
	void aFileThatIsNotAMapIsNotKept() throws IOException {
		final File root = new File(this.temp, "content");
		assertTrue(root.mkdirs());
		final byte[] junk = "not a map".getBytes(StandardCharsets.UTF_8);

		CustomContent.ImportException wrongName = assertThrows(CustomContent.ImportException.class,
				() -> CustomContent.importMap(root, "notes.txt", new ByteArrayInputStream(junk)));
		assertEquals(CustomContent.Problem.NOT_A_MAP_NAME, wrongName.problem);

		final CustomContent.ImportException damaged = assertThrows(CustomContent.ImportException.class,
				() -> CustomContent.importMap(root, "bad.w3x", new ByteArrayInputStream(junk)));
		assertEquals(CustomContent.Problem.UNREADABLE, damaged.problem);
		final String[] left = new File(root, "Maps/Custom").list();
		assertEquals(0, left.length);

		final CustomContent.ImportException noFolder = assertThrows(CustomContent.ImportException.class,
				() -> CustomContent.importMap(new File(this.temp, "gone"), "a.w3x", new ByteArrayInputStream(junk)));
		assertEquals(CustomContent.Problem.NO_FOLDER, noFolder.problem);
	}

	@Test
	void onlyTheMapsFolderOfTheContentFolderIsOfferedAsGameData() throws Exception {
		final File root = new File(this.temp, "content");
		write(new File(root, "Maps/Custom/a.w3x"), "a");
		write(new File(root, "Maps/Melee/b.w3m"), "b");
		write(new File(root, "units/UnitData.slk"), "must stay hidden");
		write(new File(root, "Replays/r.w3g"), "r");
		final DataSource source = new MapsFolderDataSource(root.toPath());

		final Collection<String> listed = source.getListfile();
		assertEquals(2, listed.size());
		assertTrue(listed.contains("Maps\\Custom\\a.w3x"));
		assertTrue(listed.contains("Maps\\Melee\\b.w3m"));
		assertTrue(source.has("maps/custom/A.W3X"));
		assertFalse(source.has("units\\UnitData.slk"));
		assertFalse(source.has("Replays\\r.w3g"));
		assertEquals(1, source.read("Maps\\Custom\\a.w3x").remaining());
		assertNull(source.read("Replays\\r.w3g"));
	}

	@Test
	void aNewMapInTheFolderChangesTheMapFingerprint() throws Exception {
		final File root = new File(this.temp, "content");
		final File data = new File(this.temp, "data");
		assertTrue(data.mkdirs());
		assertTrue(root.mkdirs());
		final String text = "[DataSources]\nCount=1\nType00=Folder\nPath00=" + data.getPath()
				+ "\n[CustomContent]\nFolder=" + root.getPath() + "\n";
		final DataTable ini = ini(text);
		CustomContent.apply(ini);
		final String before = GameCatalog.mapFingerprint(ini);
		assertEquals(before, GameCatalog.mapFingerprint(ini));

		final File map = new File(root, "Maps/Custom/new.w3x");
		write(map, "1");
		map.setLastModified(5_000_000);
		final String added = GameCatalog.mapFingerprint(ini);
		assertNotEquals(before, added);

		write(map, "12");
		map.setLastModified(6_000_000);
		final String replaced = GameCatalog.mapFingerprint(ini);
		assertNotEquals(added, replaced);

		assertTrue(map.delete());
		assertNotEquals(replaced, GameCatalog.mapFingerprint(ini));
	}

	@Test
	void theKeptListHoldsTheMapsLeftOutWithTheirReason() throws Exception {
		final File list = new File(this.temp, "cache/maps.txt");
		final List<GameCatalog.MapEntry> maps = Arrays
				.asList(new GameCatalog.MapEntry("Maps\\Custom\\a.w3x", 4, "Arena", false));
		final List<MapListCache.Skipped> skipped = Arrays.asList(
				new MapListCache.Skipped("Maps\\Custom\\bad.w3x", MapListCache.Problem.UNREADABLE),
				new MapListCache.Skipped("Maps\\Melee\\empty.w3m", MapListCache.Problem.NO_INFORMATION));
		MapListCache.write(list, "header", new MapListCache.Listing(maps, skipped));

		assertNull(MapListCache.read(list, "another header"));
		final MapListCache.Listing read = MapListCache.read(list, "header");
		assertEquals(1, read.maps.size());
		assertEquals("Arena", read.maps.get(0).name);
		assertEquals(2, read.skipped.size());
		assertEquals(MapListCache.Problem.NO_INFORMATION, read.skipped.get(1).problem);
		assertEquals(0, list.getParentFile().list().length - 1);
		assertTrue(MapListCache.isOwnMap("Maps\\Custom\\x\\y.w3x"));
		assertTrue(MapListCache.isOwnMap("maps/melee/y.w3m"));
		assertFalse(MapListCache.isOwnMap("Maps\\FrozenThrone\\y.w3x"));
	}

	private static CompoundDataSource game(final DataSource... base) {
		final CompoundDataSource inner = new CompoundDataSource(Arrays.asList(base));
		return new CompoundDataSource(Arrays.asList(inner));
	}

	private static String iniText(final File data) {
		return "[DataSources]\nCount=1\nType00=Folder\nPath00=" + data.getPath() + "\n";
	}

	@Test
	void aChosenFolderIsUsedAtOnceWithoutRestarting() throws Exception {
		final File first = new File(this.temp, "first");
		final File second = new File(this.temp, "second");
		assertTrue(first.mkdirs());
		assertTrue(second.mkdirs());
		write(new File(first, "Maps/Custom/a.w3x"), "a");
		write(new File(second, "Maps/Melee/b.w3x"), "b");
		final DataTable ini = ini(iniText(this.temp));
		final CompoundDataSource data = game(new MapsFolderDataSource(first.toPath()));
		assertTrue(data.has("Maps\\Custom\\a.w3x"));
		assertFalse(data.has("Maps\\Melee\\b.w3x"));

		// The folder is changed: the old maps are gone, the new ones are there, and lookups made before are forgotten.
		assertTrue(CustomContent.use(ini, data, second));
		assertFalse(data.has("Maps\\Custom\\a.w3x"));
		assertTrue(data.has("Maps\\Melee\\b.w3x"));
		assertTrue(data.getListfile().contains("Maps\\Melee\\b.w3x"));
		assertEquals(second.getAbsolutePath(), CustomContent.folder(ini).getPath());
		assertEquals(second.getAbsolutePath(), ini.get("DataSources").getField("Path01"));

		// A map added to the folder in use shows after the folder is taken up again.
		write(new File(second, "Maps/Custom/c.w3x"), "c");
		assertTrue(CustomContent.use(ini, data, second));
		assertTrue(data.getListfile().contains("Maps\\Custom\\c.w3x"));
		assertEquals("", ini.get("DataSources").getField("Type02"));

		assertTrue(CustomContent.use(ini, data, null));
		assertFalse(data.has("Maps\\Melee\\b.w3x"));
		assertNull(CustomContent.folder(ini));
		assertEquals("", ini.get("DataSources").getField("Type01"));
	}

	@Test
	void aFolderChosenWhenTheDataHadNoMapsSourceGetsOne() throws Exception {
		final File root = new File(this.temp, "content");
		assertTrue(root.mkdirs());
		write(new File(root, "Maps/Custom/a.w3x"), "a");
		final DataTable ini = ini(iniText(this.temp));
		final CompoundDataSource data = game();
		assertFalse(data.has("Maps\\Custom\\a.w3x"));

		assertTrue(CustomContent.use(ini, data, root));
		assertTrue(data.has("Maps\\Custom\\a.w3x"));
		assertEquals("Maps", ini.get("DataSources").getField("Type01"));
		assertEquals(2, ini.get("DataSources").getFieldValue("Count"));
		assertTrue(new File(root, "Maps/Melee").isDirectory());
	}

	@Test
	void aFolderThatIsNotThereIsNotTaken() throws Exception {
		final DataTable ini = ini(iniText(this.temp));
		assertFalse(CustomContent.use(ini, game(), new File(this.temp, "gone")));
		assertNull(CustomContent.folder(ini));
		assertEquals("", ini.get("DataSources").getField("Type01"));
	}

	@Test
	void theKeptMapListIsMadeAgainWhenTheFolderChanges() throws Exception {
		final File first = new File(this.temp, "first");
		final File second = new File(this.temp, "second");
		assertTrue(first.mkdirs());
		assertTrue(second.mkdirs());
		write(new File(first, "Maps/Custom/a.w3x"), "a");
		write(new File(second, "Maps/Custom/a.w3x"), "a");
		write(new File(second, "Maps/Custom/b.w3x"), "b");
		final DataTable ini = ini(iniText(this.temp));
		assertTrue(CustomContent.use(ini, null, first));
		final String before = MapListCache.header(GameCatalog.dataKey(ini), ini);
		assertEquals(before, MapListCache.header(GameCatalog.dataKey(ini), ini));
		assertTrue(CustomContent.use(ini, null, second));
		assertNotEquals(before, MapListCache.header(GameCatalog.dataKey(ini), ini));
	}

	@Test
	void campaignFilesAreNeitherListedNorReportedAsBrokenMaps() throws Exception {
		assertFalse(MapListContainer.isListedMap("Maps\\Custom\\Story.w3n"));
		assertFalse(MapListContainer.isListedMap("Campaigns\\Story.w3n"));
		final File root = new File(this.temp, "content");
		write(new File(root, "Campaigns/Story.w3n"), "campaign");
		write(new File(root, "Maps/Custom/Story.w3n"), "campaign");
		final DataSource source = new MapsFolderDataSource(root.toPath());
		for (final String file : source.getListfile()) {
			assertFalse(MapListContainer.isListedMap(file), file);
		}
		assertFalse(source.has("Campaigns\\Story.w3n"));
	}
}
