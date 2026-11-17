package com.etheller.warsmash.units;

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * The values of one object data field. Immutable, so the many fields that hold the same values ("0", "", "_") can
 * share one list; a one value list is the value itself.
 */
final class FieldList extends AbstractList<String> implements RandomAccess {
	private final Object data; // a String or a String[] of at least two

	private FieldList(final Object data) {
		this.data = data;
	}

	static FieldList of(final String value) {
		return value == null ? new FieldList(new String[] { null }) : new FieldList(value);
	}

	static FieldList of(final String[] values) {
		return (values.length == 1) && (values[0] != null) ? new FieldList(values[0]) : new FieldList(values);
	}

	@Override
	public String get(final int index) {
		if (this.data instanceof String) {
			if (index != 0) {
				throw new IndexOutOfBoundsException("Index " + index);
			}
			return (String) this.data;
		}
		return ((String[]) this.data)[index];
	}

	@Override
	public int size() {
		return this.data instanceof String ? 1 : ((String[]) this.data).length;
	}

	String[] toArray(final int newLength) {
		final String[] values = new String[newLength];
		if (this.data instanceof String) {
			values[0] = (String) this.data;
		}
		else {
			final String[] old = (String[]) this.data;
			System.arraycopy(old, 0, values, 0, Math.min(old.length, newLength));
		}
		return values;
	}
}
