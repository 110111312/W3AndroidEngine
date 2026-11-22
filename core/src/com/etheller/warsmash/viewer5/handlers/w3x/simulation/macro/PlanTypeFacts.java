package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;

/**
 * What a plan rule needs to know about one unit type, copied out of the techtree.
 * <p>
 * The copy exists so that the defaults are decided by a pure function of facts rather than by a walk over live engine
 * objects: a test can hand in a handful of these and say what a mission's base looks like without building a
 * simulation. {@link #of} is the only place the engine is read.
 */
public record PlanTypeFacts(War3ID id, boolean building, boolean hero, boolean worker, int goldCost, int lumberCost,
		int foodUsed, int foodMade, List<War3ID> builds, List<War3ID> trains, List<War3ID> researches,
		List<War3ID> upgradesTo) {

	public static PlanTypeFacts of(final CUnitType type) {
		if (type == null) {
			return null;
		}
		final java.util.EnumSet<CUnitClassification> classifications = type.getClassifications();
		final boolean worker = (classifications != null) && classifications.contains(CUnitClassification.PEON);
		return new PlanTypeFacts(type.getTypeId(), type.isBuilding(), type.isHero(), worker, type.getGoldCost(),
				type.getLumberCost(), type.getFoodUsed(), type.getFoodMade(), copy(type.getStructuresBuilt()),
				copy(type.getUnitsTrained()), copy(type.getResearchesAvailable()), copy(type.getUpgradesTo()));
	}

	private static List<War3ID> copy(final List<War3ID> ids) {
		if (ids == null) {
			return List.of();
		}
		return List.copyOf(ids);
	}

	/** A unit this type trains that is neither a worker nor a hero -- a soldier, in other words. */
	public boolean trainsASoldier(final PlanTypeFacts trained) {
		return (trained != null) && !trained.building() && !trained.hero() && !trained.worker();
	}
}
