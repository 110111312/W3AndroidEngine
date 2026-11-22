package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.units.ObjectData;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.PathingFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;

/**
 * Where a group order puts each unit: the slots are invented geometry, and the ground is the one thing that can make
 * one impossible. A moonwell, an Ancient or a tree line inside the block used to be invisible to the formation, which
 * then ordered a unit to a point it could never stand on and ran it at the wall for as long as the order was
 * re-issued. What the block does once it is ordered — walking, fighting — is the engine's and is not mocked here.
 */
class CGroupMoveSlotsTest {
	private static final int SIZE = 64;
	private static final float COLLISION = 24f;
	/** Eight cells square, about what an Ancient's pathing map covers, near the middle of the test map. */
	private static final int BUILDING_CELL = 18;
	private static final int BUILDING_CELLS = 8;
	/** The point a block is sent to in the middle of that building, and how far off its slot may end up. */
	private static final float TARGET_X = 640f;
	private static final float TARGET_Y = 640f;
	private static final float NEAR = 320f;

	@Test
	void aBlockLaidOverABuildingStepsOffIt() throws IOException {
		final PathingGrid grid = grid(building());
		final List<CUnit> units = archers(560f, 520f, 500f, 560f, 620f, 520f, 560f, 620f);

		final float[] slots = CGroupMove.slots(game(grid), units, TARGET_X, TARGET_Y);

		for (int i = 0; i < units.size(); i++) {
			final float x = slots[i * 2];
			final float y = slots[(i * 2) + 1];
			assertTrue(grid.isPathable(x, y, MovementType.FOOT, COLLISION),
					"slot " + i + " at " + x + ", " + y + " is ground the unit can stand on");
			assertTrue(StrictMath.hypot(x - TARGET_X, y - TARGET_Y) <= NEAR,
					"slot " + i + " at " + x + ", " + y + " is still by the point the block was sent to");
		}
	}

	@Test
	void aBlockOnOpenGroundIsLeftExactlyWhereItWas() throws IOException {
		final List<CUnit> units = archers(560f, 520f, 500f, 560f, 620f, 520f, 560f, 620f);

		// The same block and the same point on a map with no building at all, and on one whose only building stands in a
		// corner the block never comes near: nothing to step off, so nothing may move.
		final float[] onOpenGround = CGroupMove.slots(game(grid(noBuilding())), units, TARGET_X, TARGET_Y);
		final float[] withABuildingFarAway = CGroupMove.slots(game(grid(farBuilding())), units, TARGET_X, TARGET_Y);

		assertArrayEquals(onOpenGround, withABuildingFarAway, 1e-4f, "a building the block never reaches moves nothing");
	}

	@Test
	void aOneUnitOrderKeepsThePointItWasGiven() throws IOException {
		// One unit is not a block: the point is the player's or the script's own, and snapping it would be this class
		// changing an order it did not invent.
		final List<CUnit> units = archers(560f, 520f);

		final float[] slots = CGroupMove.slots(game(grid(building())), units, 700f, 700f);

		assertArrayEquals(new float[] { 700f, 700f }, slots, 1e-4f);
	}

	private static boolean[][] noBuilding() {
		return new boolean[SIZE][SIZE];
	}

	private static boolean[][] building() {
		return blocked(BUILDING_CELL, BUILDING_CELL);
	}

	/** A building the block never comes near, so only a block aimed at one can be moved by it. */
	private static boolean[][] farBuilding() {
		return blocked(1, 1);
	}

	private static boolean[][] blocked(final int firstCellX, final int firstCellY) {
		final boolean[][] blocked = new boolean[SIZE][SIZE];
		for (int y = firstCellY; y < (firstCellY + BUILDING_CELLS); y++) {
			for (int x = firstCellX; x < (firstCellX + BUILDING_CELLS); x++) {
				blocked[y][x] = true;
			}
		}
		return blocked;
	}

	private static PathingGrid grid(final boolean[][] blocked) throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		final short[] pathing = new short[SIZE * SIZE];
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				if (blocked[y][x]) {
					pathing[(y * SIZE) + x] = (short) (PathingFlags.UNWALKABLE | PathingFlags.UNBUILDABLE);
				}
			}
		}
		wpm.setPathing(pathing);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	private static CSimulation game(final PathingGrid pathingGrid) {
		final CUnitData unitData = TestObjects.allocate(CUnitData.class);
		// No unit data: every type has formation rank 0, which is all the block's shape needs from it here.
		TestObjects.set(unitData, "unitData", new ObjectData() {
			@Override
			public GameObject get(final String id) {
				return null;
			}

			@Override
			public void cloneUnit(final String parentId, final String cloneId) {
			}

			@Override
			public void inheritFrom(final String childKey, final String parentKey) {
			}

			@Override
			public void setValue(final String slk, final String id, final String field, final String value) {
			}

			@Override
			public Set<String> keySet() {
				return Set.of();
			}

			@Override
			public String getLocalizedString(final String key) {
				return "";
			}
		});
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "pathingGrid", pathingGrid);
		TestObjects.set(game, "unitData", unitData);
		return game;
	}

	/** @param positions x, y, x, y, ... of one archer each */
	private static List<CUnit> archers(final float... positions) {
		final CUnitType type = TestObjects.allocate(CUnitType.class);
		TestObjects.set(type, "collisionSize", COLLISION);
		final List<CUnit> units = new ArrayList<>();
		for (int i = 0; i < (positions.length / 2); i++) {
			final CUnit unit = TestObjects.allocate(CUnit.class);
			TestObjects.set(unit, "typeId", new War3ID(0x68617232));
			TestObjects.set(unit, "unitType", type);
			final CUnitMovementState movement = new CUnitMovementState();
			movement.movementOverride = MovementType.FOOT;
			TestObjects.set(unit, "movement", movement);
			// A unit reads its movement type as DISABLED without a move behavior, and an immovable unit takes no slot.
			TestObjects.set(unit, "moveBehavior", TestObjects.allocate(CBehaviorMove.class));
			TestObjects.set(unit, "x", positions[i * 2]);
			TestObjects.set(unit, "y", positions[(i * 2) + 1]);
			units.add(unit);
		}
		return units;
	}
}
