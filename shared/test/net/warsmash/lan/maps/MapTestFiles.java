package net.warsmash.lan.maps;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;

/** Generates files that pass the map header check, with content that exposes reordered or lost blocks. */
public final class MapTestFiles {
	private static final int BLOCK = 64 * 1024;

	private MapTestFiles() {
	}

	/** HM3W header, an MPQ header at 512, then position stamped pseudo random blocks. */
	public static File writeMap(final File file, final long size, final long seed) throws IOException {
		final byte[] head = new byte[1024];
		final Random r = new Random(seed);
		r.nextBytes(head);
		head[0] = 'H';
		head[1] = 'M';
		head[2] = '3';
		head[3] = 'W';
		for (int i = 4; i < 512; i++) {
			head[i] = 0;
		}
		head[512] = 'M';
		head[513] = 'P';
		head[514] = 'Q';
		head[515] = 0x1A;
		head[516] = 32;
		head[517] = 0;
		head[518] = 0;
		head[519] = 0;
		final byte[] base = new byte[256 * 1024];
		r.nextBytes(base);
		file.getParentFile().mkdirs();
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file), 1 << 20)) {
			long written = 0;
			final int first = (int) Math.min(size, head.length);
			out.write(head, 0, first);
			written += first;
			final byte[] block = new byte[BLOCK];
			long index = 0;
			while (written < size) {
				final int n = (int) Math.min(BLOCK, size - written);
				System.arraycopy(base, (int) ((index * 7919) % (base.length - BLOCK)), block, 0, BLOCK);
				for (int i = 0; i < 8; i++) {
					block[i] = (byte) (index >>> (8 * i));
				}
				out.write(block, 0, n);
				written += n;
				index++;
			}
		}
		return file;
	}

	/** Same length, one block in the middle differs. */
	public static File writeCorruptedCopy(final File original, final File copy) throws IOException {
		final byte[] all = java.nio.file.Files.readAllBytes(original.toPath());
		all[all.length / 2] ^= 0x55;
		java.nio.file.Files.write(copy.toPath(), all);
		return copy;
	}
}
