package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.floatcallbacks.ABFloatCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.integercallbacks.ABIntegerCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.condition.logical.ABConditionAnd;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.condition.logical.ABConditionNot;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.condition.logical.ABConditionOr;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABActionGuard;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABCondition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.timer.ABTimer;

class AbilityBuilderNullSafetyTest {
	private static final String NUMERIC = "com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.condition.numeric.ABCondition";

	private static ABIntegerCallback intOf(final Integer v) {
		return new ABIntegerCallback() {
			@Override
			public Integer callback(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
					final int castId) {
				return v;
			}
		};
	}

	private static ABFloatCallback floatOf(final Float v) {
		return new ABFloatCallback() {
			@Override
			public Float callback(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
					final int castId) {
				return v;
			}
		};
	}

	private static void set(final Object o, final String name, final Object value) throws Exception {
		final Field f = o.getClass().getDeclaredField(name);
		f.setAccessible(true);
		f.set(o, value);
	}

	private static boolean eval2(final String name, final Object a, final Object b) throws Exception {
		final ABCondition c = (ABCondition) Class.forName(NUMERIC + name).getDeclaredConstructor().newInstance();
		set(c, "value1", a);
		set(c, "value2", b);
		return c.callback(null, null, new HashMap<>(), 0);
	}

	private static boolean eval1(final String name, final Object a) throws Exception {
		final ABCondition c = (ABCondition) Class.forName(NUMERIC + name).getDeclaredConstructor().newInstance();
		set(c, "value", a);
		return c.callback(null, null, new HashMap<>(), 0);
	}

	@Test
	void everyIntegerComparisonReadsNullAsZero() throws Exception {
		for (final String op : new String[] { "Eq", "Ne", "Gt", "Gte", "Lt", "Lte" }) {
			final String name = "Integer" + op;
			assertEquals(eval2(name, intOf(0), intOf(5)), eval2(name, intOf(null), intOf(5)), name + " left");
			assertEquals(eval2(name, intOf(5), intOf(0)), eval2(name, intOf(5), intOf(null)), name + " right");
			assertEquals(eval2(name, intOf(0), intOf(0)), eval2(name, intOf(null), intOf(null)), name + " both");
		}
		for (final String op : new String[] { "Eq0", "Ne0", "Gt0", "IsEven", "IsOdd" }) {
			final String name = "Integer" + op;
			assertEquals(eval1(name, intOf(0)), eval1(name, intOf(null)), name);
		}
		assertTrue(eval2("IntegerEq", intOf(null), intOf(null)));
		assertTrue(eval1("IntegerIsOdd", intOf(-3)));
	}

	@Test
	void everyFloatComparisonReadsNullAsZero() throws Exception {
		for (final String op : new String[] { "Equal", "Ne", "Gt", "Gte", "Lt", "Lte" }) {
			final String name = "Float" + op;
			assertEquals(eval2(name, floatOf(0f), floatOf(5f)), eval2(name, floatOf(null), floatOf(5f)),
					name + " left");
			assertEquals(eval2(name, floatOf(5f), floatOf(0f)), eval2(name, floatOf(5f), floatOf(null)),
					name + " right");
			assertEquals(eval2(name, floatOf(0f), floatOf(0f)), eval2(name, floatOf(null), floatOf(null)),
					name + " both");
		}
		for (final String op : new String[] { "Eq0", "Ne0", "Gt0" }) {
			final String name = "Float" + op;
			assertEquals(eval1(name, floatOf(0f)), eval1(name, floatOf(null)), name);
		}
	}

	private static ABCondition nullCondition() {
		return new ABCondition() {
			@Override
			public Boolean callback(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
					final int castId) {
				return null;
			}
		};
	}

	@Test
	void logicalConditionsTreatNullAsFalse() throws Exception {
		final List<ABCondition> list = new ArrayList<>();
		list.add(nullCondition());
		final ABConditionAnd and = new ABConditionAnd();
		set(and, "conditions", list);
		final ABConditionOr or = new ABConditionOr();
		set(or, "conditions", list);
		final ABConditionNot not = new ABConditionNot();
		set(not, "condition", nullCondition());
		assertFalse(and.callback(null, null, new HashMap<>(), 0));
		assertFalse(or.callback(null, null, new HashMap<>(), 0));
		assertTrue(not.callback(null, null, new HashMap<>(), 0));
	}

	private static ABAction counting(final int[] runs, final boolean fail) {
		return new ABAction() {
			@Override
			public void runAction(final CSimulation game, final CUnit caster, final Map<String, Object> localStore,
					final int castId) {
				runs[0]++;
				if (fail) {
					throw new NullPointerException("unset field");
				}
			}
		};
	}

	@Test
	void failingActionStopsItsListButNotTheCaller() {
		final int[] runs = new int[1];
		final List<ABAction> actions = new ArrayList<>();
		actions.add(counting(runs, false));
		actions.add(counting(runs, true));
		actions.add(counting(runs, false));
		final int logged = Log.getLoggedThrowableCount();
		ABActionGuard.runAll("test", actions, null, null, new HashMap<>(), 7);
		assertEquals(2, runs[0]);
		assertEquals(logged + 1, Log.getLoggedThrowableCount());
		// the same failure again is skipped without another log entry
		ABActionGuard.runAll("test", actions, null, null, new HashMap<>(), 8);
		assertEquals(4, runs[0]);
		assertEquals(logged + 1, Log.getLoggedThrowableCount());
	}

	@Test
	void strictModeRethrows() {
		final List<ABAction> actions = new ArrayList<>();
		actions.add(counting(new int[1], true));
		System.setProperty("warsmash.abilityBuilderStrict", "true");
		try {
			assertThrows(NullPointerException.class,
					() -> ABActionGuard.runAll("test", actions, null, null, new HashMap<>(), 1));
		}
		finally {
			System.clearProperty("warsmash.abilityBuilderStrict");
		}
	}

	@Test
	void failingTimerActionDoesNotEscapeTheTimer() {
		final int[] runs = new int[1];
		final List<ABAction> actions = new ArrayList<>();
		actions.add(counting(runs, true));
		final Map<String, Object> store = new HashMap<>();
		final ABTimer timer = new ABTimer(null, store, actions, 3);
		timer.onFire(null);
		assertEquals(1, runs[0]);
		assertTrue(store.isEmpty());
	}
}
