package com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class IconUILazyTipTest {
	@Test
	void tipIsFilledInOnceWhenFirstAsked() {
		final AtomicInteger reads = new AtomicInteger();
		final IconUI icon = new IconUI(null, null, 0, 0, "tip", "Deals <A000,Dur1> seconds", text -> {
			reads.incrementAndGet();
			return text.replace("<A000,Dur1>", "5");
		}, 'Q');
		assertEquals(0, reads.get());
		assertEquals("Deals 5 seconds", icon.getUberTip());
		assertEquals("Deals 5 seconds", icon.getUberTip());
		assertEquals(1, reads.get());
	}

	@Test
	void settingTheTipReplacesTheUnreadOne() {
		final AtomicInteger reads = new AtomicInteger();
		final IconUI icon = new IconUI(null, null, 0, 0, "tip", "<A000,Dur1>", text -> {
			reads.incrementAndGet();
			return "read";
		}, 'Q');
		icon.setUberTip("set");
		assertEquals("set", icon.getUberTip());
		assertEquals(0, reads.get());
	}
}
