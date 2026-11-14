package com.etheller.warsmash.parsers.w3x.unitsdoo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.etheller.warsmash.util.ParseUtils;
import com.etheller.warsmash.util.War3ID;
import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;

public class War3MapUnitsDoo {
	private static final War3ID MAGIC_NUMBER = War3ID.fromString("W3do");
	private int version = 8;
	private long unknown = 11;
	private final List<Unit> units = new ArrayList<>();
	private Unit.Layout layout;

	public War3MapUnitsDoo(final LittleEndianDataInputStream stream, final War3MapW3i mapInformation)
			throws IOException {
		if (stream != null) {
			load(stream, mapInformation);
		}
	}

	private boolean load(final LittleEndianDataInputStream stream, final War3MapW3i mapInformation) throws IOException {
		final War3ID firstId = ParseUtils.readWar3ID(stream);
		if (!MAGIC_NUMBER.equals(firstId)) {
			return false;
		}

		this.version = stream.readInt();
		this.unknown = ParseUtils.readUInt32(stream);
		final int count = stream.readInt();
		final byte[] body = readRest(stream);

		// The header tells the layout for most files, but a Reforged save of an older map has other optional fields
		// than its version says; the layout that takes exactly the whole file is the right one.
		final Unit.Layout expected = Unit.Layout.of(this.version, mapInformation);
		List<Unit> parsed = null;
		for (final Unit.Layout layout : candidates(expected)) {
			try {
				final List<Unit> units = new ArrayList<>();
				final ByteArrayInputStream input = new ByteArrayInputStream(body);
				final LittleEndianDataInputStream unitStream = new LittleEndianDataInputStream(input);
				for (int i = 0; i < count; i++) {
					final Unit unit = new Unit();
					unit.load(unitStream, layout);
					units.add(unit);
				}
				if (input.available() == 0) {
					this.layout = layout;
					parsed = units;
					break;
				}
			}
			catch (final IOException e) {
				// Not this layout.
			}
		}
		if (parsed == null) {
			// No layout fits: read as the header says, which keeps what is there or fails as it always did.
			this.layout = expected;
			parsed = new ArrayList<>();
			final LittleEndianDataInputStream unitStream = new LittleEndianDataInputStream(
					new ByteArrayInputStream(body));
			for (int i = 0; i < count; i++) {
				final Unit unit = new Unit();
				unit.load(unitStream, expected);
				parsed.add(unit);
			}
		}
		this.units.addAll(parsed);
		return true;
	}

	/** The layout the header tells first, then every other combination of the optional fields. */
	private static List<Unit.Layout> candidates(final Unit.Layout expected) {
		final List<Unit.Layout> layouts = new ArrayList<>();
		layouts.add(expected);
		for (int bits = 0; bits < 8; bits++) {
			final Unit.Layout layout = new Unit.Layout((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
			if (!layout.sameAs(expected)) {
				layouts.add(layout);
			}
		}
		return layouts;
	}

	private static byte[] readRest(final InputStream stream) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[8192];
		for (int read; (read = stream.read(buffer)) != -1;) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	public void save(final LittleEndianDataOutputStream stream, final War3MapW3i mapInformation) throws IOException {
		final Unit.Layout saved = this.layout != null ? this.layout : Unit.Layout.of(this.version, mapInformation);
		ParseUtils.writeWar3ID(stream, MAGIC_NUMBER);
		stream.writeInt(this.version);
		ParseUtils.writeUInt32(stream, this.unknown);
		stream.writeInt(this.units.size());

		for (final Unit unit : this.units) {
			unit.save(stream, saved);
		}
	}

	public int getByteLength() {
		final Unit.Layout saved = this.layout != null ? this.layout : Unit.Layout.of(this.version, false);
		int size = 16;

		for (final Unit unit : this.units) {
			size += unit.getByteLength(saved);
		}

		return size;
	}

	public List<Unit> getUnits() {
		return this.units;
	}

	public int getVersion() {
		return this.version;
	}

	public void setVersion(final int version) {
		this.version = version;
	}
}
