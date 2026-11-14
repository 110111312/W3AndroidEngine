package com.etheller.warsmash.parsers.w3x.unitsdoo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.w3i.War3MapW3i;
import com.google.common.io.LittleEndianDataInputStream;

class War3MapUnitsDooTest {
	private static War3MapW3i mapOf(final int major, final int minor) throws Exception {
		final War3MapW3i map = new War3MapW3i(null);
		final Field majorField = War3MapW3i.class.getDeclaredField("gameVersionMajor");
		majorField.setAccessible(true);
		majorField.setInt(map, major);
		final Field minorField = War3MapW3i.class.getDeclaredField("gameVersionMinor");
		minorField.setAccessible(true);
		minorField.setInt(map, minor);
		return map;
	}

	private static void id(final ByteBuffer out, final String text) {
		out.put(text.getBytes(StandardCharsets.US_ASCII));
	}

	/** One unit with no item sets, items or abilities and the fixed random flag 0. */
	private static byte[] file(final int version, final boolean skin, final boolean tableAndAttributes,
			final boolean v12, final int units) {
		final ByteBuffer out = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
		id(out, "W3do");
		out.putInt(version).putInt(9).putInt(units);
		for (int i = 0; i < units; i++) {
			id(out, "hfoo");
			out.putInt(0);
			out.putFloat(100 + i).putFloat(200).putFloat(0);
			out.putFloat(0);
			out.putFloat(1).putFloat(1).putFloat(1);
			if (skin) {
				id(out, "hfoo");
			}
			if (v12) {
				out.putInt(-1);
			}
			out.put((byte) 2).putInt(i).putShort((short) 0).putInt(-1).putInt(-1);
			if (tableAndAttributes) {
				out.putInt(-1);
			}
			out.putInt(0);
			out.putInt(0).putFloat(200).putInt(0);
			if (tableAndAttributes) {
				out.putInt(0).putInt(0).putInt(0);
			}
			out.putInt(0).putInt(0);
			out.putInt(0).put(new byte[4]);
			out.putInt(0).putInt(0).putInt(7 + i);
			if (v12) {
				out.putInt(0).putInt(0).putInt(0);
			}
		}
		final byte[] bytes = new byte[out.position()];
		out.flip();
		out.get(bytes);
		return bytes;
	}

	private static War3MapUnitsDoo read(final byte[] bytes, final War3MapW3i map) throws IOException {
		return new War3MapUnitsDoo(new LittleEndianDataInputStream(new ByteArrayInputStream(bytes)), map);
	}

	private static void assertUnits(final War3MapUnitsDoo doo, final int count) {
		assertEquals(count, doo.getUnits().size());
		for (int i = 0; i < count; i++) {
			final Unit unit = doo.getUnits().get(i);
			assertEquals(100 + i, unit.getLocation()[0]);
			assertEquals(i, unit.getPlayer());
			assertEquals(7 + i, unit.getCreationNumber());
		}
	}

	@Test
	void classicFileOfAnEarlierEditor() throws Exception {
		assertUnits(read(file(8, false, true, false, 3), mapOf(1, 29)), 3);
	}

	@Test
	void fileOfTheSkinEditorWithTheItemTable() throws Exception {
		assertUnits(read(file(8, true, true, false, 3), mapOf(1, 32)), 3);
	}

	@Test
	void reforgedSaveOfAnOlderMapHasTheSkinButNotTheItemTable() throws Exception {
		assertUnits(read(file(8, true, false, false, 3), mapOf(1, 33)), 3);
	}

	@Test
	void version12FileOfTheThreeZeroData() throws Exception {
		assertUnits(read(file(12, true, false, true, 3), mapOf(2, 0)), 3);
	}

	@Test
	void layoutDoesNotFollowTheMapVersionAlone() throws Exception {
		assertUnits(read(file(8, false, true, false, 2), mapOf(2, 0)), 2);
	}

	@Test
	void emptyFile() throws Exception {
		assertEquals(0, read(file(8, true, false, false, 0), mapOf(1, 33)).getUnits().size());
	}
}
