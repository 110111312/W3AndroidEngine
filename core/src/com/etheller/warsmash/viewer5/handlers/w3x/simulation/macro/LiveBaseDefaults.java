package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The plan a mission starts with: the race's whole build order, minus what the base already has.
 * <p>
 * Nothing here is per-campaign. A campaign mission and a custom map get the same list, and they differ only because
 * their bases differ: a mission that starts with a barracks keeps every line except the barracks, so "the mission
 * already built it" needs no table of its own.
 * <ul>
 * <li><b>the order</b> comes from {@link RaceBuildOrder} -- buildings, hall upgrades and researches in the sequence the
 * melee AI uses in its happy case;</li>
 * <li><b>the filter</b> is the live base: a building the player owns, is building, has queued, or upgrades into is
 * dropped, and a hall upgrade is dropped when the player's hall is already that tier or a later one;</li>
 * <li><b>a supply building</b> is picked here too -- the one the player's workers can build that makes the most food,
 * cheapest first -- but it is not a ticket: the supply line owns it.</li>
 * </ul>
 * A race with no order in the table (naga, and anything the rules cannot name) falls back to the one thing no base
 * should have to ask for: a structure that trains a soldier, if this base can build one at all.
 * <p>
 * "No workers -> inert" and "unreadable techtree -> do nothing" are decided here rather than in the executor, so that a
 * hero-and-units mission is provably silent from the rule alone.
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
	 * a standing row and it does not refill: a player who removes a seeded ticket keeps it removed, which is what "a
	 * ticket is a decision, not a policy" means.
	 * <p>
	 * Research is kept at every level the table asks for, including a level the mission already granted: a research
	 * ticket means "the next level", so an already-maxed one is refused by the gate as at-maximum and its number is
	 * passed over. Dropping it here would need the maximum, which belongs to the engine, not to this rule.
	 *
	 * @param race            the player's race, taken from the units doing the building
	 * @param ownedOrPending  every type the player owns, has building, or has already ordered
	 * @param types           looks a type up, for the hall chain and for nothing else
	 * @param militaryType    what an unknown race falls back to; may be null
	 */
	public static List<MacroTicket> seed(final boolean hasWorker, final boolean baseReadable, final CUnitRace race,
			final Set<War3ID> ownedOrPending, final Function<War3ID, PlanTypeFacts> types, final War3ID militaryType) {
		if (!hasWorker || !baseReadable) {
			// A mission with no workers gets an inert plan; one whose techtree cannot be read gets nothing at all.
			return List.of();
		}
		final List<MacroTicket> tickets = new ArrayList<>();
		for (final MacroTicket ticket : RaceBuildOrder.forRace(race)) {
			if (!alreadyDone(ticket, ownedOrPending, types)) {
				tickets.add(ticket);
			}
		}
		if (!tickets.isEmpty()) {
			return tickets;
		}
		if ((militaryType != null) && !ownedOrPending.contains(militaryType)) {
			// No order for this race, or the mission has out-built the order: one producer, as before.
			return List.of(MacroTicket.build(militaryType));
		}
		return List.of();
	}

	/** Whether the base already did this ticket, so that seeding it would ask for something that exists. */
	private static boolean alreadyDone(final MacroTicket ticket, final Set<War3ID> ownedOrPending,
			final Function<War3ID, PlanTypeFacts> types) {
		final War3ID target = ticket.getRawcode();
		if (ownedOrPending.contains(target)) {
			// Owned, building, queued, or -- for an upgrade -- already the hall this ticket would produce.
			return true;
		}
		return (ticket.getKind() == MacroTicketKind.UPGRADE) && hallIsPast(target, ownedOrPending, types);
	}

	/**
	 * Whether the player's hall is already this tier or a later one: walk up from the ticket's target, through every
	 * upgrade it leads to, and see whether the base already owns one of those. This is what stops a mission that starts
	 * with a Fortress from being asked for a Stronghold.
	 */
	private static boolean hallIsPast(final War3ID target, final Set<War3ID> ownedOrPending,
			final Function<War3ID, PlanTypeFacts> types) {
		final Set<War3ID> seen = new HashSet<>();
		final Deque<War3ID> next = new ArrayDeque<>();
		next.add(target);
		while (!next.isEmpty()) {
			final PlanTypeFacts facts = types.apply(next.poll());
			if (facts == null) {
				continue;
			}
			for (final War3ID above : facts.upgradesTo()) {
				if (!seen.add(above)) {
					continue;
				}
				if (ownedOrPending.contains(above)) {
					return true;
				}
				next.add(above);
			}
		}
		return false;
	}
}
