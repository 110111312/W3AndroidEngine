package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;

/**
 * What the big attack button points at: the nearest thing in reach that the hero's own weapons accept.
 *
 * <p>
 * The button has no cursor to aim, so it has to pick for the player, and what it may pick is not simply "an enemy".
 * A harmless building and a gate are both owned by Neutral Passive, which is allied to every player with
 * {@link CAllianceType#PASSIVE} so that a right click on it walks instead of attacking — that alliance is the game's
 * rule for smart orders and not a statement that the building is off limits. The attack button therefore leaves
 * Neutral Passive in and takes the hero's own side out, and asks the hero's own {@code CAbilityAttack} whether the
 * widget is hittable at all, so a tree or an item is never picked and the button can never offer something the hero's
 * weapons would refuse.
 *
 * <p>
 * Both the widget list and the distance are arguments rather than lookups, because the rule itself is what is worth
 * pinning in a test: a hero has no unit type outside a loaded map, and a destructable has no owner.
 */
final class HeroAttackTarget {
	/**
	 * How far the button looks, whatever the hero's own acquisition range is. A hero standing at a wall has to be
	 * able to reach it, and the shortest acquisition range in the data is smaller than a wall is thick.
	 */
	static final float MINIMUM_RANGE = 400f;

	private HeroAttackTarget() {
	}

	/** The hero's own acquisition range, floored so that the button always has something to look at. */
	static float rangeOf(final float defaultAcquisitionRange) {
		return Math.max(MINIMUM_RANGE, defaultAcquisitionRange);
	}

	/**
	 * Everything a unit can be ordered to attack: the units and the destructables. Both lists are live, so this is a
	 * copy; the button runs on a tap, not per frame.
	 */
	static List<CWidget> candidates(final CSimulation simulation) {
		final List<CUnit> units = simulation.getUnits();
		final List<CDestructable> destructables = simulation.getDestructables();
		final List<CWidget> candidates = new ArrayList<>(units.size() + destructables.size());
		candidates.addAll(units);
		candidates.addAll(destructables);
		return candidates;
	}

	/**
	 * Whether the button is allowed to point the hero's weapons at this widget at all: not the hero, not something
	 * dead or hidden, and not the hero's own side — except Neutral Passive, which owns the buildings the button
	 * exists to break.
	 */
	static boolean isPickable(final CSimulation simulation, final int playerIndex, final CWidget hero,
			final CWidget candidate) {
		if ((candidate == null) || (candidate == hero) || candidate.isDead()) {
			return false;
		}
		if (candidate instanceof CUnit) {
			final CUnit unit = (CUnit) candidate;
			if (unit.isHidden()) {
				return false;
			}
			final int owner = unit.getPlayerIndex();
			if ((owner == playerIndex) || isAllyThatIsNotNeutral(simulation, playerIndex, owner)) {
				return false;
			}
		}
		// A destructable has no owner, so there is no side to be on: the weapon's own check decides.
		return true;
	}

	private static boolean isAllyThatIsNotNeutral(final CSimulation simulation, final int playerIndex,
			final int owner) {
		if (!simulation.getPlayer(playerIndex).hasAlliance(owner, CAllianceType.PASSIVE)) {
			return false;
		}
		// Allied *and* a real player: the hero's own army or a shared-control partner, never a target.
		return !simulation.isNeutralPassive(owner);
	}

	/**
	 * The nearest candidate inside the range that is accepted, or null when there is none. Nearest is edge-to-edge
	 * as the engine measures it, so a wide building counts as close when its wall is close.
	 */
	static CWidget nearest(final List<? extends CWidget> candidates, final float range,
			final ToDoubleFunction<CWidget> distance, final Predicate<CWidget> accepted) {
		CWidget nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (final CWidget candidate : candidates) {
			final double candidateDistance = distance.applyAsDouble(candidate);
			if ((candidateDistance > range) || (candidateDistance >= nearestDistance)) {
				continue;
			}
			if (accepted.test(candidate)) {
				nearestDistance = candidateDistance;
				nearest = candidate;
			}
		}
		return nearest;
	}
}
