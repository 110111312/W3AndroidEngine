package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.etheller.warsmash.parsers.jass.JassTextGenerator;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABSingleAction;

public class ABActionCleanUpCastInstance implements ABSingleAction {

	@Override
	public void runAction(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
			final int castId) {
		final String suffix = "#" + castId;
		final Set<String> keySet = new HashSet<>(localStore.keySet());
		for (final String key : keySet) {
			// A buff that ends while a projectile of the same cast is landing (a refreshed debuff) must not take the
			// hit data away from the actions still running; the projectile listener removes them itself.
			if (key.endsWith(suffix) && !key.startsWith(ABLocalStoreKeys.PROJECTILEHITUNIT)
					&& !key.startsWith(ABLocalStoreKeys.PROJECTILEHITDEST)
					&& !key.startsWith(ABLocalStoreKeys.THISPROJECTILE)
					&& !key.startsWith(ABLocalStoreKeys.PROJECTILECURRENTLOC)) {
				localStore.remove(key);
			}
		}
	}

	@Override
	public String generateJassEquivalent(final JassTextGenerator jassTextGenerator) {
		return "LocalStoreCleanUpCastInstance(" + jassTextGenerator.getTriggerLocalStore() + ", "
				+ jassTextGenerator.getCastId() + ")";
	}
}
