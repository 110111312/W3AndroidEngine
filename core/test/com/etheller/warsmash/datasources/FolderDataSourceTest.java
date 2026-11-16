package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderDataSourceTest {
	@TempDir
	File temp;

	@Test
	void aFileAddedRightAfterTheFolderWasListedIsListed() throws Exception {
		for (int i = 0; i < 200; i++) {
			final File folder = new File(this.temp, "f" + i);
			assertTrue(folder.mkdirs());
			Files.write(new File(folder, "a.txt").toPath(), new byte[] { 1 });
			final FolderDataSource source = new FolderDataSource(folder.toPath());
			assertTrue(source.getListfile().contains("a.txt"));
			Files.write(new File(folder, "b.txt").toPath(), new byte[] { 2 });
			assertTrue(source.getListfile().contains("b.txt"), "round " + i);
		}
	}
}
