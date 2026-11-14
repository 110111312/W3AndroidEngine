package net.warsmash.lan.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import net.warsmash.lan.maps.MapHash;
import net.warsmash.lan.maps.MapRules;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.transport.LanLoop;

/**
 * One map download on the client. The data goes to {@code <name>.<hash>.part} in the download directory; the file is
 * renamed to its real name only after size, SHA-256, CRC-32 and the MPQ header agree with what the host announced.
 * A mismatch deletes the part; an interrupted transfer leaves it for the next attempt to resume. Everything but the
 * final hashing (a helper thread) runs on the loop thread.
 */
public final class MapDownload {
	public static final int FAIL_BAD_INFO = 1;
	public static final int FAIL_BAD_HEADER = 2;
	public static final int FAIL_SIZE = 3;
	public static final int FAIL_HASH = 4;
	public static final int FAIL_IO = 5;
	public static final int FAIL_TIMEOUT = 6;
	public static final int FAIL_PROTOCOL = 7;

	public interface Callback {
		void onProgress(long done, int total);

		/** All bytes are here; hashing starts. */
		void onVerifying();

		void onFinished(File file, byte[] sha256, int crc32);

		void onFailed(int reason, String detail);
	}

	private final LanLoop loop;
	private final LanData.MapInfo info;
	private final File dir;
	private final Callback callback;
	private final int stallMs;
	private final File part;
	private RandomAccessFile out;
	private long received;
	private boolean headerChecked;
	private boolean ended;
	private volatile boolean cancelled;
	private long lastActivity;
	private LanLoop.Timer stallTimer;

	public MapDownload(final LanLoop loop, final LanData.MapInfo info, final File dir, final int stallMs,
			final Callback callback) {
		this.loop = loop;
		this.info = info;
		this.dir = dir;
		this.stallMs = stallMs;
		this.callback = callback;
		this.part = partFile(dir, info);
	}

	/** {@code <name>.<first hash bytes>.part}; the hash keeps a part of another map with the same name apart. */
	public static File partFile(final File dir, final LanData.MapInfo info) {
		final String hash = info.sha256.length >= 4 ? MapHash.hex(Arrays.copyOf(info.sha256, 4)) : "0";
		return new File(dir, info.name + "." + hash + ".part");
	}

	public long received() {
		return this.received;
	}

	/**
	 * Validates the announcement, opens the part file and returns the offset to request: the end of an earlier
	 * partial transfer, or 0. Returns -1 when nothing needs requesting (already failed, or the part is complete and
	 * is being verified).
	 */
	public int begin() {
		final String why = MapRules.checkInfo(this.info);
		if (why != null) {
			fail(FAIL_BAD_INFO, why, false);
			return -1;
		}
		try {
			if (!this.dir.isDirectory() && !this.dir.mkdirs()) {
				throw new IOException("cannot create the download directory");
			}
			if (!this.dir.getCanonicalFile().equals(this.part.getCanonicalFile().getParentFile())) {
				throw new IOException("part file outside the download directory");
			}
			this.out = new RandomAccessFile(this.part, "rw");
			long have = this.out.length();
			if (have > this.info.size) {
				this.out.setLength(0);
				have = 0;
			}
			this.received = have;
		}
		catch (final IOException e) {
			fail(FAIL_IO, e.getMessage(), false);
			return -1;
		}
		touch();
		if (this.received >= MapRules.HEADER_BYTES && !checkHeaderFromFile()) {
			return -1;
		}
		if (this.received == this.info.size) {
			verify();
			return -1;
		}
		scheduleStall();
		return (int) this.received;
	}

	/** Returns false if the chunk was a protocol violation (the download has failed). */
	public boolean onChunk(final LanMessages.MapChunk chunk) {
		if (this.ended || this.out == null) {
			return true;
		}
		if (chunk.total != this.info.size || chunk.offset != this.received || chunk.data.length == 0
				|| this.received + chunk.data.length > this.info.size) {
			fail(FAIL_PROTOCOL, "unexpected chunk at " + chunk.offset, true);
			return false;
		}
		touch();
		try {
			this.out.seek(this.received);
			this.out.write(chunk.data);
		}
		catch (final IOException e) {
			fail(FAIL_IO, e.getMessage(), true);
			return false;
		}
		this.received += chunk.data.length;
		if (!this.headerChecked && this.received >= MapRules.HEADER_BYTES && !checkHeaderFromFile()) {
			return false;
		}
		this.callback.onProgress(this.received, this.info.size);
		if (this.received == this.info.size) {
			verify();
		}
		return true;
	}

	/** Stops without a callback. The part stays on disk unless {@code deletePart}. */
	public void abort(final boolean deletePart) {
		this.cancelled = true;
		if (!this.ended) {
			this.ended = true;
			closeOut();
			cancelStall();
			if (deletePart) {
				this.part.delete();
			}
		}
	}

	private boolean checkHeaderFromFile() {
		this.headerChecked = true;
		try {
			final byte[] head = new byte[MapRules.HEADER_BYTES];
			this.out.seek(0);
			this.out.readFully(head);
			final String why = MapRules.checkHeader(head, head.length);
			if (why != null) {
				fail(FAIL_BAD_HEADER, why, true);
				return false;
			}
			return true;
		}
		catch (final IOException e) {
			fail(FAIL_IO, e.getMessage(), true);
			return false;
		}
	}

	private void verify() {
		this.ended = true;
		cancelStall();
		closeOut();
		this.callback.onVerifying();
		final Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				verifyOffLoop();
			}
		}, "lan-map-verify");
		t.setDaemon(true);
		t.start();
	}

	private void verifyOffLoop() {
		int reason = 0;
		String detail = null;
		try {
			final MapHash.Result r = MapHash.compute(this.part, new MapHash.Cancel() {
				@Override
				public boolean isCancelled() {
					return MapDownload.this.cancelled;
				}
			});
			if (r == null) {
				return;
			}
			if (r.size != this.info.size) {
				reason = FAIL_SIZE;
				detail = "size " + r.size + " instead of " + this.info.size;
			}
			else if (!Arrays.equals(r.sha256, this.info.sha256) || r.crc32 != this.info.crc32) {
				reason = FAIL_HASH;
				detail = "hash mismatch";
			}
			else {
				final byte[] head = new byte[MapRules.HEADER_BYTES];
				int len;
				try (InputStream in = new FileInputStream(this.part)) {
					len = Math.max(0, in.read(head));
				}
				final String why = MapRules.checkHeader(head, len);
				if (why != null) {
					reason = FAIL_BAD_HEADER;
					detail = why;
				}
			}
			if (reason == 0) {
				if (this.cancelled) {
					return;
				}
				final File target = new File(this.dir, this.info.name);
				try {
					Files.move(this.part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
				}
				catch (final AtomicMoveNotSupportedException e) {
					Files.move(this.part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				this.loop.execute(new Runnable() {
					@Override
					public void run() {
						if (!MapDownload.this.cancelled) {
							MapDownload.this.callback.onFinished(target, r.sha256, r.crc32);
						}
					}
				});
				return;
			}
		}
		catch (final IOException e) {
			reason = FAIL_IO;
			detail = e.getMessage();
		}
		this.part.delete();
		final int failReason = reason;
		final String failDetail = detail;
		this.loop.execute(new Runnable() {
			@Override
			public void run() {
				if (!MapDownload.this.cancelled) {
					MapDownload.this.callback.onFailed(failReason, failDetail);
				}
			}
		});
	}

	private void fail(final int reason, final String detail, final boolean deletePart) {
		if (this.cancelled) {
			return;
		}
		this.cancelled = true;
		this.ended = true;
		closeOut();
		cancelStall();
		if (deletePart) {
			this.part.delete();
		}
		this.callback.onFailed(reason, detail);
	}

	private void touch() {
		this.lastActivity = LanLoop.nowMs();
	}

	private void scheduleStall() {
		this.stallTimer = this.loop.schedule(this.stallMs, new Runnable() {
			@Override
			public void run() {
				if (MapDownload.this.ended) {
					return;
				}
				final long idle = LanLoop.nowMs() - MapDownload.this.lastActivity;
				if (idle >= MapDownload.this.stallMs) {
					// the part is kept: the next attempt resumes where this one stopped
					fail(FAIL_TIMEOUT, "no data for " + idle + " ms", false);
				}
				else {
					scheduleStall();
				}
			}
		});
	}

	private void cancelStall() {
		if (this.stallTimer != null) {
			this.stallTimer.cancel();
			this.stallTimer = null;
		}
	}

	private void closeOut() {
		if (this.out != null) {
			try {
				this.out.close();
			}
			catch (final IOException ignored) {
				// nothing to recover
			}
			this.out = null;
		}
	}
}
