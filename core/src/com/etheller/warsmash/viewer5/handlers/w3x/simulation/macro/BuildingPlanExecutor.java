package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CDestructable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityEntangledMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.mine.CAbilityGoldMinable;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.nightelf.root.CAbilityEntangleGoldMine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CTargetType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.cargohold.CAbilityCargoHold;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CAbstractRangedBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorBoardTransport;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.harvest.CAbilityHarvest;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.queue.CAbilityQueue;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.upgrade.CAbilityUpgrade;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.COrder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.BuildOnBuildingIntersector;

/**
 * The building plan, executed.
 * <p>
 * This is the half that reads the simulation and sends orders; the decisions themselves are in {@link MacroRule},
 * {@link MacroSupplyRule} and {@link LiveBaseDefaults}, which take plain values and are tested without one. The split
 * is not decoration: it is what lets the numbering rules be proved by {@code :core:test} rather than by watching a
 * phone.
 * <p>
 * Three things are copied from the computer AI rather than shared with it, because every class in
 * {@code parsers.jass.ai} is package-private and takes an {@code AIController} the player does not have: the free-worker
 * pick, the ring site search, and the build-job lifecycle. They are shapes, not code paths -- the AI's own orders go
 * straight to {@code CUnit.order}, and these go through {@link CPlayerUnitOrderListener}, which is what keeps the plan
 * replay- and LAN-safe.
 * <p>
 * The pass runs on the simulation's tick, never on the frame, and does at most one thing: supply, or the one ticket the
 * rule named. The order it issues is picked up by the delaying listener at the end of the same step, so the whole plan
 * is aligned to steps even though it is called from the interface.
 */
public final class BuildingPlanExecutor {
	/** How often the plan is looked at, in steps. One pass a second is the AI's own pace. */
	public static final int PLAN_PASS_TICKS = CTimer.wholeSteps(1);
	/** A build job older than this is given up: a worker that never arrived must not hold the plan forever. */
	private static final int JOB_TIMEOUT_TICKS = CTimer.wholeSteps(90);
	/** How long a job is given to start before the worker's order is taken as a refusal. */
	private static final int JOB_GRACE_TICKS = CTimer.wholeSteps(2);
	/** How long a refused ticket waits before it is offered again, so a refusal cannot spin. */
	private static final int RETRY_TICKS = CTimer.wholeSteps(5);
	/**
	 * How many times a ticket may be refused before it is taken to be unplaceable rather than unlucky.
	 * <p>
	 * A refusal is a race, not a verdict -- the price is charged a step after the order, and a site can be taken in
	 * between -- so one refusal waits and tries again. Three in a row is not a race, and since a waiting ticket holds
	 * its number, letting it keep trying would stall every number behind it for the rest of the mission.
	 */
	private static final int MAX_REFUSALS = 3;

	private static final float SITE_RADIUS_FLOOR = 256f;
	private static final float SITE_RADIUS_CEILING = 1800f;
	private static final float BUILDING_GAP = 96f;
	private static final float MINE_SEARCH_DISTANCE = 1600f;
	/** How far from the base the plan looks for a tree, and how many of them it offers a worker. */
	private static final float TREE_SEARCH_DISTANCE = 2500f;
	private static final int TREE_TRIES = 6;
	/** How many of the nearest trees one worker considers before picking one, and how close counts as "at" a tree. */
	private static final int TREE_CANDIDATES = 12;
	private static final float TREE_CROWD_RADIUS = 128f;
	private static final int TREE_CACHE_TICKS = CTimer.wholeSteps(1);
	/**
	 * How many workers one of the player's own gold mines takes. Five is what an entangled mine holds, which is also
	 * the limit the game gives a tree of gold: a sixth wisp there is a wisp standing still while the mine is full.
	 */
	private static final int GOLD_WORKERS_PER_MINE = 5;
	/**
	 * How long a worker that was sent to a mine and ended up idle again is kept off the mines. Standing at a full mine
	 * for a minute and then being sent back to it is worse than chopping: the second order says nothing new.
	 */
	private static final int MINE_GIVE_UP_TICKS = CTimer.wholeSteps(60);
	/** How close a finished structure has to be to the point it was ordered at to count as this job's. */
	private static final float LAID_DOWN_RADIUS = 64f;

	private final CSimulation simulation;
	private final int playerIndex;
	private final CPlayerUnitOrderListener listener;
	private final MacroPlan plan = new MacroPlan();
	private final List<BuildJob> jobs = new ArrayList<>();
	/** Keyed on ticket identity: two tickets for the same building are two different orders. */
	private final Map<MacroTicket, Issue> issues = new IdentityHashMap<>();
	/** Where each otherwise-ready ticket would go, found once so the pass does not search twice. */
	private final Map<MacroTicket, float[]> sites = new IdentityHashMap<>();
	private final MacroActivationReceiver receiver = new MacroActivationReceiver();
	private final MacroLog log = new MacroLog();
	/** The trees near the base, sorted by distance, so an idle worker does not re-scan the map every pass. */
	private List<CDestructable> treeCache;
	/** When each worker was last sent to a mine, so one that could not get in is not sent straight back. */
	private final Map<Integer, Integer> mineOrders = new HashMap<>();
	/**
	 * Workers that have just finished a structure. They go to lumber next, not to the mine: the plan promised the
	 * builder returns to harvesting between jobs, and for a Night Elf wisp "harvesting" used to mean the gold trip
	 * even when the worker had never been on gold.
	 */
	private final Set<Integer> justBuilt = new HashSet<>();
	private int treeCacheTick = Integer.MIN_VALUE;

	private boolean supplyPaused;
	private boolean seeded;
	private boolean baseReadable;
	private int nextPassTick = Integer.MIN_VALUE;

	public BuildingPlanExecutor(final CSimulation simulation, final int playerIndex,
			final CPlayerUnitOrderListener listener) {
		this.simulation = simulation;
		this.playerIndex = playerIndex;
		this.listener = listener;
	}

	// ------------------------------------------------------------------ the interface's end

	public MacroPlan getPlan() {
		return this.plan;
	}

	/** The supply line's pause, as the panel's toggle sets it. Supply keeps out of the way entirely while paused. */
	public boolean isSupplyPaused() {
		return this.supplyPaused;
	}

	public void setSupplyPaused(final boolean supplyPaused) {
		this.supplyPaused = supplyPaused;
	}

	public boolean isSeeded() {
		return this.seeded;
	}

	/** What the supply line would build, for the panel to name it. Null while the base can make no food. */
	public War3ID getSupplyType() {
		return LiveBaseDefaults.supplyType(buildableFacts());
	}

	/** The reason the player is driving, or null while the plan is running. */
	public Stuck getInert() {
		if (!this.seeded) {
			// Nothing has been decided yet: the map's script has not made the base, so there is nothing to report.
			return null;
		}
		if (!this.baseReadable) {
			return Stuck.of(Stuck.Reason.UNREADABLE);
		}
		if (!hasWorker()) {
			return Stuck.of(Stuck.Reason.NO_BUILDER);
		}
		return null;
	}

	/**
	 * One pass, on the simulation's beat.
	 * <p>
	 * Called every frame by the interface, it does its work at most once per {@link #PLAN_PASS_TICKS}. Two calls in
	 * one step are one pass, which is what keeps the plan off the frame loop.
	 */
	public void step(final int gameTurnTick) {
		if (gameTurnTick < this.nextPassTick) {
			return;
		}
		this.nextPassTick = gameTurnTick + PLAN_PASS_TICKS;
		final CPlayer player = this.simulation.getPlayer(this.playerIndex);
		if (player == null) {
			return;
		}
		retireJobs(player, gameTurnTick);
		if (!this.seeded) {
			seed(gameTurnTick);
			return;
		}
		if (!this.baseReadable) {
			this.log.say("unreadable", gameTurnTick,
					"pass " + gameTurnTick + ": the techtree cannot be read, so the plan does nothing");
			return;
		}
		restoreIdleWorker(gameTurnTick);
		ensureEntangledMine(gameTurnTick);
		if (runSupply(player, gameTurnTick)) {
			return;
		}
		final List<TicketState> states = states(gameTurnTick);
		logStates(states, gameTurnTick);
		final TicketState next = MacroRule.next(List.of(states));
		this.log.say("heartbeat", gameTurnTick,
				"pass " + gameTurnTick + ": plan=" + this.plan.size() + " next="
						+ ((next == null) ? "none" : (next.getNumber() + " " + next.getTicket())) + " food="
						+ player.getFoodUsed() + "/" + player.getFoodCapLimit() + " gold=" + player.getGold() + " lumber="
						+ player.getLumber() + " jobs=" + this.jobs.size());
		if (next != null) {
			execute(next, gameTurnTick);
		}
	}

	/**
	 * Says what each ticket thinks of itself, once per change. This is the line that separates "waiting for gold" from
	 * "no site for it" from "requirement not met yet" -- the three ways a plan looks like it is doing nothing.
	 */
	private void logStates(final List<TicketState> states, final int gameTurnTick) {
		if ((gameTurnTick % 10) != 0) {
			return;
		}
		for (final TicketState state : states) {
			final String now = describe(state);
			this.log.transition("ticket " + state.getNumber(), now,
					"t" + state.getNumber() + " " + state.getTicket() + ": " + now);
		}
	}

	private static String describe(final TicketState state) {
		if (state.isStuck()) {
			return "STUCK " + state.getStuck().getReason();
		}
		if (state.isPending()) {
			return "PENDING";
		}
		return state.isAffordable() ? "READY" : "UNAFFORDABLE";
	}

	// ------------------------------------------------------------------ defaults, once

	/**
	 * The plan a mission starts with, decided once and never again.
	 * <p>
	 * It waits for the base to exist rather than guessing: the script makes the workers during the first steps, so the
	 * first passes have nothing to read. Once it has read, it never re-seeds -- a seeded ticket the player removes stays
	 * removed, which is the difference between a default and a policy.
	 */
	private void seed(final int gameTurnTick) {
		final Set<War3ID> owned = ownedOrPendingTypes();
		if (owned.isEmpty()) {
			// Nothing of ours exists yet: the map is still being made, so there is no base to read.
			this.log.say("waiting", gameTurnTick, "seed: waiting for the base -- nothing of ours exists yet");
			return;
		}
		this.seeded = true;
		this.baseReadable = (this.simulation.getUnitData() != null);
		if (!this.baseReadable) {
			this.log.say("noseed", gameTurnTick, "seed: the techtree cannot be read, so the plan stays empty");
			return;
		}
		final CUnitRace race = playerRace();
		final List<PlanTypeFacts> buildable = buildableFacts();
		final War3ID military = LiveBaseDefaults.militaryType(buildable, typeFacts());
		final List<MacroTicket> tickets = LiveBaseDefaults.seed(hasWorker(), true, race, owned, typeLookup(),
				military);
		MacroLog.line("seed: race=" + race + " worker=" + hasWorker() + " owned=" + owned.size() + " plan="
				+ tickets.size());
		for (int i = 0; i < tickets.size(); i++) {
			MacroLog.line("  " + i + " " + tickets.get(i));
		}
		for (final MacroTicket ticket : tickets) {
			this.plan.append(ticket);
		}
		for (final CUnit unit : ours()) {
			final CAbilityCargoHold cargo = unit.getCargoData();
			if (cargo != null) {
				MacroLog.line("mine h=" + unit.getHandleId() + " type="
						+ unit.getUnitType().getTypeId().asStringValue() + " cargo=" + cargo.getCargoCount() + "/"
						+ cargo.getCargoCapacity() + " gold=" + (unit.getOverlayedGoldMineData() != null));
			}
		}
		// Every mine on the map, ours or not, with what it takes to enter it: this is the line that says why no worker
		// was sent to gold, which is otherwise indistinguishable from a map that has no mine at all.
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			final String type = unit.getUnitType().getTypeId().asStringValue();
			if (!"ngol".equals(type) && !"egol".equals(type) && !"ugol".equals(type) && !"agol".equals(type)) {
				continue;
			}
			MacroLog.line("gold: h=" + unit.getHandleId() + " type=" + type + " player="
					+ unit.getPlayerIndex() + " dead=" + unit.isDead() + " entangled="
					+ (unit.getFirstAbilityOfType(CAbilityEntangledMine.class) != null) + " overlay="
					+ (unit.getOverlayedGoldMineData() != null) + " cargo="
					+ ((unit.getCargoData() == null) ? "none"
							: (unit.getCargoData().getCargoCount() + "/" + unit.getCargoData().getCargoCapacity())));
		}
	}

	/**
	 * The race of the units that build: the worker's own, because that is the list of structures the base can actually
	 * put down. The player's {@code CPlayer.getRace()} is a handle to a race-manager entry and can be null on a neutral
	 * slot, so the live worker is both the simpler and the truer answer to "whose build order is this".
	 */
	private CUnitRace playerRace() {
		for (final CUnit unit : ours()) {
			if (!unit.isDead() && !unit.isHidden() && !unit.isBuilding() && isWorkerType(unit)) {
				final CUnitType type = unit.getUnitType();
				if ((type != null) && (type.getRace() != null)) {
					return type.getRace();
				}
			}
		}
		return CUnitRace.OTHER;
	}

	/** A type lookup for the rules that need to walk the hall chain; null for a rawcode the map does not define. */
	private Function<War3ID, PlanTypeFacts> typeLookup() {
		return rawcode -> {
			final CUnitData unitData = this.simulation.getUnitData();
			if ((unitData == null) || (rawcode == null)) {
				return null;
			}
			return PlanTypeFacts.of(unitData.getUnitType(rawcode));
		};
	}

	/** Everything the player owns, is building, has queued, or would get from a hall upgrade. */
	private Set<War3ID> ownedOrPendingTypes() {
		final Set<War3ID> types = new LinkedHashSet<>();
		for (final CUnit unit : ours()) {
			types.add(unit.getTypeId());
			final CAbilityUpgrade upgrade = unit.getFirstAbilityOfType(CAbilityUpgrade.class);
			if (upgrade != null) {
				types.addAll(upgrade.getUpgradesTo());
			}
			final CAbilityQueue queue = unit.getFirstAbilityOfType(CAbilityQueue.class);
			if (queue != null) {
				for (final War3ID queued : unit.getBuildQueue()) {
					if (queued != null) {
						types.add(queued);
					}
				}
			}
		}
		for (final BuildJob job : this.jobs) {
			types.add(job.typeId);
		}
		for (final Map.Entry<MacroTicket, Issue> entry : this.issues.entrySet()) {
			if ((entry.getKey().getKind() == MacroTicketKind.UPGRADE)
					&& this.plan.indexOf(entry.getKey()) >= 0) {
				types.add(entry.getKey().getRawcode());
			}
		}
		return types;
	}

	// ------------------------------------------------------------------ retiring

	/**
	 * Gives up on jobs and orders that are over, and removes the tickets they finished.
	 * <p>
	 * A build job ends one of three ways, all three copied from the AI: the worker died, the structure appeared within
	 * {@link #LAID_DOWN_RADIUS} of the point it was ordered at (which is the only honest "done" -- counting the
	 * player's structures would credit a mission's starting barracks to this job), or the worker stopped trying.
	 */
	private void retireJobs(final CPlayer player, final int gameTurnTick) {
		for (final Iterator<BuildJob> it = this.jobs.iterator(); it.hasNext();) {
			final BuildJob job = it.next();
			if (laidDown(job)) {
				it.remove();
				this.justBuilt.add(job.worker.getHandleId());
				if (job.ticket != null) {
					this.plan.remove(job.ticket);
					this.issues.remove(job.ticket);
				}
				continue;
			}
			if (job.worker.isDead() || (gameTurnTick - job.issuedTick > JOB_TIMEOUT_TICKS)) {
				it.remove();
				refused(job, gameTurnTick);
				continue;
			}
			if ((gameTurnTick - job.issuedTick > JOB_GRACE_TICKS) && !isBuilding(job.worker) && !isOrdered(job)) {
				// The worker took something else: the order was refused, and the ticket is offered again after its
				// backoff rather than every pass.
				it.remove();
				refused(job, gameTurnTick);
			}
		}
		final List<MacroTicket> finished = new ArrayList<>();
		for (final Map.Entry<MacroTicket, Issue> entry : this.issues.entrySet()) {
			final MacroTicket ticket = entry.getKey();
			final Issue issue = entry.getValue();
			if (this.plan.indexOf(ticket) < 0) {
				finished.add(ticket);
			}
			else if (ticket.getKind() == MacroTicketKind.RESEARCH) {
				if (player.getTechtreeUnlocked(ticket.getRawcode()) >= issue.targetLevel) {
					this.plan.remove(ticket);
					finished.add(ticket);
				}
			}
			else if (ticket.getKind() == MacroTicketKind.UPGRADE) {
				final CUnit hall = this.simulation.getUnit(issue.hallHandleId);
				if ((hall == null) || hall.isDead()) {
					finished.add(ticket);
				}
				else if (hall.getTypeId().equals(ticket.getRawcode()) && !hall.isUpgrading()) {
					this.plan.remove(ticket);
					finished.add(ticket);
				}
			}
		}
		for (final MacroTicket ticket : finished) {
			this.issues.remove(ticket);
		}
	}

	private boolean laidDown(final BuildJob job) {
		for (final CUnit unit : ours()) {
			if (unit.getTypeId().equals(job.typeId) && !unit.isDead()
					&& (distance(unit.getX(), unit.getY(), job.x, job.y) <= LAID_DOWN_RADIUS)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOrdered(final BuildJob job) {
		final COrder order = job.worker.getCurrentOrder();
		return (order != null) && (order.getOrderId() == job.typeId.getValue());
	}

	/** Remembers that this ticket's order did not take, so it waits before it is offered again. */
	private void refused(final BuildJob job, final int gameTurnTick) {
		if (job.ticket == null) {
			// The supply line owns its own building: it is simply offered again on the next pass that wants it.
			return;
		}
		final Issue previous = this.issues.get(job.ticket);
		final int retries = ((previous == null) ? 0 : previous.retries) + 1;
		this.issues.put(job.ticket, new Issue(gameTurnTick, 0, 0, retries));
	}

	// ------------------------------------------------------------------ the worker

	/**
	 * The idle-worker sweep: a builder with nothing to do goes back to the mine.
	 *
	 * A worker is idle between jobs, and a plan that leaves it standing at the site has spent the game's only source of
	 * income to save a walk. The last target is preferred, so a worker taken off lumber goes back to lumber.
	 */
	private void restoreIdleWorker(final int gameTurnTick) {
		if (!this.jobs.isEmpty()) {
			this.log.say("restore-busy", gameTurnTick, "restore: a build job is running, workers left alone");
			return;
		}
		// One pass sends every idle worker, not just the first: a pass is a second apart, so a queue of wisps used to
		// stand around for a second each waiting to be told where to go. What they are sent to is recorded as the pass
		// goes, so the second worker does not pick the tree the first one just claimed.
		final Map<CWidget, Integer> claimed = new IdentityHashMap<>();
		int ordered = 0;
		int toMine = 0;
		int toWood = 0;
		for (final CUnit unit : ours()) {
			if (unit.isDead() || unit.isHidden() || unit.isBuilding() || unit.isLoaded()
					|| (buildAbility(unit) == null)) {
				continue;
			}
			if (!isIdle(unit)) {
				continue;
			}
			// `CUnit.order` is how the engine's own AI sends a worker back to work (AIHarvest.orderHarvestTarget):
			// it picks the ability that both accepts the harvest order and can target this mine or tree. Choosing an
			// ability handle here and handing it to issueTargetOrder silently failed instead: the first ability whose
			// checkCanUse says yes is `CAbilityMove`, which says yes to every order id, so the order was built on the
			// move ability and did nothing -- the worker stood idle for the rest of the mission.
			final CWidget accepted = sendToHarvest(unit, gameTurnTick, claimed);
			ordered++;
			if (accepted instanceof CDestructable) {
				toWood++;
			}
			else if (accepted != null) {
				toMine++;
			}
			this.log.say("restore-" + unit.getHandleId(), gameTurnTick,
					"restore: idle h=" + unit.getHandleId() + " -> " + describeTarget(accepted, unit, claimed));
		}
		if (ordered == 0) {
			this.log.say("restore-none", gameTurnTick, "restore: no idle builder");
		}
		else {
			// The shape of a sweep in one line: five to the mine, the rest to lumber is what a healthy base looks like.
			this.log.say("restore-summary", gameTurnTick,
					"restore: " + ordered + " idle -> mine " + toMine + ", wood " + toWood);
		}
	}

	/**
	 * Night Elf gold comes from an entangled mine, and a campaign base starts with the plain one: no wisp can enter it
	 * until one of them entangles it, so without this the whole base went to lumber and the gold never moved off the
	 * mission's starting amount.
	 * <p>
	 * The ability is the wisp's own (it walks to the mine and builds the entangled mine over it), and one worker is put
	 * on it at a time: a second order on a mine another wisp is already walking to would be the same trip twice.
	 */
	private void ensureEntangledMine(final int gameTurnTick) {
		for (final CUnit unit : ours()) {
			if (unit.getOverlayedGoldMineData() != null) {
				return;
			}
		}
		final float[] anchor = anchor();
		CUnit mine = null;
		float mineDistance = Float.MAX_VALUE;
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			final CAbilityGoldMinable gold = unit.getGoldMineData();
			if ((gold == null) || !gold.isBaseMine() || unit.isDead() || unit.isHidden()) {
				continue;
			}
			final float distance = (anchor == null) ? 0f : distance(anchor[0], anchor[1], unit.getX(), unit.getY());
			if ((mine == null) || (distance < mineDistance)) {
				mine = unit;
				mineDistance = distance;
			}
		}
		if (mine == null) {
			return;
		}
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : ours()) {
			if (unit.isDead() || unit.isHidden() || unit.isBuilding()) {
				continue;
			}
			final COrder order = unit.getCurrentOrder();
			if ((order != null) && (order.getOrderId() == OrderIds.entangle)) {
				// One worker is already on it; nothing to add until it gets there.
				return;
			}
			if (unit.getFirstAbilityOfType(CAbilityEntangleGoldMine.class) == null) {
				continue;
			}
			final float distance = distance(unit.getX(), unit.getY(), mine.getX(), mine.getY());
			if ((best == null) || (distance < bestDistance)) {
				best = unit;
				bestDistance = distance;
			}
		}
		if ((best != null) && best.order(this.simulation, OrderIds.entangle, mine)) {
			this.log.say("entangle", gameTurnTick,
					"entangle: h=" + best.getHandleId() + " -> mine h=" + mine.getHandleId());
		}
	}

	private String describeTarget(final CWidget target, final CUnit worker,
			final Map<CWidget, Integer> claimed) {
		if (target == null) {
			return "nothing (every candidate refused the harvest order)";
		}
		if (target instanceof CDestructable) {
			return "tree " + target.getHandleId() + " (distance "
					+ (int) distance(worker.getX(), worker.getY(), target.getX(), target.getY()) + ", crowd "
					+ workersAt((CDestructable) target, worker, claimed) + ")";
		}
		return "mine " + target.getHandleId();
	}

	/**
	 * Sends a worker to harvest something it will actually harvest, and reports the target it accepted.
	 * <p>
	 * One target is not enough, because the workers disagree about what they will take. In The Broken Isles the plan
	 * offered the nearest gold mine and the wisp said no every three seconds -- that mine is a base mine, and a wisp
	 * takes a tree or *its own* entangled mine, which is a separate unit the player owns (the campaign's pre-built
	 * mine) and which {@link #nearestMine} deliberately skips because a peasant must not be sent to it. So the
	 * candidates run from the most specific to the most general and the first one the unit accepts wins: what it was
	 * last at, its own gold mine, the nearest base mine, the nearest trees, and finally the return order for a worker
	 * that is carrying something.
	 */
	private CWidget sendToHarvest(final CUnit unit, final int gameTurnTick,
			final Map<CWidget, Integer> claimed) {
		// A worker that has just finished a structure goes to lumber, as the plan promises: the mine already has its
		// five, and a wisp walking to the mine to be turned away is a walk for nothing.
		if (this.justBuilt.remove(unit.getHandleId())) {
			final CDestructable justBuiltLumber = sendToTree(unit, gameTurnTick, claimed);
			if (justBuiltLumber != null) {
				return justBuiltLumber;
			}
		}
		final CWidget last = lastHarvestTarget(unit);
		if ((last != null) && !last.isDead() && unit.order(this.simulation, OrderIds.harvest, last)) {
			claimed.merge(last, 1, Integer::sum);
			return last;
		}
		final Integer mineOrderTick = this.mineOrders.get(unit.getHandleId());
		final boolean offMines = (mineOrderTick != null) && ((gameTurnTick - mineOrderTick) < MINE_GIVE_UP_TICKS);
		final CUnit ownMine = offMines ? null : nearestOwnMine(anchor(), unit.getPlayerIndex(), claimed);
		if (ownMine != null) {
			if (unit.order(this.simulation, OrderIds.harvest, ownMine)) {
				claimed.merge(ownMine, 1, Integer::sum);
				this.mineOrders.put(unit.getHandleId(), gameTurnTick);
				return ownMine;
			}
			this.log.say("own-mine-refused", gameTurnTick, "restore: h=" + unit.getHandleId() + " refused its own mine h="
					+ ownMine.getHandleId() + " type=" + ownMine.getUnitType().getTypeId().asStringValue() + " entangled="
					+ (ownMine.getFirstAbilityOfType(CAbilityEntangledMine.class) != null) + " mine="
					+ (ownMine.getGoldMineData() != null) + " cargo=" + (ownMine.getCargoData() != null));
		}
		final CUnit mine = offMines ? null : nearestMine(anchor());
		if ((mine != null) && !mine.isDead() && unit.order(this.simulation, OrderIds.harvest, mine)) {
			claimed.merge(mine, 1, Integer::sum);
			this.mineOrders.put(unit.getHandleId(), gameTurnTick);
			return mine;
		}
		int tries = 0;
		for (final CDestructable tree : treesToTry(unit, gameTurnTick, claimed)) {
			if (tree.isDead()) {
				continue;
			}
			if (tries++ >= TREE_TRIES) {
				break;
			}
			if (unit.order(this.simulation, OrderIds.harvest, tree)) {
				claimed.merge(tree, 1, Integer::sum);
				this.mineOrders.remove(unit.getHandleId());
				return tree;
			}
		}
		// A worker carrying lumber only takes the return order: it puts the load down first and goes on from there.
		// AIHarvest.orderHarvestTarget does exactly this on the same failure.
		final CAbilityHarvest harvest = unit.getFirstAbilityOfType(CAbilityHarvest.class);
		if ((harvest != null) && (harvest.getCarriedResourceAmount() > 0)) {
			unit.order(this.simulation, OrderIds.returnresources, null);
		}
		return null;
	}

	/** The tree this worker takes, or null if every tree it was offered refused the order. */
	private CDestructable sendToTree(final CUnit unit, final int gameTurnTick,
			final Map<CWidget, Integer> claimed) {
		int tries = 0;
		for (final CDestructable tree : treesToTry(unit, gameTurnTick, claimed)) {
			if (tree.isDead()) {
				continue;
			}
			if (tries++ >= TREE_TRIES) {
				break;
			}
			if (unit.order(this.simulation, OrderIds.harvest, tree)) {
				claimed.merge(tree, 1, Integer::sum);
				this.mineOrders.remove(unit.getHandleId());
				return tree;
			}
		}
		return null;
	}

	/**
	 * The trees to offer one worker: nearest to that worker first, and among the nearest few the ones nobody is chopping.
	 * <p>
	 * The list used to be sorted once, by distance from the base, and handed to every worker unchanged. So a whole base
	 * walked to the same tree and then to that tree's neighbours, and a worker standing on the far side of the woods was
	 * sent across them, because the base was nearer to the tree than the worker was. Sorting by the worker fixes the
	 * walk; preferring a tree with nobody at it is what makes a group fan out instead of queueing at one trunk.
	 */
	private List<CDestructable> treesToTry(final CUnit worker, final int gameTurnTick,
			final Map<CWidget, Integer> claimed) {
		final List<CDestructable> candidates = new ArrayList<>();
		for (final CDestructable tree : trees(anchor(), gameTurnTick)) {
			if (!tree.isDead()) {
				candidates.add(tree);
			}
		}
		candidates.sort((a, b) -> Float.compare(distance(worker.getX(), worker.getY(), a.getX(), a.getY()),
				distance(worker.getX(), worker.getY(), b.getX(), b.getY())));
		final List<CDestructable> nearest = new ArrayList<>(
				candidates.subList(0, Math.min(TREE_CANDIDATES, candidates.size())));
		nearest.sort((a, b) -> {
			final int crowd = Integer.compare(workersAt(a, worker, claimed), workersAt(b, worker, claimed));
			return (crowd != 0) ? crowd
					: Float.compare(distance(worker.getX(), worker.getY(), a.getX(), a.getY()),
							distance(worker.getX(), worker.getY(), b.getX(), b.getY()));
		});
		return nearest;
	}

	/**
	 * The workers already chopping this tree, or standing at it. Workers only: a Tree of Life or a Moon Well standing
	 * beside a tree is not chopping it, and counting the base as a crowd pushed the pick across the woods.
	 */
	private int workersAt(final CDestructable tree, final CUnit chopping, final Map<CWidget, Integer> claimed) {
		int workers = 0;
		for (final CUnit unit : ours()) {
			if ((unit == chopping) || unit.isDead() || unit.isHidden() || unit.isBuilding() || unit.isLoaded()
					|| (buildAbility(unit) == null)) {
				continue;
			}
			final CBehavior behavior = unit.getCurrentBehavior();
			if ((behavior instanceof CAbstractRangedBehavior)
					&& (((CAbstractRangedBehavior) behavior).getTarget() == tree)) {
				workers++;
			}
			else if (distance(unit.getX(), unit.getY(), tree.getX(), tree.getY()) < TREE_CROWD_RADIUS) {
				workers++;
			}
		}
		return workers + claimed.getOrDefault(tree, 0);
	}

	private List<CDestructable> trees(final float[] anchor, final int gameTurnTick) {
		if ((this.treeCache != null) && ((gameTurnTick - this.treeCacheTick) < TREE_CACHE_TICKS)) {
			return this.treeCache;
		}
		this.treeCacheTick = gameTurnTick;
		final List<CDestructable> near = new ArrayList<>();
		if (anchor != null) {
			for (final CDestructable destructable : this.simulation.getDestructables()) {
				if (!destructable.isDead() && destructable.getDestType().getTargetedAs().contains(CTargetType.TREE)
						&& (distance(anchor[0], anchor[1], destructable.getX(), destructable.getY()) < TREE_SEARCH_DISTANCE)) {
					near.add(destructable);
				}
			}
			// Left unsorted: the order that matters is the one worker's own distance gives it, not the base's.
		}
		this.treeCache = near;
		return near;
	}

	/**
	 * The player's own gold mine with room for another worker: the entangled mine a wisp can enter and the haunted mine
	 * an acolyte can, which are separate units in front of a base mine rather than the base mine itself.
	 * <p>
	 * A mine counts the workers inside it and the workers walking to it, because counting only the ones already inside
	 * let a queue of wisps pile up outside a full mine and bounce off it for a while before giving up and going to the
	 * trees. With the walkers counted, the sixth wisp is sent to lumber at once, and a second mine gets its own five.
	 */
	private CUnit nearestOwnMine(final float[] anchor, final int playerIndex,
			final Map<CWidget, Integer> claimed) {
		if (anchor == null) {
			return null;
		}
		CUnit best = null;
		float bestDistance = MINE_SEARCH_DISTANCE;
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			if (unit.isDead() || (unit.getPlayerIndex() != playerIndex) || (unit.getOverlayedGoldMineData() == null)) {
				continue;
			}
			final CAbilityCargoHold cargo = unit.getCargoData();
			final int holds = (cargo == null) ? GOLD_WORKERS_PER_MINE
					: Math.min(GOLD_WORKERS_PER_MINE, cargo.getCargoCapacity());
			if ((workersOnMine(unit) + claimed.getOrDefault(unit, 0)) >= holds) {
				continue;
			}
			final float distance = distance(anchor[0], anchor[1], unit.getX(), unit.getY());
			if ((best == null) || (distance < bestDistance)) {
				best = unit;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** The workers already working a mine: those inside it, and those on their way in. */
	private int workersOnMine(final CUnit mine) {
		int workers = 0;
		final CAbilityCargoHold cargo = mine.getCargoData();
		if (cargo != null) {
			workers += cargo.getCargoCount();
		}
		for (final CUnit unit : ours()) {
			if (unit != mine) {
				final CBehaviorBoardTransport board = unit.getBoardTransportBehavior();
				if ((board != null) && (unit.getCurrentBehavior() == board) && (board.getTarget() == mine)) {
					workers++;
				}
			}
		}
		return workers;
	}

	private static CWidget lastHarvestTarget(final CUnit unit) {
		for (final CAbility ability : unit.getAbilities()) {
			if (ability instanceof CAbilityHarvest) {
				return ((CAbilityHarvest) ability).getLastHarvestTarget();
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ supply

	private boolean runSupply(final CPlayer player, final int gameTurnTick) {
		if (this.supplyPaused) {
			return false;
		}
		final War3ID supplyType = getSupplyType();
		if (supplyType == null) {
			return false;
		}
		final CUnitType type = this.simulation.getUnitData().getUnitType(supplyType);
		if (type == null) {
			return false;
		}
		int inFlight = 0;
		for (final BuildJob job : this.jobs) {
			if (job.typeId.equals(supplyType)) {
				inFlight++;
			}
		}
		// A supply building that is still going up counts as one in flight. Its food arrives only when it is finished,
		// so without this the line wanted the next one the moment the foundation appeared, ordered it, and left a
		// trail of foundations with a worker walking from one to the next.
		for (final CUnit unit : ours()) {
			if (!unit.isDead() && unit.getTypeId().equals(supplyType) && unit.isConstructing()) {
				inFlight++;
			}
		}
		final CUnit builder = findBuilder(supplyType, false);
		if ((builder == null) && (findBuilder(supplyType, true) != null)) {
			this.log.say("supply-gold", gameTurnTick,
					"supply: " + supplyType.asStringValue() + " is wanted but every builder is on gold");
		}
		boolean typeStuck = false;
		if (builder != null) {
			buildAbility(builder).checkCanUse(this.simulation, builder, this.playerIndex, supplyType.getValue(), false,
					this.receiver.reset());
			typeStuck = this.receiver.getOutcome() == MacroActivationReceiver.Outcome.STUCK;
		}
		else {
			typeStuck = true;
		}
		if (!MacroSupplyRule.wanted(true, typeStuck, player.getFoodUsed(), 0, player.getFoodCap(),
				player.getFoodCapLimit(), player.getFoodCapCeiling(), type.getFoodMade(), this.supplyPaused, inFlight)) {
			return false;
		}
		if ((builder == null) || !this.jobs.isEmpty()) {
			return false;
		}
		final float[] site = findSite(type, builder);
		if (site == null) {
			this.log.say("supply-nosite", gameTurnTick,
					"supply: " + supplyType.asStringValue() + " is wanted but there is no room for it near the base");
			return false;
		}
		issueBuild(builder, null, supplyType, site, gameTurnTick);
		MacroLog.line("do SUPPLY " + supplyType.asStringValue() + " with h=" + builder.getHandleId() + " at "
				+ (int) site[0] + "," + (int) site[1] + " (food " + player.getFoodUsed() + "/" + player.getFoodCapLimit()
				+ ")");
		return true;
	}

	// ------------------------------------------------------------------ the numbers

	/**
	 * Where every ticket stands this pass.
	 * <p>
	 * The ability's own gate decides affordability and stuckness, so the plan waits for exactly what the engine would
	 * refuse and nothing it would accept. A build ticket is pending rather than stuck while another job holds the
	 * worker: that is "waits for the worker, not for the gold".
	 */
	private List<TicketState> states(final int gameTurnTick) {
		this.sites.clear();
		final List<TicketState> states = new ArrayList<>();
		final List<MacroTicket> tickets = this.plan.getTickets();
		for (int number = 0; number < tickets.size(); number++) {
			final MacroTicket ticket = tickets.get(number);
			final TicketState state;
			switch (ticket.getKind()) {
				case BUILD:
					state = buildState(number, ticket, gameTurnTick);
					break;
				case RESEARCH:
					state = researchState(number, ticket, gameTurnTick);
					break;
				case UPGRADE:
					state = upgradeState(number, ticket, gameTurnTick);
					break;
				default:
					state = TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.UNREADABLE));
					break;
			}
			states.add(state);
		}
		return states;
	}

	private TicketState buildState(final int number, final MacroTicket ticket, final int gameTurnTick) {
		final War3ID rawcode = ticket.getRawcode();
		if (givenUpOn(ticket)) {
			return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.NO_SITE));
		}
		if (backingOff(ticket, gameTurnTick)) {
			return TicketState.pending(number, ticket);
		}
		for (final BuildJob job : this.jobs) {
			if (job.ticket == ticket) {
				return TicketState.pending(number, ticket);
			}
		}
		if (!this.jobs.isEmpty()) {
			// One construction job at a time: this ticket waits for the worker, and its number stays open.
			return TicketState.pending(number, ticket);
		}
		final CUnitType type = this.simulation.getUnitData().getUnitType(rawcode);
		if (type == null) {
			return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.UNREADABLE));
		}
		final CUnit builder = findBuilder(rawcode, false);
		if (builder == null) {
			if (findBuilder(rawcode, true) == null) {
				return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.NO_BUILDER));
			}
			// Waiting beats taking a wisp off gold: the ticket keeps its number and the next pass asks again.
			this.log.say("t" + number + "-gold", gameTurnTick,
					"t" + number + " " + ticket + ": waiting, every builder is on gold");
			return TicketState.pending(number, ticket);
		}
		buildAbility(builder).checkCanUse(this.simulation, builder, this.playerIndex, rawcode.getValue(), false,
				this.receiver.reset());
		switch (this.receiver.getOutcome()) {
			case OK:
				final float[] site = findSite(type, builder);
				if (site == null) {
					return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.NO_SITE));
				}
				this.sites.put(ticket, site);
				return TicketState.ready(number, ticket);
			case UNAFFORDABLE:
				return TicketState.waiting(number, ticket);
			case PENDING:
				return TicketState.pending(number, ticket);
			default:
				return TicketState.stuck(number, ticket, this.receiver.getStuck());
		}
	}

	private TicketState researchState(final int number, final MacroTicket ticket, final int gameTurnTick) {
		final War3ID rawcode = ticket.getRawcode();
		if (backingOff(ticket, gameTurnTick)) {
			return TicketState.pending(number, ticket);
		}
		final CUnit producer = findResearchProducer(rawcode);
		if (producer == null) {
			return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.NO_PRODUCER));
		}
		producer.getFirstAbilityOfType(CAbilityQueue.class).checkCanUse(this.simulation, producer, this.playerIndex,
				rawcode.getValue(), false, this.receiver.reset());
		switch (this.receiver.getOutcome()) {
			case OK:
				return TicketState.ready(number, ticket);
			case UNAFFORDABLE:
				return TicketState.waiting(number, ticket);
			case PENDING:
				return TicketState.pending(number, ticket);
			default:
				return TicketState.stuck(number, ticket, this.receiver.getStuck());
		}
	}

	private TicketState upgradeState(final int number, final MacroTicket ticket, final int gameTurnTick) {
		final War3ID rawcode = ticket.getRawcode();
		if (backingOff(ticket, gameTurnTick)) {
			return TicketState.pending(number, ticket);
		}
		final CUnit hall = findHall(rawcode);
		if (hall == null) {
			return TicketState.stuck(number, ticket, Stuck.of(Stuck.Reason.NO_HALL));
		}
		if (hall.isUpgrading()) {
			return TicketState.pending(number, ticket);
		}
		hall.getFirstAbilityOfType(CAbilityUpgrade.class).checkCanUse(this.simulation, hall, this.playerIndex,
				rawcode.getValue(), false, this.receiver.reset());
		switch (this.receiver.getOutcome()) {
			case OK:
				return TicketState.ready(number, ticket);
			case UNAFFORDABLE:
				return TicketState.waiting(number, ticket);
			case PENDING:
				return TicketState.pending(number, ticket);
			default:
				return TicketState.stuck(number, ticket, this.receiver.getStuck());
		}
	}

	private boolean backingOff(final MacroTicket ticket, final int gameTurnTick) {
		final Issue issue = this.issues.get(ticket);
		return (issue != null) && ((gameTurnTick - issue.issuedTick) < RETRY_TICKS);
	}

	/** A ticket refused too many times is treated as unplaceable, so that it stops holding its number. */
	private boolean givenUpOn(final MacroTicket ticket) {
		final Issue issue = this.issues.get(ticket);
		return (issue != null) && (issue.retries >= MAX_REFUSALS);
	}

	private void execute(final TicketState state, final int gameTurnTick) {
		final MacroTicket ticket = state.getTicket();
		final War3ID rawcode = ticket.getRawcode();
		switch (ticket.getKind()) {
			case BUILD:
				final float[] site = this.sites.get(ticket);
				final CUnit builder = findBuilder(rawcode, false);
				if ((site != null) && (builder != null) && this.jobs.isEmpty()) {
					issueBuild(builder, ticket, rawcode, site, gameTurnTick);
					MacroLog.line("do " + state.getNumber() + " BUILD " + rawcode.asStringValue() + " with h="
							+ builder.getHandleId() + " at " + (int) site[0] + "," + (int) site[1]);
				}
				break;
			case RESEARCH:
				final CUnit producer = findResearchProducer(rawcode);
				if (producer == null) {
					return;
				}
				this.listener.issueImmediateOrder(producer.getHandleId(),
						producer.getFirstAbilityOfType(CAbilityQueue.class).getHandleId(), rawcode.getValue(), false);
				MacroLog.line("do " + state.getNumber() + " RESEARCH " + rawcode.asStringValue() + " at h="
						+ producer.getHandleId());
				// The level it is aiming at is the one after what is unlocked now, so a mission that starts the line at
				// level one does not make "any level" look finished.
				this.issues.put(ticket,
						new Issue(gameTurnTick, this.simulation.getPlayer(this.playerIndex).getTechtreeUnlocked(rawcode)
								+ 1, 0, 0));
				break;
			case UPGRADE:
				final CUnit hall = findHall(rawcode);
				if (hall == null) {
					return;
				}
				this.listener.issueImmediateOrder(hall.getHandleId(),
						hall.getFirstAbilityOfType(CAbilityUpgrade.class).getHandleId(), rawcode.getValue(), false);
				MacroLog.line("do " + state.getNumber() + " UPGRADE " + rawcode.asStringValue() + " from h="
						+ hall.getHandleId());
				this.issues.put(ticket, new Issue(gameTurnTick, 0, hall.getHandleId(), 0));
				break;
			default:
				break;
		}
	}

	private void issueBuild(final CUnit builder, final MacroTicket ticket, final War3ID rawcode, final float[] site,
			final int gameTurnTick) {
		this.listener.issuePointOrder(builder.getHandleId(), buildAbility(builder).getHandleId(), rawcode.getValue(),
				site[0], site[1], false);
		this.jobs.add(new BuildJob(builder, ticket, rawcode, site[0], site[1], gameTurnTick));
	}

	// ------------------------------------------------------------------ finding things

	private List<CUnit> ours() {
		final List<CUnit> units = new ArrayList<>();
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			if (unit.getPlayerIndex() == this.playerIndex) {
				units.add(unit);
			}
		}
		return units;
	}

	private boolean hasWorker() {
		for (final CUnit unit : ours()) {
			if (!unit.isDead() && !unit.isHidden() && !unit.isBuilding() && isWorkerType(unit)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The free worker for a structure, as the AI picks one: it must be able to build this structure, it must not be
	 * out on a job, and the nearest to the base wins. One worker, and the same one every time, which is what "the
	 * builder returns to harvesting between jobs" needs.
	 * <p>
	 * A worker on the gold line is not offered unless {@code allowGoldGatherers} is set, which only the "is there a
	 * builder at all" question does: taking a wisp off gold costs a whole trip and the base's income, while a lumber
	 * worker or an idle one costs a few seconds of wood.
	 */
	private CUnit findBuilder(final War3ID rawcode, final boolean allowGoldGatherers) {
		final float[] anchor = anchor();
		CUnit best = null;
		float bestDistance = Float.MAX_VALUE;
		for (final CUnit unit : ours()) {
			if (unit.isDead() || unit.isHidden() || unit.isBuilding()) {
				continue;
			}
			final AbstractCAbilityBuild build = buildAbility(unit);
			if ((build == null) || !build.getStructuresBuilt().contains(rawcode)) {
				continue;
			}
			if (hasJob(unit) || isBuilding(unit) || (!allowGoldGatherers && isGoldGatherer(unit))) {
				continue;
			}
			final float distance = (anchor == null) ? 0f : distance(unit.getX(), unit.getY(), anchor[0], anchor[1]);
			if ((best == null) || (distance < bestDistance)
					|| ((distance == bestDistance) && (unit.getHandleId() < best.getHandleId()))) {
				best = unit;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * A worker on gold: inside a mine, walking into one, or holding the harvest order for one. The walk out and back is
	 * worth more than any building the plan can raise, so these are only ever counted, never pulled off.
	 */
	private boolean isGoldGatherer(final CUnit unit) {
		if (unit.isLoaded()) {
			return true;
		}
		final CBehaviorBoardTransport board = unit.getBoardTransportBehavior();
		if ((board != null) && (unit.getCurrentBehavior() == board) && isGoldMine(board.getTarget())) {
			return true;
		}
		final COrder order = unit.getCurrentOrder();
		if ((order != null) && (order.getOrderId() == OrderIds.harvest)) {
			return isGoldMine(order.getTarget(this.simulation));
		}
		return false;
	}

	private static boolean isGoldMine(final AbilityTarget target) {
		if (target == null) {
			return false;
		}
		final CUnit unit = target.visit(AbilityTargetVisitor.UNIT);
		return (unit != null) && (unit.getOverlayedGoldMineData() != null);
	}

	private boolean hasJob(final CUnit unit) {
		for (final BuildJob job : this.jobs) {
			if (job.worker == unit) {
				return true;
			}
		}
		return false;
	}

	private CUnit findResearchProducer(final War3ID rawcode) {
		for (final CUnit unit : ours()) {
			if (unit.isDead()) {
				continue;
			}
			final CAbilityQueue queue = unit.getFirstAbilityOfType(CAbilityQueue.class);
			if ((queue != null) && queue.getResearchesAvailable().contains(rawcode)) {
				return unit;
			}
		}
		return null;
	}

	private CUnit findHall(final War3ID rawcode) {
		for (final CUnit unit : ours()) {
			if (unit.isDead()) {
				continue;
			}
			final CAbilityUpgrade upgrade = unit.getFirstAbilityOfType(CAbilityUpgrade.class);
			if ((upgrade != null) && upgrade.getUpgradesTo().contains(rawcode)) {
				return unit;
			}
		}
		return null;
	}

	/** Everything the player's living workers could put down. */
	private List<PlanTypeFacts> buildableFacts() {
		final List<PlanTypeFacts> facts = new ArrayList<>();
		final Set<War3ID> seen = new LinkedHashSet<>();
		for (final CUnit unit : ours()) {
			if (unit.isDead() || unit.isHidden()) {
				continue;
			}
			final AbstractCAbilityBuild build = buildAbility(unit);
			if (build == null) {
				continue;
			}
			for (final War3ID id : build.getStructuresBuilt()) {
				if (!seen.add(id)) {
					continue;
				}
				final PlanTypeFacts built = this.simulation.getUnitData() == null ? null
						: PlanTypeFacts.of(this.simulation.getUnitData().getUnitType(id));
				if (built != null) {
					facts.add(built);
				}
			}
		}
		return facts;
	}

	/** The whole techtree side of the defaults, so a structure's train list can be looked at. */
	private Map<War3ID, PlanTypeFacts> typeFacts() {
		final Map<War3ID, PlanTypeFacts> facts = new LinkedHashMap<>();
		if (this.simulation.getUnitData() == null) {
			return facts;
		}
		for (final PlanTypeFacts buildable : buildableFacts()) {
			for (final War3ID trained : buildable.trains()) {
				if (!facts.containsKey(trained)) {
					final PlanTypeFacts trainedFacts = PlanTypeFacts
							.of(this.simulation.getUnitData().getUnitType(trained));
					if (trainedFacts != null) {
						facts.put(trained, trainedFacts);
					}
				}
			}
		}
		return facts;
	}

	private static AbstractCAbilityBuild buildAbility(final CUnit unit) {
		return unit.getFirstAbilityOfType(AbstractCAbilityBuild.class);
	}

	private static boolean isWorkerType(final CUnit unit) {
		return unit.getClassifications().contains(CUnitClassification.PEON);
	}

	private static boolean isIdle(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior == null) || (behavior == unit.getStopBehavior()) || (behavior instanceof CBehaviorHoldPosition);
	}

	private static boolean isBuilding(final CUnit unit) {
		final CBehavior behavior = unit.getCurrentBehavior();
		return (behavior != null) && (behavior.getBehaviorCategory() == CBehaviorCategory.BUILD);
	}

	// ------------------------------------------------------------------ the base and the site

	/** The base is the player's own hall: a script that moves the base takes the plan with it. */
	private float[] anchor() {
		CUnit hall = null;
		for (final CUnit unit : ours()) {
			if (unit.isDead() || unit.isHidden() || !unit.isBuilding()) {
				continue;
			}
			if (!unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				continue;
			}
			if ((hall == null) || (unit.getHandleId() < hall.getHandleId())) {
				hall = unit;
			}
		}
		if (hall != null) {
			return new float[] { hall.getX(), hall.getY() };
		}
		final CPlayer player = this.simulation.getPlayer(this.playerIndex);
		return player == null ? null : player.getStartLocation();
	}

	private CUnit nearestMine(final float[] anchor) {
		if (anchor == null) {
			return null;
		}
		CUnit best = null;
		float bestDistance = MINE_SEARCH_DISTANCE;
		for (final CUnit unit : this.simulation.getUnitsIncludingNew()) {
			if (unit.isDead() || (unit.getGoldMineData() == null) || (unit.getOverlayedGoldMineData() != null)) {
				continue;
			}
			final float distance = distance(anchor[0], anchor[1], unit.getX(), unit.getY());
			if ((best == null) || (distance < bestDistance)
					|| ((distance == bestDistance) && (unit.getHandleId() < best.getHandleId()))) {
				best = unit;
				bestDistance = distance;
			}
		}
		return best;
	}

	/**
	 * The AI's site search, anchored on the base rather than on a town: rings out to
	 * {@link #SITE_RADIUS_CEILING}, starting on the far side of the gold mine so the buildings do not block the walk
	 * from the hall to it.
	 */
	private float[] findSite(final CUnitType type, final CUnit builder) {
		final float[] anchor = anchor();
		if (anchor == null) {
			return null;
		}
		final ArgbImage pathing = type.getBuildingPathingPixelMap();
		final int cellsWide = (pathing == null) ? 4 : pathing.getWidth();
		final int cellsHigh = (pathing == null) ? 4 : pathing.getHeight();
		final float halfWidth = (pathing == null) ? 64f : (pathing.getWidth() * 16f);
		final float halfHeight = (pathing == null) ? 64f : (pathing.getHeight() * 16f);
		final float minRadius = Math.max(SITE_RADIUS_FLOOR,
				160f + Math.max(halfWidth, halfHeight) + 128f);
		final CUnit mine = nearestMine(anchor);
		final float preferredAngle;
		if (mine != null) {
			preferredAngle = (float) StrictMath.atan2(anchor[1] - mine.getY(), anchor[0] - mine.getX());
		}
		else {
			final PathingGrid grid = this.simulation.getPathingGrid();
			final float centerX = (grid.getWorldX(0) + grid.getWorldX(grid.getWidth() - 1)) / 2;
			final float centerY = (grid.getWorldY(0) + grid.getWorldY(grid.getHeight() - 1)) / 2;
			preferredAngle = (float) StrictMath.atan2(centerY - anchor[1], centerX - anchor[0]);
		}
		final CUnitType typeToBuild = type;
		final CUnit worker = builder;
		return MacroPlacement.findSite(anchor[0], anchor[1], minRadius, SITE_RADIUS_CEILING, preferredAngle, cellsWide,
				cellsHigh, BUILDING_GAP, !type.isCanBeBuiltOnThem(), (x, y) -> AbstractCAbilityBuild
						.isBuildLocationObstructed(this.simulation, typeToBuild, typeToBuild.getBuildingPathingPixelMap(),
								typeToBuild.isCanBeBuiltOnThem(), x, y, worker,
								BuildOnBuildingIntersector.INSTANCE.reset(x, y)));
	}

	private static float distance(final float x1, final float y1, final float x2, final float y2) {
		final float dx = x1 - x2;
		final float dy = y1 - y2;
		return (float) StrictMath.sqrt((dx * dx) + (dy * dy));
	}

	// ------------------------------------------------------------------ the bookkeeping

	/** A structure on its way up, and the worker walking to it. */
	private static final class BuildJob {
		private final CUnit worker;
		private final MacroTicket ticket;
		private final War3ID typeId;
		private final float x;
		private final float y;
		private final int issuedTick;

		private BuildJob(final CUnit worker, final MacroTicket ticket, final War3ID typeId, final float x,
				final float y, final int issuedTick) {
			this.worker = worker;
			this.ticket = ticket;
			this.typeId = typeId;
			this.x = x;
			this.y = y;
			this.issuedTick = issuedTick;
		}
	}

	/** A research or an upgrade on its way, and how to tell that it arrived. */
	private static final class Issue {
		private final int issuedTick;
		private final int targetLevel;
		private final int hallHandleId;
		/** How many times an order for this ticket did not take. */
		private final int retries;

		private Issue(final int issuedTick, final int targetLevel, final int hallHandleId, final int retries) {
			this.issuedTick = issuedTick;
			this.targetLevel = targetLevel;
			this.hallHandleId = hallHandleId;
			this.retries = retries;
		}
	}
}
