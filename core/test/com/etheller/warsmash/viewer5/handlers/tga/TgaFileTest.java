package com.etheller.warsmash.viewer5.handlers.tga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.ArgbImage;

class TgaFileTest {
	private static final int RED = 0xFFFF0000;
	private static final int GREEN = 0xFF00FF00;
	private static final int BLUE = 0xFF0000FF;
	private static final int WHITE = 0xFFFFFFFF;

	private static byte[] tga(final int idLength, final int colorMapType, final int imageType, final int mapFirst,
			final int mapLength, final int mapBits, final int w, final int h, final int depth, final int descriptor,
			final byte[] body) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(idLength);
		out.write(colorMapType);
		out.write(imageType);
		out.write(mapFirst & 0xFF);
		out.write(mapFirst >> 8);
		out.write(mapLength & 0xFF);
		out.write(mapLength >> 8);
		out.write(mapBits);
		out.write(new byte[4]);
		out.write(w & 0xFF);
		out.write(w >> 8);
		out.write(h & 0xFF);
		out.write(h >> 8);
		out.write(depth);
		out.write(descriptor);
		out.write(body);
		return out.toByteArray();
	}

	private static byte[] bytes(final int... values) {
		final byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	private static int[] decode(final byte[] file) throws IOException {
		final ArgbImage image = TgaFile.readTGAPixels("test.tga", new ByteArrayInputStream(file));
		final int[] pixels = new int[image.getWidth() * image.getHeight()];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = image.getRGB(i % image.getWidth(), i / image.getWidth());
		}
		return pixels;
	}

	// 2x2 BGR pixels, file order: red, green / blue, white
	private static final byte[] BGR_ROWS = bytes(0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255);

	@Test
	void trueColorBottomUp() throws IOException {
		// bottom-left origin: the first row in the file is the bottom row
		final int[] px = decode(tga(0, 0, 2, 0, 0, 0, 2, 2, 24, 0, BGR_ROWS));
		assertEquals(BLUE, px[0]);
		assertEquals(WHITE, px[1]);
		assertEquals(RED, px[2]);
		assertEquals(GREEN, px[3]);
	}

	@Test
	void trueColorTopLeftKeepsFileOrder() throws IOException {
		final int[] px = decode(tga(0, 0, 2, 0, 0, 0, 2, 2, 24, 0x20, BGR_ROWS));
		assertEquals(RED, px[0]);
		assertEquals(GREEN, px[1]);
		assertEquals(BLUE, px[2]);
		assertEquals(WHITE, px[3]);
	}

	@Test
	void imageIdFieldIsSkipped() throws IOException {
		final byte[] id = bytes('s', 'o', 'm', 'e', ' ', 'i', 'd');
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(id);
		body.write(BGR_ROWS);
		final int[] px = decode(tga(id.length, 0, 2, 0, 0, 0, 2, 2, 24, 0x20, body.toByteArray()));
		assertEquals(RED, px[0]);
		assertEquals(WHITE, px[3]);
	}

	@Test
	void unusedColorMapHeaderOnTrueColorImageIsSkipped() throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(new byte[2 * 3]);
		body.write(BGR_ROWS);
		final int[] px = decode(tga(0, 1, 2, 0, 2, 24, 2, 2, 24, 0x20, body.toByteArray()));
		assertEquals(RED, px[0]);
		assertEquals(BLUE, px[2]);
	}

	@Test
	void colorMappedImage() throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(bytes(7, 7));
		// palette of 3 BGR entries, indices 0..2 are red, green, blue
		body.write(bytes(0, 0, 255, 0, 255, 0, 255, 0, 0));
		body.write(bytes(0, 1, 2, 0));
		final int[] px = decode(tga(2, 1, 1, 0, 3, 24, 2, 2, 8, 0x20, body.toByteArray()));
		assertEquals(RED, px[0]);
		assertEquals(GREEN, px[1]);
		assertEquals(BLUE, px[2]);
		assertEquals(RED, px[3]);
	}

	@Test
	void colorMapWithFirstEntryOffset() throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(bytes(0, 0, 255, 0, 255, 0));
		body.write(bytes(1, 2, 2, 1));
		final int[] px = decode(tga(0, 1, 1, 1, 2, 24, 2, 2, 8, 0x20, body.toByteArray()));
		assertEquals(RED, px[0]);
		assertEquals(GREEN, px[1]);
	}

	@Test
	void runLengthColorMappedImage() throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(bytes(0, 0, 255, 255, 0, 0));
		// a run of 3 index 1, then a raw packet of 1 index 0
		body.write(bytes(0x82, 1, 0x00, 0));
		final int[] px = decode(tga(0, 1, 9, 0, 2, 24, 2, 2, 8, 0x20, body.toByteArray()));
		assertEquals(BLUE, px[0]);
		assertEquals(BLUE, px[1]);
		assertEquals(BLUE, px[2]);
		assertEquals(RED, px[3]);
	}

	@Test
	void runLengthTrueColorWithIdField() throws IOException {
		final ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write(bytes(1, 2, 3));
		// a run of 4 white pixels
		body.write(bytes(0x83, 255, 255, 255));
		final int[] px = decode(tga(3, 0, 10, 0, 0, 0, 2, 2, 24, 0x20, body.toByteArray()));
		for (final int p : px) {
			assertEquals(WHITE, p);
		}
	}

	@Test
	void thirtyTwoBitKeepsAlpha() throws IOException {
		assertEquals(0x80FF0000, decode(tga(0, 0, 2, 0, 0, 0, 1, 1, 32, 0x28, bytes(0, 0, 255, 128)))[0]);
	}

	@Test
	void greyscale() throws IOException {
		final int[] px = decode(tga(0, 0, 3, 0, 0, 0, 2, 1, 8, 0x20, bytes(0, 255)));
		assertEquals(0xFF000000, px[0]);
		assertEquals(WHITE, px[1]);
	}

	@Test
	void sixteenBit555() throws IOException {
		// 0x7C00 is full red in 5-5-5
		final int[] px = decode(tga(0, 0, 2, 0, 0, 0, 1, 1, 16, 0x20, bytes(0x00, 0x7C)));
		assertEquals(RED, px[0]);
	}

	@Test
	void rightToLeftOrigin() throws IOException {
		final int[] px = decode(tga(0, 0, 2, 0, 0, 0, 2, 1, 24, 0x30, bytes(0, 0, 255, 0, 255, 0)));
		assertEquals(GREEN, px[0]);
		assertEquals(RED, px[1]);
	}

	@Test
	void truncatedDataAndBadColorMapIndexFail() {
		assertThrows(IllegalStateException.class,
				() -> decode(tga(0, 0, 2, 0, 0, 0, 2, 2, 24, 0x20, bytes(1, 2, 3))));
		assertThrows(IllegalStateException.class,
				() -> decode(tga(0, 1, 1, 0, 1, 24, 1, 1, 8, 0x20, bytes(0, 0, 0, 5))));
		assertThrows(IllegalStateException.class, () -> decode(tga(0, 0, 0, 0, 0, 0, 1, 1, 24, 0x20, bytes(0, 0, 0))));
	}
}
