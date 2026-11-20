package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Rectangle;

import com.etheller.warsmash.testing.TestObjects;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid.MovementType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorStop;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CWeaponType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The rule set of the army's follow order: who walks, who keeps fighting, when the hero is too far, and where the
 * block stands relative to her. What the order does once it is issued — walking, fighting, the formation's shape — is
 * the engine's and is not mocked here.
 */
class CHeroArmyTest {
	private static final int PLAYER = 0;
	private static final int MOVE_ABILITY_HANDLE_ID = 900;
	private static final int HERO_HANDLE_ID = 100;

	@Test
	void anIdleFollowerFormsUpOnTheHerosOwnLine() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(MOVE_ABILITY_HANDLE_ID), orders.abilityHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
		// A melee hero is the front of the block: the slot is her own line, not the stick's point 300 units ahead.
		assertEquals(List.of(0f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	@Test
	void aRangedHeroKeepsTheFrontRankAtHerReach() {
		final CUnit hero = hero(CBehaviorStop.class);
		giveRangedAttack(hero, 600);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final Orders orders = new Orders();

		// The stick points straight along +x, so the front line is the hero's position plus her range on that axis.
		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(600f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	@Test
	void theFrontRowIsPlacedOnTheFrontLineAndNothingAheadOfIt() {
		// Two rows as CGroupMove centres them on the point it is given: one row past it, one row behind, marching +y.
		final float[] slots = { 60f, 142f, -60f, 142f, 60f, -142f, -60f, -142f };

		CHeroArmy.placeBehind(slots, 0f, 0f, 0f, 1f);

		for (int i = 0; i < slots.length; i += 2) {
			assertTrue(slots[i + 1] <= 0f, "no unit is left in front of the line the hero walks on");
		}
		// The row that was ahead now stands exactly on the line; the one behind only moved by the same amount.
		assertEquals(0f, slots[1], 1e-4f);
		assertEquals(0f, slots[3], 1e-4f);
		assertEquals(-284f, slots[5], 1e-4f);
	}

	@Test
	void theArmyFormsUpAroundTheHeroWhenSheStops() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit walkingHome = unit(1, -800f, 0f, CBehaviorMove.class);
		final Orders orders = new Orders();

		CHeroArmy.formUp(game(hero, walkingHome), PLAYER, hero, orders.listener());

		// Walking on to the old point is what piled the squad up in front of her.
		assertEquals(List.of(0f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	@Test
	void aFollowerAlreadyInFrontIsLeftWhereItStandsWhenSheStops() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit inFront = unit(1, 300f, 0f, CBehaviorMove.class);
		final Orders orders = new Orders();

		CHeroArmy.formUp(game(hero, inFront), PLAYER, hero, orders.listener());

		// Calling it back would send it through her, so it simply stops where it is.
		assertTrue(orders.calls.isEmpty());
	}

	@Test
	void aFollowerAlreadyWalkingIsSentToTheNewFormation() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorMove.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		// Every new order re-centres the formation, so steering the stick corrects the army instead of leaving it
		// walking to where the hero used to be going.
		assertEquals(List.of(1), orders.unitHandleIds);
	}

	@Test
	void aFightingFollowerKeepsItsTargetWhileTheHeroMoves() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit attacking = unit(1, 300f, 0f, CBehaviorAttack.class);
		final CUnit attackMoving = unit(2, 400f, 0f, CBehaviorAttackMove.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, attacking, attackMoving), PLAYER, hero, 900f, 900f, orders.listener());

		assertTrue(orders.calls.isEmpty(), "a unit in a fight is not re-ordered by the stick");
	}

	@Test
	void aFightingFollowerJoinsTheFormationOnceTheHeroIsAThousandOff() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit fighting = unit(1, CHeroArmy.RETREAT_DISTANCE + 1f, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, fighting), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
	}

	@Test
	void aChaseTheHeroIsFarFromIsGivenUpOnTheScanBeat() {
		final CUnit hero = hero(CBehaviorStop.class);
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
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit chaser = unit(1, CHeroArmy.RETREAT_DISTANCE, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.giveUpFarChases(game(hero, chaser), PLAYER, hero, orders.listener());

		assertTrue(orders.calls.isEmpty(), "only a hero further off than the retreat distance takes a fight away");
	}

	/**
	 * The tight ground: a unit between the hero and where she is going is already on the far side of her, so it is told
	 * to keep walking that way and she follows into the ground it leaves. It is never sent back through her.
	 */
	@Test
	void aFollowerAheadOfTheHeroKeepsWalkingAhead() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit inTheCorridor = unit(1, 300f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, inTheCorridor), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(400f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	/** One order per side, and the side each unit stands on decides which: nobody crosses her. */
	@Test
	void theArmyIsSplitOnTheLineTheHeroWalks() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit behind = unit(1, -300f, 0f, CBehaviorStop.class);
		final CUnit ahead = unit(2, 300f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, behind, ahead), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of("issueGroupPointOrder", "issueGroupPointOrder"), orders.calls);
		assertEquals(List.of(1, 2), orders.unitHandleIds);
		assertEquals(List.of(0f, 400f), orders.xs);
	}

	@Test
	void aFollowerLevelWithTheHeroIsKeptBehindHer() {
		// The front row of the trailing block stands on her own line; it must not be read as having overtaken her.
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit level = unit(1, 100f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, level), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(0f), orders.xs);
	}

	@Test
	void theLeadingBlockIsPushedOffTheHerosFeet() {
		// Two rows as CGroupMove centres them on the point it is given: aimed at her, so the back row is behind her.
		final float[] slots = { 0f, 142f, 0f, -142f };

		CHeroArmy.placeClearOfHer(slots, 0f, 0f, 0f, 1f, 100f);

		assertEquals(384f, slots[1], 1e-4f);
		assertEquals(100f, slots[3], 1e-4f);
	}

	@Test
	void aUnitWithAnEnemyInSightKeepsItsFight() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final CUnit enemy = unit(2, 40f, 40f, CBehaviorStop.class);
		TestObjects.set(enemy, "playerIndex", PLAYER + 1);
		final FakeWorldCollision worldCollision = new FakeWorldCollision();
		worldCollision.near.add(enemy);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(worldCollision, hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		// Marching to a slot through a battle is what reads as the army ignoring the enemy standing next to it.
		assertTrue(orders.calls.isEmpty(), "a unit with an enemy in sight is left to the fight it is about to join");
	}

	@Test
	void aUnitWalkingToItsTargetKeepsItsFight() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorMove.class);
		final Object followerOrders = TestObjects.get(follower, "orders");
		final Object walk = TestObjects.get(followerOrders, "currentBehavior");
		TestObjects.set(walk, "rangedBehavior", TestObjects.allocate(CBehaviorAttack.class));
		final Orders issued = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, issued.listener());

		assertTrue(issued.calls.isEmpty(), "closing in on a target it already has is a fight too");
	}

	@Test
	void theChaseIsLookedAtOnTheIdleScansHalfSecond() {
		// The spec of the chase cap is the idle scan's cadence, not a number of its own.
		assertEquals(CTimer.wholeSteps(0.5f), CHeroArmy.RETREAT_SCAN_TICKS);
	}

	@Test
	void onlyThePlayersOwnUnitsAreOrdered() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit own = unit(1, 50f, 50f, CBehaviorStop.class);
		final CUnit anotherPlayers = unit(2, 60f, 60f, CBehaviorStop.class);
		TestObjects.set(anotherPlayers, "playerIndex", PLAYER + 1);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, own, anotherPlayers), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
	}

	@Test
	void aUnitInHerWayIsSentToTheSideItLeansTo() {
		assertArrayEquals(new float[] { 0f, 52f, 0f, -12f },
				CHeroArmy.asideFrom(0f, 20f, 0f, 0f, 1f, 0f), 1e-4f);
		assertArrayEquals(new float[] { 0f, -52f, 0f, 12f },
				CHeroArmy.asideFrom(0f, -20f, 0f, 0f, 1f, 0f), 1e-4f);
	}

	@Test
	void aUnitExactlyOnHerLineAlwaysGoesTheSameWay() {
		// Which side is decided by where the unit already is, so a unit on her line cannot flip between the two and
		// shuffle in and out of her way.
		assertArrayEquals(new float[] { 0f, 32f, 0f, -32f }, CHeroArmy.asideFrom(0f, 0f, 0f, 0f, 1f, 0f), 1e-4f);
	}

	@Test
	void aUnitIsNeverSentForwardOrBack() {
		// A step aside keeps the unit's place along her way: it walks out of her path and nothing else changes.
		final float[] aside = CHeroArmy.asideFrom(40f, 25f, 0f, 0f, 1f, 0f);

		assertEquals(40f, aside[0], 1e-4f);
		assertEquals(40f, aside[2], 1e-4f);
	}

	private static CSimulation game(final CUnit... units) {
		return game(new FakeWorldCollision(), units);
	}

	private static CSimulation game(final CWorldCollision worldCollision, final CUnit... units) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "units", new ArrayList<>(Arrays.asList(units)));
		TestObjects.set(game, "newUnits", new ArrayList<CUnit>());
		TestObjects.set(game, "worldCollision", worldCollision);
		TestObjects.set(game, "players", new ArrayList<>(List.of(player())));
		return game;
	}

	/** The alliance table alone: {@code hasAlliance} reads it and nothing else a test reaches. */
	@SuppressWarnings("unchecked")
	private static CPlayer player() {
		final CPlayer player = TestObjects.allocate(CPlayer.class);
		final EnumSet<CAllianceType>[] alliances = new EnumSet[24];
		for (int i = 0; i < alliances.length; i++) {
			alliances[i] = EnumSet.noneOf(CAllianceType.class);
		}
		TestObjects.set(player, "alliances", alliances);
		return player;
	}

	/**
	 * Who the engine says is nearby: the real query enumerates its grid, the tests hand over the units themselves and
	 * the rule decides what "near" means.
	 */
	private static final class FakeWorldCollision extends CWorldCollision {
		private final List<CUnit> near = new ArrayList<>();

		FakeWorldCollision() {
			super(new Rectangle(0, 0, 0, 0), 0f);
		}

		@Override
		public void enumUnitsInRange(final float x, final float y, final float radius,
				final CUnitEnumFunction callback) {
			for (final CUnit unit : this.near) {
				if (callback.call(unit)) {
					return;
				}
			}
		}
	}

	private static CUnit hero(final Class<? extends CBehavior> behavior) {
		return unit(HERO_HANDLE_ID, 0f, 0f, behavior);
	}

	/** A hero who shoots: her own attack range is what sets how far in front of her the front rank stands. */
	private static void giveRangedAttack(final CUnit hero, final int range) {
		final CUnitAttack attack = TestObjects.allocate(CUnitAttackMissile.class);
		TestObjects.set(attack, "range", range);
		TestObjects.set(attack, "weaponType", CWeaponType.MISSILE);
		final List<CUnitAttack> attacks = new ArrayList<>();
		attacks.add(attack);
		TestObjects.set(hero, "unitSpecificCurrentAttacks", attacks);
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
		// Every real unit has a type; the step it takes and the cell it stands on are read from it.
		final CUnitType type = TestObjects.allocate(CUnitType.class);
		TestObjects.set(type, "collisionSize", 32f);
		TestObjects.set(type, "movementType", MovementType.FOOT);
		TestObjects.set(unit, "unitType", type);
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
