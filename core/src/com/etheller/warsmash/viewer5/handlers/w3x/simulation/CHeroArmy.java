package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
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
 * are standing still). There the roles swap: {@link #makeWay} sends the army on ahead of her so it clears the ground
 * she needs, which is the one arrangement where the side that has the free path goes first.
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
	 * How long the army keeps leading the hero out after a make-way, in steps: long enough for a block to clear a
	 * corridor, short enough that the normal trailing formation comes back on its own. The caller asks again on the
	 * next progress check if she is still stopped, so a longer delay costs nothing but a longer wait.
	 */
	public static final int MAKE_WAY_TICKS = CTimer.wholeSteps(1f);

	/** Where the leading block is aimed, in world units in front of the hero, and how close it may come to her feet. */
	private static final float MAKE_WAY_STEP = 400f;
	private static final float MAKE_WAY_CLEARANCE = 100f;

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
		trail(simulation, playerIndex, hero, following(simulation, playerIndex, hero), dirX / length, dirY / length,
				listener);
	}

	/**
	 * When the hero stops: the army settles into its slots around her instead of walking on to the point her last
	 * stick order named. She stopped short of that point, so a block that keeps going piles up in front of where the
	 * player is standing, which was half of what the first phone run noticed.
	 */
	public static void formUp(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final CPlayerUnitOrderListener listener) {
		trail(simulation, playerIndex, hero, following(simulation, playerIndex, hero), facingX(hero), facingY(hero),
				listener);
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
		trail(simulation, playerIndex, hero, retreating, facingX(hero), facingY(hero), listener);
	}

	/**
	 * The hero is stopped by her own army: the army is sent on ahead of her, out of the ground she needs, and the block
	 * is placed so that none of it is left on her feet. This is the case the trailing order cannot solve — a squad that
	 * walked into a corridor in front of her is between her and the way out, and nothing in the engine pushes a unit
	 * aside.
	 * <p>
	 * The army has the free path here: she is behind it, so it can walk out and she can follow. The caller keeps asking
	 * for this while she stays stopped ({@link #MAKE_WAY_TICKS}) and then goes back to the trailing formation, which is
	 * what keeps the block from swapping back through her before she has moved.
	 */
	public static void makeWay(final CSimulation simulation, final int playerIndex, final CUnit hero, final float dirX,
			final float dirY, final CPlayerUnitOrderListener listener) {
		final float x = hero.getX() + (dirX * MAKE_WAY_STEP);
		final float y = hero.getY() + (dirY * MAKE_WAY_STEP);
		final Walkers walkers = walkers(simulation, playerIndex, following(simulation, playerIndex, hero), x, y);
		if (walkers.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(simulation, walkers.units, x, y);
		placeClearOfHer(slots, hero.getX(), hero.getY(), dirX, dirY, MAKE_WAY_CLEARANCE);
		issue(listener, walkers, slots);
	}

	/** The trailing formation: the hero at the front of the block, the rest of it behind her. */
	private static void trail(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final List<CUnit> roster, final float dirX, final float dirY,
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
		issue(listener, walkers, slots);
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
	private static void issue(final CPlayerUnitOrderListener listener, final Walkers walkers, final float[] slots) {
		final int count = walkers.units.size();
		final int[] unitHandleIds = new int[count];
		final int[] abilityHandleIds = new int[count];
		final float[] xs = new float[count];
		final float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			unitHandleIds[i] = walkers.units.get(i).getHandleId();
			abilityHandleIds[i] = walkers.abilities.get(i).getHandleId();
			xs[i] = slots[i * 2];
			ys[i] = slots[(i * 2) + 1];
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
	 * Pushes a formation forward along the way it marches until every slot is at least {@code clearance} ahead of the
	 * hero, which is what makes the make-way order a way <em>out</em>: a block aimed at a point in front of her is
	 * centred on it, so its back rows would otherwise land on the ground she is standing on.
	 */
	static void placeClearOfHer(final float[] slots, final float heroX, final float heroY, final float dirX,
			final float dirY, final float clearance) {
		float back = Float.MAX_VALUE;
		for (int i = 0; i < slots.length; i += 2) {
			back = Math.min(back, ((slots[i] - heroX) * dirX) + ((slots[i + 1] - heroY) * dirY));
		}
		if (back >= clearance) {
			return;
		}
		final float shift = clearance - back;
		for (int i = 0; i < slots.length; i += 2) {
			slots[i] += dirX * shift;
			slots[i + 1] += dirY * shift;
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
