package net.warsmash.lan.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

/** A screen's thread does not wait long for a loop to stop, and a question to a stopping loop is still answered. */
class LanLoopShutdownTest {
	private static void occupy(final LanLoop loop, final CountDownLatch release) throws InterruptedException {
		final CountDownLatch busy = new CountDownLatch(1);
		loop.execute(() -> {
			busy.countDown();
			try {
				release.await();
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		busy.await();
	}

	@Test
	void aBoundedShutdownReturnsWhileTheLoopIsBusy() throws Exception {
		final LanLoop loop = new LanLoop();
		loop.start();
		final CountDownLatch release = new CountDownLatch(1);
		occupy(loop, release);
		try {
			final long start = System.nanoTime();
			assertTimeoutPreemptively(Duration.ofSeconds(3), () -> loop.shutdown(LanLoop.RESPONSIVE_WAIT_MS));
			assertTrue(((System.nanoTime() - start) / 1_000_000) < 2000);
		}
		finally {
			release.countDown();
		}
	}

	@Test
	void aShutdownWithoutWaitingNeverBlocks() throws Exception {
		final LanLoop loop = new LanLoop();
		loop.start();
		final CountDownLatch release = new CountDownLatch(1);
		occupy(loop, release);
		try {
			final long start = System.nanoTime();
			loop.shutdown(0);
			assertTrue(((System.nanoTime() - start) / 1_000_000) < 500);
		}
		finally {
			release.countDown();
		}
	}

	@Test
	void aQuestionToALoopStoppedWhileBusyIsStillAnswered() throws Exception {
		final LanLoop loop = new LanLoop();
		loop.start();
		final CountDownLatch release = new CountDownLatch(1);
		occupy(loop, release);
		loop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
		final Thread later = new Thread(() -> {
			try {
				Thread.sleep(300);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			release.countDown();
		});
		later.start();
		assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertEquals(Integer.valueOf(3), loop.call(() -> 3)));
		later.join();
	}

	@Test
	void aShutdownOfAnIdleLoopStopsItAtOnce() {
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			final LanLoop loop = new LanLoop();
			loop.start();
			loop.shutdown(LanLoop.RESPONSIVE_WAIT_MS);
			assertEquals("x", loop.call(() -> "x"));
		});
	}
}
