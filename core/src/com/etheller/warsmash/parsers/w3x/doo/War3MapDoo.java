package com.etheller.warsmash.parsers.w3x.doo;

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

/**
 * war3map.doo - the doodad and destructible file.
 */
public class War3MapDoo {
	private static final War3ID MAGIC_NUMBER = War3ID.fromString("W3do");
	private int version = 0;
	private Boolean hasSkinId;
	private final short[] u1 = new short[4];
	private final List<Doodad> doodads = new ArrayList<>();
	private final short[] u2 = new short[4];
	private final List<TerrainDoodad> terrainDoodads = new ArrayList<>();

	public War3MapDoo(final LittleEndianDataInputStream stream, final War3MapW3i mapInformation) throws IOException {
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
		ParseUtils.readUInt8Array(stream, this.u1);
		final long doodadCount = ParseUtils.readUInt32(stream);
		final byte[] body = readRest(stream);

		// The editor version of the map tells whether the doodads have a skin id, but a file saved by another editor
		// may not agree; the one that takes exactly the whole file is the right one.
		final boolean expected = ((mapInformation.getGameVersionMajor() * 100)
				+ mapInformation.getGameVersionMinor()) >= 132;
		for (final boolean skin : new boolean[] { expected, !expected }) {
			try {
				final ByteArrayInputStream input = new ByteArrayInputStream(body);
				readBody(new LittleEndianDataInputStream(input), doodadCount, skin);
				if (input.available() == 0) {
					this.hasSkinId = skin;
					return true;
				}
			}
			catch (final IOException e) {
				// Not this layout.
			}
			this.doodads.clear();
			this.terrainDoodads.clear();
		}
		// No layout fits: read as the editor version says, which keeps what is there or fails as it always did.
		this.hasSkinId = expected;
		readBody(new LittleEndianDataInputStream(new ByteArrayInputStream(body)), doodadCount, expected);
		return true;
	}

	private void readBody(final LittleEndianDataInputStream stream, final long doodadCount, final boolean skin)
			throws IOException {
		for (long i = 0; i < doodadCount; i++) {
			final Doodad doodad = new Doodad();

			doodad.load(stream, this.version, skin);

			this.doodads.add(doodad);
		}

		ParseUtils.readUInt8Array(stream, this.u2);

		for (int i = 0, l = stream.readInt(); i < l; i++) {
			final TerrainDoodad terrainDoodad = new TerrainDoodad();

			terrainDoodad.load(stream, this.version);

			this.terrainDoodads.add(terrainDoodad);
		}
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
		final boolean skin = this.hasSkinId != null ? this.hasSkinId
				: ((mapInformation.getGameVersionMajor() * 100) + mapInformation.getGameVersionMinor()) >= 132;

		ParseUtils.writeWar3ID(stream, MAGIC_NUMBER);
		stream.writeInt(this.version);
		ParseUtils.writeUInt8Array(stream, this.u1);
		ParseUtils.writeUInt32(stream, this.doodads.size());

		for (final Doodad doodad : this.doodads) {
			doodad.save(stream, this.version, skin);
		}

		ParseUtils.writeUInt8Array(stream, this.u2);
		ParseUtils.writeUInt32(stream, this.terrainDoodads.size());

		for (final TerrainDoodad terrainDoodad : this.terrainDoodads) {
			terrainDoodad.save(stream, this.version);
		}
	}

	public int getByteLength() {
		int size = 24 + (this.terrainDoodads.size() * 16);
		final boolean skin = Boolean.TRUE.equals(this.hasSkinId);

		for (final Doodad doodad : this.doodads) {
			size += doodad.getByteLength(this.version, skin);
		}

		return size;
	}

	public int getVersion() {
		return this.version;
	}

	public short[] getU1() {
		return this.u1;
	}

	public List<Doodad> getDoodads() {
		return this.doodads;
	}

	public short[] getU2() {
		return this.u2;
	}

	public List<TerrainDoodad> getTerrainDoodads() {
		return this.terrainDoodads;
	}

}
