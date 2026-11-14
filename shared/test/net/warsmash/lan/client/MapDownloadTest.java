package net.warsmash.lan.client;

import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.warsmash.lan.LanTestSupport;
import net.warsmash.lan.maps.MapHash;
import net.warsmash.lan.maps.MapTestFiles;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanLoop;

/** The download state machine without a network: chunks are fed by hand, also the wrong ones. */
class MapDownloadTest {
	@TempDir
	Path tmp;

	private LanLoop loop;
	private File dir;
	private File source;
	private byte[] bytes;
	private LanData.MapInfo info;
	private final List<String> events = new CopyOnWriteArrayList<String>();
	private volatile File finished;
	private volatile int failure;

	private final MapDownload.Callback callback = new MapDownload.Callback() {
		@Override
		public void onProgress(final long done, final int total) {
			events.add("progress");
		}

		@Override
		public void onVerifying() {
			events.add("verifying");
		}

		@Override
		public void onFinished(final File file, final byte[] sha256, final int crc32) {
			events.add("finished");
			finished = file;
		}

		@Override
		public void onFailed(final int reason, final String detail) {
			events.add("failed");
			failure = reason;
		}
	};

	@BeforeEach
	void setUp() throws Exception {
		this.loop = new LanLoop("lan-net-test-download", LanTestSupport.fastTimings());
		this.loop.start();
		this.dir = this.tmp.resolve("Download").toFile();
		this.source = MapTestFiles.writeMap(this.tmp.resolve("Source.w3x").toFile(), 100000, 11);
		this.bytes = Files.readAllBytes(this.source.toPath());
		this.info = MapHash.describe(this.source, MapHash.compute(this.source));
	}

	@AfterEach
	void tearDown() throws Exception {
		this.loop.shutdown();
		waitFor("threads", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private <T> T onLoop(final Callable<T> c) throws Exception {
		return this.loop.call(c);
	}

	private MapDownload create(final int stallMs) {
		return new MapDownload(this.loop, this.info, this.dir, stallMs, this.callback);
	}

	private LanMessages.MapChunk chunk(final int offset, final int length) {
		return new LanMessages.MapChunk(offset, this.bytes.length, Arrays.copyOfRange(this.bytes, offset, offset + length));
	}

	private void feed(final MapDownload d, final int from, final int to) throws Exception {
		for (int off = from; off < to; off += LanProtocol.MAX_MAP_CHUNK) {
			final int o = off;
			final int n = Math.min(LanProtocol.MAX_MAP_CHUNK, to - off);
			assertTrue(onLoop(() -> d.onChunk(chunk(o, n))));
		}
	}

	private File part() {
		return MapDownload.partFile(this.dir, this.info);
	}

	private String[] listing() {
		final String[] names = this.dir.list();
		if (names == null) {
			return new String[0];
		}
		Arrays.sort(names);
		return names;
	}

	@Test
	void goodDownloadEndsAsTheRealFile() throws Exception {
		final MapDownload d = create(10000);
		assertEquals(0, (int) onLoop(d::begin));
		assertTrue(part().isFile());
		feed(d, 0, this.bytes.length);
		waitFor("finished", () -> this.finished != null);
		assertArrayEquals(this.bytes, Files.readAllBytes(this.finished.toPath()));
		assertEquals(this.info.name, this.finished.getName());
		assertEquals(this.info.name, listing()[0]);
		assertEquals(1, listing().length, "no part file is left");
		assertEquals(1, countOf(this.events, "verifying"));
	}

	private static int countOf(final List<String> l, final String s) {
		int n = 0;
		for (final String e : l) {
			if (e.equals(s)) {
				n++;
			}
		}
		return n;
	}

	@Test
	void partNameCarriesTheHashSoAnotherMapDoesNotResumeIt() throws Exception {
		final LanData.MapInfo other = MapHash.describe(this.source, MapHash.compute(this.source));
		other.sha256 = other.sha256.clone();
		other.sha256[0] ^= 1;
		assertFalse(MapDownload.partFile(this.dir, other).equals(part()));
		assertTrue(part().getName().endsWith(".part"));
	}

	@Test
	void chunkAtTheWrongOffsetFailsAndDeletesThePart() throws Exception {
		final MapDownload d = create(10000);
		onLoop(d::begin);
		feed(d, 0, 32768);
		assertFalse(onLoop(() -> d.onChunk(chunk(40000, 1000))));
		assertEquals(Arrays.asList("progress", "progress", "failed"), this.events);
		assertEquals(MapDownload.FAIL_PROTOCOL, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void chunkBeyondTheAnnouncedSizeFails() throws Exception {
		final MapDownload d = create(10000);
		onLoop(d::begin);
		feed(d, 0, 99984);
		final LanMessages.MapChunk tooMuch = new LanMessages.MapChunk(99984, this.bytes.length, new byte[100]);
		assertFalse(onLoop(() -> d.onChunk(tooMuch)));
		assertEquals(MapDownload.FAIL_PROTOCOL, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void chunkWithAnotherTotalOrNoDataFails() throws Exception {
		final MapDownload d = create(10000);
		onLoop(d::begin);
		assertFalse(onLoop(() -> d.onChunk(new LanMessages.MapChunk(0, this.bytes.length + 1, new byte[10]))));
		assertEquals(MapDownload.FAIL_PROTOCOL, this.failure);
		this.events.clear();
		final MapDownload e = create(10000);
		onLoop(e::begin);
		assertFalse(onLoop(() -> e.onChunk(new LanMessages.MapChunk(0, this.bytes.length, new byte[0]))));
		assertEquals(Arrays.asList("failed"), this.events);
	}

	@Test
	void wrongHashDeletesThePartAndLeavesNoMap() throws Exception {
		this.info.sha256 = this.info.sha256.clone();
		this.info.sha256[5] ^= 0x40;
		final MapDownload d = create(10000);
		onLoop(d::begin);
		feed(d, 0, this.bytes.length);
		waitFor("failed", () -> this.failure != 0);
		assertEquals(MapDownload.FAIL_HASH, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void wrongCrcAloneIsAlsoRefused() throws Exception {
		this.info.crc32 ^= 1;
		final MapDownload d = create(10000);
		onLoop(d::begin);
		feed(d, 0, this.bytes.length);
		waitFor("failed", () -> this.failure != 0);
		assertEquals(MapDownload.FAIL_HASH, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void notAnArchiveIsRefusedAsSoonAsTheHeaderIsHere() throws Exception {
		Arrays.fill(this.bytes, 0, 2048, (byte) 'x');
		final MapDownload d = create(10000);
		onLoop(d::begin);
		assertTrue(onLoop(() -> d.onChunk(chunk(0, 512))));
		assertFalse(onLoop(() -> d.onChunk(chunk(512, 1024))));
		assertEquals(MapDownload.FAIL_BAD_HEADER, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void announcementWithAnUnsafeNameOrNoHashNeverOpensAFile() throws Exception {
		this.info.name = "..\\Evil.w3x";
		MapDownload d = create(10000);
		assertEquals(-1, (int) onLoop(d::begin));
		assertEquals(MapDownload.FAIL_BAD_INFO, this.failure);
		this.info.name = "Fine.w3x";
		this.info.sha256 = new byte[0];
		this.failure = 0;
		d = create(10000);
		assertEquals(-1, (int) onLoop(d::begin));
		assertEquals(MapDownload.FAIL_BAD_INFO, this.failure);
		assertFalse(this.dir.exists(), "not even the directory was created");
	}

	@Test
	void resumesAnEarlierPartAndFinishes() throws Exception {
		this.dir.mkdirs();
		Files.write(part().toPath(), Arrays.copyOf(this.bytes, 40000));
		final MapDownload d = create(10000);
		assertEquals(40000, (int) onLoop(d::begin));
		feed(d, 40000, this.bytes.length);
		waitFor("finished", () -> this.finished != null);
		assertArrayEquals(this.bytes, Files.readAllBytes(this.finished.toPath()));
		assertEquals(1, listing().length);
	}

	@Test
	void aPartThatWasCompleteIsOnlyVerified() throws Exception {
		this.dir.mkdirs();
		Files.write(part().toPath(), this.bytes);
		final MapDownload d = create(10000);
		assertEquals(-1, (int) onLoop(d::begin));
		waitFor("finished", () -> this.finished != null);
		assertEquals(1, listing().length);
	}

	@Test
	void aPartWithTheWrongStartIsDroppedOnResume() throws Exception {
		this.dir.mkdirs();
		final byte[] junk = new byte[30000];
		Arrays.fill(junk, (byte) 'j');
		Files.write(part().toPath(), junk);
		final MapDownload d = create(10000);
		assertEquals(-1, (int) onLoop(d::begin));
		assertEquals(MapDownload.FAIL_BAD_HEADER, this.failure);
		assertEquals(0, listing().length);
	}

	@Test
	void aPartLongerThanTheMapStartsOver() throws Exception {
		this.dir.mkdirs();
		Files.write(part().toPath(), new byte[this.bytes.length + 10]);
		final MapDownload d = create(10000);
		assertEquals(0, (int) onLoop(d::begin));
		assertEquals(0, part().length());
		onLoop(() -> {
			d.abort(false);
			return null;
		});
	}

	@Test
	void silenceFailsButKeepsThePartForResuming() throws Exception {
		final MapDownload d = create(200);
		onLoop(d::begin);
		feed(d, 0, 32768);
		waitFor("timeout", 3000, () -> this.failure != 0);
		assertEquals(MapDownload.FAIL_TIMEOUT, this.failure);
		assertEquals(32768, part().length());
	}

	@Test
	void abortKeepsOrDeletesThePartAsAsked() throws Exception {
		MapDownload d = create(10000);
		onLoop(d::begin);
		feed(d, 0, 32768);
		final MapDownload kept = d;
		onLoop(() -> {
			kept.abort(false);
			return null;
		});
		assertEquals(32768, part().length());
		d = create(10000);
		assertEquals(32768, (int) onLoop(d::begin));
		final MapDownload dropped = d;
		onLoop(() -> {
			dropped.abort(true);
			return null;
		});
		assertFalse(part().exists());
		assertEquals(0, countOf(this.events, "failed"), "an abort is not a failure");
	}

	@Test
	void unwritableDirectoryFailsCleanly() throws Exception {
		Files.write(this.dir.toPath(), new byte[1]);
		final MapDownload d = create(10000);
		assertEquals(-1, (int) onLoop(d::begin));
		assertEquals(MapDownload.FAIL_IO, this.failure);
	}
}
