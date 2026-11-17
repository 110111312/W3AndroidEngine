package com.etheller.warsmash.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class FieldMapTest {
	@Test
	void behavesAsAHashMapOfCaseInsensitiveKeysThroughManyPutsAndRemoves() {
		final FieldMap map = new FieldMap();
		final Map<String, String> expected = new HashMap<>();
		final Random random = new Random(11);
		for (int i = 0; i < 5000; i++) {
			final String name = "Field" + random.nextInt(300);
			final StringKey key = new StringKey(random.nextBoolean() ? name : name.toUpperCase());
			if (random.nextInt(4) == 0) {
				map.remove(key);
				expected.remove(name.toLowerCase());
			}
			else {
				final String value = "v" + i;
				map.put(key, FieldList.of(value));
				expected.put(name.toLowerCase(), value);
			}
			assertEquals(expected.size(), map.size());
		}
		for (int i = 0; i < 300; i++) {
			final String name = "field" + i;
			final FieldList found = map.get(new StringKey(name));
			if (expected.containsKey(name)) {
				assertEquals(expected.get(name), found.get(0));
			}
			else {
				assertNull(found);
			}
		}
		assertEquals(expected.size(), map.keyList().size());
	}

	@Test
	void anEmptyMapHasNothing() {
		final FieldMap map = new FieldMap();
		assertNull(map.get(new StringKey("a")));
		map.remove(new StringKey("a"));
		assertTrue(map.keyList().isEmpty());
		assertEquals(0, map.size());
	}

	@Test
	void fieldListsReadLikeLists() {
		assertEquals(Arrays.asList("a"), FieldList.of("a"));
		assertEquals(Arrays.asList("a", "b", "c"), FieldList.of(new String[] { "a", "b", "c" }));
		assertEquals(1, FieldList.of(new String[] { "a" }).size());
		final List<String> nullValue = FieldList.of((String) null);
		assertEquals(1, nullValue.size());
		assertNull(nullValue.get(0));
	}

	@Test
	void gameObjectsKeepTheListSemanticsOfTheFieldTable() {
		final Element element = new Element("test", null);
		element.setField("Name", "Footman");
		element.setField("Data", "1", 0);
		element.setField("Data", "2", 1);
		element.setField("Data", "3", 3);
		assertEquals(Arrays.asList("1", "2", "", "3"), element.getFieldAsList("DATA"));
		assertEquals("1,2,,3", element.getField("data"));
		element.setField("Data", "9");
		assertEquals(Arrays.asList("9", "2", "", "3"), element.getFieldAsList("data"));
		// A first value at a later index is not stored.
		element.setField("Late", "x", 2);
		assertNull(element.getFieldAsList("Late"));
		element.setField("Gone", Arrays.asList("a", "b"));
		assertEquals(Arrays.asList("a", "b"), element.getFieldAsList("gone"));
		element.setField("Gone", Arrays.asList());
		assertNull(element.getFieldAsList("Gone"));
		element.clearFieldList("", "Name");
		assertEquals("", element.getField("Name"));
	}

	@Test
	void equalSingleValuesShareOneList() {
		final Element a = new Element("a", null);
		final Element b = new Element("b", null);
		a.setField("Hotkey", "Q");
		b.setField("Hotkey", "Q");
		assertSame(a.getFieldAsList("Hotkey"), b.getFieldAsList("Hotkey"));
	}
}
