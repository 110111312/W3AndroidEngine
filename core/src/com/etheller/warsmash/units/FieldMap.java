package com.etheller.warsmash.units;

import java.util.ArrayList;
import java.util.List;

/**
 * The fields of one object: an open addressing table of two arrays. The game's object data holds about a million
 * fields, which cost a hash map node each in the plain map.
 */
final class FieldMap {
	private StringKey[] keys;
	private FieldList[] values;
	private int size;

	int size() {
		return this.size;
	}

	FieldList get(final StringKey key) {
		if (this.keys == null) {
			return null;
		}
		final int mask = this.keys.length - 1;
		for (int i = key.hashCode() & mask;; i = (i + 1) & mask) {
			final StringKey found = this.keys[i];
			if (found == null) {
				return null;
			}
			if (found.equals(key)) {
				return this.values[i];
			}
		}
	}

	boolean containsKey(final StringKey key) {
		return get(key) != null;
	}

	void put(final StringKey key, final FieldList value) {
		if ((this.keys == null) || (((this.size + 1) * 4) > (this.keys.length * 3))) {
			resize(this.keys == null ? 4 : this.keys.length * 2);
		}
		final int mask = this.keys.length - 1;
		for (int i = key.hashCode() & mask;; i = (i + 1) & mask) {
			final StringKey found = this.keys[i];
			if (found == null) {
				this.keys[i] = key;
				this.values[i] = value;
				this.size++;
				return;
			}
			if (found.equals(key)) {
				// An existing key keeps its spelling, as the plain map does.
				this.values[i] = value;
				return;
			}
		}
	}

	void remove(final StringKey key) {
		if (this.keys == null) {
			return;
		}
		final int mask = this.keys.length - 1;
		int i = key.hashCode() & mask;
		while (this.keys[i] != null) {
			if (this.keys[i].equals(key)) {
				this.keys[i] = null;
				this.values[i] = null;
				this.size--;
				// Entries that probed past the freed slot move up so that lookups still find them.
				for (int j = (i + 1) & mask; this.keys[j] != null; j = (j + 1) & mask) {
					final StringKey moveKey = this.keys[j];
					final FieldList moveValue = this.values[j];
					this.keys[j] = null;
					this.values[j] = null;
					this.size--;
					put(moveKey, moveValue);
				}
				return;
			}
			i = (i + 1) & mask;
		}
	}

	List<StringKey> keyList() {
		final List<StringKey> list = new ArrayList<>(this.size);
		if (this.keys != null) {
			for (final StringKey key : this.keys) {
				if (key != null) {
					list.add(key);
				}
			}
		}
		return list;
	}

	private void resize(final int capacity) {
		final StringKey[] oldKeys = this.keys;
		final FieldList[] oldValues = this.values;
		this.keys = new StringKey[capacity];
		this.values = new FieldList[capacity];
		this.size = 0;
		for (int i = 0; oldKeys != null && i < oldKeys.length; i++) {
			if (oldKeys[i] != null) {
				put(oldKeys[i], oldValues[i]);
			}
		}
	}
}
