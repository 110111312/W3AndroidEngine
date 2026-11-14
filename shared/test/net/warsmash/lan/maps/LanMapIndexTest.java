package net.warsmash.lan.maps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.warsmash.lan.protocol.LanData;

class LanMapIndexTest {
	@TempDir
	Path tmp;

	private File dir(final String name) {
		final File d = this.tmp.resolve(name).toFile();
		d.mkdirs();
		return d;
	}

	@Test
	void findsTheSameContentUnderAnotherNameAndInSubfolders() throws IOException {
		final File maps = dir("Maps");
		final File original = MapTestFiles.writeMap(new File(maps, "Pack/Deep/Renamed.w3x"), 70000, 3);
		MapTestFiles.writeMap(new File(maps, "Other.w3x"), 70000, 4);
		final LanMapIndex index = new LanMapIndex(null, dir("Download")).addRoot(maps);
		final LanData.MapInfo wanted = MapHash.describe(original, MapHash.compute(original));
		wanted.name = "TheirName.w3x";
		assertEquals(original, index.find(wanted));
	}

	@Test
	void sameSizeDifferentContentIsNotFound() throws IOException {
		final File maps = dir("Maps");
		final File a = MapTestFiles.writeMap(new File(maps, "A.w3x"), 70000, 3);
		final File elsewhere = MapTestFiles.writeMap(this.tmp.resolve("Elsewhere.w3x").toFile(), 70000, 9);
		final LanMapIndex index = new LanMapIndex(null, dir("Download")).addRoot(maps);
		final LanData.MapInfo wanted = MapHash.describe(elsewhere, MapHash.compute(elsewhere));
		assertNull(index.find(wanted));
		assertTrue(a.exists());
	}

	@Test
	void searchesTheDownloadFolderToo() throws IOException {
		final File download = dir("Download");
		final File got = MapTestFiles.writeMap(new File(download, "Got.w3x"), 70000, 5);
		final LanMapIndex index = new LanMapIndex(null, download);
		assertEquals(got, index.find(MapHash.describe(got, MapHash.compute(got))));
	}

	@Test
	void hashesAreCachedByPathSizeAndModificationTime() throws IOException {
		final File maps = dir("Maps");
		final File f = MapTestFiles.writeMap(new File(maps, "A.w3x"), 70000, 3);
		final File cache = this.tmp.resolve("cache/hashes.txt").toFile();
		final LanData.MapInfo wanted = MapHash.describe(f, MapHash.compute(f));

		final LanMapIndex first = new LanMapIndex(cache, dir("Download")).addRoot(maps);
		assertEquals(f, first.find(wanted));
		assertEquals(1, first.hashesComputed());
		assertEquals(f, first.find(wanted));
		assertEquals(1, first.hashesComputed());
		assertTrue(cache.isFile());

		final LanMapIndex second = new LanMapIndex(cache, dir("Download")).addRoot(maps);
		assertEquals(f, second.find(wanted));
		assertEquals(0, second.hashesComputed(), "a new run reads the cache file");

		final long before = f.lastModified();
		assertTrue(f.setLastModified(before + 5000));
		final LanMapIndex third = new LanMapIndex(cache, dir("Download")).addRoot(maps);
		assertEquals(f, third.find(wanted));
		assertEquals(1, third.hashesComputed(), "a changed modification time means hashing again");
	}

	@Test
	void findsAMapThatIsOnlyInTheGameData() throws IOException {
		final File data = dir("Data");
		final File inArchive = MapTestFiles.writeMap(new File(data, "Melee.w3x"), 70000, 5);
		final File other = MapTestFiles.writeMap(new File(data, "Other.w3x"), 70000, 6);
		final LanData.MapInfo wanted = MapHash.describe(inArchive, MapHash.compute(inArchive));
		wanted.name = "Melee.w3x";
		final long[] stamp = { 1 };
		final LanMapIndex.VirtualMaps virtual = new LanMapIndex.VirtualMaps() {
			@Override
			public java.util.List<String> pathsNamed(final String fileName) {
				final java.util.List<String> paths = new java.util.ArrayList<String>();
				paths.add("Maps\\Other\\" + fileName);
				paths.add("Maps\\Melee\\" + fileName);
				return paths;
			}

			@Override
			public File extract(final String path) {
				return path.contains("Other") ? other : inArchive;
			}

			@Override
			public long stamp() {
				return stamp[0];
			}
		};
		final File cache = this.tmp.resolve("virtual.txt").toFile();
		final LanMapIndex index = new LanMapIndex(cache, dir("Download")).setVirtualMaps(virtual);
		assertEquals(inArchive, index.find(wanted), "same name, other content, is told apart by the hash");
		assertEquals(2, index.hashesComputed());
		assertEquals(inArchive, index.find(wanted));
		assertEquals(2, index.hashesComputed(), "remembered");

		final LanMapIndex again = new LanMapIndex(cache, dir("Download")).setVirtualMaps(virtual);
		assertEquals(inArchive, again.find(wanted));
		assertEquals(0, again.hashesComputed(), "a new run reads the cache file");
		stamp[0] = 2;
		assertEquals(inArchive, again.find(wanted));
		assertEquals(2, again.hashesComputed(), "other game data, hashed again");
		assertNull(new LanMapIndex(null, dir("Download")).find(wanted), "without the game data it is not there");
	}

	@Test
	void changedContentIsNoLongerTheMap() throws IOException {
		final File maps = dir("Maps");
		final File f = MapTestFiles.writeMap(new File(maps, "A.w3x"), 70000, 3);
		final LanData.MapInfo wanted = MapHash.describe(f, MapHash.compute(f));
		final LanMapIndex index = new LanMapIndex(null, dir("Download")).addRoot(maps);
		assertEquals(f, index.find(wanted));
		final long modified = f.lastModified();
		MapTestFiles.writeCorruptedCopy(f, f);
		assertTrue(f.setLastModified(modified + 2000));
		assertNull(index.find(wanted));
	}

	@Test
	void damagedCacheFileIsIgnored() throws IOException {
		final File maps = dir("Maps");
		final File f = MapTestFiles.writeMap(new File(maps, "A.w3x"), 70000, 3);
		final File cache = this.tmp.resolve("hashes.txt").toFile();
		Files.write(cache.toPath(), "garbage\n\t\t\t\t\nx\t1\t2\tnothex\t4\n".getBytes("UTF-8"));
		final LanMapIndex index = new LanMapIndex(cache, dir("Download")).addRoot(maps);
		assertEquals(f, index.find(MapHash.describe(f, MapHash.compute(f))));
	}

	@Test
	void nothingToFindWithoutAHash() throws IOException {
		final LanData.MapInfo none = new LanData.MapInfo();
		none.name = "A.w3x";
		none.size = 100;
		assertNull(new LanMapIndex(null, dir("Download")).find(none));
	}

	@Test
	void cleansOnlyOldPartFiles() throws IOException {
		final File download = dir("Download");
		final File old = new File(download, "Old.w3x.deadbeef.part");
		final File fresh = new File(download, "Fresh.w3x.deadbeef.part");
		final File keep = new File(download, "Keep.w3x");
		for (final File f : new File[] { old, fresh, keep }) {
			assertTrue(f.createNewFile());
		}
		assertTrue(old.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000));
		assertTrue(keep.setLastModified(System.currentTimeMillis() - 10L * 24 * 3600 * 1000));
		new LanMapIndex(null, download).cleanStaleParts();
		assertFalse(old.exists());
		assertTrue(fresh.exists());
		assertTrue(keep.exists());
	}
}
