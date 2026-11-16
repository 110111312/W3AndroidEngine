package com.hiveworkshop.blizzard.casc.storage;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.hiveworkshop.blizzard.casc.nio.MalformedCASCStructureException;

/**
 * Decodes the banks of stored BLTE data while it is read, straight from the data file. Only the part read is held in
 * memory, so a large file can be read through, or skipped through, without a buffer of its size. Gives the same bytes
 * as {@link BankStream}.
 */
public class BankInputStream extends InputStream {
	/** The data file the banks are in. Asked again for every read, since a reader's interrupt closes a channel. */
	public interface DataFile {
		FileChannel channel() throws IOException;
	}

	/** A channel reads a heap buffer through a direct buffer as big as the read; Android counts those on its heap. */
	private static final int READ_CHUNK = 1 << 16;

	private final DataFile dataFile;
	private final BLTEContent[] banks;
	private int bank = -1;
	private long nextBankOffset;
	private long bankLeft;
	private long totalLeft;
	private boolean compressed;
	/** Uncompressed: where the bank's next bytes are. Compressed: where its next unread encoded bytes are. */
	private long dataOffset;
	private long encodedEnd;
	private Inflater inflater;
	private byte[] input;
	private byte[] skipBuffer;
	private final byte[] single = new byte[1];
	private final byte[] readOne = new byte[1];
	private boolean closed;

	/**
	 * @param dataOffset where the first bank's encoded data starts in the data file, after the BLTE header
	 * @param banks      the banks of the BLTE header, which the caller has checked to fit the stored data
	 */
	public BankInputStream(final DataFile dataFile, final long dataOffset, final BLTEContent[] banks) {
		this.dataFile = dataFile;
		this.nextBankOffset = dataOffset;
		this.banks = banks;
		long total = 0;
		for (final BLTEContent content : banks) {
			total += content.getDecompressedSize();
		}
		this.totalLeft = total;
	}

	/** @return false at the end of the data */
	private boolean nextBytes() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		while (this.bankLeft == 0) {
			if ((this.bank + 1) >= this.banks.length) {
				return false;
			}
			openBank(this.bank + 1);
		}
		return true;
	}

	private void openBank(final int index) throws IOException {
		final BLTEContent content = this.banks[index];
		final long start = this.nextBankOffset;
		final long encodedSize = content.getCompressedSize();
		final long decodedSize = content.getDecompressedSize();
		this.bank = index;
		this.nextBankOffset = start + encodedSize;
		if (encodedSize < 1) {
			throw new MalformedCASCStructureException("bank without an encoding mode");
		}
		final char encodingMode = (char) readByte(start);
		switch (encodingMode) {
		case 'N':
			if ((encodedSize - 1) != decodedSize) {
				throw new MalformedCASCStructureException("not enough uncompressed bytes");
			}
			this.compressed = false;
			this.dataOffset = start + 1;
			break;
		case 'Z':
			if (this.inflater == null) {
				this.inflater = new Inflater();
				this.input = new byte[(int) Math.min(READ_CHUNK, Math.max(encodedSize - 1, 1))];
			}
			else {
				this.inflater.reset();
			}
			this.compressed = true;
			this.dataOffset = start + 1;
			this.encodedEnd = start + encodedSize;
			break;
		default:
			throw new UnsupportedEncodingException("unsupported encoding mode: " + encodingMode);
		}
		this.bankLeft = decodedSize;
	}

	private int readByte(final long offset) throws IOException {
		final ByteBuffer buffer = ByteBuffer.wrap(this.single);
		readFully(buffer, offset);
		return this.single[0] & 0xFF;
	}

	private void readFully(final ByteBuffer buffer, final long offset) throws IOException {
		final FileChannel channel = this.dataFile.channel();
		final int start = buffer.position();
		while (buffer.hasRemaining()) {
			if (channel.read(buffer, (offset + buffer.position()) - start) < 0) {
				throw new EOFException("unexpected end of file");
			}
		}
	}

	@Override
	public int read() throws IOException {
		return read(this.readOne, 0, 1) < 0 ? -1 : this.readOne[0] & 0xFF;
	}

	@Override
	public int read(final byte[] buffer, final int offset, final int length) throws IOException {
		if ((offset < 0) || (length < 0) || (length > (buffer.length - offset))) {
			throw new IndexOutOfBoundsException();
		}
		if (length == 0) {
			return 0;
		}
		if (!nextBytes()) {
			return -1;
		}
		final int wanted = (int) Math.min(length, this.bankLeft);
		final int got;
		if (this.compressed) {
			got = inflate(buffer, offset, wanted);
		}
		else {
			got = Math.min(wanted, READ_CHUNK);
			readFully(ByteBuffer.wrap(buffer, offset, got), this.dataOffset);
			this.dataOffset += got;
		}
		this.bankLeft -= got;
		this.totalLeft -= got;
		return got;
	}

	private int inflate(final byte[] buffer, final int offset, final int length) throws IOException {
		try {
			while (true) {
				final int got = this.inflater.inflate(buffer, offset, length);
				if (got > 0) {
					return got;
				}
				if (this.inflater.finished() || this.inflater.needsDictionary()) {
					throw new MalformedCASCStructureException("not enough bytes generated");
				}
				if (this.inflater.needsInput()) {
					final int count = (int) Math.min(this.input.length, this.encodedEnd - this.dataOffset);
					if (count <= 0) {
						throw new MalformedCASCStructureException("not enough bytes generated");
					}
					readFully(ByteBuffer.wrap(this.input, 0, count), this.dataOffset);
					this.dataOffset += count;
					this.inflater.setInput(this.input, 0, count);
				}
			}
		}
		catch (final DataFormatException e) {
			throw new MalformedCASCStructureException("zlib inflate exception", e);
		}
	}

	@Override
	public long skip(final long count) throws IOException {
		long left = count;
		while ((left > 0) && nextBytes()) {
			if (this.compressed) {
				if (this.skipBuffer == null) {
					this.skipBuffer = new byte[8192];
				}
				final int got = read(this.skipBuffer, 0, (int) Math.min(left, this.skipBuffer.length));
				left -= got;
			}
			else {
				final long skipped = Math.min(left, this.bankLeft);
				this.dataOffset += skipped;
				this.bankLeft -= skipped;
				this.totalLeft -= skipped;
				left -= skipped;
			}
		}
		return count - Math.max(left, 0);
	}

	/** The bytes left to read, as a fully read file's stream gives. */
	@Override
	public int available() throws IOException {
		if (this.closed) {
			throw new IOException("stream closed");
		}
		return (int) Math.min(Integer.MAX_VALUE, this.totalLeft);
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			if (this.inflater != null) {
				// releases native zlib memory now instead of whenever the GC runs
				this.inflater.end();
			}
		}
	}
}
