package com.etheller.warsmash.viewer5.handlers.mdx;

import java.util.Arrays;
import java.util.HashMap;

/**
 * One copy of equal key arrays while a model loads: the timelines of a model mostly have their keys at the same
 * frames, and many hold the same values.
 */
final class ArrayPool {
	private final HashMap<IntKey, int[]> ints = new HashMap<>();
	private final HashMap<FloatKey, float[]> floats = new HashMap<>();

	int[] intern(final int[] array) {
		final int[] existing = this.ints.putIfAbsent(new IntKey(array), array);
		return existing == null ? array : existing;
	}

	float[] intern(final float[] array) {
		if (array == null) {
			return null;
		}
		final float[] existing = this.floats.putIfAbsent(new FloatKey(array), array);
		return existing == null ? array : existing;
	}

	private static final class IntKey {
		private final int[] array;
		private final int hash;

		private IntKey(final int[] array) {
			this.array = array;
			this.hash = Arrays.hashCode(array);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(final Object other) {
			return (other instanceof IntKey) && Arrays.equals(this.array, ((IntKey) other).array);
		}
	}

	private static final class FloatKey {
		private final float[] array;
		private final int hash;

		private FloatKey(final float[] array) {
			this.array = array;
			this.hash = Arrays.hashCode(array);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(final Object other) {
			return (other instanceof FloatKey) && Arrays.equals(this.array, ((FloatKey) other).array);
		}
	}
}
