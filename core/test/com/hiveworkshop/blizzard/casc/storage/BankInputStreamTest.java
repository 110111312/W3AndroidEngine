package com.hiveworkshop.blizzard.casc.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hiveworkshop.blizzard.casc.nio.MalformedCASCStructureException;

/**
 * A file read while it is decoded gives the bytes of the file decoded all at once, in reads of any size and with
 * skips over and inside banks.
 */
class BankInputStreamTest {
	/** Bytes in front of the stored data in the test data file, as other files are in a real one. */
	private static final int LEAD = 1000;

	private Path file;
	private FileChannel channel;

	@BeforeEach
	void open() throws IOException {
		this.file = Files.createTempFile("bank-input-stream", ".data");
	}

	@AfterEach
	void close() throws IOException {
		if (this.channel != null) {
			this.channel.close();
		}
		Files.deleteIfExists(this.file);
	}

	private static byte[] plain(final byte[] data) {
		final byte[] bank = new byte[data.length + 1];
		bank[0] = 'N';
		System.arraycopy(data, 0, bank, 1, data.length);
		return bank;
	}

	private static byte[] zlib(final byte[] data) {
		final Deflater deflater = new Deflater();
		deflater.setInput(data);
		deflater.finish();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write('Z');
		final byte[] chunk = new byte[4096];
		while (!deflater.finished()) {
			out.write(chunk, 0, deflater.deflate(chunk));
		}
		deflater.end();
		return out.toByteArray();
	}

	private static byte[] blte(final byte[][] banks, final int[] decodedSizes) {
		final ByteArrayOutputStream blte = new ByteArrayOutputStream();
		final int headerSize = 8 + 4 + (banks.length * 24);
		final ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN);
		header.put(new byte[] { 'B', 'L', 'T', 'E' }).putInt(headerSize);
		header.put((byte) 0x0F).put((byte) 0).put((byte) (banks.length >> 8)).put((byte) banks.length);
		for (int i = 0; i < banks.length; i++) {
			header.putInt(banks[i].length).putInt(decodedSizes[i]).put(new byte[16]);
		}
		blte.write(header.array(), 0, headerSize);
		for (final byte[] bank : banks) {
			blte.write(bank, 0, bank.length);
		}
		return blte.toByteArray();
	}

	private static ByteBuffer container(final byte[] body) {
		final ByteBuffer buffer = ByteBuffer.allocate(30 + body.length).order(ByteOrder.LITTLE_ENDIAN);
		buffer.put(new byte[16]).putInt(30 + body.length).putShort((short) 0).putInt(0).putInt(0).put(body);
		buffer.flip();
		return buffer;
	}

	private static byte[] data(final int size, final long seed, final boolean compressible) {
		final byte[] data = new byte[size];
		final Random random = new Random(seed);
		for (int i = 0; i < size; i++) {
			data[i] = (byte) (compressible ? (i / 7) + random.nextInt(3) : random.nextInt());
		}
		return data;
	}

	/** The parts of the file, stored as banks of these kinds: 'N' plain, 'Z' zlib. */
	private static final class Stored {
		private final byte[] whole;
		private final byte[] blte;

		private Stored(final byte[][] parts, final char[] kinds) {
			final ByteArrayOutputStream whole = new ByteArrayOutputStream();
			final byte[][] banks = new byte[parts.length][];
			final int[] sizes = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				whole.write(parts[i], 0, parts[i].length);
				banks[i] = kinds[i] == 'Z' ? zlib(parts[i]) : plain(parts[i]);
				sizes[i] = parts[i].length;
			}
			this.whole = whole.toByteArray();
			this.blte = blte(banks, sizes);
		}
	}

	private static Stored sample() {
		return new Stored(new byte[][] { data(70000, 1, false), data(150000, 2, true), data(3, 3, false),
				data(0, 4, false), data(200000, 5, true), data(65536, 6, false) },
				new char[] { 'N', 'Z', 'N', 'N', 'Z', 'N' });
	}

	private InputStream stream(final Stored stored) throws IOException {
		final ByteBuffer container = container(stored.blte);
		final byte[] fileBytes = new byte[LEAD + container.remaining() + 50];
		container.get(fileBytes, LEAD, container.remaining());
		Files.write(this.file, fileBytes);
		this.channel = FileChannel.open(this.file, StandardOpenOption.READ);
		final ByteBuffer header = container(stored.blte);
		header.position(30);
		final BLTEContent[] banks = BLTEContent.decodeContent(header);
		return new BankInputStream(() -> this.channel, LEAD + header.position(), banks);
	}

	private static byte[] decodedAtOnce(final Stored stored) throws IOException {
		final BankStream banks = new BankStream(container(stored.blte), null);
		final ByteBuffer out = ByteBuffer.allocate(stored.whole.length);
		while (banks.hasNextBank()) {
			banks.getBank(out);
		}
		return out.array();
	}

	private static byte[] readAll(final InputStream stream, final int readSize) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final byte[] buffer = new byte[readSize];
		int read;
		while ((read = stream.read(buffer, 0, readSize)) >= 0) {
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	@Test
	void givesTheBytesOfTheWholeFile() throws IOException {
		final Stored stored = sample();
		assertArrayEquals(stored.whole, decodedAtOnce(stored));
		for (final int readSize : new int[] { 1, 13, 4096, 1 << 20 }) {
			try (InputStream stream = stream(stored)) {
				assertEquals(stored.whole.length, stream.available());
				assertArrayEquals(stored.whole, readAll(stream, readSize), "reads of " + readSize);
				assertEquals(0, stream.available());
				assertEquals(-1, stream.read());
			}
			this.channel.close();
		}
	}

	@Test
	void singleBytesFollowTheData() throws IOException {
		final Stored stored = sample();
		try (InputStream stream = stream(stored)) {
			for (int i = 0; i < 1000; i++) {
				assertEquals(stored.whole[i] & 0xFF, stream.read());
			}
		}
	}

	@Test
	void skipsLandWhereReadsWould() throws IOException {
		final Stored stored = sample();
		final long[] skips = { 5, 69990, 100, 149000, 2000, 200001, 10 };
		try (InputStream stream = stream(stored)) {
			int position = 0;
			for (final long skip : skips) {
				assertEquals(skip, stream.skip(skip));
				position += skip;
				final byte[] next = new byte[7];
				final int read = stream.read(next);
				assertArrayEquals(Arrays.copyOfRange(stored.whole, position, position + read),
						Arrays.copyOf(next, read));
				position += read;
				assertEquals(stored.whole.length - position, stream.available());
			}
			assertEquals(stored.whole.length - position, stream.skip(Long.MAX_VALUE));
			assertEquals(-1, stream.read());
		}
	}

	@Test
	void plainBankOfTheWrongSizeIsRefused() throws IOException {
		final byte[] bank = plain(data(100, 7, false));
		final ByteBuffer header = container(blte(new byte[][] { bank }, new int[] { 99 }));
		Files.write(this.file, header.array());
		this.channel = FileChannel.open(this.file, StandardOpenOption.READ);
		header.position(30);
		final BLTEContent[] banks = BLTEContent.decodeContent(header);
		try (InputStream stream = new BankInputStream(() -> this.channel, header.position(), banks)) {
			assertThrows(MalformedCASCStructureException.class, stream::read);
		}
	}
}
