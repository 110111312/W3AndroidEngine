package net.warsmash.lan;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;
import net.warsmash.lan.transport.LanLog;

/**
 * Every turn of a game in order, in memory and optionally appended to disk (record = i32 length + turn body).
 * Used for rejoin, host migration and replay. Disk writes happen on a separate {@code lan-io} thread so the network
 * thread never waits for storage. Turns are kept encoded: a 30 minute game is about 300 KB.
 */
public final class TurnArchive {
	private final List<byte[]> turns = new ArrayList<byte[]>();
	private final File file;
	private OutputStream out;
	private ExecutorService io;
	private volatile IOException diskError;
	private long steps;

	public TurnArchive() {
		this.file = null;
	}

	private TurnArchive(final File file, final boolean append) throws IOException {
		this.file = file;
		this.out = new BufferedOutputStream(new FileOutputStream(file, append));
	}

	/** Starts an empty archive that mirrors every appended turn to the file (replacing it). */
	public static TurnArchive create(final File file) throws IOException {
		return new TurnArchive(file, false);
	}

	/**
	 * Reloads an archive from disk and keeps appending to it. A damaged or truncated tail (crash while writing) is
	 * cut off; everything before it is kept.
	 */
	public static TurnArchive recover(final File file) throws IOException {
		final List<byte[]> loaded = new ArrayList<byte[]>();
		long good = 0;
		long recoveredSteps = 0;
		if (file.isFile()) {
			final DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(file)));
			try {
				for (;;) {
					final int len;
					try {
						len = in.readInt();
					}
					catch (final EOFException e) {
						break;
					}
					if (len < 0 || len > LanProtocol.MAX_FRAME_BODY) {
						break;
					}
					final byte[] body = new byte[len];
					final int turnSteps;
					try {
						in.readFully(body);
						final TurnData t = TurnData.fromBytes(body);
						if (t.n != loaded.size()) {
							break;
						}
						turnSteps = t.steps;
					}
					catch (final EOFException e) {
						break;
					}
					catch (final LanProtocolException e) {
						break;
					}
					loaded.add(body);
					recoveredSteps += turnSteps;
					good += 4 + len;
				}
			}
			finally {
				in.close();
			}
			final java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "rw");
			try {
				raf.setLength(good);
			}
			finally {
				raf.close();
			}
		}
		final TurnArchive a = new TurnArchive(file, true);
		a.turns.addAll(loaded);
		a.steps = recoveredSteps;
		return a;
	}

	/** A copy in memory only, which goes on by itself: the turns now held are the same, later ones are not shared. */
	public synchronized TurnArchive snapshot() {
		final TurnArchive a = new TurnArchive();
		a.turns.addAll(this.turns);
		a.steps = this.steps;
		return a;
	}

	/** Number of turns held; the next turn to append has this number. */
	public synchronized int size() {
		return this.turns.size();
	}

	/** The simulation steps of all the turns held. */
	public synchronized long totalSteps() {
		return this.steps;
	}

	public synchronized void append(final TurnData t) throws LanProtocolException {
		if (t.n != this.turns.size()) {
			throw new LanProtocolException("turn " + t.n + " is not next (" + this.turns.size() + ")");
		}
		final byte[] body = t.toBytes();
		this.turns.add(body);
		this.steps += t.steps;
		if (this.out != null) {
			writeAsync(body);
		}
	}

	private void writeAsync(final byte[] body) {
		if (this.io == null) {
			this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
				@Override
				public Thread newThread(final Runnable r) {
					final Thread th = new Thread(r, "lan-io");
					th.setDaemon(true);
					return th;
				}
			});
		}
		final OutputStream o = this.out;
		this.io.execute(new Runnable() {
			@Override
			public void run() {
				try {
					o.write(new byte[] { (byte) (body.length >>> 24), (byte) (body.length >>> 16),
							(byte) (body.length >>> 8), (byte) body.length });
					o.write(body);
					o.flush();
				}
				catch (final IOException e) {
					TurnArchive.this.diskError = e;
					LanLog.warn("turn archive write failed", e);
				}
			}
		});
	}

	public synchronized TurnData get(final int n) throws LanProtocolException {
		if (n < 0 || n >= this.turns.size()) {
			throw new LanProtocolException("no such turn " + n);
		}
		return TurnData.fromBytes(this.turns.get(n));
	}

	/** Turns {@code from} (inclusive) to {@code to} (exclusive), clipped to what is held. */
	public synchronized List<TurnData> range(final int from, final int to) throws LanProtocolException {
		final List<TurnData> list = new ArrayList<TurnData>();
		for (int i = Math.max(0, from); i < Math.min(to, this.turns.size()); i++) {
			list.add(TurnData.fromBytes(this.turns.get(i)));
		}
		return list;
	}

	public IOException diskError() {
		return this.diskError;
	}

	public File file() {
		return this.file;
	}

	/** Flushes pending writes and releases the file and thread. */
	public void close() {
		final ExecutorService e;
		final OutputStream o;
		synchronized (this) {
			e = this.io;
			o = this.out;
			this.io = null;
			this.out = null;
		}
		if (e != null) {
			e.shutdown();
			try {
				e.awaitTermination(5, TimeUnit.SECONDS);
			}
			catch (final InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		if (o != null) {
			try {
				o.close();
			}
			catch (final IOException ex) {
				LanLog.warn("closing turn archive", ex);
			}
		}
	}
}
