package com.etheller.warsmash.datasources;

/**
 * Small whole numbers remembered for paths, such as which source has a file. The paths asked for run into the
 * hundreds of thousands (every prefix, extension and alias of a name is asked on its own), and a map of strings and
 * boxed numbers took about a hundred bytes for each of them. This keeps a 64 bit hash of the path and the number, twelve
 * bytes per entry, and not the path. Two different paths are taken as one only when all 64 bits of their hashes agree,
 * which is not to be expected before the number of paths runs into the billions.
 */
final class PathCache {
	private static final int SEGMENT_BITS = 4;
	private static final int SEGMENTS = 1 << SEGMENT_BITS;
	private static final int INITIAL_CAPACITY = 16;

	private final Segment[] segments = new Segment[SEGMENTS];

	PathCache() {
		for (int i = 0; i < SEGMENTS; i++) {
			this.segments[i] = new Segment();
		}
	}

	/** @return the number remembered for the path, or the given absent value */
	int get(final String path, final int absent) {
		final long hash = hash(path);
		return this.segments[segment(hash)].get(hash, absent);
	}

	void put(final String path, final int value) {
		final long hash = hash(path);
		this.segments[segment(hash)].put(hash, value);
	}

	void clear() {
		for (final Segment segment : this.segments) {
			segment.clear();
		}
	}

	int size() {
		int size = 0;
		for (final Segment segment : this.segments) {
			size += segment.size();
		}
		return size;
	}

	private static int segment(final long hash) {
		return (int) (hash >>> (64 - SEGMENT_BITS));
	}

	/** Never 0, which marks a free place. */
	static long hash(final String path) {
		long h = 0xcbf29ce484222325L;
		final int length = path.length();
		for (int i = 0; i < length; i++) {
			h = (h ^ path.charAt(i)) * 0x100000001b3L;
		}
		h = (h ^ length) * 0x100000001b3L;
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return h == 0 ? 1 : h;
	}

	private static final class Segment {
		private long[] keys = new long[INITIAL_CAPACITY];
		private int[] values = new int[INITIAL_CAPACITY];
		private int size;

		synchronized int get(final long hash, final int absent) {
			final int mask = this.keys.length - 1;
			int slot = (int) hash & mask;
			while (true) {
				final long key = this.keys[slot];
				if (key == hash) {
					return this.values[slot];
				}
				if (key == 0) {
					return absent;
				}
				slot = (slot + 1) & mask;
			}
		}

		synchronized void put(final long hash, final int value) {
			if (((this.size + 1) * 4) > (this.keys.length * 3)) {
				grow();
			}
			final int mask = this.keys.length - 1;
			int slot = (int) hash & mask;
			while (true) {
				final long key = this.keys[slot];
				if (key == hash) {
					this.values[slot] = value;
					return;
				}
				if (key == 0) {
					this.keys[slot] = hash;
					this.values[slot] = value;
					this.size++;
					return;
				}
				slot = (slot + 1) & mask;
			}
		}

		private void grow() {
			final long[] oldKeys = this.keys;
			final int[] oldValues = this.values;
			final int capacity = oldKeys.length << 1;
			this.keys = new long[capacity];
			this.values = new int[capacity];
			final int mask = capacity - 1;
			for (int i = 0; i < oldKeys.length; i++) {
				if (oldKeys[i] != 0) {
					int slot = (int) oldKeys[i] & mask;
					while (this.keys[slot] != 0) {
						slot = (slot + 1) & mask;
					}
					this.keys[slot] = oldKeys[i];
					this.values[slot] = oldValues[i];
				}
			}
		}

		synchronized void clear() {
			this.keys = new long[INITIAL_CAPACITY];
			this.values = new int[INITIAL_CAPACITY];
			this.size = 0;
		}

		synchronized int size() {
			return this.size;
		}
	}
}
