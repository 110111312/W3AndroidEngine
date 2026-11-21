package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.util.War3ID;

/**
 * The plan a mission starts with, worked out from the base that is actually there.
 * <p>
 * Nothing here is per-campaign: the same two answers serve a campaign mission and a custom map, and they differ only
 * because the base differs. The rules are deliberately the weakest ones that keep a base playing itself:
 * <ul>
 * <li><b>one military producer if there is none</b> -- a structure that trains a soldier, which excludes a farm and an
 * altar without naming either, since one trains nothing and the other trains heroes;</li>
 * <li><b>a supply building</b> -- the one the player's workers can build that makes the most food, cheapest first. It is
 * not a ticket: the supply line owns it.</li>
 * </ul>
 * "No workers -> inert" and "unreadable techtree -> do nothing" are both decided here rather than in the executor, so
 * that a hero-and-units mission is provably silent from the rule alone.
 */
public final class LiveBaseDefaults {
	private LiveBaseDefaults() {
	}

	/**
	 * The player's own food building: among what the workers can build, the one with the most food made, the cheapest
	 * breaking a tie, and the rawcode breaking that. Null when nothing the player can build makes food.
	 */
	public static War3ID supplyType(final Collection<PlanTypeFacts> buildable) {
		PlanTypeFacts best = null;
		for (final PlanTypeFacts facts : buildable) {
			if (facts.foodMade() <= 0) {
				continue;
			}
			if ((best == null) || (facts.foodMade() > best.foodMade())
					|| ((facts.foodMade() == best.foodMade()) && (facts.goldCost() < best.goldCost()))
					|| ((facts.foodMade() == best.foodMade()) && (facts.goldCost() == best.goldCost())
							&& (facts.id().getValue() < best.id().getValue()))) {
				best = facts;
			}
		}
		return best == null ? null : best.id();
	}

	/**
	 * The player's military production building: among what the workers can build, one that trains a soldier, the
	 * cheapest first. Null when the base cannot make one at all.
	 *
	 * @param types every unit type the rules may need to look a trained unit up in
	 */
	public static War3ID militaryType(final Collection<PlanTypeFacts> buildable,
			final Map<War3ID, PlanTypeFacts> types) {
		PlanTypeFacts best = null;
		for (final PlanTypeFacts facts : buildable) {
			if (!trainsASoldier(facts, types)) {
				continue;
			}
			if ((best == null) || (facts.goldCost() < best.goldCost())
					|| ((facts.goldCost() == best.goldCost()) && (facts.id().getValue() < best.id().getValue()))) {
				best = facts;
			}
		}
		return best == null ? null : best.id();
	}

	/** A structure whose train list holds at least one soldier. */
	public static boolean trainsASoldier(final PlanTypeFacts structure, final Map<War3ID, PlanTypeFacts> types) {
		for (final War3ID trainedId : structure.trains()) {
			if (structure.trainsASoldier(types.get(trainedId))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The tickets a base starts with, so that the plan is never empty by accident but is never seeded twice either.
	 * <p>
	 * Called once, on the first pass on which the base can be read -- that is, once the player owns a worker. It is not
	 * a standing row and it does not refill: a player who removes the seeded ticket keeps it removed, which is what
	 * "a ticket is a decision, not a policy" means.
	 *
	 * @param ownedOrPending every type the player owns, has building, or has already ordered
	 */
	public static List<MacroTicket> seed(final boolean hasWorker, final boolean baseReadable,
			final War3ID militaryType, final Set<War3ID> ownedOrPending) {
		if (!hasWorker || !baseReadable) {
			// A mission with no workers gets an inert plan; one whose techtree cannot be read gets nothing at all.
			return List.of();
		}
		if ((militaryType == null) || ownedOrPending.contains(militaryType)) {
			// The mission already gave one, or this base can never make one: nothing to ask for.
			return List.of();
		}
		final List<MacroTicket> tickets = new ArrayList<>();
		tickets.add(MacroTicket.build(militaryType));
		return tickets;
	}
}
