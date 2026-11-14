package net.warsmash.lan;

import static net.warsmash.lan.LanSessionTest.sender;
import static net.warsmash.lan.LanSessionTest.settle;
import static net.warsmash.lan.LanTestSupport.assertConsistent;
import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.LanTestSupport.Recorder;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanProtocol;

/** Games of many players, with observers and referees. */
@Timeout(540)
class LanManyTest {
	private static final byte PAUSE = 0x7F;

	private final LanTestSupport env = new LanTestSupport();

	/** Tells what a participant heard of host changes. */
	private static final class Hosts extends Recorder {
		volatile LanHost promoted;

		@Override
		public void onPromoted(final LanHost host) {
			this.promoted = host;
		}
	}

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 5000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private LanHost.Config hostConfig(final int slots) {
		final LanHost.Config hc = LanTestSupport.hostConfig();
		hc.maxSlots = slots;
		hc.commandValidator = new LanHost.CommandValidator() {
			@Override
			public boolean accept(final int slot, final byte[] command) {
				// a referee gives the pause and nothing else
				return slot < LanProtocol.WATCHER_SLOT_BASE || (command.length == 1 && command[0] == PAUSE);
			}
		};
		return hc;
	}

	private Node join(final String name, final int role, final Recorder rec, final boolean candidate)
			throws Exception {
		final LanClient.Config c = LanTestSupport.clientConfig(name);
		c.wantedRole = role;
		if (candidate) {
			c.hostTemplate = hostConfig(4);
			c.reconnectMs = 20000;
		}
		final Node n = this.env.join(c, rec);
		waitFor("welcome of " + name, () -> n.rec.welcome != null || !n.rec.rejects.isEmpty());
		return n;
	}

	private Node join(final String name, final int role) throws Exception {
		return join(name, role, new Recorder(), false);
	}

	private List<Node> joinPlayers(final int count) throws Exception {
		final List<Node> nodes = new ArrayList<Node>();
		for (int i = 0; i < count; i++) {
			nodes.add(this.env.joinAndWait("P" + i));
		}
		return nodes;
	}

	private static void report(final int tick, final int crc, final List<Node> nodes) {
		for (final Node n : nodes) {
			n.client.checksum(tick, crc);
		}
	}

	private static boolean told(final Recorder rec, final int kind) {
		for (final int[] r : rec.resyncs) {
			if (r[0] == kind) {
				return true;
			}
		}
		return false;
	}

	private static List<Node> nodes(final List<Node> a, final List<Node> b) {
		final List<Node> all = new ArrayList<Node>(a);
		all.addAll(b);
		return all;
	}

	// ------------------------------------------------------------------ many players

	@Test
	void twelvePlayersPlayTheSameGame() throws Exception {
		this.env.startHost(hostConfig(12));
		final List<Node> players = joinPlayers(12);
		this.env.startGame(players.toArray(new Node[0]));
		final List<Thread> senders = new ArrayList<Thread>();
		for (int i = 0; i < players.size(); i++) {
			senders.add(sender(players.get(i), i, 60, i + 1));
		}
		for (final Thread t : senders) {
			t.join();
		}
		settle(60, players.toArray(new Node[0]));
		assertConsistent(60, players.toArray(new Node[0]));
	}

	@Test
	void theOpenSeatsStayForPlayersWhenWatchersFillTheRest() throws Exception {
		this.env.startHost(hostConfig(12));
		this.env.joinAndWait("Owner");
		// one player and 11 open seats leave room for 12 watchers of the 24 participants
		for (int i = 0; i < 12; i++) {
			final Node w = join("W" + i, i % 2 == 0 ? LanProtocol.ROLE_OBSERVER : LanProtocol.ROLE_REFEREE);
			assertNotNull(w.rec.welcome, "watcher " + i);
		}
		final Recorder late = new Recorder();
		join("TooMany", LanProtocol.ROLE_OBSERVER, late, false);
		assertEquals(1, late.rejects.size());
		assertEquals(LanProtocol.REJECT_FULL, late.rejects.get(0).reason);
		// a player still finds a seat
		final Node player = this.env.joinAndWait("Player");
		assertNotNull(player.rec.welcome);
	}

	@Test
	void aWatcherNeedsASeatNobodyPlays() throws Exception {
		this.env.startHost(hostConfig(LanProtocol.MAX_SLOTS));
		final Node owner = this.env.joinAndWait("Owner");
		for (int i = 1; i < LanProtocol.MAX_SLOTS - 1; i++) {
			owner.client.hostSlot(i, LanProtocol.ACTION_COMPUTER, 1);
		}
		owner.client.hostSlot(LanProtocol.MAX_SLOTS - 1, LanProtocol.ACTION_CLOSE, 0);
		waitFor("the seats set", () -> owner.rec.lobby != null
				&& owner.rec.lobby.slots.get(LanProtocol.MAX_SLOTS - 1).kind == LanProtocol.SLOT_CLOSED
				&& owner.rec.lobby.slots.get(LanProtocol.MAX_SLOTS - 2).kind == LanProtocol.SLOT_COMPUTER);
		final Node watcher = join("Watcher", LanProtocol.ROLE_OBSERVER);
		assertNotNull(watcher.rec.welcome, "a closed seat is a seat nobody plays");
		owner.client.hostSlot(LanProtocol.MAX_SLOTS - 1, LanProtocol.ACTION_COMPUTER, 1);
		waitFor("every seat taken", () -> {
			for (final LanData.SlotInfo s : owner.rec.lobby.slots) {
				if (s.kind == LanProtocol.SLOT_OPEN || s.kind == LanProtocol.SLOT_CLOSED) {
					return false;
				}
			}
			return true;
		});
		final Recorder lateRec = new Recorder();
		join("Late", LanProtocol.ROLE_OBSERVER, lateRec, false);
		assertEquals(1, lateRec.rejects.size(), "no seat is left to watch from");
		owner.client.setReady(true);
		waitFor("ready", () -> {
			for (final LanData.SlotInfo s : owner.rec.lobby.slots) {
				if (s.kind == LanProtocol.SLOT_HUMAN && s.ready) {
					return true;
				}
			}
			return false;
		});
		owner.client.requestStart();
		Thread.sleep(400);
		assertEquals(LanProtocol.STATE_LOBBY, this.env.host.state(), "the game with a watcher and no free seat waits");
	}

	// ---------------------------------------------------- watchers: input, kick, chat

	@Test
	void anObserverGivesNoCommandsAndARefereeOnlyThePause() throws Exception {
		this.env.startHost(hostConfig(4));
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node referee = join("Ref", LanProtocol.ROLE_REFEREE);
		final Node observer = join("Obs", LanProtocol.ROLE_OBSERVER);
		this.env.startGame(a, b);
		waitFor("go", () -> referee.rec.go && observer.rec.go);
		final int refSlot = referee.client.setup().commandSlot(referee.client.participantId());
		assertTrue(refSlot >= LanProtocol.WATCHER_SLOT_BASE);
		assertEquals(LanProtocol.NO_SLOT, referee.client.setup().commandSlot(observer.client.participantId()));
		// a referee's order to units is refused and counted against it, its pause goes through once
		referee.client.sendCommand(new byte[] { 1, 2, 3 });
		referee.client.sendCommand(new byte[] { PAUSE });
		a.client.sendCommand(LanTestSupport.command(0, 1));
		waitFor("the pause in a turn", () -> pauseTurns(a.rec.turns, refSlot) == 1);
		observer.client.sendCommand(LanTestSupport.command(2, 1));
		Thread.sleep(300);
		for (final TurnData t : a.rec.turns) {
			for (final CmdEntry e : t.cmds) {
				assertTrue(e.slot == refSlot ? (e.bytes.length == 1 && e.bytes[0] == PAUSE)
						: e.slot == LanTestSupport.slotOf(a),
						"only the player's command and the referee's pause are in the turns: slot " + e.slot);
			}
		}
		assertEquals(1, pauseTurns(a.rec.turns, refSlot));
		waitFor("the observer's link refused", () -> !observer.rec.disconnects.isEmpty()
				|| this.env.host.presenceOf(observer.client.participantId()) != LanProtocol.PRESENCE_PLAYING);
	}

	private static int pauseTurns(final List<TurnData> turns, final int slot) {
		int n = 0;
		for (final TurnData t : turns) {
			for (final CmdEntry e : t.cmds) {
				if (e.slot == slot && e.bytes.length == 1 && e.bytes[0] == PAUSE) {
					n++;
				}
			}
		}
		return n;
	}

	@Test
	void theHostRemovesAWatcher() throws Exception {
		this.env.startHost(hostConfig(4));
		final Node owner = this.env.joinAndWait("Owner");
		final Node guest = this.env.joinAndWait("Guest");
		final Node watcher = join("Watcher", LanProtocol.ROLE_OBSERVER);
		// a guest has no say
		guest.client.hostSlot(watcher.client.participantId(), LanProtocol.ACTION_KICK_WATCHER, 0);
		Thread.sleep(200);
		assertTrue(watcher.rec.kicks.isEmpty());
		owner.client.hostSlot(watcher.client.participantId(), LanProtocol.ACTION_KICK_WATCHER, 0);
		waitFor("kicked", () -> !watcher.rec.kicks.isEmpty());
		waitFor("gone from the lobby", () -> owner.rec.lobby != null && owner.rec.lobby.observers.isEmpty());
		// a player is not removed this way, and neither is the host
		owner.client.hostSlot(guest.client.participantId(), LanProtocol.ACTION_KICK_WATCHER, 0);
		owner.client.hostSlot(owner.client.participantId(), LanProtocol.ACTION_KICK_WATCHER, 0);
		Thread.sleep(200);
		assertTrue(guest.rec.kicks.isEmpty());
		assertEquals(2, this.env.host.participantCount());
	}

	@Test
	void anObserverIsHeardByObserversAndARefereeByEveryone() throws Exception {
		this.env.startHost(hostConfig(4));
		final Node owner = this.env.joinAndWait("Owner");
		final Node guest = this.env.joinAndWait("Guest");
		final Node observer = join("Obs", LanProtocol.ROLE_OBSERVER);
		final Node otherObserver = join("Obs2", LanProtocol.ROLE_OBSERVER);
		final Node referee = join("Ref", LanProtocol.ROLE_REFEREE);
		observer.client.chat(LanProtocol.CHAT_ALL, "from an observer");
		referee.client.chat(LanProtocol.CHAT_ALL, "from a referee");
		waitFor("the referee is heard", () -> chatTexts(guest.rec).contains("from a referee")
				&& chatTexts(owner.rec).contains("from a referee") && chatTexts(otherObserver.rec).contains("from a referee"));
		waitFor("the observer is heard by the watchers", () -> chatTexts(otherObserver.rec).contains("from an observer")
				&& chatTexts(referee.rec).contains("from an observer"));
		assertFalse(chatTexts(guest.rec).contains("from an observer"));
		assertFalse(chatTexts(owner.rec).contains("from an observer"));
		for (final net.warsmash.lan.protocol.LanMessages.Chat c : otherObserver.rec.chats) {
			if (c.text.equals("from an observer")) {
				assertEquals(LanProtocol.CHAT_OBSERVERS, c.channel);
			}
		}
	}

	private static List<String> chatTexts(final Recorder rec) {
		final List<String> texts = new CopyOnWriteArrayList<String>();
		for (final net.warsmash.lan.protocol.LanMessages.Chat c : rec.chats) {
			texts.add(c.text);
		}
		return texts;
	}

	// ------------------------------------------------------------------ the lobby's map

	@Test
	void aSmallerMapKeepsEveryTeamWithinTheSeats() throws Exception {
		final LanHost.Config hc = hostConfig(8);
		this.env.startHost(hc);
		final Node owner = this.env.joinAndWait("Owner");
		owner.client.hostSlot(3, LanProtocol.ACTION_COMPUTER, 2);
		waitFor("computer", () -> owner.rec.lobby != null && owner.rec.lobby.slots.get(3).kind == LanProtocol.SLOT_COMPUTER);
		owner.client.hostSetup(3, 1, 7, 3, 100);
		waitFor("team 7", () -> owner.rec.lobby.slots.get(3).team == 7);
		assertTrue(this.env.host.changeMap(hc.map, null, 4, false));
		waitFor("four seats", () -> owner.rec.lobby.slots.size() == 4);
		for (final LanData.SlotInfo s : owner.rec.lobby.slots) {
			assertTrue(s.team <= 3, "team " + s.team + " of slot " + s.index);
		}
		assertEquals(3, owner.rec.lobby.slots.get(3).team);
		// and a choice beyond the seats is kept inside them
		owner.client.slotRequest(0, 0, 9, 0, 100);
		waitFor("team clamp", () -> owner.rec.lobby.slots.get(0).team == 3);
	}

	// ------------------------------------------------------------------ desync

	@Test
	void severalMinoritiesAreBroughtBackAtTheSameTime() throws Exception {
		this.env.startHost(hostConfig(8));
		final List<Node> players = joinPlayers(8);
		this.env.startGame(players.toArray(new Node[0]));
		report(20, 60, players);
		final List<Node> majority = players.subList(0, 4);
		final List<Node> minorityOne = players.subList(4, 6);
		final List<Node> minorityTwo = players.subList(6, 8);
		report(40, 120, majority);
		report(40, 777, minorityOne);
		report(40, 888, minorityTwo);
		for (final Node n : nodes(minorityOne, minorityTwo)) {
			waitFor("told " + n.client.participantId(), () -> told(n.rec, LanProtocol.RESYNC_DESYNC));
		}
		for (final Node n : majority) {
			assertTrue(n.rec.resyncs.isEmpty(), "the biggest group goes on");
		}
		for (final Node n : nodes(minorityOne, minorityTwo)) {
			n.client.resumeTurns();
		}
		for (final Node n : nodes(minorityOne, minorityTwo)) {
			waitFor("caught up " + n.client.participantId(), 30000, () -> n.rec.caughtUp > 0);
			waitFor("counted in again", () -> this.env.host.presenceOf(n.client.participantId())
					== LanProtocol.PRESENCE_PLAYING);
		}
		for (final Node n : players) {
			assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(n.client.participantId()));
		}
		for (final Node n : majority) {
			assertTrue(n.rec.disconnects.isEmpty());
		}
	}

	@Test
	void sixAgainstSixIsDecidedForTheHostsGroup() throws Exception {
		this.env.startHost(hostConfig(12));
		final List<Node> players = joinPlayers(12);
		this.env.startGame(players.toArray(new Node[0]));
		report(20, 60, players);
		report(40, 120, players.subList(0, 6));
		report(40, 999, players.subList(6, 12));
		for (final Node n : players.subList(6, 12)) {
			waitFor("told " + n.client.participantId(), () -> told(n.rec, LanProtocol.RESYNC_DESYNC));
		}
		for (final Node n : players.subList(0, 6)) {
			assertTrue(n.rec.resyncs.isEmpty(), "the host's group of the tie goes on");
		}
	}

	@Test
	void aTieIsNotBrokenByWatchers() throws Exception {
		this.env.startHost(hostConfig(4));
		final List<Node> players = joinPlayers(4);
		final List<Node> watchers = new ArrayList<Node>();
		for (int i = 0; i < 3; i++) {
			watchers.add(join("W" + i, i == 0 ? LanProtocol.ROLE_REFEREE : LanProtocol.ROLE_OBSERVER));
		}
		this.env.startGame(players.toArray(new Node[0]));
		for (final Node w : watchers) {
			waitFor("go", () -> w.rec.go);
		}
		report(20, 60, nodes(players, watchers));
		report(40, 120, players.subList(0, 2));
		// the other two players and all three watchers are of another opinion: three more of them, the same two players
		report(40, 555, players.subList(2, 4));
		report(40, 555, watchers);
		for (final Node n : players.subList(2, 4)) {
			waitFor("told " + n.client.participantId(), () -> told(n.rec, LanProtocol.RESYNC_DESYNC));
		}
		for (final Node n : watchers) {
			waitFor("watcher told " + n.client.participantId(), () -> told(n.rec, LanProtocol.RESYNC_DESYNC));
		}
		for (final Node n : players.subList(0, 2)) {
			assertTrue(n.rec.resyncs.isEmpty());
		}
	}

	// ------------------------------------------------------------------ migration order

	@Test
	void aPlayerTakesTheGameOverBeforeAReferee_andARefereeBeforeAnObserver() throws Exception {
		this.env.timings.hostGoneMs = 300;
		this.env.timings.migrateWaitMs = 1500;
		this.env.timings.reconnectRetryMs = 30;
		this.env.startHost(hostConfig(4));
		final Node hostPlayer = this.env.joinAndWait("Host");
		final Hosts ro = new Hosts();
		final Hosts rr = new Hosts();
		final Hosts rp = new Hosts();
		// the observer is in before the referee and before the player
		final Node observer = join("Obs", LanProtocol.ROLE_OBSERVER, ro, true);
		final Node referee = join("Ref", LanProtocol.ROLE_REFEREE, rr, true);
		final Node player = join("Player", LanProtocol.ROLE_PLAYER, rp, true);
		this.env.startGame(hostPlayer, player);
		waitFor("go", () -> observer.rec.go && referee.rec.go);
		waitFor("turns", () -> player.client.lastTurn() > 20 && observer.client.lastTurn() > 20);
		hostPlayer.loop.shutdown();
		this.env.hostLoop.shutdown();
		waitFor("a new host", 20000, () -> rp.promoted != null || rr.promoted != null || ro.promoted != null);
		assertNotNull(rp.promoted, "the player first");
		assertNull(rr.promoted);
		assertNull(ro.promoted);
		final int at = player.client.lastTurn();
		waitFor("the game goes on", () -> observer.client.lastTurn() > at + 10 && referee.client.lastTurn() > at + 10);
		// the player goes too: the referee is before the observer
		player.loop.shutdown();
		waitFor("the next host", 20000, () -> rr.promoted != null || ro.promoted != null);
		assertNotNull(rr.promoted, "the referee before the observer");
		assertNull(ro.promoted);
		assertEquals(referee.client.participantId(), observer.client.hostId());
	}

	@Test
	void aRefereeCommandSlotIsKeptByTheNewHost() throws Exception {
		this.env.timings.hostGoneMs = 300;
		this.env.timings.migrateWaitMs = 1500;
		this.env.timings.reconnectRetryMs = 30;
		this.env.startHost(hostConfig(4));
		final Node hostPlayer = this.env.joinAndWait("Host");
		final Hosts rp = new Hosts();
		final Hosts rr = new Hosts();
		final Node player = join("Player", LanProtocol.ROLE_PLAYER, rp, true);
		final Node referee = join("Ref", LanProtocol.ROLE_REFEREE, rr, true);
		this.env.startGame(hostPlayer, player);
		waitFor("go", () -> referee.rec.go);
		final int refSlot = referee.client.setup().commandSlot(referee.client.participantId());
		referee.client.sendCommand(new byte[] { PAUSE });
		waitFor("the pause", () -> pauseTurns(player.rec.turns, refSlot) == 1);
		hostPlayer.loop.shutdown();
		this.env.hostLoop.shutdown();
		waitFor("a new host", 20000, () -> rp.promoted != null);
		final int at = player.client.lastTurn();
		waitFor("turns", () -> referee.client.lastTurn() > at + 10);
		// the new host numbers the referee's commands on from the archive: the second is accepted, not repeated
		referee.client.sendCommand(new byte[] { PAUSE });
		waitFor("a second pause", 10000, () -> pauseTurns(player.rec.turns, refSlot) == 2);
		Thread.sleep(300);
		assertEquals(2, pauseTurns(player.rec.turns, refSlot));
		assertEquals(2, pauseTurns(referee.rec.turns, refSlot));
	}

	@Test
	void commandSlotsOfASetup() {
		final LanGameSetup g = new LanGameSetup();
		g.participants.add(info(1, LanProtocol.ROLE_PLAYER, 3));
		g.participants.add(info(2, LanProtocol.ROLE_OBSERVER, LanProtocol.NO_SLOT));
		g.participants.add(info(3, LanProtocol.ROLE_REFEREE, LanProtocol.NO_SLOT));
		assertEquals(3, g.commandSlot(1));
		assertEquals(LanProtocol.NO_SLOT, g.commandSlot(2));
		assertEquals(LanProtocol.WATCHER_SLOT_BASE + 2, g.commandSlot(3));
		assertEquals(LanProtocol.NO_SLOT, g.commandSlot(9));
		assertEquals(3, g.commandSender(3).slot);
		assertEquals(3, g.commandSender(LanProtocol.WATCHER_SLOT_BASE + 2).id);
		assertNull(g.commandSender(LanProtocol.WATCHER_SLOT_BASE + 5));
		assertNull(g.commandSender(5));
	}

	@Test
	void theHostOrderPutsPlayersThenRefereesThenObservers() {
		final int player = LanProtocol.hostKey(LanProtocol.ROLE_PLAYER, 11, 7);
		final int referee = LanProtocol.hostKey(LanProtocol.ROLE_REFEREE, LanProtocol.NO_SLOT, 1);
		final int observer = LanProtocol.hostKey(LanProtocol.ROLE_OBSERVER, LanProtocol.NO_SLOT, 0);
		assertTrue(player < referee);
		assertTrue(referee < observer);
	}

	private static LanData.ParticipantInfo info(final int id, final int role, final int slot) {
		final LanData.ParticipantInfo p = new LanData.ParticipantInfo();
		p.id = id;
		p.role = role;
		p.slot = slot;
		return p;
	}
}
