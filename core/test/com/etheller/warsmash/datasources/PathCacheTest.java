package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class PathCacheTest {
	@Test
	void givesBackWhatWasPutAndTheAbsentValueForOtherPaths() {
		final PathCache cache = new PathCache();
		assertEquals(-7, cache.get("units\\a.mdx", -7));
		cache.put("units\\a.mdx", 3);
		cache.put("Units\\a.mdx", 4);
		cache.put("", 5);
		assertEquals(3, cache.get("units\\a.mdx", -7));
		assertEquals(4, cache.get("Units\\a.mdx", -7));
		assertEquals(5, cache.get("", -7));
		cache.put("units\\a.mdx", -1);
		assertEquals(-1, cache.get("units\\a.mdx", -7));
		assertEquals(3, cache.size());
		cache.clear();
		assertEquals(0, cache.size());
		assertEquals(-7, cache.get("units\\a.mdx", -7));
	}

	@Test
	void agreesWithAMapOverManyPathsAndGrowth() {
		final PathCache cache = new PathCache();
		final Map<String, Integer> expected = new HashMap<>();
		final Random random = new Random(11);
		for (int i = 0; i < 300000; i++) {
			final String path = "war3.w3mod\\" + Integer.toString(random.nextInt(1 << 22), 36) + ".mdx";
			final int value = random.nextInt(9) - 1;
			cache.put(path, value);
			expected.put(path, value);
		}
		assertEquals(expected.size(), cache.size());
		for (final Map.Entry<String, Integer> entry : expected.entrySet()) {
			assertEquals(entry.getValue().intValue(), cache.get(entry.getKey(), -99), entry.getKey());
		}
		assertEquals(-99, cache.get("war3.w3mod\\not-there.mdx", -99));
	}

	@Test
	void hashesOfSimilarPathsDiffer() {
		assertNotEquals(PathCache.hash("a\\b"), PathCache.hash("a\\c"));
		assertNotEquals(PathCache.hash("ab"), PathCache.hash("ba"));
		assertNotEquals(PathCache.hash(""), 0L);
	}
}
