package com.etheller.warsmash.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.StringBundle;

/**
 * SLK files as the game ships them, and as map optimizers write them (rows in alternating directions, a row's key
 * cell last).
 */
class DataTableSlkTest {
	private static DataTable read(final String... records) throws Exception {
		final DataTable table = new DataTable(StringBundle.EMPTY);
		table.readSLK(new ByteArrayInputStream(String.join("\r\n", records).getBytes(StandardCharsets.UTF_8)));
		return table;
	}

	@Test
	void rowsWrittenBackwardsKeepTheirCells() throws Exception {
		final DataTable table = read("ID;PWXL;N;E", "B;X4;Y4;D0", //
				"C;X1;Y1;K\"unitBalanceID\"", "C;X2;K\"HP\"", "C;X3;K\"spd\"", "C;X4;K\"type\"", //
				"C;X4;Y2;K\"_\"", "C;X3;K270", "C;X2;K420", "C;X1;K\"hfoo\"", //
				"C;Y3;K\"u00Z\"", "C;X2;K220", "C;X4;K\"Peon,undead\"", //
				"C;Y4;K\"x\"", "C;X2;K150", "C;X1;K\"n01F\"", //
				"E");
		assertEquals("420", table.get("hfoo").getField("HP"));
		assertEquals("270", table.get("hfoo").getField("spd"));
		assertEquals("_", table.get("hfoo").getField("type"));
		assertEquals("220", table.get("u00Z").getField("HP"));
		assertEquals("Peon", table.get("u00Z").getField("type", 0));
		assertEquals("undead", table.get("u00Z").getField("type", 1));
		assertEquals("", table.get("u00Z").getField("spd"));
		assertEquals("150", table.get("n01F").getField("HP"));
		assertEquals("x", table.get("n01F").getField("type"));
	}

	@Test
	void gameFilesReadAsBefore() throws Exception {
		// A row without a key (AbilityData.slk has one) still writes into the row before it.
		final DataTable table = read("ID;PWXL;N;E", "B;X3;Y4;D0", //
				"C;X1;Y1;K\"alias\"", "C;X2;K\"Cool1\"", "C;X3;K\"Rng1\"", //
				"C;X1;Y2;K\"ANvc\"", "C;X2;K90", "C;X3;K800", //
				"C;X3;Y3;K\"-\"", //
				"C;X1;Y4;K\"ANin\"", "C;X2;K180", //
				"E");
		assertEquals("90", table.get("ANvc").getField("Cool1"));
		assertEquals("-", table.get("ANvc").getField("Rng1"));
		assertEquals("180", table.get("ANin").getField("Cool1"));
		assertNull(table.get("-"));
	}
}
