package com.etheller.warsmash.viewer5.handlers.mdx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.etheller.warsmash.util.RenderMathUtils;

class GeosetPickTest {
	/** A grid of quads in which every corner is stored once for each of its triangles, as seams do in a model. */
	private static float[] seamedGrid(final int size, final char[] faces) {
		final float[] vertices = new float[size * size * 6 * 3];
		int vertex = 0;
		int face = 0;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				final float[][] corners = { { x, y, (x + y) % 3 }, { x + 1, y, (x + 1 + y) % 3 },
						{ x, y + 1, (x + y + 1) % 3 }, { x + 1, y, (x + 1 + y) % 3 },
						{ x + 1, y + 1, (x + y + 2) % 3 }, { x, y + 1, (x + y + 1) % 3 } };
				for (final float[] corner : corners) {
					vertices[vertex * 3] = corner[0];
					vertices[(vertex * 3) + 1] = corner[1];
					vertices[(vertex * 3) + 2] = corner[2];
					faces[face++] = (char) vertex++;
				}
			}
		}
		return vertices;
	}

	@Test
	void uniquePositionsKeepTheTrianglesAndDropTheRepeats() {
		final int size = 12;
		final char[] faces = new char[size * size * 6];
		final float[] vertices = seamedGrid(size, faces);
		final char[] oldFaces = faces.clone();
		final float[] unique = Geoset.uniquePositions(vertices, faces);
		assertTrue(unique.length < vertices.length);
		assertEquals((size + 1) * (size + 1) * 3, unique.length);
		for (int i = 0; i < faces.length; i++) {
			for (int k = 0; k < 3; k++) {
				assertEquals(vertices[(oldFaces[i] * 3) + k], unique[(faces[i] * 3) + k]);
			}
		}
	}

	@Test
	void rayTestsAgreeBetweenTheArraysAndTheNativeBuffers() {
		final int size = 12;
		final char[] faces = new char[size * size * 6];
		final float[] vertices = seamedGrid(size, faces);
		final char[] uniqueFaces = faces.clone();
		final float[] unique = Geoset.uniquePositions(vertices, uniqueFaces);
		final FloatBuffer bufferVertices = ByteBuffer.allocateDirect(unique.length * 4).order(ByteOrder.nativeOrder())
				.asFloatBuffer().put(unique);
		final CharBuffer bufferFaces = ByteBuffer.allocateDirect(uniqueFaces.length * 2).order(ByteOrder.nativeOrder())
				.asCharBuffer().put(uniqueFaces);
		final Random random = new Random(3);
		int hits = 0;
		for (int i = 0; i < 400; i++) {
			final Vector3 origin = new Vector3(random.nextFloat() * (size + 4) - 2, random.nextFloat() * (size + 4) - 2,
					10 + random.nextFloat());
			final Vector3 target = new Vector3(random.nextFloat() * size, random.nextFloat() * size, 0);
			final Ray ray = new Ray(origin, target.sub(origin).nor());
			final Vector3 expected = new Vector3();
			final Vector3 actual = new Vector3();
			final boolean expectedHit = RenderMathUtils.intersectRayTriangles(ray, vertices, faces, 3, expected);
			final boolean actualHit = RenderMathUtils.intersectRayTriangles(ray, bufferVertices, bufferFaces, 3,
					actual);
			assertEquals(expectedHit, actualHit);
			if (expectedHit) {
				hits++;
				assertEquals(expected, actual);
			}
		}
		assertTrue(hits > 100);
	}
}
