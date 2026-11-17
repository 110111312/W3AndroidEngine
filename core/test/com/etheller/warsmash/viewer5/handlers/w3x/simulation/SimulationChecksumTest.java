package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum.RowCache;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.SimulationChecksum.RowEmitter;

/**
 * Blocks hashed apart and joined must give the checksum of their bytes hashed in one go, or kept blocks would change
 * the checksum lines.
 */
class SimulationChecksumTest {
	@Test
	void joinedBlocksHashAsOne() {
		final Random random = new Random(7);
		for (final int[] lengths : new int[][] { { 1, 1 }, { 0, 5 }, { 5, 0 }, { 37, 64 }, { 4096, 3 },
				{ 65536, 70000 }, { 1, 2, 3, 400, 5000 } }) {
			int joined = 0;
			final CRC32 whole = new CRC32();
			for (final int length : lengths) {
				final byte[] bytes = new byte[length];
				random.nextBytes(bytes);
				final CRC32 part = new CRC32();
				part.update(bytes);
				whole.update(bytes);
				joined = SimulationChecksum.combine(joined, (int) part.getValue(),
						SimulationChecksum.crcShift(length));
			}
			assertEquals((int) whole.getValue(), joined);
		}
	}

	@Test
	void ownCrcIsTheStandardOne() {
		final Random random = new Random(11);
		for (final int length : new int[] { 0, 1, 7, 255, 256, 1000 }) {
			final byte[] bytes = new byte[length + 3];
			random.nextBytes(bytes);
			final CRC32 crc = new CRC32();
			crc.update(bytes, 0, length);
			assertEquals((int) crc.getValue(), SimulationChecksum.crc32(bytes, length));
		}
	}

	private static final String[] CODES = { "-", "?", "Move", "Attack", "Harvest", "Build" };
	private static final int FIELDS = 24;

	/** A row of the shape a unit's is: fixed fields, then lists whose length and names come from the values. */
	private static final class Rows implements RowEmitter {
		final List<int[]> state = new ArrayList<>();

		@Override
		public void emit(final SimulationChecksum checksum, final Object key) {
			final int[] s = this.state.get((Integer) key);
			checksum.begin(0, "unit", s[0]).id("type", s[1] == 0 ? null : new War3ID(s[1])).i("owner", s[2])
					.f("x", Float.intBitsToFloat(s[3])).f("y", Float.intBitsToFloat(s[4])).b("dead", s[5] != 0)
					.s("behavior", CODES[s[6] % CODES.length]);
			if ((s[7] & 1) != 0) {
				checksum.i("level", s[8]).i("xp", s[9]);
			}
			for (int slot = 0; slot < (s[10] % 7); slot++) {
				checksum.name("slot", slot, "").value(s[11 + slot]);
			}
			for (int ability = 0; ability < (s[18] % 4); ability++) {
				checksum.name("a", s[19 + ability], "").value(new War3ID(s[19 + ability] * 31));
				checksum.name("a", s[19 + ability], ".left").value(Float.intBitsToFloat(s[22]));
				checksum.name("a", s[19 + ability], ".disabled").value(s[23] != 0);
			}
		}
	}

	/** The bits of a float between 1 and 2, any of them: a value that differs in any bit is a different value. */
	private static int someFloat(final Random random) {
		return 0x3f800000 | (random.nextInt(8) == 0 ? 0 : random.nextInt(1 << 23));
	}

	private static int[] row(final Random random, final int handle) {
		final int[] s = new int[FIELDS];
		for (int i = 0; i < FIELDS; i++) {
			s[i] = random.nextInt(8);
		}
		s[0] = handle;
		s[3] = someFloat(random);
		s[4] = someFloat(random);
		s[22] = someFloat(random);
		return s;
	}

	private static int[] hashed(final Rows rows, final RowCache cache, final Object[] keys) throws IOException {
		final SimulationChecksum checksum = new SimulationChecksum();
		if (cache == null) {
			for (final Object key : keys) {
				checksum.plainRow(key, rows);
			}
		}
		else {
			checksum.hashRows(cache, keys, rows);
		}
		return new int[] { checksum.checksumSoFar(), checksum.crcs[0] };
	}

	/** Rows kept from one checksum to the next must never stand for a row that changed in any value it is made of. */
	@Test
	void keptRowsGiveTheChecksumOfRowsHashedAgain() throws IOException {
		for (int seed = 0; seed < 30; seed++) {
			final Random random = new Random(seed);
			final Rows rows = new Rows();
			final RowCache cache = new RowCache();
			final int count = random.nextBoolean() ? 20 : 150;
			for (int i = 0; i < count; i++) {
				rows.state.add(row(random, 1000 + i));
			}
			// how many rows a round changes: none, a few, or nearly all (which makes the cache skip some rounds)
			final int[] changed = { 0, 1, 3, count / 2, count };
			for (int round = 0; round < 40; round++) {
				final int changes = changed[random.nextInt(changed.length)];
				for (int change = 0; change < changes; change++) {
					final int[] s = rows.state.get(random.nextInt(rows.state.size()));
					final int field = 1 + random.nextInt(FIELDS - 1);
					s[field] = (field == 3) || (field == 4) || (field == 22) ? someFloat(random) : random.nextInt(8);
				}
				if (random.nextInt(6) == 0) {
					rows.state.add(row(random, 5000 + (round * 10)));
				}
				final Object[] keys = new Object[rows.state.size()];
				for (int i = 0; i < keys.length; i++) {
					keys[i] = i;
				}
				final int[] expected = hashed(rows, null, keys);
				final int[] kept = hashed(rows, cache, keys);
				assertEquals(expected[0], kept[0], "total, seed " + seed + " round " + round);
				assertEquals(expected[1], kept[1], "units, seed " + seed + " round " + round);
			}
		}
	}

	@Test
	void unchangedRowsAreNotWrittenAgain() throws IOException {
		final Random random = new Random(5);
		final Rows rows = new Rows();
		final RowCache cache = new RowCache();
		final Object[] keys = new Object[100];
		for (int i = 0; i < keys.length; i++) {
			rows.state.add(row(random, i));
			keys[i] = i;
		}
		hashed(rows, cache, keys);
		assertEquals(100, cache.misses);
		hashed(rows, cache, keys);
		assertEquals(100, cache.hits);
		rows.state.get(40)[8] += 1;
		rows.state.get(40)[7] |= 1;
		hashed(rows, cache, keys);
		assertEquals(99, cache.hits);
		assertEquals(1, cache.misses);
	}
}
