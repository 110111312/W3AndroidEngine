package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;

/**
 * Hero mode's army: the follow order the stick gives the player's units, the one that ends a fight it should not be in,
 * and the formation it settles into when she stops — see {@code docs/hero-mode.md}.
 * <p>
 * The follow order goes out as one point order per unit through {@link CPlayerUnitOrderListener}, as a right-click
 * does, so a replay and a LAN peer apply what the player saw; the engine then links the walkers into a group, so they
 * share their way and pace themselves to the slowest. The hero is never part of it — her order is her own, at her own
 * speed.
 * <p>
 * A unit in a fight is left alone: it keeps its target while the player moves, casts and attacks freely. Only a hero
 * more than {@link #RETREAT_DISTANCE} from it turns the order into a retreat, and the same distance is the chase cap,
 * so nothing pursues an enemy that far from a hero who stands still.
 * <p>
 * <b>While she walks there is no formation</b>, only a place per unit: its own depth along her way, held at least
 * {@link #CORRIDOR} off her line, and walked. A block of slots cannot answer this case — {@link CGroupMove#slots} has
 * no idea which cells are walkable, so in confined ground most of its slots lie in the trees, and a block laid out
 * <em>around</em> her is laid out relative to her way, so its anchor swings around her on every turn and the army
 * spends a circle running round her instead of following her. Aims that each move with her, and that are translated in
 * common when she turns, have no pivot.
 * <p>
 * <b>Every aim is checked against the ground before it is ordered.</b> {@link PathingGrid#isPathable} is asked, for the
 * unit's own footprint, about the point the rules named; if that point is inside a wall or a tree, the nearest point
 * around it that the unit can stand on is found (within {@link #SNAP_CELLS} cells), preferring one that stays out of her
 * corridor. If there is none, the unit is not ordered at all. This is the fix for the phone's "a unit walks the same
 * way the hero does and gets stuck on something": a unit pressing into a tree used to be re-ordered toward the same
 * trunk four times a second, and the engine's own pathfinder cannot help with a destination it cannot reach.
 * <p>
 * <b>When she stops the block does appear</b> ({@link #formUp}), and it is two of them: the units level with her or
 * behind her, its front row on her line; and the units in front of her, its back row {@link #MARCH_GAP} beyond it, so
 * that calling nothing back through her still lines everyone up. Both are {@link CGroupMove#slots} laid out across her
 * way, and both have their slots snapped to ground the units can stand on, so the block forms in a corridor instead of
 * half of it standing in the trees.
 */
public final class CHeroArmy {
	/**
	 * How far the hero may get from a unit in a fight before the follow order becomes a retreat, in world units, and
	 * the furthest a unit chases anything from the hero. Not data and not a setting: it is a knob for the balance pass.
	 */
	public static final float RETREAT_DISTANCE = 1000f;

	/**
	 * How often the chase cap is looked at: the half second the idle scan already runs on, so a chase costs one
	 * distance check per beat and one order when it trips. The caller keeps the step it last looked at, because a
	 * frame that falls between two steps must not miss the beat.
	 */
	public static final int RETREAT_SCAN_TICKS = CUnitAcquisition.IDLE_ACQUIRE_SCAN_TICKS;

	/**
	 * How far behind her a follower aims. A unit further back than this walks to this depth rather than its own, so a
	 * straggler catches up instead of trailing the army for the rest of the map.
	 */
	private static final float MARCH_BACK = 300f;

	/**
	 * The spacing of the rows a follower behind her aims at, in world units. Aiming at its own depth would aim at its
	 * own feet - the aim point then does not move - so an army that had fallen behind would stand still and never catch
	 * up. Rows are multiples of this behind her, so the aim travels as she does, and a unit keeps the row it is in.
	 */
	private static final float MARCH_ROW = 100f;

	/**
	 * How far off her line a follower is kept, in world units: wider than her own footprint — three pathing cells for a
	 * hero — plus a cell, so the cell she is stepping into is never a target of her own army's.
	 */
	private static final float CORRIDOR = 110f;

	/** How near its own feet a follower's target may lie and still be no order at all, in world units. */
	private static final float IN_PLACE = 1f;

	/**
	 * How far in front of the hero a unit has to stand to count as ahead of her when she stops, in world units: more
	 * than the row spacing of the block, so a unit level with her is settled around her rather than read as having
	 * overtaken her.
	 */
	private static final float AHEAD_MARGIN = 150f;

	/**
	 * How far in front of her own line the block of units that were already ahead of her stands when she stops, in
	 * world units: its back row is here, which leaves her own footprint and the cell she is stepping into free, and it
	 * is beyond her own reach, so her front block never stands in her firing line either.
	 */
	private static final float MARCH_GAP = 150f;

	/**
	 * How far along her way the point is put that gives the block its facing, in world units. {@link CGroupMove#slots}
	 * takes its rows from the line between the army and the point it is given, so the point is put far along her way
	 * from the army's own centre: the rows then stand across her way, instead of at whatever angle the army happened to
	 * be standing at.
	 */
	private static final float MARCH_FACING = 600f;

	/**
	 * The ground a blocked aim is searched for, in world units and in pathing cells: a point inside a tree gets the
	 * nearest cell around it that the unit's whole footprint fits on. Four cells is two unit widths, which reaches
	 * around the corner of a wall or a clump of trees without teleporting a unit across one.
	 */
	private static final float CELL = 32f;
	private static final int SNAP_CELLS = 4;

	/** How near two aims may lie before the second one is moved aside, in world units: nearer than a unit's width. */
	private static final float UNIT_GAP = 48f;

	/** How far inside the corridor a point may land and still count as out of it, in world units: float slack. */
	private static final float LANE_EPSILON = 0.5f;

	private static final double RETREAT_DISTANCE_SQUARED = RETREAT_DISTANCE * (double) RETREAT_DISTANCE;
	private static final double UNIT_GAP_SQUARED = UNIT_GAP * (double) UNIT_GAP;

	private CHeroArmy() {
	}

	/**
	 * The stick's order: every movable unit of the player's that is not in a fight — or is in one the hero has left
	 * more than {@link #RETREAT_DISTANCE} behind — walks the way she is walking, keeping its own depth along her way and
	 * staying out of the corridor she walks in. Nothing is slotted; {@link #marchTo} says why.
	 * <p>
	 * The army is read now, from the units the simulation has, so nothing is kept between orders: a unit that leaves a
	 * fight rejoins on the hero's next move, and a unit that walks into one is left there by the move after it.
	 */
	public static void follow(final CSimulation simulation, final int playerIndex, final CUnit hero, final float x,
			final float y, final CPlayerUnitOrderListener listener) {
		final float dirX = x - hero.getX();
		final float dirY = y - hero.getY();
		final float length = (float) StrictMath.hypot(dirX, dirY);
		if (length < 1f) {
			// The point is the hero's own feet, so there is no way to march: she faces where she faces.
			formUp(simulation, playerIndex, hero, 0f, 0f, listener);
			return;
		}
		march(simulation, playerIndex, hero, dirX / length, dirY / length, following(simulation, playerIndex, hero),
				listener);
	}

	/**
	 * The march: one point order per unit, each at its own place along her way, each on ground the unit can stand on.
	 * There is no block to form and nothing to re-plan, so the army cannot be sent to ground it cannot walk to —
	 * which is what a slot laid out around the hero does in a corridor, and why the front rank used to end up standing
	 * in the cell she was stepping into.
	 */
	private static void march(final CSimulation simulation, final int playerIndex, final CUnit hero, final float wayX,
			final float wayY, final List<CUnit> roster, final CPlayerUnitOrderListener listener) {
		final Walkers walkers = new Walkers();
		final PathingGrid grid = simulation.getPathingGrid();
		for (final CUnit unit : roster) {
			final float[] aim = marchTo(unit.getX(), unit.getY(), hero.getX(), hero.getY(), wayX, wayY);
			if ((StrictMath.abs(aim[0] - unit.getX()) < IN_PLACE)
					&& (StrictMath.abs(aim[1] - unit.getY()) < IN_PLACE)) {
				// Already where the march wants it. An order to its own feet would only make the engine separate it from
				// the units around it, which is the shuffling this rule set exists to avoid.
				continue;
			}
			final float[] target = standable(unit, aim[0], aim[1], grid, Collections.<float[]> emptyList(), wayX, wayY,
					hero.getX(), hero.getY(), CORRIDOR);
			if (target == null) {
				// Every cell around that point it could stand on is inside her corridor or inside the map's furniture:
				// standing still is the only answer that does not walk it into the same tree every quarter second.
				continue;
			}
			final CAbility move = moveAbility(simulation, unit, playerIndex,
					new AbilityPointTarget(target[0], target[1]));
			if (move == null) {
				continue;
			}
			walkers.add(unit, move, target);
		}
		if (walkers.isEmpty()) {
			return;
		}
		issue(listener, walkers);
	}

	/**
	 * Where a follower aims while the hero walks: its own place along her way, and at least {@link #CORRIDOR} to one
	 * side of her line. Two rules fall out of it, and they are the whole of it:
	 * <ul>
	 * <li>a unit behind her aims at the nearest row behind her, floored so it is never aimed backwards, and never
	 * further back than {@link #MARCH_BACK} - so a straggler walks to the catch-up depth instead of trailing the army
	 * for the rest of the map;</li>
	 * <li>a unit that is already ahead of her keeps its own depth, so it marches on at its own pace and is never sent
	 * back through her.</li>
	 * </ul>
	 * Every aim point is measured from the hero, so it travels as she does: the army moves <em>with</em> her. Aiming at
	 * the depth a unit already has would not - that point does not move, and the unit would stand on it while she walked
	 * away.
	 */
	static float[] marchTo(final float unitX, final float unitY, final float heroX, final float heroY, final float wayX,
			final float wayY) {
		final float leftX = -wayY;
		final float leftY = wayX;
		final float along = ((unitX - heroX) * wayX) + ((unitY - heroY) * wayY);
		final float lateral = ((unitX - heroX) * leftX) + ((unitY - heroY) * leftY);
		final float depth;
		if (along >= 0f) {
			// A unit already ahead of her keeps its own depth: it marches on at its own pace, and it is never sent back
			// through her.
			depth = along;
		} else {
			// The nearest row behind her, floored so that a unit is never aimed behind where it already stands. A unit
			// half a row behind her aims level with her, which is where the front rank of the block stands when she
			// stops - the aim is the point that travels with her, not the unit's own feet, or the army would freeze.
			depth = -StrictMath.min(MARCH_BACK, (float) StrictMath.floor(-along / MARCH_ROW) * MARCH_ROW);
		}
		final float side = StrictMath.abs(lateral) < CORRIDOR ? (lateral < 0f ? -CORRIDOR : CORRIDOR) : lateral;
		return new float[] { heroX + (wayX * depth) + (leftX * side), heroY + (wayY * depth) + (leftY * side) };
	}

	/**
	 * When the hero stops: the army settles into its slots instead of walking on to the point her last stick order
	 * named. She stopped short of that point, so a block that keeps going piles up in front of where the player is
	 * standing, which was half of what the first phone run noticed.
	 * <p>
	 * The way the block is laid out across is the way the stick was pointing when she stopped, not the way her model
	 * happens to be turned: {@code CUnit.getFacing()} is the drawn facing, which lags a turn by up to a second, and a
	 * block laid out across the wrong line is not a formation. {@code 0, 0} means the caller has no way to give, and
	 * her facing is used.
	 * <p>
	 * Two blocks, because there are two populations. Calling the units in front of her back would send them through
	 * her, and leaving them standing is what the phone complained about next — an army that stops with half of it in
	 * front of the hero and never lines up. They get their own block instead, beyond her reach and beyond
	 * {@link #MARCH_GAP}, laid out back-most row first so that nothing of it lands on her.
	 */
	public static void formUp(final CSimulation simulation, final int playerIndex, final CUnit hero, final float wayX,
			final float wayY, final CPlayerUnitOrderListener listener) {
		final float[] way = way(hero, wayX, wayY);
		final float reach = reach(hero);
		final List<CUnit> behind = new ArrayList<>();
		final List<CUnit> ahead = new ArrayList<>();
		for (final CUnit unit : following(simulation, playerIndex, hero)) {
			final float along = along(hero, unit, way[0], way[1]);
			(along <= AHEAD_MARGIN ? behind : ahead).add(unit);
		}
		block(simulation, playerIndex, hero, behind, way[0], way[1], reach, false, listener);
		block(simulation, playerIndex, hero, ahead, way[0], way[1], reach + MARCH_GAP, true, listener);
	}

	/**
	 * The chase cap: a unit that is in a fight more than {@link #RETREAT_DISTANCE} from the hero breaks it off and walks
	 * back to the formation around her. This is the case the stick cannot catch, because the hero may be standing still
	 * while the unit chases an enemy across the map; the caller asks for it on the idle scan's beat
	 * ({@link #RETREAT_SCAN_TICKS}).
	 * <p>
	 * The order is an explicit move, so it suppresses the idle scan on the way back: the unit is not re-baited by the
	 * enemies it passes.
	 */
	public static void giveUpFarChases(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final CPlayerUnitOrderListener listener) {
		final List<CUnit> retreating = new ArrayList<>();
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (isArmy(unit, hero, playerIndex) && fights(unit, simulation) && isBeyondReach(unit, hero)) {
				retreating.add(unit);
			}
		}
		block(simulation, playerIndex, hero, retreating, facingX(hero), facingY(hero), reach(hero), false, listener);
	}

	/** The unit direction the block is laid out across: the caller's, or the way the hero faces when it has none. */
	private static float[] way(final CUnit hero, final float wayX, final float wayY) {
		final float length = (float) StrictMath.hypot(wayX, wayY);
		if (length < 1f) {
			return new float[] { facingX(hero), facingY(hero) };
		}
		return new float[] { wayX / length, wayY / length };
	}

	/** How far along her way a unit stands, in world units: positive in front of her, negative behind her. */
	private static float along(final CUnit hero, final CUnit unit, final float wayX, final float wayY) {
		return ((unit.getX() - hero.getX()) * wayX) + ((unit.getY() - hero.getY()) * wayY);
	}

	/**
	 * One block of the formation: the engine's own {@link CGroupMove#slots}, laid out across her way from a point
	 * {@link #MARCH_FACING} along it from the army's own centre, placed by its front row {@code lineGap} from her line
	 * — behind it, or in front of it when the units were already there — and then each slot moved onto ground its unit
	 * can stand on. A unit whose slot has no such ground within {@link #SNAP_CELLS} keeps what it was doing.
	 */
	private static void block(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final List<CUnit> roster, final float wayX, final float wayY, final float lineGap, final boolean inFront,
			final CPlayerUnitOrderListener listener) {
		if (roster.isEmpty()) {
			return;
		}
		final float layoutX = centreX(roster) + (wayX * MARCH_FACING);
		final float layoutY = centreY(roster) + (wayY * MARCH_FACING);
		final Walkers walkers = walkers(simulation, playerIndex, roster, layoutX, layoutY);
		if (walkers.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(simulation, walkers.units, layoutX, layoutY);
		final float lineX = hero.getX() + (wayX * lineGap);
		final float lineY = hero.getY() + (wayY * lineGap);
		if (inFront) {
			placeAhead(slots, lineX, lineY, wayX, wayY);
		} else {
			placeBehind(slots, lineX, lineY, wayX, wayY);
		}
		final Walkers settled = settle(simulation, walkers, slots, wayX, wayY);
		if (settled.isEmpty()) {
			return;
		}
		issue(listener, settled);
	}

	/**
	 * Each slot moved onto ground its unit can stand on, and away from a slot another unit already took. The block's
	 * shape survives the moves because every slot is searched from where the formation put it; what does not survive is
	 * a slot in the trees, which is what made a formation in a corridor look like no formation at all.
	 */
	private static Walkers settle(final CSimulation simulation, final Walkers walkers, final float[] slots,
			final float wayX, final float wayY) {
		final PathingGrid grid = simulation.getPathingGrid();
		final Walkers settled = new Walkers();
		for (int i = 0; i < walkers.units.size(); i++) {
			final float[] aim = standable(walkers.units.get(i), slots[i * 2], slots[(i * 2) + 1], grid, settled.aims,
					wayX, wayY, 0f, 0f, 0f);
			if (aim == null) {
				continue;
			}
			settled.add(walkers.units.get(i), walkers.abilities.get(i), aim);
		}
		return settled;
	}

	/**
	 * The nearest point to {@code x, y} that this unit can stand on, or null when there is none within
	 * {@link #SNAP_CELLS} cells. A point already inside her corridor is refused when {@code corridor} is positive, and a
	 * point within {@link #UNIT_GAP} of one in {@code taken} is refused too: two units are never aimed at one cell.
	 * <p>
	 * The aim itself is always tried first, so an army that is already in good ground is ordered exactly where the rule
	 * set says and not one unit off it.
	 */
	static float[] standable(final CUnit unit, final float x, final float y, final PathingGrid grid,
			final List<float[]> taken, final float wayX, final float wayY, final float lineX, final float lineY,
			final float corridor) {
		if (stands(unit, x, y, grid) && offLine(x, y, wayX, wayY, lineX, lineY, corridor) && free(x, y, taken)) {
			return new float[] { x, y };
		}
		float bestX = 0f;
		float bestY = 0f;
		float best = Float.MAX_VALUE;
		for (int cellX = -SNAP_CELLS; cellX <= SNAP_CELLS; cellX++) {
			for (int cellY = -SNAP_CELLS; cellY <= SNAP_CELLS; cellY++) {
				if ((cellX == 0) && (cellY == 0)) {
					continue;
				}
				final float candidateX = x + (cellX * CELL);
				final float candidateY = y + (cellY * CELL);
				final float distance = ((candidateX - x) * (candidateX - x)) + ((candidateY - y) * (candidateY - y));
				if (distance >= best) {
					continue;
				}
				if (!offLine(candidateX, candidateY, wayX, wayY, lineX, lineY, corridor)
						|| !free(candidateX, candidateY, taken) || !stands(unit, candidateX, candidateY, grid)) {
					continue;
				}
				best = distance;
				bestX = candidateX;
				bestY = candidateY;
			}
		}
		return best == Float.MAX_VALUE ? null : new float[] { bestX, bestY };
	}

	/** Whether the unit's own footprint fits on the ground at that point, as the engine's pathing grid has it. */
	private static boolean stands(final CUnit unit, final float x, final float y, final PathingGrid grid) {
		if (grid == null) {
			// No map: the tests' simulation has no pathing grid, and an order it could not reach is not this rule's
			// business anyway.
			return true;
		}
		MovementType movementType = unit.getMovementType();
		if (movementType == null) {
			movementType = MovementType.DISABLED;
		}
		// Its whole footprint where the ground allows it, and otherwise the cell under its feet: in a corridor two cells
		// wide no point fits a three-cell footprint, and refusing every aim there would freeze the army in the one place
		// it has to keep moving. The engine's own step test still refuses the steps that are really illegal.
		return grid.isPathable(x, y, movementType, unit.getUnitType().getCollisionSize())
				|| grid.isPathable(x, y, movementType);
	}

	/** Whether the point is far enough off her line to be out of the ground she walks on. */
	private static boolean offLine(final float x, final float y, final float wayX, final float wayY, final float lineX,
			final float lineY, final float corridor) {
		if (corridor <= 0f) {
			return true;
		}
		final float lateral = ((x - lineX) * -wayY) + ((y - lineY) * wayX);
		// {@link #LANE_EPSILON} of slack: the aim was put exactly on the corridor edge, and the float that comes back
		// out of that arithmetic is as likely to be a hair inside it as a hair outside.
		return StrictMath.abs(lateral) >= (corridor - LANE_EPSILON);
	}

	/** Whether no point in {@code taken} is near enough to this one for the two units to stand on each other. */
	private static boolean free(final float x, final float y, final List<float[]> taken) {
		for (final float[] point : taken) {
			final float offsetX = point[0] - x;
			final float offsetY = point[1] - y;
			if (((offsetX * offsetX) + (offsetY * offsetY)) < UNIT_GAP_SQUARED) {
				return false;
			}
		}
		return true;
	}

	private static float centreX(final List<CUnit> roster) {
		float centre = 0f;
		for (final CUnit unit : roster) {
			centre += unit.getX();
		}
		return centre / roster.size();
	}

	private static float centreY(final List<CUnit> roster) {
		float centre = 0f;
		for (final CUnit unit : roster) {
			centre += unit.getY();
		}
		return centre / roster.size();
	}

	/** Every movable unit the player owns except the hero. {@link CGroupMove#canTakeSlot} leaves out buildings. */
	private static List<CUnit> following(final CSimulation simulation, final int playerIndex, final CUnit hero) {
		final List<CUnit> army = new ArrayList<>();
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (isArmy(unit, hero, playerIndex) && takesTheOrder(unit, hero, simulation)) {
				army.add(unit);
			}
		}
		return army;
	}

	private static boolean isArmy(final CUnit unit, final CUnit hero, final int playerIndex) {
		return (unit != hero) && (unit.getPlayerIndex() == playerIndex) && !unit.isDead()
				&& CGroupMove.canTakeSlot(unit);
	}

	/**
	 * Whether the order takes this unit now: a unit in a fight keeps it, unless the hero has left it further behind
	 * than {@link #RETREAT_DISTANCE}, in which case the order is that retreat.
	 */
	private static boolean takesTheOrder(final CUnit unit, final CUnit hero, final CSimulation simulation) {
		return !fights(unit, simulation) || isBeyondReach(unit, hero);
	}

	/**
	 * A unit in a fight: attacking, attack-moving (which the engine treats as walking, but which finds targets of its
	 * own), walking to a target it already picked, or standing with an enemy inside its own acquisition range — the idle
	 * scan would take that enemy up half a second later, so the stick does not pull the unit out of the fight it is
	 * about to join. Without the last case the army walks through a battle to reach its slots.
	 */
	private static boolean fights(final CUnit unit, final CSimulation simulation) {
		final CBehavior behavior = unit.getCurrentBehavior();
		if ((behavior instanceof CBehaviorAttack) || (behavior instanceof CBehaviorAttackMove)) {
			return true;
		}
		if (behavior instanceof CBehaviorMove) {
			// Closing in on a target it already has: a move that wraps the attack, which is how the engine's own
			// acquisition reads a walker.
			if (((CBehaviorMove) behavior).getRangedBehavior() instanceof CBehaviorAttack) {
				return true;
			}
		}
		return enemyInSight(unit, simulation);
	}

	private static boolean enemyInSight(final CUnit unit, final CSimulation simulation) {
		final boolean[] seen = new boolean[1];
		final CPlayer player = simulation.getPlayer(unit.getPlayerIndex());
		simulation.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), unit.getAcquisitionRange(),
				(other) -> {
					if ((other != unit) && !other.isDead() && !other.isHidden()
							&& !player.hasAlliance(other.getPlayerIndex(), CAllianceType.PASSIVE)) {
						seen[0] = true;
						return true;
					}
					return false;
				});
		return seen[0];
	}

	private static boolean isBeyondReach(final CUnit unit, final CUnit hero) {
		return unit.distanceSquaredNoCollision(hero.getX(), hero.getY()) > RETREAT_DISTANCE_SQUARED;
	}

	/**
	 * How far in front of the hero the army's front line stands: on her own line for a melee hero, who wants the army
	 * behind her, and out at her reach for a ranged one, who fights from behind the rank in front of her. The reach is
	 * the hero's own attack range, so a bigger gun keeps the army further off.
	 */
	private static float reach(final CUnit hero) {
		float reach = 0f;
		for (final CUnitAttack attack : hero.getCurrentAttacks()) {
			if (attack.getWeaponType().isRanged()) {
				reach = Math.max(reach, attack.getRange());
			}
		}
		return reach;
	}

	private static float facingX(final CUnit hero) {
		return (float) StrictMath.cos(StrictMath.toRadians(hero.getFacing()));
	}

	private static float facingY(final CUnit hero) {
		return (float) StrictMath.sin(StrictMath.toRadians(hero.getFacing()));
	}

	/**
	 * The units of the roster that can take a plain move, and the per-unit ability that carries it — the way
	 * {@code MeleeUIInput} finds the walkers of a selection.
	 */
	private static Walkers walkers(final CSimulation simulation, final int playerIndex, final List<CUnit> roster,
			final float x, final float y) {
		final Walkers walkers = new Walkers();
		if (roster.isEmpty()) {
			return walkers;
		}
		final AbilityPointTarget point = new AbilityPointTarget(x, y);
		for (final CUnit unit : roster) {
			final CAbility move = moveAbility(simulation, unit, playerIndex, point);
			if (move != null) {
				walkers.units.add(unit);
				walkers.abilities.add(move);
			}
		}
		return walkers;
	}

	/**
	 * One point order per unit, each already sent to its own formation slot, through the listener the console orders
	 * through, so a replay and a LAN peer apply exactly what the player saw.
	 */
	private static void issue(final CPlayerUnitOrderListener listener, final Walkers walkers) {
		final int count = walkers.units.size();
		final int[] unitHandleIds = new int[count];
		final int[] abilityHandleIds = new int[count];
		final float[] xs = new float[count];
		final float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			unitHandleIds[i] = walkers.units.get(i).getHandleId();
			abilityHandleIds[i] = walkers.abilities.get(i).getHandleId();
			xs[i] = walkers.aims.get(i)[0];
			ys[i] = walkers.aims.get(i)[1];
		}
		listener.issueGroupPointOrder(unitHandleIds, abilityHandleIds, OrderIds.move, xs, ys, false);
	}

	/**
	 * Pulls a formation back along the way it marches until its front-most slot is the front line. {@code CGroupMove}
	 * centres its rows on the point it is given — the front row lands a row <em>past</em> it — so a block centred on
	 * the hero's destination is a block she has to walk through. Nothing is pulled forward: slots that are already
	 * behind the line are left where they are.
	 */
	static void placeBehind(final float[] slots, final float frontX, final float frontY, final float dirX,
			final float dirY) {
		float front = 0f;
		for (int i = 0; i < slots.length; i += 2) {
			front = Math.max(front, ((slots[i] - frontX) * dirX) + ((slots[i + 1] - frontY) * dirY));
		}
		if (front == 0f) {
			return;
		}
		for (int i = 0; i < slots.length; i += 2) {
			slots[i] -= dirX * front;
			slots[i + 1] -= dirY * front;
		}
	}

	/**
	 * Pushes a formation forward along the way it stands until its back-most slot is the line, which is what makes the
	 * front block a block <em>in front of</em> her: {@code CGroupMove} centres its rows on the point it is given, so a
	 * block centred beyond her would otherwise have its back row on the ground she is standing on.
	 */
	static void placeAhead(final float[] slots, final float lineX, final float lineY, final float dirX,
			final float dirY) {
		if (slots.length == 0) {
			return;
		}
		float back = Float.MAX_VALUE;
		for (int i = 0; i < slots.length; i += 2) {
			back = Math.min(back, ((slots[i] - lineX) * dirX) + ((slots[i + 1] - lineY) * dirY));
		}
		if (back == 0f) {
			return;
		}
		for (int i = 0; i < slots.length; i += 2) {
			slots[i] -= dirX * back;
			slots[i + 1] -= dirY * back;
		}
	}

	private static CAbility moveAbility(final CSimulation simulation, final CUnit unit, final int playerIndex,
			final AbilityPointTarget point) {
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
		for (final CAbility ability : unit.getAbilities()) {
			ability.checkCanUse(simulation, unit, playerIndex, OrderIds.move, false, activationReceiver.reset());
			if (!activationReceiver.isOk()) {
				continue;
			}
			ability.checkCanTarget(simulation, unit, playerIndex, OrderIds.move, false, point, targetReceiver.reset());
			if (targetReceiver.getTarget() != null) {
				return ability;
			}
		}
		return null;
	}

	/** A roster narrowed to the units that can walk, with the ability that carries each one's move and where to. */
	private static final class Walkers {
		final List<CUnit> units = new ArrayList<>();
		final List<CAbility> abilities = new ArrayList<>();
		final List<float[]> aims = new ArrayList<>();

		void add(final CUnit unit, final CAbility ability, final float[] aim) {
			this.units.add(unit);
			this.abilities.add(ability);
			this.aims.add(aim);
		}

		boolean isEmpty() {
			return this.units.isEmpty();
		}
	}
}
