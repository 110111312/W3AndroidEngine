package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Asks an ability whether the plan may use it, and turns the answer into a {@link Stuck} or an affordability.
 * <p>
 * This is the whole reason the plan does not re-implement the engine's gates. Every one of them -- the build, the queue
 * and the hall upgrade -- already answers "can this be done, and if not why" through this listener, with the food, the
 * hero tokens, the relative upgrade cost, the maximum and the missing level all already accounted for. Re-deriving any
 * of that here would be a second copy of the rules, and the two would drift.
 * <p>
 * The one thing the engine does not distinguish is "not now" from "never": an unaffordable order and a stuck one are
 * both a failure. Flattening them into {@link Outcome} is what lets the plan wait for the first and skip the second.
 */
public final class MacroActivationReceiver implements AbilityActivationReceiver {
	public enum Outcome {
		/** It can be ordered right now. */
		OK,
		/** Waiting will help: the price is not there yet. */
		UNAFFORDABLE,
		/** Waiting will help: something of the same thing is already in progress, or the producer is busy. */
		PENDING,
		/** Waiting will not help until something else happens first. */
		STUCK
	}

	private Outcome outcome = Outcome.STUCK;
	private Stuck stuck = Stuck.of(Stuck.Reason.UNREADABLE);

	public MacroActivationReceiver reset() {
		this.outcome = Outcome.STUCK;
		this.stuck = Stuck.of(Stuck.Reason.UNREADABLE);
		return this;
	}

	public Outcome getOutcome() {
		return this.outcome;
	}

	public Stuck getStuck() {
		return this.stuck;
	}

	public boolean isOk() {
		return this.outcome == Outcome.OK;
	}

	private void fail(final Outcome outcome, final Stuck stuck) {
		this.outcome = outcome;
		this.stuck = stuck;
	}

	@Override
	public void useOk() {
		this.outcome = Outcome.OK;
		this.stuck = null;
	}

	@Override
	public void unknownReasonUseNotOk() {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.UNREADABLE));
	}

	@Override
	public void notAnActiveAbility() {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.UNREADABLE));
	}

	@Override
	public void missingRequirement(final War3ID type, final int level) {
		fail(Outcome.STUCK, Stuck.missing(type, level));
	}

	@Override
	public void missingHeroLevelRequirement(final int level) {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.MISSING_REQUIREMENT));
	}

	@Override
	public void noHeroSkillPointsAvailable() {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.UNREADABLE));
	}

	@Override
	public void disabled() {
		// A hall still finishing an upgrade, or a producer whose queue slot is taken: it clears by itself.
		fail(Outcome.PENDING, null);
	}

	@Override
	public void techtreeMaximumReached() {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.AT_MAXIMUM));
	}

	@Override
	public void techItemAlreadyInProgress() {
		fail(Outcome.PENDING, null);
	}

	@Override
	public void cooldownNotYetReady(final float cooldownRemaining, final float cooldown) {
		fail(Outcome.PENDING, null);
	}

	@Override
	public void noChargesRemaining() {
		fail(Outcome.STUCK, Stuck.of(Stuck.Reason.UNREADABLE));
	}

	@Override
	public void activationCheckFailed(final String commandStringErrorKey) {
		if (CommandStringErrorKeys.NOT_ENOUGH_GOLD.equals(commandStringErrorKey)
				|| CommandStringErrorKeys.NOT_ENOUGH_LUMBER.equals(commandStringErrorKey)
				|| CommandStringErrorKeys.NOT_ENOUGH_FOOD.equals(commandStringErrorKey)) {
			fail(Outcome.UNAFFORDABLE, null);
		}
		else {
			fail(Outcome.STUCK, Stuck.of(Stuck.Reason.UNREADABLE));
		}
	}
}
