package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;

/**
 * Hero mode's army: the follow order the stick gives the player's units, and the one order that takes a unit away from
 * a fight — see {@code docs/hero-mode.md}.
 * <p>
 * The follow order is the console's own group move: {@link CGroupMove#slots} around the hero's stick destination, rank
 * sorted, so melee stand in front and ranged behind because the unit data says so, and the army links among itself so
 * it marches as one block. The hero is never part of it — her order is her own, at her own speed — and it goes out one
 * point order per unit through {@link CPlayerUnitOrderListener}, as a right-click does, so a replay and a LAN peer
 * apply what the player saw.
 * <p>
 * A unit in a fight is left alone: it keeps its target while the player moves, casts and attacks freely. Only a hero
 * more than {@link #RETREAT_DISTANCE} from it turns the order into a retreat, and the same distance is the chase cap,
 * so nothing pursues an enemy that far from a hero who stands still.
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

	private static final double RETREAT_DISTANCE_SQUARED = RETREAT_DISTANCE * (double) RETREAT_DISTANCE;

	private CHeroArmy() {
	}

	/**
	 * The stick's order: every movable unit of the player's that is not in a fight — or is in one the hero has left
	 * more than {@link #RETREAT_DISTANCE} behind — walks to a formation slot around the point the hero was sent to.
	 * <p>
	 * The army is read now, from the units the simulation has, so nothing is kept between orders: a unit that leaves a
	 * fight rejoins on the hero's next move, and a unit that walks into one is left there by the move after it.
	 */
	public static void follow(final CSimulation simulation, final int playerIndex, final CUnit hero, final float x,
			final float y, final CPlayerUnitOrderListener listener) {
		final List<CUnit> army = new ArrayList<>();
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (isArmy(unit, hero, playerIndex) && takesTheOrder(unit, hero)) {
				army.add(unit);
			}
		}
		orderToFormation(simulation, playerIndex, army, x, y, listener);
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
			if (isArmy(unit, hero, playerIndex) && fights(unit) && isBeyondReach(unit, hero)) {
				retreating.add(unit);
			}
		}
		orderToFormation(simulation, playerIndex, retreating, hero.getX(), hero.getY(), listener);
	}

	/** Every movable unit the player owns except the hero. {@link CGroupMove#canTakeSlot} leaves out buildings. */
	private static boolean isArmy(final CUnit unit, final CUnit hero, final int playerIndex) {
		return (unit != hero) && (unit.getPlayerIndex() == playerIndex) && !unit.isDead()
				&& CGroupMove.canTakeSlot(unit);
	}

	/**
	 * Whether the order takes this unit now: a unit in a fight keeps it, unless the hero has left it further behind
	 * than {@link #RETREAT_DISTANCE}, in which case the order is that retreat.
	 */
	private static boolean takesTheOrder(final CUnit unit, final CUnit hero) {
		return !fights(unit) || isBeyondReach(unit, hero);
	}

	/**
	 * A unit in a fight: attacking, or attack-moving, which the engine treats as moving but which finds targets of its
	 * own and is therefore a fight too.
	 */
	private static boolean fights(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior instanceof CBehaviorAttack) || (behavior instanceof CBehaviorAttackMove);
	}

	private static boolean isBeyondReach(final CUnit unit, final CUnit hero) {
		return unit.distanceSquaredNoCollision(hero.getX(), hero.getY()) > RETREAT_DISTANCE_SQUARED;
	}

	/**
	 * One point order per unit, each already sent to its own formation slot, through the listener the console orders
	 * through — the per-unit ability that accepts a plain move, the way {@code MeleeUIInput} finds the walkers of a
	 * selection.
	 */
	private static void orderToFormation(final CSimulation simulation, final int playerIndex, final List<CUnit> units,
			final float x, final float y, final CPlayerUnitOrderListener listener) {
		if (units.isEmpty()) {
			return;
		}
		final AbilityPointTarget point = new AbilityPointTarget(x, y);
		final List<CUnit> walkers = new ArrayList<>(units.size());
		final List<CAbility> abilities = new ArrayList<>(units.size());
		for (final CUnit unit : units) {
			final CAbility move = moveAbility(simulation, unit, playerIndex, point);
			if (move != null) {
				walkers.add(unit);
				abilities.add(move);
			}
		}
		if (walkers.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(simulation, walkers, x, y);
		final int count = walkers.size();
		final int[] unitHandleIds = new int[count];
		final int[] abilityHandleIds = new int[count];
		final float[] xs = new float[count];
		final float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			unitHandleIds[i] = walkers.get(i).getHandleId();
			abilityHandleIds[i] = abilities.get(i).getHandleId();
			xs[i] = slots[i * 2];
			ys[i] = slots[(i * 2) + 1];
		}
		listener.issueGroupPointOrder(unitHandleIds, abilityHandleIds, OrderIds.move, xs, ys, false);
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
}
