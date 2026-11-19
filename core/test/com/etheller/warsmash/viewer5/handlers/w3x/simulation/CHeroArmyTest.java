package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The rule set of the army's follow order: who walks, who keeps fighting, and when the hero is too far. What the
 * order does once it is issued — walking, fighting, the formation's shape — is the engine's and is not mocked here.
 */
class CHeroArmyTest {
	private static final int PLAYER = 0;
	private static final int MOVE_ABILITY_HANDLE_ID = 900;
	private static final int HERO_HANDLE_ID = 100;

	@Test
	void anIdleFollowerWalksToASlotAroundThePoint() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(MOVE_ABILITY_HANDLE_ID), orders.abilityHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
		// One walker takes the point itself; the formation is CGroupMove's and is not re-tested here.
		assertEquals(List.of(300f), orders.xs);
		assertEquals(List.of(400f), orders.ys);
	}

	@Test
	void aFollowerAlreadyWalkingIsSentToTheNewFormation() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorMove.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		// Every new order re-centres the formation, so steering the stick corrects the army instead of leaving it
		// walking to where the hero used to be going.
		assertEquals(List.of(1), orders.unitHandleIds);
	}

	@Test
	void aFightingFollowerKeepsItsTargetWhileTheHeroMoves() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit attacking = unit(1, 300f, 0f, CBehaviorAttack.class);
		final CUnit attackMoving = unit(2, 400f, 0f, CBehaviorAttackMove.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, attacking, attackMoving), PLAYER, hero, 900f, 900f, orders.listener());

		assertTrue(orders.calls.isEmpty(), "a unit in a fight is not re-ordered by the stick");
	}

	@Test
	void aFightingFollowerJoinsTheFormationOnceTheHeroIsAThousandOff() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit fighting = unit(1, CHeroArmy.RETREAT_DISTANCE + 1f, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, fighting), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
	}

	@Test
	void aChaseTheHeroIsFarFromIsGivenUpOnTheScanBeat() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit chaser = unit(1, 1200f, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.giveUpFarChases(game(hero, chaser), PLAYER, hero, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		// The retreat is to the formation around the hero, who is standing still.
		assertEquals(List.of(0f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	@Test
	void aChaseWithinReachIsLeftAlone() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit chaser = unit(1, CHeroArmy.RETREAT_DISTANCE, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.giveUpFarChases(game(hero, chaser), PLAYER, hero, orders.listener());

		assertTrue(orders.calls.isEmpty(), "only a hero further off than the retreat distance takes a fight away");
	}

	@Test
	void theChaseIsLookedAtOnTheIdleScansHalfSecond() {
		// The spec of the chase cap is the idle scan's cadence, not a number of its own.
		assertEquals(CTimer.wholeSteps(0.5f), CHeroArmy.RETREAT_SCAN_TICKS);
	}

	@Test
	void onlyThePlayersOwnUnitsAreOrdered() {
		final CUnit hero = unit(HERO_HANDLE_ID, 0f, 0f, CBehaviorStop.class);
		final CUnit own = unit(1, 50f, 50f, CBehaviorStop.class);
		final CUnit anotherPlayers = unit(2, 60f, 60f, CBehaviorStop.class);
		TestObjects.set(anotherPlayers, "playerIndex", PLAYER + 1);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, own, anotherPlayers), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
	}

	private static CSimulation game(final CUnit... units) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "units", new ArrayList<>(Arrays.asList(units)));
		TestObjects.set(game, "newUnits", new ArrayList<CUnit>());
		return game;
	}

	private static CUnit unit(final int handleId, final float x, final float y,
			final Class<? extends CBehavior> behavior) {
		final CUnit unit = TestObjects.allocate(CUnit.class);
		final CUnitOrderState orders = new CUnitOrderState();
		orders.currentBehavior = TestObjects.allocate(behavior);
		TestObjects.set(unit, "handleId", handleId);
		TestObjects.set(unit, "x", x);
		TestObjects.set(unit, "y", y);
		TestObjects.set(unit, "life", 100f);
		TestObjects.set(unit, "playerIndex", PLAYER);
		TestObjects.set(unit, "finalSpeed", 270);
		TestObjects.set(unit, "movement", new CUnitMovementState());
		TestObjects.set(unit, "orders", orders);
		final List<CAbility> abilities = new ArrayList<>();
		abilities.add(new CAbilityMove(MOVE_ABILITY_HANDLE_ID));
		TestObjects.set(unit, "abilities", abilities);
		unit.setMoveBehavior(TestObjects.allocate(CBehaviorMove.class));
		return unit;
	}

	/** What the army's order reached the player's listener as: one entry per unit of each group order. */
	private static final class Orders {
		final List<String> calls = new ArrayList<>();
		final List<Integer> unitHandleIds = new ArrayList<>();
		final List<Integer> abilityHandleIds = new ArrayList<>();
		final List<Integer> orderIds = new ArrayList<>();
		final List<Float> xs = new ArrayList<>();
		final List<Float> ys = new ArrayList<>();

		CPlayerUnitOrderListener listener() {
			return (CPlayerUnitOrderListener) Proxy.newProxyInstance(Orders.class.getClassLoader(),
					new Class<?>[] { CPlayerUnitOrderListener.class }, (proxy, method, args) -> {
						calls.add(method.getName());
						if ("issueGroupPointOrder".equals(method.getName())) {
							final int[] units = (int[]) args[0];
							final int[] abilities = (int[]) args[1];
							final float[] pointsX = (float[]) args[3];
							final float[] pointsY = (float[]) args[4];
							for (int i = 0; i < units.length; i++) {
								unitHandleIds.add(units[i]);
								abilityHandleIds.add(abilities[i]);
								orderIds.add((Integer) args[2]);
								xs.add(pointsX[i]);
								ys.add(pointsY[i]);
							}
						}
						return null;
					});
		}
	}
}
