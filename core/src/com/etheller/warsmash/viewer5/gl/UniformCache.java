package com.etheller.warsmash.viewer5.gl;

import java.util.Arrays;

/**
 * Remembers the last value sent for each uniform location of one program, so an unchanged value is not sent again.
 * Values are compared by their bits, so only an identical value is skipped. The methods return whether the value
 * must be sent; a negative location (an optimised-out uniform) never must.
 */
public final class UniformCache {
	/** Longer arrays cost more to compare than the call they would save. */
	public static final int MAX_CACHED_LENGTH = 16;

	private float[][] floats = new float[32][];
	private boolean[] floatKnown = new boolean[32];
	private int[] ints = new int[32];
	private boolean[] intKnown = new boolean[32];

	public void clear() {
		Arrays.fill(this.floatKnown, false);
		Arrays.fill(this.intKnown, false);
	}

	public boolean changed(final int location, final int value) {
		if (location < 0) {
			return false;
		}
		if (location >= this.ints.length) {
			grow(location);
		}
		final boolean known = this.intKnown[location];
		this.intKnown[location] = true;
		if (known && (this.ints[location] == value)) {
			return false;
		}
		this.ints[location] = value;
		return true;
	}

	public boolean changed(final int location, final float x) {
		final float[] slot = slot(location, 1);
		if (slot == null) {
			return false;
		}
		final boolean known = markKnown(location);
		if (known && same(slot, 0, x)) {
			return false;
		}
		slot[0] = x;
		return true;
	}

	public boolean changed(final int location, final float x, final float y) {
		final float[] slot = slot(location, 2);
		if (slot == null) {
			return false;
		}
		final boolean known = markKnown(location);
		if (known && same(slot, 0, x) && same(slot, 1, y)) {
			return false;
		}
		slot[0] = x;
		slot[1] = y;
		return true;
	}

	public boolean changed(final int location, final float x, final float y, final float z) {
		final float[] slot = slot(location, 3);
		if (slot == null) {
			return false;
		}
		final boolean known = markKnown(location);
		if (known && same(slot, 0, x) && same(slot, 1, y) && same(slot, 2, z)) {
			return false;
		}
		slot[0] = x;
		slot[1] = y;
		slot[2] = z;
		return true;
	}

	public boolean changed(final int location, final float x, final float y, final float z, final float w) {
		final float[] slot = slot(location, 4);
		if (slot == null) {
			return false;
		}
		final boolean known = markKnown(location);
		if (known && same(slot, 0, x) && same(slot, 1, y) && same(slot, 2, z) && same(slot, 3, w)) {
			return false;
		}
		slot[0] = x;
		slot[1] = y;
		slot[2] = z;
		slot[3] = w;
		return true;
	}

	public boolean changed(final int location, final float[] values, final int offset, final int length) {
		if (location < 0) {
			return false;
		}
		if (length > MAX_CACHED_LENGTH) {
			forget(location);
			return true;
		}
		final float[] slot = slot(location, length);
		final boolean known = markKnown(location);
		if (known) {
			boolean differs = false;
			for (int i = 0; i < length; i++) {
				if (!same(slot, i, values[offset + i])) {
					differs = true;
					break;
				}
			}
			if (!differs) {
				return false;
			}
		}
		System.arraycopy(values, offset, slot, 0, length);
		return true;
	}

	/** For values written some way that bypasses the cache. */
	public void forget(final int location) {
		if (location >= 0) {
			if (location < this.floatKnown.length) {
				this.floatKnown[location] = false;
			}
			if (location < this.intKnown.length) {
				this.intKnown[location] = false;
			}
		}
	}

	private static boolean same(final float[] slot, final int index, final float value) {
		return Float.floatToRawIntBits(slot[index]) == Float.floatToRawIntBits(value);
	}

	private boolean markKnown(final int location) {
		final boolean known = this.floatKnown[location];
		this.floatKnown[location] = true;
		return known;
	}

	private float[] slot(final int location, final int length) {
		if (location < 0) {
			return null;
		}
		if (location >= this.floats.length) {
			grow(location);
		}
		float[] slot = this.floats[location];
		if ((slot == null) || (slot.length != length)) {
			slot = new float[length];
			this.floats[location] = slot;
			this.floatKnown[location] = false;
		}
		return slot;
	}

	private void grow(final int location) {
		final int size = Math.max(location + 1, this.floats.length * 2);
		this.floats = Arrays.copyOf(this.floats, size);
		this.floatKnown = Arrays.copyOf(this.floatKnown, size);
		this.ints = Arrays.copyOf(this.ints, size);
		this.intKnown = Arrays.copyOf(this.intKnown, size);
	}
}
