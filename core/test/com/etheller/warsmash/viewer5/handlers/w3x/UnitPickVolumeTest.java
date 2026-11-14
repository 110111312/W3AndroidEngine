package com.etheller.warsmash.viewer5.handlers.w3x;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.math.Vector3;

class UnitPickVolumeTest {
	private static final float SELECTION_CIRCLE_FACTOR = 72f;

	private static Ray rayDownAt(final float x, final float y) {
		return new Ray(new Vector3(x, y, 1000), new Vector3(0, 0, -1));
	}

	private static float pickRadius(final float selectionScale) {
		final float size = selectionScale * SELECTION_CIRCLE_FACTOR;
		return size * 0.65f;
	}

	@Test
	void rayThroughTheCylinderHitsInsideIt() {
		final Vector3 out = new Vector3();
		assertTrue(MapViewerSelection.intersectRayWithCylinder(rayDownAt(10, 10), 0, 0, 0, 40, 100, out));
		assertEquals(50, out.z, 0.01f);
	}

	@Test
	void rayBesideOrAboveMisses() {
		final Vector3 out = new Vector3();
		assertFalse(MapViewerSelection.intersectRayWithCylinder(rayDownAt(41, 0), 0, 0, 0, 40, 100, out));
		final Ray sideways = new Ray(new Vector3(-500, 0, 150), new Vector3(1, 0, 0));
		assertFalse(MapViewerSelection.intersectRayWithCylinder(sideways, 0, 0, 0, 40, 100, out));
		final Ray low = new Ray(new Vector3(-500, 0, 50), new Vector3(1, 0, 0));
		assertTrue(MapViewerSelection.intersectRayWithCylinder(low, 0, 0, 0, 40, 100, out));
	}

	@Test
	void slantedRayThroughTheTopHits() {
		final Vector3 out = new Vector3();
		final Vector3 direction = new Vector3(1, 0, -1).nor();
		assertTrue(MapViewerSelection.intersectRayWithCylinder(new Ray(new Vector3(-200, 0, 200), direction), 0, 0, 0, 40,
				100, out));
	}

	@Test
	void pickRadiusCoversTheClassicSpheres() {
		// Selection scale of the unit data and the largest reach of the Classic model's collision spheres:
		// Footman 1.0 / 43, Grunt 1.0 / 52, Ghoul 1.1 / 56, Tauren 1.6 / 79.
		final float[][] units = { { 1.0f, 43 }, { 1.0f, 52 }, { 1.1f, 56 }, { 1.6f, 79 } };
		for (final float[] unit : units) {
			final float ratio = pickRadius(unit[0]) / unit[1];
			assertTrue((ratio > 0.8f) && (ratio < 1.25f), "pick radius " + pickRadius(unit[0]) + " vs " + unit[1]);
		}
	}
}
