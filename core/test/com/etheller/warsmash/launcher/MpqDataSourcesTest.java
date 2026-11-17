package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.units.Element;
import com.etheller.warsmash.util.StringBundle;

/**
 * The configuration an install of MPQ archives is played with: the archives in load order, the maps beside them, and
 * the engine's own folders last, because the later data source wins.
 */
class MpqDataSourcesTest {
	@Test
	void namesTheArchivesInLoadOrderThenTheMapsAndTheOverridingFolders(@TempDir final File folder) throws IOException {
		final File resources = new File(folder, "resources");
		final File engine = new File(folder, "engine");
		assertTrue(resources.mkdirs());
		assertTrue(engine.mkdirs());
		final List<File> archives = Arrays.asList(new File(folder, "War3.mpq"), new File(folder, "War3x.mpq"),
				new File(folder, "War3Local.mpq"), new File(folder, "War3xLocal.mpq"));

		final String section = DataFolderCheck.mpqDataSourcesSection(folder, archives, resources,
				new File(folder, "absent"), engine);

		final String[] lines = section.split("\n");
		assertEquals("[DataSources]", lines[0]);
		assertEquals("Count=7", lines[1]);
		assertEquals("Type00=MPQ", lines[2]);
		assertEquals("Path00=\"" + new File(folder, "War3.mpq").getAbsolutePath() + "\"", lines[3]);
		assertEquals("Type03=MPQ", lines[8]);
		assertEquals("Path03=\"" + new File(folder, "War3xLocal.mpq").getAbsolutePath() + "\"", lines[9]);
		assertEquals("Type04=Maps", lines[10]);
		assertEquals("Path04=\"" + folder.getAbsolutePath() + "\"", lines[11]);
		assertEquals("Type05=Folder", lines[12]);
		assertEquals("Path05=\"" + resources.getAbsolutePath() + "\"", lines[13]);
		// A folder that is not there is not named, and the count leaves it out.
		assertEquals("Type06=Folder", lines[14]);
		assertEquals("Path06=\"" + engine.getAbsolutePath() + "\"", lines[15]);
	}

	@Test
	void keepsThePathsOfAFolderWithSpacesInOnePiece(@TempDir final File folder) throws IOException {
		final File game = new File(folder, "Warcraft III");
		assertTrue(game.mkdirs());

		final String section = DataFolderCheck.mpqDataSourcesSection(game,
				Arrays.asList(new File(game, "War3.mpq")), new File(game, "missing"));

		assertTrue(section.contains("Path00=\"" + new File(game, "War3.mpq").getAbsolutePath() + "\""),
				section);
		assertEquals("Count=2", section.split("\n")[1]);
	}

	@Test
	void isReadBackAsGameDataEvenThoughTheFirstSourceIsAnArchive(@TempDir final File folder) throws IOException {
		final File ini = new File(folder, "warsmash.ini");
		Files.write(ini.toPath(),
				DataFolderCheck.mpqDataSourcesSection(folder, Arrays.asList(new File(folder, "War3.mpq")))
						.getBytes(StandardCharsets.UTF_8));

		// hasGameData only looks at a Folder or CASC first source; an archive is taken as it is.
		assertTrue(DataFolderCheck.hasGameData(readDataSources(ini)));
	}

	private static Element readDataSources(final File ini) throws IOException {
		final DataTable table = new DataTable(StringBundle.EMPTY);
		table.readTXT(ini, true);
		return table.get("DataSources");
	}
}
