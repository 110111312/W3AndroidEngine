package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;

/**
 * Runs the action list of an ability builder timer, tick or event. A map's ability data can fail in a place nobody
 * tested (a missing store entry, an unset field); like a failing script thread, the list stops there and the game goes
 * on. Every peer fails at the same place, so skipping stays in step. Set "warsmash.abilityBuilderStrict" to rethrow
 * instead, for tests that must not hide a failing action.
 */
public final class ABActionGuard {
	private static final int MAX_LOGGED_SITES = 256;
	private static final Set<String> LOGGED_SITES = ConcurrentHashMap.newKeySet();

	private ABActionGuard() {
	}

	public static void runAll(final String context, final List<ABAction> actions, final CSimulation game,
			final CUnit caster, final Map<String, Object> localStore, final int castId) {
		if (actions == null) {
			return;
		}
		try {
			for (final ABAction action : actions) {
				action.runAction(game, caster, localStore, castId);
			}
		}
		catch (final RuntimeException exc) {
			if (Boolean.getBoolean("warsmash.abilityBuilderStrict")) {
				throw exc;
			}
			report(context, caster, castId, exc);
		}
	}

	/** Logs a failure once per context and place, so a timer failing every tick does not flood the log. */
	public static void report(final String context, final CUnit caster, final int castId,
			final RuntimeException exc) {
		final StackTraceElement[] trace = exc.getStackTrace();
		final String site = context + " " + exc.getClass().getName() + (trace.length > 0 ? " " + trace[0] : "");
		if ((LOGGED_SITES.size() < MAX_LOGGED_SITES) && LOGGED_SITES.add(site)) {
			Log.error("Ability builder " + context + " failed and was skipped (caster "
					+ (caster == null ? "none" : caster.getTypeId()) + ", cast " + castId + ")", exc);
		}
	}
}
