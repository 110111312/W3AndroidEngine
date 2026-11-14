package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Paths are named for the storage in one pass; every path must come out as the three string passes it replaced made it.
 */
class CascDataSourceTest {
	private static final String[] PATHS = { "", "a", "A", "units\\human\\footman\\footman.mdx",
			"Units/Human/Footman/Footman.MDX", "UI:Glues:MainMenu.fdf", "ui/FrameDef\\mixed:Separators.toc",
			"ReplaceableTextures\\CommandButtons\\BTNFootman.blp", "war3map.j", "Scripts\\Common.J", "\\leading",
			"trailing\\", "//", "::", "Zz@[`{", "İstanbul\\Dotted.mdx", "UI\\Ärger.blp",
			"K\\Kelvin.mdx", "café/Menu:Item", "dir\\😀.blp" };

	@Test
	void normalisedPathsAreTheOnesTheOldChainMade() {
		for (final String path : PATHS) {
			final String expected = path.toLowerCase(Locale.US).replace('/', '\\').replace(':', '\\');
			assertEquals(expected, CascDataSource.normalizePath(path), path);
		}
	}

	@Test
	void anAlreadyNormalPathIsNotCopied() {
		final String path = "units\\human\\footman\\footman.mdx";
		assertSame(path, CascDataSource.normalizePath(path));
	}
}
