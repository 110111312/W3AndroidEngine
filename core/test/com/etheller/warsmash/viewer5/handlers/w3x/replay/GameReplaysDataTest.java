package com.etheller.warsmash.viewer5.handlers.w3x.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.datasources.GraphicsSet;
import com.etheller.warsmash.datasources.GraphicsSettings;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

/**
 * A replay's data checksum is taken on the game data with the graphics the game is played with, so the check of a
 * replay must read the same layered data: the graphics packages override skin files the checksum covers.
 */
class GameReplaysDataTest {
	private static final String SKIN = "Units\\UnitSkin.txt";

	@Test
	void layeredDataHasAnotherChecksumThanTheBase() throws Exception {
		final DataSource base = installed();
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.DE, 0);
		final DataSource layered = GraphicsSettings.gameData(base, ini, GraphicsSet.DE);
		assertNotEquals(GameReplays.dataChecksum(base), GameReplays.dataChecksum(layered));
	}

	@Test
	void replayGraphicsFollowTheOptions() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		assertEquals(GraphicsSet.CLASSIC, GameReplays.playbackGraphics(installed(), ini, "Maps/Test.w3x"));
		GraphicsSettings.apply(ini, GraphicsSet.DE, 0);
		assertEquals(GraphicsSet.DE, GameReplays.playbackGraphics(installed(), ini, "Maps/Test.w3x"));
		GraphicsSettings.apply(ini, GraphicsSet.REFORGED, 0);
		assertEquals(GraphicsSet.REFORGED, GameReplays.playbackGraphics(installed(), ini, "Maps/Test.w3x"));
	}

	@Test
	void gameGraphicsRules() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.DE, 0);
		assertEquals(GraphicsSet.DE, GraphicsSettings.gameGraphics(ini, "Maps/Test.w3x", false, false));
		assertEquals(GraphicsSet.DE, GraphicsSettings.gameGraphics(ini, "Maps/Test.w3x", false, true));
		assertEquals(GraphicsSet.CLASSIC, GraphicsSettings.gameGraphics(ini, "Campaign/Classic/M1.w3x", false, false));
		assertEquals(GraphicsSet.REFORGED, GraphicsSettings.gameGraphics(ini, "Campaign/Classic/M1.w3x", true, false));
		final DataTable classic = new DataTable(StringBundle.EMPTY);
		assertEquals(GraphicsSet.REFORGED, GraphicsSettings.gameGraphics(classic, "Maps/Test.w3x", false, true));
	}

	private static DataSource installed() {
		final Map<String, String> files = new HashMap<>();
		files.put("units\\human\\footman\\footman.mdx", "classic");
		files.put("_hd.w3mod\\units\\human\\footman\\footman.mdx", "hd");
		files.put("_de.w3mod\\units\\human\\footman\\footman.mdx", "de");
		files.put(SKIN.toLowerCase(Locale.ROOT), "classic skin");
		files.put("_hd.w3mod\\" + SKIN.toLowerCase(Locale.ROOT), "hd skin");
		files.put("_de.w3mod\\" + SKIN.toLowerCase(Locale.ROOT), "de skin");
		return new Fake(files);
	}

	private static final class Fake implements DataSource {
		private final Map<String, String> files;

		Fake(final Map<String, String> files) {
			this.files = files;
		}

		private String find(final String path) {
			return path == null ? null : this.files.get(path.replace('/', '\\').toLowerCase(Locale.ROOT));
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			final String content = find(filepath);
			return content == null ? null : new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			final String content = find(path);
			return content == null ? null : ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public boolean has(final String filepath) {
			return find(filepath) != null;
		}

		@Override
		public Collection<String> getListfile() {
			return this.files.keySet();
		}

		@Override
		public void close() {
		}
	}
}
