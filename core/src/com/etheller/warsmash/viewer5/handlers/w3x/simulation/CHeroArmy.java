package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttackMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CGroupMove;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CAllianceType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;

/**
 * Hero mode's army: the follow order the stick gives the player's units, the one that ends a fight it should not be in,
 * and the one that gets the army out of the hero's way when she is boxed in — see {@code docs/hero-mode.md}.
 * <p>
 * The follow order goes out as one point order per unit through {@link CPlayerUnitOrderListener}, as a right-click
 * does, so a replay and a LAN peer apply what the player saw; the engine then links the walkers into a group, so they
 * share their way and pace themselves to the slowest. The hero is never part of it — her order is her own, at her own
 * speed.
 * <p>
 * <b>When the block is formed, the hero is at its front.</b> {@code CGroupMove.slots} centres its rows on the point it is given, so
 * a block centred on the hero's stick destination has her walking inside her own squad — which is what the first
 * phone run showed, units body-blocking her the whole way. The front line is placed on the hero instead: on her own
 * line for a melee hero, who wants the army behind her, and out at her attack range for a ranged one, who shoots over
 * the front rank from behind it. The range is the hero's own, so this is data, not a constant.
 * <p>
 * A unit in a fight is left alone: it keeps its target while the player moves, casts and attacks freely. Only a hero
 * more than {@link #RETREAT_DISTANCE} from it turns the order into a retreat, and the same distance is the chase cap,
 * so nothing pursues an enemy that far from a hero who stands still.
 * <p>
 * The front rank cuts both ways in tight ground. Walking forward, the hero goes first and the army follows; reversing
 * into a corridor, the army is suddenly between her and the way out, and nothing in the engine pushes a unit aside
 * ({@code CBehaviorMove} refuses a step into another unit's footprint, {@code CUnitPlacement} only separates units that
 * are standing still). A block of slots cannot answer that: {@link CGroupMove#slots} has no idea which cells are
 * walkable, so in confined ground most of its slots lie in the trees, the units stand wherever there is room — which is
 * around her feet — and the stick re-aims them at those cells four times a second.
 * <p>
 * So while she walks there is no block at all: every unit keeps its own depth along her way and is held at least
 * {@link #CORRIDOR} off her line, and walks that. Her own path is the one ground known to be walkable, so following it
 * cannot jam. The block is formed when she stops ({@link #formUp}), which is when it is both cheap and visible.
 */
public final class CHeroArmy {
	/**
	 * How far the hero may get from a unit in a fight before the follow order becomes a retreat, in world units, and
	 * the furthest a unit chases anything from the hero. Not data and not a setting: it is a knob for the balance pass.
	 */
	public static final float RETREAT_DISTANCE = 1000f;

	/**
	 * How often the chase cap is looked at: the half second the idle scan already runs on, so a chase costs one
	 * distance check per beat and one order when it trips. The caller keeps the step it last looked at, because a
	 * frame that falls between two steps must not miss the beat.
	 */
	public static final int RETREAT_SCAN_TICKS = CUnitAcquisition.IDLE_ACQUIRE_SCAN_TICKS;

	/**
	 * How far behind her a follower aims. A unit further back than this walks to this depth rather than its own, so a
	 * straggler catches up instead of trailing the army for the rest of the map.
	 */
	private static final float MARCH_BACK = 300f;

	/**
	 * How far off her line a follower is kept, in world units: wider than her own footprint — three pathing cells for a
	 * hero — plus a cell, so the cell she is stepping into is never a target of her own army's.
	 */
	private static final float CORRIDOR = 110f;

	/** How near its own feet a follower's target may lie and still be no order at all, in world units. */
	private static final float IN_PLACE = 1f;

	/**
	 * How far in front of the hero a unit has to stand to count as ahead of her when she stops, in world units: more
	 * than the row spacing of the block, so a unit level with her is settled around her rather than read as having
	 * overtaken her.
	 */
	private static final float AHEAD_MARGIN = 150f;

	private static final double RETREAT_DISTANCE_SQUARED = RETREAT_DISTANCE * (double) RETREAT_DISTANCE;

	private CHeroArmy() {
	}

	/**
	 * The stick's order: every movable unit of the player's that is not in a fight — or is in one the hero has left
	 * more than {@link #RETREAT_DISTANCE} behind — walks the way she is walking, keeping its own depth along her way and
	 * staying out of the corridor she walks in. Nothing is slotted; {@link #marchTo} says why.
	 * <p>
	 * The army is read now, from the units the simulation has, so nothing is kept between orders: a unit that leaves a
	 * fight rejoins on the hero's next move, and a unit that walks into one is left there by the move after it.
	 */
	public static void follow(final CSimulation simulation, final int playerIndex, final CUnit hero, final float x,
			final float y, final CPlayerUnitOrderListener listener) {
		final float dirX = x - hero.getX();
		final float dirY = y - hero.getY();
		final float length = (float) StrictMath.hypot(dirX, dirY);
		if (length < 1f) {
			// The point is the hero's own feet, so there is no way to march: she faces where she faces.
			formUp(simulation, playerIndex, hero, listener);
			return;
		}
		march(simulation, playerIndex, hero, dirX / length, dirY / length, following(simulation, playerIndex, hero),
				listener);
	}

	/**
	 * The march: one point order per unit, each at its own place along her way. There is no block to form and nothing
	 * to re-plan, so the army cannot be sent to ground it cannot walk to — which is what a slot laid out around the
	 * hero does in a corridor, and why the front rank used to end up standing in the cell she was stepping into.
	 */
	private static void march(final CSimulation simulation, final int playerIndex, final CUnit hero, final float wayX,
			final float wayY, final List<CUnit> roster, final CPlayerUnitOrderListener listener) {
		final Walkers walkers = new Walkers();
		final float[] slots = new float[roster.size() * 2];
		for (final CUnit unit : roster) {
			final float[] target = marchTo(unit.getX(), unit.getY(), hero.getX(), hero.getY(), wayX, wayY);
			if ((StrictMath.abs(target[0] - unit.getX()) < IN_PLACE)
					&& (StrictMath.abs(target[1] - unit.getY()) < IN_PLACE)) {
				// Already where the march wants it. An order to its own feet would only make the engine separate it from
				// the units around it, which is the shuffling this rule set exists to avoid.
				continue;
			}
			final CAbility move = moveAbility(simulation, unit, playerIndex,
					new AbilityPointTarget(target[0], target[1]));
			if (move == null) {
				continue;
			}
			final int index = walkers.units.size();
			slots[index * 2] = target[0];
			slots[(index * 2) + 1] = target[1];
			walkers.units.add(unit);
			walkers.abilities.add(move);
		}
		if (walkers.isEmpty()) {
			return;
		}
		issue(listener, walkers, slots);
	}

	/**
	 * Where a follower aims while the hero walks: its own place along her way, and at least {@link #CORRIDOR} to one
	 * side of her line. Two rules fall out of it, and they are the whole of it:
	 * <ul>
	 * <li>a unit more than {@link #MARCH_BACK} behind her aims at that depth instead of its own, so a straggler catches
	 * up;</li>
	 * <li>a unit that is already ahead of her keeps its own depth, so it marches on at its own pace and is never sent
	 * back through her.</li>
	 * </ul>
	 * Aiming at the depth a unit already has is what makes the army move <em>with</em> her rather than to her: the point
	 * is measured from the hero, so it travels as she does, and a unit is never given a cell that is not its own ground.
	 */
	static float[] marchTo(final float unitX, final float unitY, final float heroX, final float heroY, final float wayX,
			final float wayY) {
		final float leftX = -wayY;
		final float leftY = wayX;
		final float along = ((unitX - heroX) * wayX) + ((unitY - heroY) * wayY);
		final float lateral = ((unitX - heroX) * leftX) + ((unitY - heroY) * leftY);
		final float depth = along < 0f ? StrictMath.max(-MARCH_BACK, along) : along;
		final float side = StrictMath.abs(lateral) < CORRIDOR ? (lateral < 0f ? -CORRIDOR : CORRIDOR) : lateral;
		return new float[] { heroX + (wayX * depth) + (leftX * side), heroY + (wayY * depth) + (leftY * side) };
	}

	/**
	 * When the hero stops: the army settles into its slots around her instead of walking on to the point her last
	 * stick order named. She stopped short of that point, so a block that keeps going piles up in front of where the
	 * player is standing, which was half of what the first phone run noticed.
	 */
	public static void formUp(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final CPlayerUnitOrderListener listener) {
		final float dirX = facingX(hero);
		final float dirY = facingY(hero);
		trail(simulation, playerIndex, hero, behind(hero, following(simulation, playerIndex, hero), dirX, dirY), dirX,
				dirY, listener);
		// The units already in front of her are left where they stand: ordering them back would send them through her,
		// which is the one thing this rule set exists to prevent.
	}

	/**
	 * The chase cap: a unit that is in a fight more than {@link #RETREAT_DISTANCE} from the hero breaks it off and walks
	 * back to the formation around her. This is the case the stick cannot catch, because the hero may be standing still
	 * while the unit chases an enemy across the map; the caller asks for it on the idle scan's beat
	 * ({@link #RETREAT_SCAN_TICKS}).
	 * <p>
	 * The order is an explicit move, so it suppresses the idle scan on the way back: the unit is not re-baited by the
	 * enemies it passes.
	 */
	public static void giveUpFarChases(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final CPlayerUnitOrderListener listener) {
		final List<CUnit> retreating = new ArrayList<>();
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (isArmy(unit, hero, playerIndex) && fights(unit, simulation) && isBeyondReach(unit, hero)) {
				retreating.add(unit);
			}
		}
		trail(simulation, playerIndex, hero, retreating, facingX(hero), facingY(hero), listener);
	}

	/** The units level with her or behind her on her facing line: the ones the block settles around when she stops. */
	private static List<CUnit> behind(final CUnit hero, final List<CUnit> roster, final float dirX,
			final float dirY) {
		final List<CUnit> behind = new ArrayList<>(roster.size());
		for (final CUnit unit : roster) {
			final float along = ((unit.getX() - hero.getX()) * dirX) + ((unit.getY() - hero.getY()) * dirY);
			if (along <= AHEAD_MARGIN) {
				behind.add(unit);
			}
		}
		return behind;
	}

	/** The trailing formation: the hero at the front of the block, the rest of it behind her. */
	private static void trail(final CSimulation simulation, final int playerIndex, final CUnit hero,
			final List<CUnit> roster, final float dirX, final float dirY,
			final CPlayerUnitOrderListener listener) {
		final float reach = reach(hero);
		final float frontX = hero.getX() + (dirX * reach);
		final float frontY = hero.getY() + (dirY * reach);
		final Walkers walkers = walkers(simulation, playerIndex, roster, frontX, frontY);
		if (walkers.isEmpty()) {
			return;
		}
		final float[] slots = CGroupMove.slots(simulation, walkers.units, frontX, frontY);
		placeBehind(slots, frontX, frontY, dirX, dirY);
		issue(listener, walkers, slots);
	}

	/** Every movable unit the player owns except the hero. {@link CGroupMove#canTakeSlot} leaves out buildings. */
	private static List<CUnit> following(final CSimulation simulation, final int playerIndex, final CUnit hero) {
		final List<CUnit> army = new ArrayList<>();
		for (final CUnit unit : simulation.getUnitsIncludingNew()) {
			if (isArmy(unit, hero, playerIndex) && takesTheOrder(unit, hero, simulation)) {
				army.add(unit);
			}
		}
		return army;
	}

	private static boolean isArmy(final CUnit unit, final CUnit hero, final int playerIndex) {
		return (unit != hero) && (unit.getPlayerIndex() == playerIndex) && !unit.isDead()
				&& CGroupMove.canTakeSlot(unit);
	}

	/**
	 * Whether the order takes this unit now: a unit in a fight keeps it, unless the hero has left it further behind
	 * than {@link #RETREAT_DISTANCE}, in which case the order is that retreat.
	 */
	private static boolean takesTheOrder(final CUnit unit, final CUnit hero, final CSimulation simulation) {
		return !fights(unit, simulation) || isBeyondReach(unit, hero);
	}

	/**
	 * A unit in a fight: attacking, attack-moving (which the engine treats as walking, but which finds targets of its
	 * own), walking to a target it already picked, or standing with an enemy inside its own acquisition range — the idle
	 * scan would take that enemy up half a second later, so the stick does not pull the unit out of the fight it is
	 * about to join. Without the last case the army walks through a battle to reach its slots.
	 */
	private static boolean fights(final CUnit unit, final CSimulation simulation) {
		final CBehavior behavior = unit.getCurrentBehavior();
		if ((behavior instanceof CBehaviorAttack) || (behavior instanceof CBehaviorAttackMove)) {
			return true;
		}
		if (behavior instanceof CBehaviorMove) {
			// Closing in on a target it already has: a move that wraps the attack, which is how the engine's own
			// acquisition reads a walker.
			if (((CBehaviorMove) behavior).getRangedBehavior() instanceof CBehaviorAttack) {
				return true;
			}
		}
		return enemyInSight(unit, simulation);
	}

	private static boolean enemyInSight(final CUnit unit, final CSimulation simulation) {
		final boolean[] seen = new boolean[1];
		final CPlayer player = simulation.getPlayer(unit.getPlayerIndex());
		simulation.getWorldCollision().enumUnitsInRange(unit.getX(), unit.getY(), unit.getAcquisitionRange(),
				(other) -> {
					if ((other != unit) && !other.isDead() && !other.isHidden()
							&& !player.hasAlliance(other.getPlayerIndex(), CAllianceType.PASSIVE)) {
						seen[0] = true;
						return true;
					}
					return false;
				});
		return seen[0];
	}

	private static boolean isBeyondReach(final CUnit unit, final CUnit hero) {
		return unit.distanceSquaredNoCollision(hero.getX(), hero.getY()) > RETREAT_DISTANCE_SQUARED;
	}

	/**
	 * How far in front of the hero the army's front line stands: on her own line for a melee hero, who wants the army
	 * behind her, and out at her reach for a ranged one, who fights from behind the rank in front of her. The reach is
	 * the hero's own attack range, so a bigger gun keeps the army further off.
	 */
	private static float reach(final CUnit hero) {
		float reach = 0f;
		for (final CUnitAttack attack : hero.getCurrentAttacks()) {
			if (attack.getWeaponType().isRanged()) {
				reach = Math.max(reach, attack.getRange());
			}
		}
		return reach;
	}

	private static float facingX(final CUnit hero) {
		return (float) StrictMath.cos(StrictMath.toRadians(hero.getFacing()));
	}

	private static float facingY(final CUnit hero) {
		return (float) StrictMath.sin(StrictMath.toRadians(hero.getFacing()));
	}

	/**
	 * The units of the roster that can take a plain move, and the per-unit ability that carries it — the way
	 * {@code MeleeUIInput} finds the walkers of a selection.
	 */
	private static Walkers walkers(final CSimulation simulation, final int playerIndex, final List<CUnit> roster,
			final float x, final float y) {
		final Walkers walkers = new Walkers();
		if (roster.isEmpty()) {
			return walkers;
		}
		final AbilityPointTarget point = new AbilityPointTarget(x, y);
		for (final CUnit unit : roster) {
			final CAbility move = moveAbility(simulation, unit, playerIndex, point);
			if (move != null) {
				walkers.units.add(unit);
				walkers.abilities.add(move);
			}
		}
		return walkers;
	}

	/**
	 * One point order per unit, each already sent to its own formation slot, through the listener the console orders
	 * through, so a replay and a LAN peer apply exactly what the player saw.
	 */
	private static void issue(final CPlayerUnitOrderListener listener, final Walkers walkers, final float[] slots) {
		final int count = walkers.units.size();
		final int[] unitHandleIds = new int[count];
		final int[] abilityHandleIds = new int[count];
		final float[] xs = new float[count];
		final float[] ys = new float[count];
		for (int i = 0; i < count; i++) {
			unitHandleIds[i] = walkers.units.get(i).getHandleId();
			abilityHandleIds[i] = walkers.abilities.get(i).getHandleId();
			xs[i] = slots[i * 2];
			ys[i] = slots[(i * 2) + 1];
		}
		listener.issueGroupPointOrder(unitHandleIds, abilityHandleIds, OrderIds.move, xs, ys, false);
	}

	/**
	 * Pulls a formation back along the way it marches until its front-most slot is the front line. {@code CGroupMove}
	 * centres its rows on the point it is given — the front row lands a row <em>past</em> it — so a block centred on
	 * the hero's destination is a block she has to walk through. Nothing is pulled forward: slots that are already
	 * behind the line are left where they are.
	 */
	static void placeBehind(final float[] slots, final float frontX, final float frontY, final float dirX,
			final float dirY) {
		float front = 0f;
		for (int i = 0; i < slots.length; i += 2) {
			front = Math.max(front, ((slots[i] - frontX) * dirX) + ((slots[i + 1] - frontY) * dirY));
		}
		if (front == 0f) {
			return;
		}
		for (int i = 0; i < slots.length; i += 2) {
			slots[i] -= dirX * front;
			slots[i + 1] -= dirY * front;
		}
	}

	/**
	 * Pushes a formation forward along the way it marches until every slot is at least {@code clearance} ahead of the
	 * hero, which is what makes the make-way order a way <em>out</em>: a block aimed at a point in front of her is
	 * centred on it, so its back rows would otherwise land on the ground she is standing on.
	 */
	private static CAbility moveAbility(final CSimulation simulation, final CUnit unit, final int playerIndex,
			final AbilityPointTarget point) {
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
		for (final CAbility ability : unit.getAbilities()) {
			ability.checkCanUse(simulation, unit, playerIndex, OrderIds.move, false, activationReceiver.reset());
			if (!activationReceiver.isOk()) {
				continue;
			}
			ability.checkCanTarget(simulation, unit, playerIndex, OrderIds.move, false, point, targetReceiver.reset());
			if (targetReceiver.getTarget() != null) {
				return ability;
			}
		}
		return null;
	}

	/** A roster narrowed to the units that can walk, with the ability that carries each one's move. */
	private static final class Walkers {
		final List<CUnit> units = new ArrayList<>();
		final List<CAbility> abilities = new ArrayList<>();

		boolean isEmpty() {
			return this.units.isEmpty();
		}
	}
}
