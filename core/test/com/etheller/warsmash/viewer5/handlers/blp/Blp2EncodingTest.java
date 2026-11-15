package com.etheller.warsmash.viewer5.handlers.blp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

/** Every BLP2 encoding a map picture can come in, built here as 4 x 4 images. */
class Blp2EncodingTest {
	private static final int PAYLOAD = 1200;

	@Test
	void palettedWithoutAlphaIsOpaque() throws IOException {
		final ByteBuffer file = header(1, 1, 0, 0, 4, 4);
		palette(file, 0, 10, 20, 30, 99);
		palette(file, 1, 0, 0, 255, 99);
		file.put(PAYLOAD + 5, (byte) 1);
		final ByteBuffer rgba = decode(file, 16);
		assertPixel(rgba, 0, 30, 20, 10, 255);
		assertPixel(rgba, 5, 255, 0, 0, 255);
	}

	@Test
	void palettedWithEightBitAlpha() throws IOException {
		final ByteBuffer file = header(1, 1, 8, 0, 4, 4);
		palette(file, 0, 10, 20, 30, 0);
		for (int i = 0; i < 16; i++) {
			file.put(PAYLOAD + 16 + i, (byte) (i * 16));
		}
		final ByteBuffer rgba = decode(file, 32);
		assertPixel(rgba, 0, 30, 20, 10, 0);
		assertPixel(rgba, 7, 30, 20, 10, 112);
	}

	@Test
	void palettedWithFourBitAlpha() throws IOException {
		final ByteBuffer file = header(1, 1, 4, 0, 4, 4);
		palette(file, 0, 10, 20, 30, 0);
		for (int i = 0; i < 8; i++) {
			file.put(PAYLOAD + 16 + i, (byte) ((((2 * i) + 1) << 4) | (2 * i)));
		}
		final ByteBuffer rgba = decode(file, 24);
		assertPixel(rgba, 0, 30, 20, 10, 0);
		assertPixel(rgba, 1, 30, 20, 10, 17);
		assertPixel(rgba, 15, 30, 20, 10, 255);
	}

	@Test
	void palettedWithOneBitAlpha() throws IOException {
		final ByteBuffer file = header(1, 1, 1, 0, 4, 4);
		palette(file, 0, 10, 20, 30, 0);
		file.put(PAYLOAD + 16, (byte) 0xAA);
		file.put(PAYLOAD + 17, (byte) 0xAA);
		final ByteBuffer rgba = decode(file, 18);
		assertPixel(rgba, 0, 30, 20, 10, 0);
		assertPixel(rgba, 1, 30, 20, 10, 255);
		assertPixel(rgba, 14, 30, 20, 10, 0);
		assertPixel(rgba, 15, 30, 20, 10, 255);
	}

	@Test
	void uncompressedBgra() throws IOException {
		final ByteBuffer file = header(1, 3, 8, 0, 4, 4);
		file.put(PAYLOAD, (byte) 1);
		file.put(PAYLOAD + 1, (byte) 2);
		file.put(PAYLOAD + 2, (byte) 3);
		file.put(PAYLOAD + 3, (byte) 4);
		assertPixel(decode(file, 64), 0, 3, 2, 1, 4);
		final ByteBuffer opaque = header(1, 3, 0, 0, 4, 4);
		opaque.put(PAYLOAD + 3, (byte) 4);
		assertPixel(decode(opaque, 64), 0, 0, 0, 0, 255);
	}

	@Test
	void dxt1WithoutAlphaTurnsTransparentBlackOpaque() throws IOException {
		final ByteBuffer file = header(1, 2, 0, 0, 4, 4);
		// Color 0 below color 1 makes index 3 transparent.
		file.putInt(PAYLOAD, 0xFFFF0000);
		file.putInt(PAYLOAD + 4, 0xFFFFFFFF);
		assertPixel(decode(file, 8), 3, 0, 0, 0, 255);
	}

	@Test
	void dxt1WithOneBitAlphaKeepsTransparentBlack() throws IOException {
		final ByteBuffer file = header(1, 2, 1, 0, 4, 4);
		file.putInt(PAYLOAD, 0xFFFF0000);
		file.putInt(PAYLOAD + 4, 0xFFFFFFFF);
		assertPixel(decode(file, 8), 3, 0, 0, 0, 0);
		final ByteBuffer solid = header(1, 2, 1, 0, 4, 4);
		solid.putInt(PAYLOAD, 0x0000F800);
		assertPixel(decode(solid, 8), 9, 255, 0, 0, 255);
	}

	@Test
	void dxt3ExplicitAlpha() throws IOException {
		final ByteBuffer file = header(1, 2, 8, 1, 4, 4);
		file.put(PAYLOAD, (byte) 0x31);
		file.putInt(PAYLOAD + 8, 0x0000F800);
		final ByteBuffer rgba = decode(file, 16);
		assertPixel(rgba, 0, 255, 0, 0, 17);
		assertPixel(rgba, 1, 255, 0, 0, 51);
	}

	@Test
	void dxt5InterpolatedAlpha() throws IOException {
		final ByteBuffer file = header(1, 2, 8, 7, 4, 4);
		file.put(PAYLOAD, (byte) 200);
		file.put(PAYLOAD + 1, (byte) 100);
		file.put(PAYLOAD + 2, (byte) 0x08);
		file.putInt(PAYLOAD + 8, 0x0000F800);
		final ByteBuffer rgba = decode(file, 16);
		assertPixel(rgba, 0, 255, 0, 0, 200);
		assertPixel(rgba, 1, 255, 0, 0, 100);
	}

	@Test
	void mipLevelsOfADxtImageAreDecodedToo() throws IOException {
		final ByteBuffer file = header(1, 2, 0, 0, 8, 8);
		file.put(11, (byte) 1);
		file.putInt(20, PAYLOAD);
		file.putInt(84, 32);
		file.putInt(24, PAYLOAD + 32);
		file.putInt(88, 8);
		for (int i = 0; i < 4; i++) {
			file.putInt(PAYLOAD + (i * 8), 0x0000F800);
		}
		file.putInt(PAYLOAD + 32, 0x000007E0);
		file.limit(PAYLOAD + 40);
		final MipLevels levels = BlpDecoder.decode(file, 0, true).getLevels();
		assertEquals(2, levels.count());
		assertEquals(4, levels.getWidth(1));
		assertEquals(255, levels.getRgba(0).get(0) & 0xFF);
		assertEquals(255, levels.getRgba(1).get(1) & 0xFF);
	}

	@Test
	void unknownBlp2EncodingIsRejected() {
		assertTrue(!BlpDecoder.isBlp(header(1, 9, 0, 0, 4, 4)));
		assertTrue(!BlpDecoder.isBlp(header(1, 2, 0, 5, 4, 4)));
	}

	private static ByteBuffer decode(final ByteBuffer file, final int payloadBytes) throws IOException {
		file.putInt(20, PAYLOAD);
		file.putInt(84, payloadBytes);
		file.limit(PAYLOAD + payloadBytes);
		final BlpDecoder image = BlpDecoder.decode(file);
		assertEquals(4, image.getWidth());
		assertEquals(4, image.getHeight());
		return image.getRgba();
	}

	private static ByteBuffer header(final int content, final int encoding, final int alphaDepth,
			final int alphaType, final int width, final int height) {
		final ByteBuffer file = ByteBuffer.allocate(PAYLOAD + 512).order(ByteOrder.LITTLE_ENDIAN);
		file.putInt(0, 0x32504C42);
		file.putInt(4, content);
		file.put(8, (byte) encoding);
		file.put(9, (byte) alphaDepth);
		file.put(10, (byte) alphaType);
		file.putInt(12, width);
		file.putInt(16, height);
		file.putInt(20, PAYLOAD);
		file.putInt(84, 1);
		file.limit(PAYLOAD + 512);
		return file;
	}

	private static void palette(final ByteBuffer file, final int index, final int blue, final int green,
			final int red, final int alpha) {
		final int at = 148 + (index * 4);
		file.put(at, (byte) blue);
		file.put(at + 1, (byte) green);
		file.put(at + 2, (byte) red);
		file.put(at + 3, (byte) alpha);
	}

	private static void assertPixel(final ByteBuffer rgba, final int pixel, final int red, final int green,
			final int blue, final int alpha) {
		assertEquals(red, rgba.get(pixel * 4) & 0xFF, "red of pixel " + pixel);
		assertEquals(green, rgba.get((pixel * 4) + 1) & 0xFF, "green of pixel " + pixel);
		assertEquals(blue, rgba.get((pixel * 4) + 2) & 0xFF, "blue of pixel " + pixel);
		assertEquals(alpha, rgba.get((pixel * 4) + 3) & 0xFF, "alpha of pixel " + pixel);
	}
}
