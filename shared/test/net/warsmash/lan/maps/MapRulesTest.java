package net.warsmash.lan.maps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.warsmash.lan.protocol.LanData;

class MapRulesTest {
	@TempDir
	Path tmp;

	@Test
	void acceptsPlainMapNames() {
		assertNull(MapRules.checkName("(2)LostTemple.w3x"));
		assertNull(MapRules.checkName("Twisted Meadows.W3M"));
		assertNull(MapRules.checkName("Mapa su ąčę.w3x"));
	}

	@Test
	void rejectsNamesThatCouldLeaveTheDirectory() {
		final String[] bad = { "", "..\\evil.w3x", "../evil.w3x", "sub/dir.w3x", "sub\\dir.w3x", "C:\\evil.w3x",
				"evil.w3x:stream", "a..b.w3x", ".hidden.w3x", " lead.w3x", "trail.w3x ", "dot.w3x.", "evil.exe",
				"evil.w3x.exe", "noext", "con.w3x", "NUL.w3m", "star*.w3x", "ctl\u0001.w3x", "x\u007f.w3x",
				"long" + "x".repeat(70) + ".w3x" };
		for (final String n : bad) {
			assertNotNull(MapRules.checkName(n), "should be refused: " + n);
		}
		assertNotNull(MapRules.checkName(null));
	}

	@Test
	void checksSizeAndHashOfAnnouncement() {
		final LanData.MapInfo m = new LanData.MapInfo();
		m.name = "A.w3x";
		m.size = 1000;
		m.sha256 = new byte[32];
		assertNull(MapRules.checkInfo(m));
		m.size = 0;
		assertNotNull(MapRules.checkInfo(m));
		m.size = MapRules.MAX_SIZE + 1;
		assertNotNull(MapRules.checkInfo(m));
		m.size = MapRules.MAX_SIZE;
		assertNull(MapRules.checkInfo(m));
		m.sha256 = new byte[0];
		assertNotNull(MapRules.checkInfo(m));
	}

	private static byte[] mpq(final int at, final int headerSize) {
		final byte[] b = new byte[MapRules.HEADER_BYTES];
		b[at] = 'M';
		b[at + 1] = 'P';
		b[at + 2] = 'Q';
		b[at + 3] = 0x1A;
		b[at + 4] = (byte) headerSize;
		b[at + 5] = (byte) (headerSize >>> 8);
		return b;
	}

	@Test
	void checksTheMpqHeader() {
		assertNull(MapRules.checkHeader(mpq(0, 32), 1024));
		assertNull(MapRules.checkHeader(mpq(0, 44), 1024));
		final byte[] hm3w = mpq(512, 32);
		hm3w[0] = 'H';
		hm3w[1] = 'M';
		hm3w[2] = '3';
		hm3w[3] = 'W';
		assertNull(MapRules.checkHeader(hm3w, 1024));
		assertNotNull(MapRules.checkHeader(mpq(0, 0), 1024), "header size 0");
		assertNotNull(MapRules.checkHeader(mpq(0, 1 << 20), 1024), "header size huge");
		assertNotNull(MapRules.checkHeader(mpq(256, 32), 1024), "archive at a odd place");
		hm3w[512] = 'X';
		assertNotNull(MapRules.checkHeader(hm3w, 1024), "HM3W without archive");
		assertNotNull(MapRules.checkHeader(new byte[1024], 1024));
		assertNotNull(MapRules.checkHeader(new byte[2], 2));
		assertNotNull(MapRules.checkHeader(mpq(0, 32), 6), "too short to hold the header size");
		final byte[] pk = new byte[1024];
		pk[0] = 'P';
		pk[1] = 'K';
		assertNotNull(MapRules.checkHeader(pk, 1024), "a zip is not a map");
	}

	@Test
	void describeRefusesFilesThatMayNotBeShared() throws IOException {
		final File good = MapTestFiles.writeMap(this.tmp.resolve("Good.w3x").toFile(), 5000, 1);
		final LanData.MapInfo info = MapHash.describe(good, MapHash.compute(good));
		assertEquals("Good.w3x", info.name);
		assertEquals(5000, info.size);
		assertEquals(32, info.sha256.length);

		final File notMap = this.tmp.resolve("Text.w3x").toFile();
		Files.write(notMap.toPath(), "hello, this is not an archive".getBytes("UTF-8"));
		assertThrows(IOException.class, () -> MapHash.describe(notMap, MapHash.compute(notMap)));

		final File wrongName = this.tmp.resolve("Map.txt").toFile();
		Files.copy(good.toPath(), wrongName.toPath());
		assertThrows(IOException.class, () -> MapHash.describe(wrongName, MapHash.compute(wrongName)));

		final File empty = this.tmp.resolve("Empty.w3x").toFile();
		assertEquals(true, empty.createNewFile());
		assertThrows(IOException.class, () -> MapHash.describe(empty, MapHash.compute(empty)));
	}

	@Test
	void hashesMatchTheStandardAlgorithms() throws Exception {
		final File f = this.tmp.resolve("Hash.w3x").toFile();
		Files.write(f.toPath(), "abc".getBytes("UTF-8"));
		final MapHash.Result r = MapHash.compute(f);
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", MapHash.hex(r.sha256));
		assertEquals(0x352441C2, r.crc32);
		assertEquals(3, r.size);
		assertNull(MapHash.fromHex("zz"));
		assertNull(MapHash.fromHex("abc"));
	}
}
