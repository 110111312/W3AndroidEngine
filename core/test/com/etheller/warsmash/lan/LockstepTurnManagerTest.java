package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerCommandCodec;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerUnitOrderListener;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.PresenceEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanProtocol;

/** The lockstep rule of the game's steps, against a fake host and a fake game. */
class LockstepTurnManagerTest {
	private static final int INTERVAL = 4;

	private static final class FakeTurns implements LockstepTurnManager.Turns {
		final List<TurnData> queue = new ArrayList<TurnData>();
		final List<Integer> processed = new ArrayList<Integer>();
		final Map<Integer, Integer> checksums = new HashMap<Integer, Integer>();
		int polls;
		int leaves;
		final List<Boolean> pauseReports = new ArrayList<Boolean>();

		@Override
		public void simPaused(final boolean paused) {
			this.pauseReports.add(paused);
		}

		@Override
		public TurnData poll() {
			this.polls++;
			return this.queue.isEmpty() ? null : this.queue.remove(0);
		}

		@Override
		public void processed(final int turn) {
			this.processed.add(turn);
		}

		@Override
		public void checksum(final int tick, final int crc) {
			this.checksums.put(tick, crc);
		}

		@Override
		public void leave() {
			this.leaves++;
		}

		boolean catching;
		int catchTotal;
		int received = -1;
		int caughtUpCalls;
		int tickAtCaughtUp = -1;
		java.util.function.IntSupplier tickSource = () -> -1;

		@Override
		public boolean catchingUp() {
			return this.catching;
		}

		@Override
		public int catchUpTotal() {
			return this.catchTotal;
		}

		@Override
		public int received() {
			return this.received;
		}

		@Override
		public void caughtUp() {
			this.caughtUpCalls++;
			this.tickAtCaughtUp = this.tickSource.getAsInt();
			this.catching = false;
		}
	}

	private static final class FakeGame implements LockstepTurnManager.Game {
		int tick;
		int checksumCalls;
		boolean ended;
		boolean paused;
		final Map<Integer, CPlayerCommandCodec.Encoder> players = new HashMap<Integer, CPlayerCommandCodec.Encoder>();
		final Map<Integer, Integer> slotOfParticipant = new HashMap<Integer, Integer>();
		final List<String> events = new ArrayList<String>();

		@Override
		public int tick() {
			return this.tick;
		}

		@Override
		public int checksum() {
			this.checksumCalls++;
			return 1000 + this.tick;
		}

		@Override
		public boolean ended() {
			return this.ended;
		}

		@Override
		public boolean simPaused() {
			return this.paused;
		}

		@Override
		public CPlayerUnitOrderListener player(final int slot) {
			if (slot > 3) {
				return null;
			}
			return this.players.computeIfAbsent(slot, s -> new CPlayerCommandCodec.Encoder());
		}

		@Override
		public int slotOf(final int participant) {
			return this.slotOfParticipant.getOrDefault(participant, -1);
		}
	}

	private final FakeTurns turns = new FakeTurns();
	private final FakeGame game = new FakeGame();
	private final LockstepTurnManager manager = new LockstepTurnManager(this.turns, this.game, INTERVAL);

	private static TurnData turn(final int n, final int steps, final CmdEntry... commands) {
		return new TurnData(n, steps, Arrays.asList(commands), new ArrayList<PresenceEntry>());
	}

	/** What the viewer does each frame: steps while the manager allows. Returns the steps made. */
	private int frame() {
		int made = 0;
		while (this.manager.getLatestCompletedTurn() >= this.game.tick) {
			this.game.tick++;
			this.manager.turnCompleted(this.game.tick);
			made++;
		}
		return made;
	}

	private static byte[] move(final float x, final float y) {
		final CPlayerCommandCodec.Encoder encoder = new CPlayerCommandCodec.Encoder();
		encoder.issuePointOrder(7, 8, 851971, x, y, false);
		return encoder.take();
	}

	@Test
	void aGameTheHostSaysIsNotTheOthersIsDroppedWithoutLeavingAndTakesNoMoreTurns() {
		final LanGameSetup setup = new LanGameSetup();
		final LanPlay play = new LanPlay(setup, 1, new net.warsmash.lan.protocol.LanTimings(), v -> {
		}, () -> 0L);
		this.manager.setPlay(play);
		final int[] restarts = { 0 };
		this.manager.setRestarter(() -> restarts[0]++);
		this.turns.queue.add(turn(0, 2));
		assertEquals(2, frame());
		play.onResync(LanProtocol.RESYNC_DESYNC, 40);
		this.turns.queue.add(turn(1, 2));
		assertEquals(0, frame(), "no step after the request");
		assertEquals(1, restarts[0]);
		assertEquals(1, this.turns.queue.size(), "the turn belongs to the new game");
		frame();
		assertEquals(1, restarts[0], "asked for once");
		this.game.ended = true;
		this.manager.turnCompleted(this.game.tick);
		assertEquals(0, this.turns.leaves, "the player did not leave the LAN game");
	}

	@Test
	void noStepWithoutATurn() {
		assertEquals(0, frame());
		assertEquals(0, this.game.tick);
		assertTrue(this.manager.getLatestCompletedTurn() < this.game.tick);
	}

	@Test
	void aTurnAllowsExactlyItsSteps() {
		this.turns.queue.add(turn(0, 2));
		assertEquals(2, frame());
		assertEquals(2, this.game.tick);
		assertEquals(0, frame());
		this.turns.queue.add(turn(1, 2));
		this.turns.queue.add(turn(2, 2));
		assertEquals(4, frame());
		assertEquals(6, this.game.tick);
		assertEquals(3, this.manager.turnsApplied());
	}

	@Test
	void commandsAreAppliedOnceBeforeTheFirstStepOfTheirTurn() {
		final byte[] a = move(1, 2);
		final byte[] b = move(3, 4);
		this.turns.queue.add(turn(0, 2, new CmdEntry(1, 1, a), new CmdEntry(0, 1, b), new CmdEntry(1, 2, b)));
		this.manager.getLatestCompletedTurn();
		assertEquals(0, this.game.tick);
		assertArrayEquals(concat(a, b), this.game.players.get(1).take());
		assertArrayEquals(b, this.game.players.get(0).take());
		this.manager.getLatestCompletedTurn();
		this.manager.getLatestCompletedTurn();
		assertFalse(this.game.players.get(1).take().length > 0, "a turn is applied once");
		assertEquals(1, this.manager.turnsApplied());
	}

	private static byte[] concat(final byte[] a, final byte[] b) {
		final byte[] both = Arrays.copyOf(a, a.length + b.length);
		System.arraycopy(b, 0, both, a.length, b.length);
		return both;
	}

	@Test
	void aSlotWithoutAPlayerIsSkipped() {
		this.turns.queue.add(turn(0, 2, new CmdEntry(9, 1, move(1, 1))));
		assertEquals(2, frame());
		assertTrue(this.game.players.isEmpty());
	}

	@Test
	void aBrokenCommandDoesNotStopTheGame() {
		this.turns.queue.add(turn(0, 2, new CmdEntry(0, 1, new byte[] { 99 }), new CmdEntry(1, 2, move(5, 5))));
		assertEquals(2, frame());
		assertTrue(this.game.players.get(1).take().length > 0);
	}

	@Test
	void theHostHearsOfATurnAfterItsLastStep() {
		this.turns.queue.add(turn(0, 2));
		this.turns.queue.add(turn(1, 2));
		frame();
		assertEquals(Arrays.asList(0, 1), this.turns.processed);
	}

	@Test
	void checksumsAreSentAtTheInterval() {
		for (int n = 0; n < 4; n++) {
			this.turns.queue.add(turn(n, 2));
		}
		frame();
		assertEquals(2, this.turns.checksums.size());
		assertEquals(1004, this.turns.checksums.get(4));
		assertEquals(1008, this.turns.checksums.get(8));
		assertEquals(2, this.game.checksumCalls);
	}

	@Test
	void theRecordingAndTheHostShareOneChecksum() {
		this.turns.queue.add(turn(0, 2));
		this.turns.queue.add(turn(1, 2));
		this.manager.checksumFunction();
		frame();
		// the function a recording uses answers from the value the host was sent
		final int before = this.game.checksumCalls;
		assertEquals(1004, this.manager.checksumFunction().applyAsInt(null));
		assertEquals(before, this.game.checksumCalls);
	}

	@Test
	void aPlayerWhoLeftEndsUpLeavingTheGame() {
		this.game.slotOfParticipant.put(5, 2);
		final List<PresenceEntry> presence = new ArrayList<PresenceEntry>();
		presence.add(new PresenceEntry(5, LanProtocol.PRESENCE_LEFT));
		presence.add(new PresenceEntry(6, LanProtocol.PRESENCE_ABSENT));
		this.turns.queue.add(new TurnData(0, 2, new ArrayList<CmdEntry>(), presence));
		frame();
		final CPlayerCommandCodec.Encoder encoder = this.game.players.get(2);
		assertArrayEquals(new byte[] { CPlayerCommandCodec.LEAVE_GAME }, encoder.take());
		assertEquals(1, this.game.players.size());
	}

	@Test
	void anEndedGameLeavesOnce() {
		this.turns.queue.add(turn(0, 2));
		this.turns.queue.add(turn(1, 2));
		this.game.ended = true;
		frame();
		assertEquals(1, this.turns.leaves);
	}

	@Test
	void aTurnWithNoStepsIsConsumedAndTheNextIsWaitedFor() {
		this.turns.queue.add(turn(0, 0));
		this.turns.queue.add(turn(1, 2));
		assertEquals(2, frame());
		assertEquals(2, this.manager.turnsApplied());
	}

	@Test
	void theTickListenerSeesEveryStep() {
		final List<Integer> ticks = new ArrayList<Integer>();
		this.manager.setTickListener(ticks::add);
		this.turns.queue.add(turn(0, 2));
		frame();
		assertEquals(Arrays.asList(1, 2), ticks);
	}

	@Test
	void aPausedGameTakesTurnsWithoutStepsAndTellsTheHost() {
		this.turns.queue.add(turn(0, 2));
		assertEquals(2, frame());
		this.game.paused = true;
		this.turns.queue.add(turn(1, 0, new CmdEntry(0, 1, move(1, 2))));
		this.manager.whilePaused();
		assertEquals(Arrays.asList(true), this.turns.pauseReports);
		assertTrue(this.game.players.get(0).take().length > 0, "the command of a turn without steps is applied");
		assertEquals(Arrays.asList(0, 1), this.turns.processed);
		assertEquals(2, this.game.tick);
		this.game.paused = false;
		this.manager.whilePaused();
		assertEquals(Arrays.asList(true, false), this.turns.pauseReports);
		this.turns.queue.add(turn(2, 2));
		assertEquals(2, frame());
	}

	@Test
	void aTurnWithStepsSentBeforeTheStopRunsAfterIt() {
		this.turns.queue.add(turn(0, 2));
		frame();
		this.game.paused = true;
		this.turns.queue.add(turn(1, 2, new CmdEntry(0, 1, move(1, 2))));
		this.turns.queue.add(turn(2, 0));
		this.manager.whilePaused();
		assertEquals(2, this.game.tick);
		assertTrue(this.game.players.get(0).take().length > 0, "its commands are applied during the stop");
		this.game.paused = false;
		assertEquals(2, frame());
		assertEquals(4, this.game.tick);
		assertEquals(0, frame());
	}

	@Test
	void aTurnThatComesLateDuringTheStopIsAppliedOnce() {
		this.turns.queue.add(turn(0, 2));
		frame();
		this.game.paused = true;
		this.manager.whilePaused();
		this.turns.queue.add(turn(1, 0, new CmdEntry(0, 1, move(3, 4))));
		this.manager.whilePaused();
		this.manager.whilePaused();
		assertTrue(this.game.players.get(0).take().length > 0);
		assertFalse(this.game.players.get(0).take().length > 0, "applied once");
	}

	@Test
	void aLostHostLeavesTheGameAndTheGamePlaysOnForAMoment() {
		final net.warsmash.lan.LanGameSetup setup = new net.warsmash.lan.LanGameSetup();
		setup.hostOrder = new int[] { 7 };
		final LanPlay play = new LanPlay(setup, 8, new net.warsmash.lan.protocol.LanTimings(), vote -> {
		}, () -> 0L);
		this.manager.setPlay(play);
		this.game.slotOfParticipant.put(7, 1);
		this.turns.queue.add(turn(0, 2));
		assertEquals(2, frame());
		play.onDisconnected("closed by host");
		final int steps = frame();
		assertTrue(steps > 0 && steps < 100, "the game plays a few steps alone: " + steps);
		assertTrue(this.game.players.get(1).take().length > 0, "the host's player leaves the game");
		assertEquals(0, frame());
	}

	@Test
	void aRejoinerReportsCaughtUpOnlyAfterTheArchiveAndWithinTwoTurnsOfTheLiveOnes() {
		this.turns.catching = true;
		this.turns.catchTotal = 5;
		for (int i = 0; i < 9; i++) {
			this.turns.queue.add(turn(i, 2));
		}
		this.turns.received = 8;
		this.turns.tickSource = () -> this.game.tick;
		frame();
		assertEquals(1, this.turns.caughtUpCalls);
		// turn 6 is the first with at most two behind it (8 received); its steps are made first: 7 turns of 2 steps
		assertEquals(14, this.turns.tickAtCaughtUp);
		assertTrue(this.turns.processed.contains(6));
	}

	@Test
	void anEmptyArchiveIsCaughtUpAtOnce() {
		this.turns.catching = true;
		this.turns.catchTotal = 0;
		this.turns.received = -1;
		frame();
		assertEquals(1, this.turns.caughtUpCalls);
	}

	@Test
	void pausesOfThePastAreNotReportedWhileCatchingUpButThePresentOneIs() {
		this.turns.catching = true;
		this.turns.catchTotal = 100;
		this.turns.received = 200;
		this.turns.queue.add(turn(0, 0));
		this.game.paused = true;
		this.manager.whilePaused();
		assertTrue(this.turns.pauseReports.isEmpty());
		this.turns.catching = false;
		this.manager.whilePaused();
		assertEquals(Arrays.asList(true), this.turns.pauseReports);
	}

	@Test
	void zeroStepTurnsOfAPauseInTheArchiveAreAllPlayedThroughWithoutStepping() {
		this.turns.catching = true;
		this.turns.catchTotal = 40;
		this.turns.received = 41;
		this.turns.queue.add(turn(0, 2));
		this.game.paused = true;
		for (int i = 1; i < 30; i++) {
			this.turns.queue.add(turn(i, 0));
		}
		this.manager.whilePaused();
		assertEquals(30, this.manager.turnsApplied());
		assertTrue(this.turns.queue.isEmpty());
	}

	@Test
	void aLongPauseInTheArchiveIsPlayedThroughInFewFramesWhileCatchingUp() {
		this.turns.catching = true;
		this.turns.catchTotal = 6001;
		this.turns.received = 6002;
		this.turns.queue.add(turn(0, 2));
		this.game.paused = true;
		for (int i = 1; i < 6000; i++) {
			this.turns.queue.add(turn(i, 0));
		}
		this.manager.whilePaused();
		assertEquals(4096, this.manager.turnsApplied());
		this.manager.whilePaused();
		assertEquals(6000, this.manager.turnsApplied());
		assertTrue(this.turns.queue.isEmpty());
	}

	@Test
	void aPauseOfTheLiveGameTakesNoMoreThanAFewTurnsPerFrame() {
		this.game.paused = true;
		for (int i = 0; i < 200; i++) {
			this.turns.queue.add(turn(i, 0));
		}
		this.manager.whilePaused();
		assertEquals(64, this.manager.turnsApplied());
	}
}
