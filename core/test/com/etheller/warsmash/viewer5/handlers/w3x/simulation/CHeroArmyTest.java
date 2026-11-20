package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

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
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
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
	void aFollowerBehindHerMarchesOnItsOwnDepthOutOfHerCorridor() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, follower), PLAYER, hero, 300f, 400f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(MOVE_ABILITY_HANDLE_ID), orders.abilityHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
		// Its own depth along the stick's way, and one corridor to its own side: the stick's point 500 units off is not
		// where it is sent, because that ground is not known to be walkable.
		assertEquals(List.of(130f), orders.xs);
		assertEquals(List.of(-10f), orders.ys);
	}

	@Test
	void aRangedHeroHoldsTheFrontRankAtHerReachWhenSheStops() {
		final CUnit hero = hero(CBehaviorStop.class);
		giveRangedAttack(hero, 600);
		final CUnit follower = unit(1, 50f, 50f, CBehaviorStop.class);
		final Orders orders = new Orders();

		// She faces +x on her own line, so the front line of the block she forms on stopping is her range along it.
		CHeroArmy.formUp(game(hero, follower), PLAYER, hero, 0f, 0f, orders.listener());

		assertEquals(List.of(600f), orders.xs);
		// The block is laid out around the army's own centre, so the row keeps the follower's place across her line.
		assertEquals(List.of(50f), orders.ys);
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

		CHeroArmy.formUp(game(hero, walkingHome), PLAYER, hero, 0f, 0f, orders.listener());

		// Walking on to the old point is what piled the squad up in front of her, and a unit eight hundred behind does
		// not get sent to her feet either: the block is laid out around the army's own centre, so it closes up by its
		// own width and stops two hundred behind her line instead of walking through her.
		assertEquals(List.of(-200f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	/**
	 * The units in front of her are not called back through her, and they are not left standing either: leaving them
	 * out is what the phone saw as an army that stops and never lines up.
	 */
	@Test
	void aFollowerAlreadyInFrontFormsItsOwnBlockInFrontOfHer() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit inFront = unit(1, 400f, 0f, CBehaviorMove.class);
		final Orders orders = new Orders();

		CHeroArmy.formUp(game(hero, inFront), PLAYER, hero, 0f, 0f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		// Its back row is one gap in front of her own line, so nothing of the block stands on her.
		assertEquals(List.of(150f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
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
		final CUnit fighting = unit(1, -CHeroArmy.RETREAT_DISTANCE - 1f, 0f, CBehaviorAttack.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, fighting), PLAYER, hero, 300f, 0f, orders.listener());

		// The fight it is in is further behind her than the chase cap, so the stick wins: it comes back to the catch-up
		// depth rather than staying where it is.
		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(OrderIds.move), orders.orderIds);
		assertEquals(List.of(-300f), orders.xs);
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
	void aFollowerAheadOfTheHeroKeepsItsDepthAndStepsAside() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit inTheCorridor = unit(1, 300f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, inTheCorridor), PLAYER, hero, 300f, 0f, orders.listener());

		// It keeps the depth it had — it is never sent back through her — and moves out of the corridor she walks in.
		assertEquals(List.of(1), orders.unitHandleIds);
		assertEquals(List.of(300f), orders.xs);
		assertEquals(List.of(110f), orders.ys);
	}

	/** One order per unit, each on its own place along her way: no block, nothing to stand in the ground she needs. */
	@Test
	void theArmyMarchesOnOneOrderWithoutSlots() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit behind = unit(1, -300f, 0f, CBehaviorStop.class);
		final CUnit ahead = unit(2, 300f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, behind, ahead), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of("issueGroupPointOrder"), orders.calls);
		assertEquals(List.of(1, 2), orders.unitHandleIds);
		assertEquals(List.of(-300f, 300f), orders.xs);
	}

	@Test
	void aFollowerStandingOnHerLineIsSentOutOfHerCorridor() {
		// The case the phone found: a unit level with her, standing in the cell she is about to step into. Its depth is
		// kept, so it neither runs ahead nor falls back, and it steps {@link CHeroArmy#CORRIDOR} away from her line.
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit level = unit(1, 100f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, level), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(100f), orders.xs);
		assertEquals(List.of(110f), orders.ys);
	}

	@Test
	void aStragglerIsSentToTheDepthItCatchesUpOn() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit far = unit(1, -1000f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, far), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(-300f), orders.xs);
	}

	@Test
	void aFollowerNearerThanARowWalksTheRowAheadOfIt() {
		// Its own depth would put the aim point on its own feet, and a point that does not move leaves the army standing
		// while the hero walks away: the row behind her travels with her, so the unit keeps walking too.
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit close = unit(1, -140f, 0f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, close), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(-100f), orders.xs);
		assertEquals(List.of(110f), orders.ys);
	}

	@Test
	void aFollowerOutsideTheCorridorIsGivenNoOrder() {
		// It is already where the march wants it: ordering it to its own feet would only make the engine separate it
		// from the units around it, which is the shuffling this rule set exists to avoid.
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit beside = unit(1, -200f, 300f, CBehaviorStop.class);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(hero, beside), PLAYER, hero, 300f, 0f, orders.listener());

		assertTrue(orders.calls.isEmpty());
	}

	/**
	 * The phone's worst case: a unit marching her way meets the tree she walked around, and pressing into it is what
	 * stopped the army. The aim is moved to the nearest ground the unit's own footprint fits on, on her far side.
	 */
	@Test
	void aFollowerIsAimedAtGoundItCanStandOnInsteadOfTheTreeInItsWay() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 100f, 0f, CBehaviorStop.class);
		final FakePathingGrid grid = fakeGrid();
		// The cell the rules aim it at, and its neighbours on that side, are a clump of trees.
		grid.block(84f, 94f, 32f, 32f);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(grid, hero, follower), PLAYER, hero, 300f, 0f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		// One cell further out of her corridor, which is the nearest cell the unit fits on.
		assertEquals(List.of(68f), orders.xs);
		assertEquals(List.of(110f), orders.ys);
	}

	@Test
	void aFollowerWithNowhereToStandAroundItsAimIsNotOrdered() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, 100f, 0f, CBehaviorStop.class);
		final FakePathingGrid grid = fakeGrid();
		grid.block(-1000f, -1000f, 2000f, 2000f);
		final Orders orders = new Orders();

		CHeroArmy.follow(game(grid, hero, follower), PLAYER, hero, 300f, 0f, orders.listener());

		// Re-ordering it at the same wall four times a second is the stuck unit, not the fix for it.
		assertTrue(orders.calls.isEmpty(), "a unit with no ground to walk to keeps what it was doing");
	}

	/** A formation slot in the trees is moved, or the block that forms in a corridor is half units and half trunks. */
	@Test
	void aFormationSlotInTheTreesIsMovedToGoundTheUnitCanStandOn() {
		final CUnit hero = hero(CBehaviorStop.class);
		final CUnit follower = unit(1, -800f, 0f, CBehaviorStop.class);
		final FakePathingGrid grid = fakeGrid();
		// The slot the block of one is aimed at: put a tree on it.
		grid.block(-216f, -16f, 32f, 32f);
		final Orders orders = new Orders();

		CHeroArmy.formUp(game(grid, hero, follower), PLAYER, hero, 0f, 0f, orders.listener());

		assertEquals(List.of(1), orders.unitHandleIds);
		// One cell out of the tree, and nothing else about the block changed.
		assertEquals(List.of(-232f), orders.xs);
		assertEquals(List.of(0f), orders.ys);
	}

	@Test
	void aSecondUnitIsNeverAimedAtTheGroundAnotherOneTook() {
		final CUnit unit = unit(1, 0f, 0f, CBehaviorStop.class);
		final List<float[]> taken = new ArrayList<>();
		taken.add(new float[] { 100f, 0f });

		final float[] aim = CHeroArmy.standable(unit, 100f, 0f, null, taken, 0f, 1f, 0f, 0f, 0f);

		assertTrue(aim != null, "there is plenty of ground around the point the first unit took");
		assertTrue(StrictMath.hypot(aim[0] - 100f, aim[1] - 0f) >= 48f,
				"two units on one cell is the pile-up the formation exists to avoid");
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

	private static CSimulation game(final CUnit... units) {
		return game(new FakeWorldCollision(), units);
	}

	private static CSimulation game(final CWorldCollision worldCollision, final CUnit... units) {
		return game(worldCollision, null, units);
	}

	private static CSimulation game(final PathingGrid pathingGrid, final CUnit... units) {
		return game(new FakeWorldCollision(), pathingGrid, units);
	}

	private static CSimulation game(final CWorldCollision worldCollision, final PathingGrid pathingGrid,
			final CUnit... units) {
		final CSimulation game = TestObjects.allocate(CSimulation.class);
		TestObjects.set(game, "units", new ArrayList<>(Arrays.asList(units)));
		TestObjects.set(game, "newUnits", new ArrayList<CUnit>());
		TestObjects.set(game, "worldCollision", worldCollision);
		TestObjects.set(game, "pathingGrid", pathingGrid);
		TestObjects.set(game, "players", new ArrayList<>(List.of(player())));
		return game;
	}

	/**
	 * The map's furniture, as the rule set asks about it: rectangles a unit's footprint may not enter. Allocated
	 * without its constructor, like the rest of the engine here — a real grid needs a loaded map.
	 */
	private static final class FakePathingGrid extends PathingGrid {
		private final List<Rectangle> trees = new ArrayList<>();

		@SuppressWarnings("unused")
		FakePathingGrid() {
			super(null, null);
		}

		void block(final float x, final float y, final float width, final float height) {
			this.trees.add(new Rectangle(x, y, width, height));
		}

		@Override
		public boolean isPathable(final float x, final float y, final MovementType pathingType,
				final float collisionSize) {
			return isPathable(x, y, pathingType);
		}

		@Override
		public boolean isPathable(final float x, final float y, final MovementType pathingType) {
			for (final Rectangle tree : this.trees) {
				if (tree.contains(x, y)) {
					return false;
				}
			}
			return true;
		}
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

	private static FakePathingGrid fakeGrid() {
		final FakePathingGrid grid = TestObjects.allocate(FakePathingGrid.class);
		// Allocated without its constructor, so the field initialiser above did not run.
		TestObjects.set(grid, "trees", new ArrayList<Rectangle>());
		return grid;
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
