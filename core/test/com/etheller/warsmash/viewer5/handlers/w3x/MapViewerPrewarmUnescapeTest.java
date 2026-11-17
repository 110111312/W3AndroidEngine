package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapViewerPrewarmUnescapeTest {
	@Test
	void escapesAreReadInOnePass() {
		assertEquals("a\\\"b", MapViewerPrewarm.unescape("a\\\\\\\"b"));
		assertEquals("a\\nb", MapViewerPrewarm.unescape("a\\\\nb"));
		assertEquals("a\"b", MapViewerPrewarm.unescape("a\\\"b"));
		assertEquals("a\nb", MapViewerPrewarm.unescape("a\\nb"));
		assertEquals("a\\\nb", MapViewerPrewarm.unescape("a\\\\\\nb"));
		assertEquals("a\\\\nb", MapViewerPrewarm.unescape("a\\\\\\\\nb"));
		assertEquals("a\\qb", MapViewerPrewarm.unescape("a\\qb"));
		assertEquals("ab\\", MapViewerPrewarm.unescape("ab\\"));
		assertEquals("scripts\\Human.pld", MapViewerPrewarm.unescape("scripts\\\\Human.pld"));
	}
}
