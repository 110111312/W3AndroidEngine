package com.etheller.warsmash.viewer5.gl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class UniformCacheTest {
	@Test
	public void sameIntIsSkippedAfterTheFirstSend() {
		final UniformCache cache = new UniformCache();
		assertTrue(cache.changed(3, 0));
		assertFalse(cache.changed(3, 0));
		assertTrue(cache.changed(3, 1));
		assertFalse(cache.changed(3, 1));
		assertTrue(cache.changed(4, 1));
	}

	@Test
	public void floatsCompareEveryComponent() {
		final UniformCache cache = new UniformCache();
		assertTrue(cache.changed(1, 0f));
		assertFalse(cache.changed(1, 0f));
		assertTrue(cache.changed(1, -0f));
		assertTrue(cache.changed(2, 1f, 2f));
		assertFalse(cache.changed(2, 1f, 2f));
		assertTrue(cache.changed(2, 1f, 3f));
		assertTrue(cache.changed(5, 1f, 2f, 3f));
		assertFalse(cache.changed(5, 1f, 2f, 3f));
		assertTrue(cache.changed(5, 1f, 2f, 4f));
		assertTrue(cache.changed(6, 1f, 2f, 3f, 4f));
		assertFalse(cache.changed(6, 1f, 2f, 3f, 4f));
		assertTrue(cache.changed(6, 0f, 2f, 3f, 4f));
	}

	@Test
	public void arraysCompareOnlyTheRequestedRange() {
		final UniformCache cache = new UniformCache();
		final float[] values = { 9, 1, 2, 3, 4, 9 };
		assertTrue(cache.changed(7, values, 1, 4));
		assertFalse(cache.changed(7, values, 1, 4));
		values[0] = 5;
		values[5] = 5;
		assertFalse(cache.changed(7, values, 1, 4));
		values[4] = 8;
		assertTrue(cache.changed(7, values, 1, 4));
		assertFalse(cache.changed(7, values, 1, 4));
		assertTrue(cache.changed(7, values, 1, 2));
	}

	@Test
	public void nanIsSkippedOnlyWhenItsBitsMatch() {
		final UniformCache cache = new UniformCache();
		assertTrue(cache.changed(1, Float.NaN));
		assertFalse(cache.changed(1, Float.NaN));
	}

	@Test
	public void clearForgetsEverything() {
		final UniformCache cache = new UniformCache();
		cache.changed(1, 5);
		cache.changed(2, 5f);
		cache.changed(3, new float[] { 1, 2 }, 0, 2);
		cache.clear();
		assertTrue(cache.changed(1, 5));
		assertTrue(cache.changed(2, 5f));
		assertTrue(cache.changed(3, new float[] { 1, 2 }, 0, 2));
	}

	@Test
	public void forgetDropsOneLocation() {
		final UniformCache cache = new UniformCache();
		cache.changed(1, 5f);
		cache.changed(2, 5f);
		cache.forget(1);
		assertTrue(cache.changed(1, 5f));
		assertFalse(cache.changed(2, 5f));
	}

	@Test
	public void missingLocationIsNeverSent() {
		final UniformCache cache = new UniformCache();
		assertFalse(cache.changed(-1, 1));
		assertFalse(cache.changed(-1, 1f));
		assertFalse(cache.changed(-1, new float[] { 1 }, 0, 1));
	}

	@Test
	public void longArraysAreAlwaysSent() {
		final UniformCache cache = new UniformCache();
		final float[] values = new float[UniformCache.MAX_CACHED_LENGTH + 1];
		assertTrue(cache.changed(1, values, 0, values.length));
		assertTrue(cache.changed(1, values, 0, values.length));
	}

	@Test
	public void locationsBeyondTheInitialCapacityGrowTheCache() {
		final UniformCache cache = new UniformCache();
		assertTrue(cache.changed(500, 1));
		assertFalse(cache.changed(500, 1));
		assertTrue(cache.changed(900, 1f));
		assertFalse(cache.changed(900, 1f));
		assertFalse(cache.changed(500, 1));
	}
}
