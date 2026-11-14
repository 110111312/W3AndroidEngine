package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.RemovablePathingMapInstance;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * Sight worked out with a unit's stamp must leave the fog exactly as sight worked out afresh does, whatever the other
 * units, moves and sight blockers did in between.
 */
class CUnitVisionStampTest {
	private static final int SIZE = 256;
	private static final int TILES = (SIZE / 4) + 2;

	private static final class World implements CSightWorld {
		final int[][] heights = new int[TILES][TILES];
		final boolean[][] water = new boolean[TILES][TILES];
		final boolean[][] ramps = new boolean[TILES][TILES];
		int version;

		@Override
		public boolean isFogMaskEnabled() {
			return true;
		}

		@Override
		public boolean isFogEnabled() {
			return true;
		}

		@Override
		public byte getFogStateFromSettings(final byte state) {
			return state;
		}

		private static int tile(final float v) {
			return (int) Math.floor(v / 128f);
		}

		private boolean outside(final int tx, final int ty) {
			return (tx < 0) || (ty < 0) || (tx >= TILES) || (ty >= TILES);
		}

		@Override
		public int getTerrainHeight(final float x, final float y) {
			final int tx = tile(x);
			final int ty = tile(y);
			return outside(tx, ty) ? CTerrainData.OUTSIDE_LAYER_HEIGHT : this.heights[ty][tx];
		}

		@Override
		public boolean isTerrainRomp(final float x, final float y) {
			final int tx = tile(x);
			final int ty = tile(y);
			return !outside(tx, ty) && this.ramps[ty][tx];
		}

		@Override
		public boolean isTerrainWater(final float x, final float y) {
			final int tx = tile(x);
			final int ty = tile(y);
			return !outside(tx, ty) && this.water[ty][tx];
		}
	}

	private static final class Viewer {
		float x;
		float y;
		float radius;
		boolean flying;
		int player;
		final List<Integer> detectors = new ArrayList<>();
	}

	private static PathingGrid grid() throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		wpm.setPathing(new short[SIZE * SIZE]);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	/** The sight loop as it was before stamps, with the cell checks that read everything every time. */
	private static void scanFresh(final CSightWorld game, final PathingGrid pathingGrid, final CPlayerFogOfWar fogOfWar,
			final float myX, final float myY, final float sightRadius, final boolean flying,
			final List<Integer> detectorLevels) {
		final int sightCells = CPlayerFogOfWar.getSightCells(sightRadius);
		final int myZ = flying ? Integer.MAX_VALUE : game.getTerrainHeight(myX, myY);
		byte detections = 0;
		for (int d = 0, dn = detectorLevels.size(); d < dn; d++) {
			final int detectionLevel = detectorLevels.get(d);
			if ((detectionLevel >> 8) > 0) {
				detections |= (byte) detectionLevel;
			}
		}
		fogOfWar.setDetecting(pathingGrid.getFogOfWarIndexX(myX), pathingGrid.getFogOfWarIndexY(myY),
				CFogState.VISIBLE, detections);
		final int myXi = pathingGrid.getFogOfWarIndexX(myX);
		final int myYi = pathingGrid.getFogOfWarIndexY(myY);
		final float cellX = pathingGrid.getFogOfWarSampleX(myXi);
		final float cellY = pathingGrid.getFogOfWarSampleY(myYi);
		fogOfWar.setViewerCell(myXi, myYi);
		for (int a = 1; a <= sightCells; a++) {
			detections = 0;
			for (int d = 0, dn = detectorLevels.size(); d < dn; d++) {
				final int detectionLevel = detectorLevels.get(d);
				if ((detectionLevel >> 8) > a) {
					detections |= (byte) detectionLevel;
				}
			}
			fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi, myYi - a, myXi, (myYi - a) + 1,
					cellX, cellY - (a * CPlayerFogOfWar.GRID_STEP), cellX,
					cellY - ((a - 1) * CPlayerFogOfWar.GRID_STEP), myZ);
			fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi, myYi + a, myXi, (myYi + a) - 1,
					cellX, cellY + (a * CPlayerFogOfWar.GRID_STEP), cellX,
					cellY + ((a - 1) * CPlayerFogOfWar.GRID_STEP), myZ);
			fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi - a, myYi, (myXi - a) + 1, myYi,
					cellX - (a * CPlayerFogOfWar.GRID_STEP), cellY, cellX - ((a - 1) * CPlayerFogOfWar.GRID_STEP),
					cellY, myZ);
			fogOfWar.checkCardinalVision(game, pathingGrid, detections, flying, myXi + a, myYi, (myXi + a) - 1, myYi,
					cellX + (a * CPlayerFogOfWar.GRID_STEP), cellY, cellX + ((a - 1) * CPlayerFogOfWar.GRID_STEP),
					cellY, myZ);
		}
		for (int y = 1; y <= sightCells; y++) {
			for (int x = 1; x <= sightCells; x++) {
				final float distance = (x * x) + (y * y);
				detections = 0;
				for (int d = 0, dn = detectorLevels.size(); d < dn; d++) {
					final int detectionLevel = detectorLevels.get(d);
					if (((detectionLevel >> 8) * (detectionLevel >> 8)) > distance) {
						detections |= (byte) detectionLevel;
					}
				}
				if (CPlayerFogOfWar.isInSight(x, y, sightCells)) {
					final int xf = x * CPlayerFogOfWar.GRID_STEP;
					final int yf = y * CPlayerFogOfWar.GRID_STEP;
					fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi - x, myYi - y,
							(myXi - x) + 1, (myYi - y) + 1, cellX - xf, cellY - yf,
							(cellX - xf) + CPlayerFogOfWar.GRID_STEP, (cellY - yf) + CPlayerFogOfWar.GRID_STEP, myZ);
					fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi - x, myYi + y,
							(myXi - x) + 1, (myYi + y) - 1, cellX - xf, cellY + yf,
							(cellX - xf) + CPlayerFogOfWar.GRID_STEP, (cellY + yf) - CPlayerFogOfWar.GRID_STEP, myZ);
					fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi + x, myYi - y,
							(myXi + x) - 1, (myYi - y) + 1, cellX + xf, cellY - yf,
							(cellX + xf) - CPlayerFogOfWar.GRID_STEP, (cellY - yf) + CPlayerFogOfWar.GRID_STEP, myZ);
					fogOfWar.checkDiagonalVision(game, pathingGrid, detections, flying, x, y, myXi + x, myYi + y,
							(myXi + x) - 1, (myYi + y) - 1, cellX + xf, cellY + yf,
							(cellX + xf) - CPlayerFogOfWar.GRID_STEP, (cellY + yf) - CPlayerFogOfWar.GRID_STEP, myZ);
				}
			}
		}
		fogOfWar.clearViewerCell();
	}

	private static byte[] detectionOf(final CPlayerFogOfWar fog) throws ReflectiveOperationException {
		final Field field = CPlayerFogOfWar.class.getDeclaredField("detection");
		field.setAccessible(true);
		return (byte[]) field.get(fog);
	}

	private static void randomTerrain(final World world, final Random random) {
		for (int ty = 0; ty < TILES; ty++) {
			for (int tx = 0; tx < TILES; tx++) {
				world.heights[ty][tx] = random.nextInt(8) == 0 ? 1 + random.nextInt(2) : 0;
				world.water[ty][tx] = random.nextInt(12) == 0;
				world.ramps[ty][tx] = random.nextInt(10) == 0;
			}
		}
	}

	@Test
	void stampedSightLeavesTheSameFogAsFreshSight() throws ReflectiveOperationException, IOException {
		for (int seed = 1; seed <= 6; seed++) {
			runScenario(seed);
		}
	}

	private static void runScenario(final int seed) throws ReflectiveOperationException, IOException {
		final Random random = new Random(seed);
		final World world = new World();
		randomTerrain(world, random);
		final PathingGrid grid = grid();
		final int players = 2;
		final CPlayerFogOfWar[] fresh = new CPlayerFogOfWar[players];
		final CPlayerFogOfWar[] stamped = new CPlayerFogOfWar[players];
		for (int p = 0; p < players; p++) {
			fresh[p] = new CPlayerFogOfWar(grid);
			stamped[p] = new CPlayerFogOfWar(grid);
		}
		final List<Viewer> viewers = new ArrayList<>();
		final List<CUnitVisionStamp> stamps = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			final Viewer viewer = new Viewer();
			viewer.x = random.nextInt(SIZE * 32);
			viewer.y = random.nextInt(SIZE * 32);
			viewer.radius = 256 + random.nextInt(1800);
			if (i % 13 == 0) {
				viewer.radius = 5000 + random.nextInt(2000);
			}
			viewer.flying = random.nextInt(6) == 0;
			viewer.player = random.nextInt(players);
			if (random.nextInt(5) == 0) {
				viewer.detectors.add((random.nextInt(12) << 8) | (1 << random.nextInt(4)));
			}
			viewers.add(viewer);
			stamps.add(new CUnitVisionStamp());
		}
		final List<RemovablePathingMapInstance> blockers = new ArrayList<>();
		final ArgbImage block = new ArgbImage(2, 2, new int[] { 0xFFFF0000, 0xFFFF0000, 0xFFFF0000, 0xFFFF0000 });
		int stampedHits = 0;
		for (int step = 0; step < 60; step++) {
			if (random.nextInt(3) == 0) {
				if (!blockers.isEmpty() && random.nextBoolean()) {
					blockers.remove(random.nextInt(blockers.size())).remove();
				}
				else {
					final RemovablePathingMapInstance blocker = grid.createRemovablePathingOverlayTexture(
							random.nextInt(SIZE * 32), random.nextInt(SIZE * 32), 0, block);
					blocker.setBlocksVision(true);
					blocker.add();
					blockers.add(blocker);
				}
			}
			if (random.nextInt(8) == 0) {
				world.ramps[random.nextInt(TILES)][random.nextInt(TILES)] ^= true;
				world.version++;
			}
			for (final Viewer viewer : viewers) {
				if (random.nextInt(3) == 0) {
					viewer.x = Math.max(0, Math.min((SIZE * 32) - 1, viewer.x + random.nextInt(400) - 200));
					viewer.y = Math.max(0, Math.min((SIZE * 32) - 1, viewer.y + random.nextInt(400) - 200));
				}
				if (random.nextInt(25) == 0) {
					viewer.radius = 256 + random.nextInt(1800);
				}
				if (random.nextInt(40) == 0) {
					viewer.flying = !viewer.flying;
				}
			}
			for (int p = 0; p < players; p++) {
				fresh[p].convertVisibleToFogged();
				stamped[p].convertVisibleToFogged();
				if (random.nextBoolean()) {
					final int cx = random.nextInt(60);
					final int cy = random.nextInt(60);
					for (int k = 0; k < 6; k++) {
						fresh[p].setVisible(cx + k, cy, CFogState.VISIBLE);
						stamped[p].setVisible(cx + k, cy, CFogState.VISIBLE);
					}
				}
			}
			final int blockerVersion = grid.getOverlayVersion() + world.version;
			for (int i = 0; i < viewers.size(); i++) {
				final Viewer viewer = viewers.get(i);
				if (random.nextInt(15) == 0) {
					continue;
				}
				final int sightCells = CPlayerFogOfWar.getSightCells(viewer.radius);
				final int myZ = viewer.flying ? Integer.MAX_VALUE : world.getTerrainHeight(viewer.x, viewer.y);
				scanFresh(world, grid, fresh[viewer.player], viewer.x, viewer.y, viewer.radius, viewer.flying,
						viewer.detectors);
				final CUnitVisionStamp stamp = sightCells <= CUnitVisionStamp.MAX_SIGHT_CELLS ? stamps.get(i) : null;
				CUnitVisibility.scanSight(world, grid, stamped[viewer.player], viewer.x, viewer.y, sightCells,
						viewer.flying, myZ, viewer.detectors, stamp, blockerVersion);
				if (stamp != null) {
					stampedHits++;
				}
			}
			for (int p = 0; p < players; p++) {
				assertArrayEquals(fresh[p].getFogOfWarBuffer().array(), stamped[p].getFogOfWarBuffer().array(),
						"fog, seed " + seed + " step " + step + " player " + p);
				assertArrayEquals(detectionOf(fresh[p]), detectionOf(stamped[p]),
						"detection, seed " + seed + " step " + step + " player " + p);
			}
		}
		assertTrue(stampedHits > 0);
	}
}
