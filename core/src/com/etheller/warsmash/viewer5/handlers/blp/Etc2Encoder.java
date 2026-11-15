package com.etheller.warsmash.viewer5.handlers.blp;

import java.util.stream.IntStream;

/**
 * Encodes RGBA pixels into the block formats every OpenGL ES 3.0 GPU reads: ETC2 RGB8 (4 bits a pixel), ETC2 RGBA8
 * with an EAC alpha block (8 bits) and EAC RG11 (8 bits). The color blocks use the ETC1 subset of ETC2 (two
 * sub blocks, one brightness table each), which any ETC2 decoder reads as it is; the encoder picks sub block colors
 * from the averages and the best brightness table by squared error, which is fast enough for a phone's CPU.
 */
public final class Etc2Encoder {
	private static final int[][] COLOR_MODIFIERS = { { 2, 8 }, { 5, 17 }, { 9, 29 }, { 13, 42 }, { 18, 60 },
		{ 24, 80 }, { 33, 106 }, { 47, 183 } };
	private static final int[][] ALPHA_MODIFIERS = { { -3, -6, -9, -15, 2, 5, 8, 14 }, { -3, -7, -10, -13, 2, 6, 9, 12 },
		{ -2, -5, -8, -13, 1, 4, 7, 12 }, { -2, -4, -6, -13, 1, 3, 5, 12 }, { -3, -6, -8, -12, 2, 5, 7, 11 },
		{ -3, -7, -9, -11, 2, 6, 8, 10 }, { -4, -7, -8, -11, 3, 6, 7, 10 }, { -3, -5, -8, -11, 2, 4, 7, 10 },
		{ -2, -6, -8, -10, 1, 5, 7, 9 }, { -2, -5, -8, -10, 1, 4, 7, 9 }, { -2, -4, -8, -10, 1, 3, 7, 9 },
		{ -2, -5, -7, -10, 1, 4, 6, 9 }, { -3, -4, -7, -10, 2, 3, 6, 9 }, { -1, -2, -3, -10, 0, 1, 2, 9 },
		{ -4, -6, -8, -9, 3, 5, 7, 8 }, { -3, -5, -7, -9, 2, 4, 6, 8 } };
	/** The table whose index 4 is a zero modifier: a block of one value is stored exactly with it. */
	private static final int FLAT_ALPHA_TABLE = 13;
	private static final int FLAT_ALPHA_INDEX = 4;
	/** Blocks of at least this many in an image are encoded on several threads. */
	private static final int PARALLEL_BLOCKS = 2048;

	private Etc2Encoder() {
	}

	/** @return the ETC2 RGB8 blocks of a width * height RGBA image, rows from the top; alpha is ignored */
	public static byte[] encodeRgb(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 8, KIND_RGB);
	}

	/** @return the ETC2 RGBA8 blocks (EAC alpha block, then the RGB block) of a width * height RGBA image */
	public static byte[] encodeRgba(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 16, KIND_RGBA);
	}

	/** @return the EAC RG11 blocks (red block, then green block) of the red and green channels of an RGBA image */
	public static byte[] encodeRg(final byte[] rgba, final int width, final int height) {
		return encode(rgba, width, height, 16, KIND_RG);
	}

	/** The bytes of the blocks of an image of this size, in any of the three formats. */
	public static int byteCount(final int width, final int height, final int blockBytes) {
		return Math.max(1, (width + 3) / 4) * Math.max(1, (height + 3) / 4) * blockBytes;
	}

	private static final int KIND_RGB = 0;
	private static final int KIND_RGBA = 1;
	private static final int KIND_RG = 2;

	private static byte[] encode(final byte[] rgba, final int width, final int height, final int blockBytes,
			final int kind) {
		final int blocksWide = Math.max(1, (width + 3) / 4);
		final int blocksHigh = Math.max(1, (height + 3) / 4);
		final byte[] out = new byte[blocksWide * blocksHigh * blockBytes];
		final IntStream rows = IntStream.range(0, blocksHigh);
		if ((blocksWide * blocksHigh) >= PARALLEL_BLOCKS) {
			rows.parallel().forEach(row -> encodeRow(rgba, width, height, row, blocksWide, out, blockBytes, kind));
		}
		else {
			rows.forEach(row -> encodeRow(rgba, width, height, row, blocksWide, out, blockBytes, kind));
		}
		return out;
	}

	private static void encodeRow(final byte[] rgba, final int width, final int height, final int blockY,
			final int blocksWide, final byte[] out, final int blockBytes, final int kind) {
		final Scratch scratch = new Scratch();
		for (int blockX = 0; blockX < blocksWide; blockX++) {
			scratch.load(rgba, width, height, blockX * 4, blockY * 4);
			int offset = ((blockY * blocksWide) + blockX) * blockBytes;
			if (kind == KIND_RG) {
				putLong(out, offset, scratch.encodeChannel(scratch.r));
				putLong(out, offset + 8, scratch.encodeChannel(scratch.g));
			}
			else {
				if (kind == KIND_RGBA) {
					putLong(out, offset, scratch.encodeChannel(scratch.a));
					offset += 8;
				}
				putLong(out, offset, scratch.encodeColor());
			}
		}
	}

	private static void putLong(final byte[] out, final int offset, final long value) {
		for (int i = 0; i < 8; i++) {
			out[offset + i] = (byte) (value >>> (56 - (8 * i)));
		}
	}

	private static int clamp(final int value) {
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	private static int expand4(final int value) {
		return value * 17;
	}

	private static int expand5(final int value) {
		return (value << 3) | (value >> 2);
	}

	/** The 16 pixels of a block, each channel indexed y * 4 + x, and the working arrays of the encoder. */
	private static final class Scratch {
		final int[] r = new int[16];
		final int[] g = new int[16];
		final int[] b = new int[16];
		final int[] a = new int[16];
		private final int[] tableIndices = new int[16];
		private final int[] flipIndices = new int[16];
		private final int[] sub = new int[8];
		private final int[] result = new int[1];
		private long layoutError;

		void load(final byte[] rgba, final int width, final int height, final int x0, final int y0) {
			for (int y = 0; y < 4; y++) {
				final int py = Math.min(y0 + y, height - 1);
				for (int x = 0; x < 4; x++) {
					final int px = Math.min(x0 + x, width - 1);
					final int at = ((py * width) + px) * 4;
					final int i = (y * 4) + x;
					this.r[i] = rgba[at] & 0xFF;
					this.g[i] = rgba[at + 1] & 0xFF;
					this.b[i] = rgba[at + 2] & 0xFF;
					this.a[i] = rgba[at + 3] & 0xFF;
				}
			}
		}

		/** One 8 bit channel as an EAC block, which is also the layout of an RG11 half. */
		long encodeChannel(final int[] v) {
			int min = 255;
			int max = 0;
			for (int i = 0; i < 16; i++) {
				min = Math.min(min, v[i]);
				max = Math.max(max, v[i]);
			}
			if (min == max) {
				long block = ((long) min << 56) | (1L << 52) | ((long) FLAT_ALPHA_TABLE << 48);
				for (int p = 0; p < 16; p++) {
					block |= (long) FLAT_ALPHA_INDEX << (45 - (3 * p));
				}
				return block;
			}
			long bestBlock = 0;
			long bestError = Long.MAX_VALUE;
			for (int table = 0; table < 16; table++) {
				final int[] mods = ALPHA_MODIFIERS[table];
				final int tableMin = mods[3];
				final int tableMax = mods[7];
				final int range = tableMax - tableMin;
				final int multiplier = Math.max(1, Math.min(15, ((max - min) + range - 1) / range));
				final int base = clamp((int) Math.round(((min + max) / 2.0)
						- (multiplier * (tableMin + tableMax) / 2.0)));
				long error = 0;
				long indices = 0;
				for (int p = 0; (p < 16) && (error < bestError); p++) {
					final int value = v[((p & 3) * 4) + (p >> 2)];
					int bestIndex = 0;
					int bestDelta = Integer.MAX_VALUE;
					for (int i = 0; i < 8; i++) {
						final int delta = Math.abs(clamp(base + (mods[i] * multiplier)) - value);
						if (delta < bestDelta) {
							bestDelta = delta;
							bestIndex = i;
						}
					}
					error += (long) bestDelta * bestDelta;
					indices |= (long) bestIndex << (45 - (3 * p));
				}
				if (error < bestError) {
					bestError = error;
					bestBlock = ((long) base << 56) | ((long) multiplier << 52) | ((long) table << 48) | indices;
				}
			}
			return bestBlock;
		}

		/** The pixels as an ETC1 style block: the better of the two sub block layouts. */
		long encodeColor() {
			final long first = encodeLayout(0);
			final long firstError = this.layoutError;
			final long second = encodeLayout(1);
			return this.layoutError < firstError ? second : first;
		}

		/** Encodes the block with the two sub blocks side by side (flip 0) or one above the other (flip 1). */
		private long encodeLayout(final int flip) {
			long totalError = 0;
			final int[] base = new int[6];
			final int[] quant5 = new int[6];
			final int[] quant4 = new int[6];
			final int[] tables = new int[2];
			for (int s = 0; s < 2; s++) {
				fillSub(flip, s);
				int sr = 0;
				int sg = 0;
				int sb = 0;
				for (int i = 0; i < 8; i++) {
					sr += this.r[this.sub[i]];
					sg += this.g[this.sub[i]];
					sb += this.b[this.sub[i]];
				}
				final int[] avg = { (sr + 4) >> 3, (sg + 4) >> 3, (sb + 4) >> 3 };
				for (int c = 0; c < 3; c++) {
					quant5[(s * 3) + c] = ((avg[c] * 31) + 127) / 255;
					quant4[(s * 3) + c] = ((avg[c] * 15) + 127) / 255;
				}
			}
			boolean differential = true;
			for (int c = 0; c < 3; c++) {
				final int delta = quant5[3 + c] - quant5[c];
				if ((delta < -4) || (delta > 3)) {
					differential = false;
				}
			}
			for (int i = 0; i < 6; i++) {
				base[i] = differential ? expand5(quant5[i]) : expand4(quant4[i]);
			}
			for (int s = 0; s < 2; s++) {
				fillSub(flip, s);
				totalError += bestTable(base[s * 3], base[(s * 3) + 1], base[(s * 3) + 2]);
				tables[s] = this.result[0];
			}
			this.layoutError = totalError;
			long block = 0;
			if (differential) {
				block |= ((long) quant5[0] << 59) | ((long) ((quant5[3] - quant5[0]) & 7) << 56)
						| ((long) quant5[1] << 51) | ((long) ((quant5[4] - quant5[1]) & 7) << 48)
						| ((long) quant5[2] << 43) | ((long) ((quant5[5] - quant5[2]) & 7) << 40) | (1L << 33);
			}
			else {
				block |= ((long) quant4[0] << 60) | ((long) quant4[3] << 56) | ((long) quant4[1] << 52)
						| ((long) quant4[4] << 48) | ((long) quant4[2] << 44) | ((long) quant4[5] << 40);
			}
			block |= ((long) tables[0] << 37) | ((long) tables[1] << 34) | ((long) flip << 32);
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					final int index = this.flipIndices[(y * 4) + x];
					final int p = (x * 4) + y;
					block |= ((long) (index >> 1) << (16 + p)) | ((long) (index & 1) << p);
				}
			}
			return block;
		}

		private void fillSub(final int flip, final int s) {
			int n = 0;
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					final boolean inSub = flip == 0 ? (x >> 1) == s : (y >> 1) == s;
					if (inSub) {
						this.sub[n++] = (y * 4) + x;
					}
				}
			}
		}

		/**
		 * Picks the brightness table of the current sub block for this base color; the table in result[0], its pixel
		 * indices in flipIndices, and the squared error as the return value.
		 */
		private long bestTable(final int baseR, final int baseG, final int baseB) {
			long bestError = Long.MAX_VALUE;
			int bestTable = 0;
			for (int table = 0; table < 8; table++) {
				final int small = COLOR_MODIFIERS[table][0];
				final int large = COLOR_MODIFIERS[table][1];
				final int threshold = (small + large) / 2;
				long error = 0;
				for (int n = 0; (n < 8) && (error < bestError); n++) {
					final int i = this.sub[n];
					final int delta = ((this.r[i] - baseR) + (this.g[i] - baseG) + (this.b[i] - baseB)) / 3;
					final int index;
					final int modifier;
					if (delta >= 0) {
						index = delta >= threshold ? 1 : 0;
						modifier = index == 1 ? large : small;
					}
					else {
						index = -delta >= threshold ? 3 : 2;
						modifier = index == 3 ? -large : -small;
					}
					final int er = clamp(baseR + modifier) - this.r[i];
					final int eg = clamp(baseG + modifier) - this.g[i];
					final int eb = clamp(baseB + modifier) - this.b[i];
					error += (er * er) + (eg * eg) + (eb * eb);
					this.tableIndices[i] = index;
				}
				if (error < bestError) {
					bestError = error;
					bestTable = table;
					for (int n = 0; n < 8; n++) {
						this.flipIndices[this.sub[n]] = this.tableIndices[this.sub[n]];
					}
				}
			}
			this.result[0] = bestTable;
			return bestError;
		}
	}
}
