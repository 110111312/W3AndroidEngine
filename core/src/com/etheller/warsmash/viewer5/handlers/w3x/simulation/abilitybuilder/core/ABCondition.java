package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core;

import java.util.Map;

import com.etheller.warsmash.parsers.jass.JassTextGeneratorExpr;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.booleancallbacks.ABBooleanCallback;

public abstract class ABCondition extends ABBooleanCallback implements JassTextGeneratorExpr {

	abstract public Boolean callback(final CSimulation game, final CUnit caster, final Map<String, Object> localStore, final int castId);

	/** A callback that finds nothing (a missing local store key, an unset field) reads as 0, as an unset JASS number does. */
	protected static int intValue(final Integer value) {
		return value == null ? 0 : value;
	}

	protected static float floatValue(final Float value) {
		return value == null ? 0f : value;
	}
}
