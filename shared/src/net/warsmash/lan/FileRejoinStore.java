package net.warsmash.lan;

import java.io.File;
import java.io.IOException;

import net.warsmash.lan.transport.LanLog;

/** One small file per game in a folder. Keys of games nobody came back to are dropped after a week. */
public final class FileRejoinStore implements RejoinStore {
	private static final long STALE_MS = 7L * 24 * 60 * 60 * 1000;

	private final File folder;

	public FileRejoinStore(final File folder) {
		this.folder = folder;
		final File[] old = folder.listFiles();
		if (old != null) {
			final long now = System.currentTimeMillis();
			for (final File f : old) {
				if (f.getName().endsWith(".key") && now - f.lastModified() > STALE_MS) {
					RejoinKey.delete(f);
				}
			}
		}
	}

	private File fileOf(final long gameId) {
		return new File(this.folder, Long.toHexString(gameId) + ".key");
	}

	@Override
	public RejoinKey load(final long gameId) {
		final RejoinKey key = RejoinKey.load(fileOf(gameId));
		return key != null && key.gameId == gameId ? key : null;
	}

	@Override
	public void save(final RejoinKey key) {
		try {
			this.folder.mkdirs();
			key.save(fileOf(key.gameId));
		}
		catch (final IOException e) {
			LanLog.warn("cannot store the rejoin key", e);
		}
	}

	@Override
	public java.util.List<RejoinKey> all() {
		final java.util.List<RejoinKey> keys = new java.util.ArrayList<RejoinKey>();
		final File[] files = this.folder.listFiles();
		if (files != null) {
			for (final File f : files) {
				if (f.getName().endsWith(".key")) {
					final RejoinKey key = RejoinKey.load(f);
					if (key != null) {
						keys.add(key);
					}
				}
			}
		}
		return keys;
	}

	@Override
	public void clear(final long gameId) {
		RejoinKey.delete(fileOf(gameId));
	}
}
