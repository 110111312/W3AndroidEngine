package net.warsmash.lan.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** A question asked of a loop that has stopped is answered, not waited on forever (a listener after a session closed). */
class LanLoopCallTest {
	@Test
	void callAnsweredWhileRunning() throws Exception {
		final LanLoop loop = new LanLoop();
		loop.start();
		try {
			assertEquals("lan-net", loop.call(() -> Thread.currentThread().getName()));
		}
		finally {
			loop.shutdown();
		}
	}

	@Test
	void callAfterShutdownDoesNotHang() {
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			final LanLoop loop = new LanLoop();
			loop.start();
			loop.shutdown();
			assertEquals(Integer.valueOf(7), loop.call(() -> 7));
		});
	}

	@Test
	void callOnLoopNeverStartedButShutDownDoesNotHang() {
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			final LanLoop loop = new LanLoop();
			loop.shutdown();
			assertEquals("x", loop.call(() -> "x"));
		});
	}
}
