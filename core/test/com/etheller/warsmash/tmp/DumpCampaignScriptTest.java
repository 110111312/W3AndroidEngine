package com.etheller.warsmash.tmp;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.MpqDataSource;

import mpq.MPQArchive;

/** Throwaway: which gold mines each Night Elf campaign map hands the player, and whether it hands over wisps. */
class DumpCampaignScriptTest {
	private static final File BASE = new File("C:/W/W3AndroidEngine/wc3/Files_1.29.2/Warcraft III");
	private static final File OUT = new File("C:/Users/Admin/AppData/Local/Temp/w3shots/campaign.txt");

	@Test
	void dump() throws Exception {
		final PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUT.toPath(), StandardCharsets.UTF_8));
		try (SeekableByteChannel channel = Files.newByteChannel(new File(BASE, "War3xLocal.mpq").toPath())) {
			final MpqDataSource source = new MpqDataSource(new MPQArchive(channel), channel);
			final Collection<String> listfile = source.getListfile();
			for (final String path : listfile) {
				if (!path.startsWith("Maps\\FrozenThrone\\Campaign\\NightElf") || !path.endsWith(".w3x")) {
					continue;
				}
				out.println("=== " + path.substring(path.lastIndexOf('\\') + 1));
				final File map = File.createTempFile("map", ".w3x");
				try (InputStream in = source.getResourceAsStream(path)) {
					Files.copy(in, map.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
				try (SeekableByteChannel mapChannel = Files.newByteChannel(map.toPath())) {
					final MpqDataSource mapSource = new MpqDataSource(new MPQArchive(mapChannel), mapChannel);
					final InputStream script = mapSource.getResourceAsStream("war3map.j");
					if (script == null) {
						out.println("  (no war3map.j)");
						continue;
					}
					int lineNumber = 0;
					for (final String line : new String(script.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
						lineNumber++;
						if (line.contains("'egol'") || line.contains("'ngol'") || line.contains("'ewsp'")) {
							out.println("  " + lineNumber + ": " + line.trim());
						}
					}
				}
				map.delete();
			}
		}
		finally {
			out.close();
		}
	}
}
