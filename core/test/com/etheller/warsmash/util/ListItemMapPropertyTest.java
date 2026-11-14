package com.etheller.warsmash.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/** A map listed only to say it cannot be read comes after the maps that can be played. */
class ListItemMapPropertyTest {
	@Test
	void unreadableMapsComeLast() {
		final ListItemMapProperty broken = new ListItemMapProperty("Maps/Custom/a.w3x", "a.w3x (damaged)", 0, false,
				true);
		final ListItemMapProperty two = new ListItemMapProperty("Maps/b.w3x", "b", 2, true);
		final ListItemMapProperty eight = new ListItemMapProperty("Maps/c.w3x", "c", 8, false);
		final List<ListItemMapProperty> list = new ArrayList<>(List.of(broken, eight, two));
		Collections.sort(list, (a, b) -> a.compare(b));
		assertTrue(list.get(0) == two);
		assertTrue(list.get(1) == eight);
		assertTrue(list.get(2) == broken);
	}
}
