package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
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
 * and the one that gets the army out of the hero's way when she is boxed in — see {@code docs/hero-mode.md}.
 * <p>
 * The follow order is the console's own group move: {@link CGroupMove#slots} around the hero, rank sorted, so melee
 * stand in front and ranged behind because the unit data says so, and the army links among itself so it marches as one
 * block. The hero is never part of it — her order is her own, at her own speed — and it goes out one point order per
 * unit through {@link CPlayerUnitOrderListener}, as a right-click does, so a replay and a LAN peer apply what the
 * player saw.
 * <p>
 * <b>The hero keeps the front of the block.</b> {@code CGroupMove.slots} centres its rows on the point it is given, so
 * a block centred on the hero's stick destination has her walking inside her own squad — which is what the first
 * phone run showed, units body-blocking her the whole way. The front line is placed on the hero instead: on her own
 * line for a melee hero, who wants the army behind her, and out at her attack range for a ranged one, who shoots over
 * the front rank from behind it. The range is the hero's own, so this is data, not a constant.
 * <p>
 * A unit in a fight is left alone: it keeps its target while the player moves, casts and attacks freely. Only a hero
 * more than {@link #RETREAT_DISTANCE} from it turns the order into a retreat, and the same distance is the chase cap,
 * so nothing pursues an enemy that far from a hero who stands still.
 * <p>
 * The front rank cuts both ways in tight ground. Walking forward, the hero goes first and the army follows; reversing
 * into a corridor, the army is suddenly between her and the way out, and nothing in the engine pushes a unit aside
 * ({@code CBehaviorMove} refuses a step into another unit's footprint, {@code CUnitPlacement} only separates units that
 * are standing still). So she crosses the army instead of the army crossing her, in three rules:
 * <ul>
 * <li>a unit standing in the ground she is walking into is not given its slot this beat ({@link #aheadOfHer}) - every
 * slot is at or behind her front line, so taking it would mean walking back across her;</li>
 * <li>the unit on the step she is about to take is told to move one cell aside ({@link #makeRoom});</li>
 * <li>once she is past a unit, that unit's slot is on her side of her line again and it follows - the front line
 * first, the rank behind it once she has walked past that too.</li>
 * </ul>
 * Nothing is remembered between orders: every beat reads where the units actually are, so a rule that changes its mind
 * cannot leave the army holding a promise it made a second ago.
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
	 * How far ahead of the hero along her way a unit may stand and still be given its slot: half a pathing cell. A unit
	 * further ahead than that is in the ground she is walking into; see {@link #aheadOfHer}.
	 */
	private static final float AHEAD_MARGIN = 16f;

	/**
	 * One pathing cell along the way a unit walks - the step {@code CBehaviorMove} refuses when another unit's footprint
	 * is on it - how far to the side a unit standing there is sent, and how far that search may wander.
	 */
	private static final float STEP_AHEAD = 32f;
	private static final float STEP_ASIDE = 32f;
	private static final float STEP_ASIDE_REACH = 96f;

	/** On top of the two footprints, how near a step a unit may stand and still be taken for the one in the way. */
	private static final float STEP_ASIDE_SLACK = 16f;

	private static final double RETREAT_DISTANCE_SQUARED = RETREAT_DISTANCE * (double) RETREAT_DISTANCE;

	private CHeroArmy() {
	}

	/**
	 * The stick's order: every movable unit of the player's that is not in a fight — or is in one the hero has left
	 * more than {@link #RETREAT_DISTANCE} behind — walks to a formation slot around the hero, with her at the front of
	 * the block, facing the way the stick points.
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
			formUp(simulation, playerIndex, hero, listener);
			return;
		}
		final float wayX = dirX / length;
		final float wayY = dirY / length;
		final List<CUnit> roster = following(simulation, playerIndex, hero);
		// Whoever stands on the step she is about to take moves aside and sits out this beat, so the formation order
		// below does not call it straight back into the way it just left her.
		final CUnit aside = makeRoom(simulation, playerIndex, hero, roster, wayX, wayY, listener);
		if (aside != null) {
			roster.remove(aside);
		}
		trail(simulation, playerIndex, hero, roster, wayX, wayY, true, listener);
	}

	/**
	 * When the hero stops: the army settles into its slots around her instead of walking on to the point her last
	 * stick order named. She stopped short of that point, so a block that keeps going piles up in front of where the
	 * player is standing, which was half of what the first phone run noticed.
	 */
	public static void formUp(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final CPlayerUnitOrderListener listener) {
		trail(simulation, playerIndex, hero, following(simulation, playerIndex, hero), facingX(hero), facingY(hero),
				true, listener);
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
		// No hold rule here: a unit this far out is coming home whether or not she stands between it and the block, and
		// the retreat is the one order that must not be dropped for the sake of her feet.
		trail(simulation, playerIndex, hero, retreating, facingX(hero), facingY(hero), false, listener);
	}

	/**
	 * The trailing formation: the hero at the front of the block, the rest of it behind her.
	 *
	 * @param holdAhead whether a unit standing in the ground she is walking into keeps its place instead of walking back
	 *        across her; see {@link #aheadOfHer}
	 */
	private static void trail(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final List<CUnit> roster, final float dirX, final float dirY, final boolean holdAhead,
			final CPlayerUnitOrderListener listener) {
		final float reach = reach(hero);
		final float frontX = hero.getX() + (dirX * reach);
		final float frontY = hero.getY() + (dirY * reach);
		final Walkers walkers = walkers(simulation, playerIndex, roster, frontX, frontY);
		if (walkers.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(simulation, walkers.units, frontX, frontY);
		placeBehind(slots, frontX, frontY, dirX, dirY);
		final List<Integer> kept = new ArrayList<>();
		for (int i = 0; i < walkers.units.size(); i++) {
			final CUnit unit = walkers.units.get(i);
			if (holdAhead && aheadOfHer(hero, unit, dirX, dirY) && !isBeyondReach(unit, hero)) {
				continue;
			}
			kept.add(i);
		}
		stepAsideForTheWalkers(simulation, playerIndex, walkers.units, kept, slots, listener);
		if (kept.isEmpty()) {
			return;
		}
		issue(listener, walkers, slots, kept);
	}

	/**
	 * Whether the unit stands in the ground the hero is about to walk into. {@link #placeBehind} leaves every slot at or
	 * behind her front line, so a unit ahead of her can only reach its slot by walking back across her, and
	 * {@code CBehaviorMove} walks straight at the point it is given: it refuses the step into her footprint instead of
	 * going round her. The unit keeps its place instead, and its slot comes back to it on the beat after she is past.
	 * <p>
	 * This is the whole of "she crosses the army, the army never crosses her". Reversing the stick does not re-aim the
	 * block into a march through her either, because every unit of it is ahead of her along the new way and holds; she
	 * walks into the gap the rule leaves and the block reforms around her from the far side.
	 */
	static boolean aheadOfHer(final CUnit hero, final CUnit unit, final float dirX, final float dirY) {
		return (((unit.getX() - hero.getX()) * dirX) + ((unit.getY() - hero.getY()) * dirY)) > AHEAD_MARGIN;
	}

	/**
	 * The walkers on their way to a slot can be blocked by a unit standing on the step they are about to take - the front
	 * line walking back through the rank behind it is the usual case, and that rank is exactly the part of the block
	 * that does not want to move. That unit is stepped one cell aside, and is not given its own slot on this beat, or the
	 * order would call it straight back into the way it just left.
	 */
	private static void stepAsideForTheWalkers(final CSimulation simulation, final int playerIndex,
			final List<CUnit> candidates, final List<Integer> kept, final float[] slots,
			final CPlayerUnitOrderListener listener) {
		final List<CUnit> steppedAside = new ArrayList<>();
		for (final int at : kept) {
			final CUnit walker = candidates.get(at);
			final float wayX = slots[at * 2] - walker.getX();
			final float wayY = slots[(at * 2) + 1] - walker.getY();
			final float length = (float) StrictMath.hypot(wayX, wayY);
			if (length < STEP_AHEAD) {
				continue;
			}
			final CUnit aside = makeRoom(simulation, playerIndex, walker, candidates, wayX / length, wayY / length,
					listener);
			if (aside != null) {
				steppedAside.add(aside);
			}
		}
		kept.removeIf(at -> steppedAside.contains(candidates.get(at)));
	}

	/**
	 * A unit standing on the next step of a way somebody is walking is told to move one cell aside. It is the one thing
	 * a slot cannot answer: a unit can be exactly where the formation wants it and still stand on the cell a foot is
	 * about to land in, and {@code CBehaviorMove} refuses that step rather than going round the unit.
	 * <p>
	 * The side is the one the unit already leans to, so the same side comes out on every beat instead of shuffling it
	 * between the two; when that side has no room the other is tried. Nothing is remembered: the next beat reads where
	 * the unit actually is. A unit in a fight is not in the roster, so it is never the one moved.
	 *
	 * @return the unit that was moved, or null when the step is free, when the unit on it is not one of the candidates,
	 *         or when neither side has room within {@link #STEP_ASIDE_REACH}
	 */
	private static CUnit makeRoom(final CSimulation simulation, final int playerIndex, final CUnit mover,
			final List<CUnit> candidates, final float wayX, final float wayY,
			final CPlayerUnitOrderListener listener) {
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		if (candidates.isEmpty() || (mover.getUnitType() == null) || (pathingGrid == null)) {
			return null;
		}
		final MovementType movementType = mover.getMovementType();
		if ((movementType == null) || (movementType == MovementType.FLY)) {
			return null;
		}
		final CWorldCollision collision = simulation.getWorldCollision();
		final float collisionSize = mover.getUnitType().getCollisionSize();
		final float stepX = mover.getX() + (wayX * STEP_AHEAD);
		final float stepY = mover.getY() + (wayY * STEP_AHEAD);
		// The test the step is refused by: another unit's footprint on the cell the foot would land in.
		if (!CUnitPlacement.blockedByUnit(mover, stepX, stepY, collision, pathingGrid, collisionSize, movementType)) {
			return null;
		}
		CUnit blocker = null;
		float nearest = Float.MAX_VALUE;
		for (final CUnit unit : candidates) {
			if ((unit == mover) || (unit.getUnitType() == null)) {
				continue;
			}
			final float apart = (float) StrictMath.hypot(unit.getX() - stepX, unit.getY() - stepY);
			final float touching = ((collisionSize + unit.getUnitType().getCollisionSize()) * 0.5f)
					+ STEP_ASIDE_SLACK;
			if ((apart <= touching) && (apart < nearest)) {
				blocker = unit;
				nearest = apart;
			}
		}
		if (blocker == null) {
			// Blocked by a unit in a fight, or by one that is not hers: not this rule's to move.
			return null;
		}
		final float[] free = freeSpot(blocker,
				asideFrom(blocker.getX(), blocker.getY(), mover.getX(), mover.getY(), wayX, wayY), simulation);
		if (free == null) {
			return null;
		}
		final CAbility move = moveAbility(simulation, blocker, playerIndex, new AbilityPointTarget(free[0], free[1]));
		if (move == null) {
			return null;
		}
		listener.issuePointOrder(blocker.getHandleId(), move.getHandleId(), OrderIds.move, free[0], free[1], false);
		return blocker;
	}

	/**
	 * The first of the points the unit can stand on, or the nearest free cell to it, or null when neither side has room
	 * within {@link #STEP_ASIDE_REACH}. {@code findNearestFreeCell} answers with the point it was given when its spiral
	 * finds nothing, so its answer is asked again whether it is really free.
	 */
	private static float[] freeSpot(final CUnit unit, final float[] points, final CSimulation simulation) {
		final MovementType movementType = unit.getMovementType();
		if (movementType == null) {
			return null;
		}
		final CWorldCollision collision = simulation.getWorldCollision();
		final PathingGrid pathingGrid = simulation.getPathingGrid();
		final float collisionSize = unit.getUnitType().getCollisionSize();
		for (int i = 0; i < points.length; i += 2) {
			if (CUnitPlacement.fitsAt(unit, points[i], points[i + 1], collision, pathingGrid, collisionSize,
					movementType)) {
				return new float[] { points[i], points[i + 1] };
			}
			final float[] free = CUnitPlacement.findNearestFreeCell(unit, points[i], points[i + 1], collision,
					pathingGrid, collisionSize, movementType);
			if (CUnitPlacement.fitsAt(unit, free[0], free[1], collision, pathingGrid, collisionSize, movementType)
					&& (StrictMath.hypot(free[0] - unit.getX(), free[1] - unit.getY()) <= STEP_ASIDE_REACH)) {
				return free;
			}
		}
		return null;
	}

	/**
	 * The two points one cell either side of a unit standing on somebody's way, as x, y, x, y: first the side the unit
	 * already leans to, so a unit on the line always goes the same way and never shuffles between the two. Both are given
	 * in one call so the caller can take the other side when the first has no room.
	 */
	static float[] asideFrom(final float unitX, final float unitY, final float walkerX, final float walkerY,
			final float wayX, final float wayY) {
		final float leftX = -wayY;
		final float leftY = wayX;
		final float lean = ((unitX - walkerX) * leftX) + ((unitY - walkerY) * leftY);
		final float first = lean < 0f ? -1f : 1f;
		return new float[] { unitX + (leftX * STEP_ASIDE * first), unitY + (leftY * STEP_ASIDE * first),
				unitX - (leftX * STEP_ASIDE * first), unitY - (leftY * STEP_ASIDE * first) };
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
	private static void issue(final CPlayerUnitOrderListener listener, final Walkers walkers, final float[] slots,
			final List<Integer> kept) {
		final int count = kept.size();
		final int[] unitHandleIds = new int[count];
		final int[] abilityHandleIds = new int[count];
		final float[] xs = new float[count];
		final float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			final int at = kept.get(i);
			unitHandleIds[i] = walkers.units.get(at).getHandleId();
			abilityHandleIds[i] = walkers.abilities.get(at).getHandleId();
			xs[i] = slots[at * 2];
			ys[i] = slots[(at * 2) + 1];
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

	/** A roster narrowed to the units that can walk, with the ability that carries each one's move. */
	private static final class Walkers {
		final List<CUnit> units = new ArrayList<>();
		final List<CAbility> abilities = new ArrayList<>();

		boolean isEmpty() {
			return this.units.isEmpty();
		}
	}
}
