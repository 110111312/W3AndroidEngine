package com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.pathing.CPathPoint;

/**
 * A point order given to several units at once. Measured on 1.29: the units do not all head for the clicked point;
 * each gets a slot of a formation centred on it, rows across the way from the group's centre to the point. The
 * formation has ceil(sqrt(n)) lanes (n lanes up to 3 units), lanes 4 * collision + 60 apart and rows 2 * collision
 * + 78 apart for the largest collision in the group. Units with a lower formation rank take the front rows, then the
 * units already nearest the front; within a row they keep their order from left to right, and a short last row
 * spreads over the outer lanes. A group move walks at the speed of its slowest unit still on the way, and a script
 * group order reaches at most 12 units.
 * <p>
 * A slot is a place a unit is asked to stand, and the ground decides whether that is possible: one laid over a
 * building, a tree, a cliff or water is moved to the nearest cell the unit can stand on ({@link #snapToWalkable}), so
 * the block steps around what is in its way instead of running at it.
 */
public final class CGroupMove {
	public static final int MAX_SCRIPT_GROUP_ORDER_UNITS = 12;
	private static final float LEVEL_ACROSS = 16f;

	private final List<CUnit> members;

	private CGroupMove(final List<CUnit> members) {
		this.members = members;
	}

	public static boolean usesFormation(final int orderId) {
		return (orderId == OrderIds.move) || (orderId == OrderIds.smart) || (orderId == OrderIds.attack)
				|| (orderId == OrderIds.AImove);
	}

	public static boolean canTakeSlot(final CUnit unit) {
		return !unit.isMovementDisabled() && !unit.isBuilding() && (unit.getSpeed() > 0);
	}

	public int limitSpeed(final CUnit unit, final int speed) {
		int limited = speed;
		for (final CUnit member : this.members) {
			if ((member != unit) && !member.isDead() && (member.getMoveBehavior() != null)
					&& (member.getMoveBehavior().getGroup() == this)
					&& (member.getCurrentBehavior() == member.getMoveBehavior())) {
				final int memberSpeed = member.getSpeed();
				if (memberSpeed > 0) {
					limited = Math.min(limited, memberSpeed);
				}
			}
		}
		return limited;
	}

	/**
	 * Links the units whose order just started a plain move, so they walk together. Queued orders and units that are
	 * already doing something else keep their own speed.
	 */
	public static void linkMoving(final List<CUnit> units) {
		final List<CUnit> moving = new ArrayList<>();
		for (final CUnit unit : units) {
			if (((unit.getMoveBehavior() != null) && (unit.getCurrentBehavior() == unit.getMoveBehavior()))
					|| (unit.getCurrentBehavior() instanceof CBehaviorAttackMove)) {
				moving.add(unit);
			}
		}
		if (moving.size() < 2) {
			return;
		}
		final CGroupMove group = new CGroupMove(moving);
		float pointX = 0;
		float pointY = 0;
		int pointWalkers = 0;
		for (final CUnit unit : moving) {
			final CPathPoint goal = pointGoal(unit);
			if (goal != null) {
				pointX += goal.x;
				pointY += goal.y;
				pointWalkers++;
			}
		}
		for (final CUnit unit : moving) {
			final CPathPoint goal = pointGoal(unit);
			final boolean lane = (goal != null) && (pointWalkers > 1);
			final float laneX = lane ? goal.x - (pointX / pointWalkers) : 0;
			final float laneY = lane ? goal.y - (pointY / pointWalkers) : 0;
			if (unit.getCurrentBehavior() instanceof CBehaviorAttackMove) {
				((CBehaviorAttackMove) unit.getCurrentBehavior()).joinGroup(group, laneX, laneY);
				continue;
			}
			final CBehaviorMove move = unit.getMoveBehavior();
			move.setGroup(group);
			move.setLane(laneX, laneY);
		}
	}

	private static CPathPoint pointGoal(final CUnit unit) {
		if (unit.getCurrentBehavior() instanceof CBehaviorAttackMove) {
			final AbilityTarget target = ((CBehaviorAttackMove) unit.getCurrentBehavior()).getTarget();
			return target == null ? null : new CPathPoint(target.getX(), target.getY());
		}
		return unit.getMoveBehavior().getPointGoal();
	}

	/** Slot of each unit as x, y pairs in the order of the list. */
	public static float[] slots(final CSimulation simulation, final List<CUnit> units, final float targetX,
			final float targetY) {
		final int n = units.size();
		final float[] result = new float[n * 2];
		if (n == 0) {
			return result;
		}
		if (n == 1) {
			result[0] = targetX;
			result[1] = targetY;
			return result;
		}
		float centerX = 0;
		float centerY = 0;
		float maxCollision = 0;
		for (final CUnit unit : units) {
			centerX += unit.getX();
			centerY += unit.getY();
			maxCollision = Math.max(maxCollision, unit.getUnitType().getCollisionSize());
		}
		centerX /= n;
		centerY /= n;
		float dirX = targetX - centerX;
		float dirY = targetY - centerY;
		final float length = (float) StrictMath.hypot(dirX, dirY);
		if (length < 1) {
			final double facing = StrictMath.toRadians(units.get(0).getFacing());
			dirX = (float) StrictMath.cos(facing);
			dirY = (float) StrictMath.sin(facing);
		}
		else {
			dirX /= length;
			dirY /= length;
		}
		final float leftX = -dirY;
		final float leftY = dirX;
		final float[] along = new float[n];
		final float[] side = new float[n];
		final int[] rank = new int[n];
		final Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) {
			final CUnit unit = units.get(i);
			final float offsetX = unit.getX() - centerX;
			final float offsetY = unit.getY() - centerY;
			along[i] = (offsetX * dirX) + (offsetY * dirY);
			side[i] = (offsetX * leftX) + (offsetY * leftY);
			rank[i] = simulation.getUnitData().getFormationRank(unit.getTypeId());
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> {
			if (rank[a] != rank[b]) {
				return Integer.compare(rank[a], rank[b]);
			}
			return Float.compare(along[b], along[a]);
		});
		final boolean far = length >= CBehaviorMove.SHARED_ROUTE_MIN_DISTANCE;
		final int lanes = n <= 3 ? n : (int) Math.ceil(Math.sqrt(n));
		final int rows = (n + lanes - 1) / lanes;
		final float laneSpacing = (4 * maxCollision) + 60;
		final float rowSpacing = (2 * maxCollision) + 78;
		for (int row = 0; row < rows; row++) {
			final int from = row * lanes;
			final int to = Math.min(n, from + lanes);
			final int count = to - from;
			Arrays.sort(order, from, to, (a, b) -> Float.compare(side[b], side[a]));
			if (far) {
				frontFirstWithinReach(order, from, to, side, along);
			}
			final float alongOffset = (((rows - 1) / 2f) - row) * rowSpacing;
			for (int k = 0; k < count; k++) {
				final float lane;
				if (count == lanes) {
					lane = k;
				}
				else if (count == 1) {
					lane = (lanes - 1) / 2f;
				}
				else {
					lane = Math.round((k * (lanes - 1)) / (float) (count - 1));
				}
				final float sideOffset = (((lanes - 1) / 2f) - lane) * laneSpacing;
				final int index = order[from + k];
				result[index * 2] = targetX + (dirX * alongOffset) + (leftX * sideOffset);
				result[(index * 2) + 1] = targetY + (dirY * alongOffset) + (leftY * sideOffset);
			}
		}
		snapToWalkable(simulation, units, result);
		return result;
	}

	/**
	 * Moves the slots that landed on ground their unit cannot stand on - a building's footprint, a tree, a cliff, water -
	 * to the nearest cell it can. A slot is invented here rather than given by the player or a script, so nothing else
	 * bounds it: a block laid over a moonwell otherwise orders that unit at the wall, where it can never arrive, and the
	 * order is re-issued with the block. Slots on ground the search says nothing about (no map yet) and slots that are
	 * already fine are left exactly as they are; so are blocks of one, whose point is the caller's own.
	 */
	public static void snapToWalkable(final CSimulation simulation, final List<CUnit> units, final float[] slots) {
		if ((simulation == null) || (units.size() < 2)) {
			return;
		}
		for (int i = 0; i < units.size(); i++) {
			final float[] standable = simulation.nearestStandablePoint(units.get(i), slots[i * 2], slots[(i * 2) + 1]);
			if (standable != null) {
				slots[i * 2] = standable[0];
				slots[(i * 2) + 1] = standable[1];
			}
		}
	}

	/**
	 * Measured: on a long way units standing within a few steps of each other across the way count as level, and the
	 * one further ahead takes the lane to the left.
	 */
	private static void frontFirstWithinReach(final Integer[] order, final int from, final int to, final float[] side,
			final float[] along) {
		int start = from;
		while (start < to) {
			int end = start + 1;
			while ((end < to) && ((side[order[end - 1]] - side[order[end]]) < LEVEL_ACROSS)) {
				end++;
			}
			Arrays.sort(order, start, end, (a, b) -> Float.compare(along[b], along[a]));
			start = end;
		}
	}

	public interface PointOrderAction {
		boolean order(CUnit unit, float x, float y);
	}

	/**
	 * Orders the units to the point as one group: units that can walk take formation slots for a move or attack-move,
	 * the rest get the point itself.
	 */
	public static boolean order(final CSimulation simulation, final List<CUnit> units, final int orderId,
			final float x, final float y, final PointOrderAction action) {
		final List<CUnit> walkers = new ArrayList<>();
		if (usesFormation(orderId)) {
			for (final CUnit unit : units) {
				if (canTakeSlot(unit)) {
					walkers.add(unit);
				}
			}
		}
		final float[] slots = slots(simulation, walkers, x, y);
		boolean success = true;
		for (final CUnit unit : units) {
			final int index = walkers.indexOf(unit);
			if (index >= 0) {
				success &= action.order(unit, slots[index * 2], slots[(index * 2) + 1]);
			}
			else {
				success &= action.order(unit, x, y);
			}
		}
		linkMoving(walkers);
		return success;
	}
}
