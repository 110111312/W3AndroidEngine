package net.warsmash.lan;

import static net.warsmash.lan.LanSessionTest.sender;
import static net.warsmash.lan.LanSessionTest.settle;
import static net.warsmash.lan.LanTestSupport.assertConsistent;
import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.LanTestSupport.Recorder;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanData.PresenceEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanFaultInjector;
import net.warsmash.lan.transport.LanLoop;

/** The host is gone: the next participant takes the game (or the lobby) over and everybody goes on. */
@Timeout(360)
class LanMigrationTest {
	private final LanTestSupport env = new LanTestSupport();

	/** Tells what a participant heard of host changes. */
	private static final class Hosts extends Recorder {
		final List<Integer> changed = new CopyOnWriteArrayList<Integer>();
		final List<Integer> firstTurns = new CopyOnWriteArrayList<Integer>();
		volatile int migrating;
		volatile LanHost promoted;

		@Override
		public void onMigrating() {
			this.migrating++;
		}

		@Override
		public void onHostChanged(final int newHost, final int firstTurn) {
			this.changed.add(newHost);
			this.firstTurns.add(firstTurn);
		}

		@Override
		public void onPromoted(final LanHost host) {
			this.promoted = host;
		}
	}

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private LanClient.Config candidate(final String name) {
		final LanClient.Config c = LanTestSupport.clientConfig(name);
		c.hostTemplate = LanTestSupport.hostConfig();
		c.reconnectMs = 20000;
		return c;
	}

	private Node join(final String name, final Hosts rec) throws Exception {
		final Node n = this.env.join(candidate(name), rec);
		waitFor("welcome of " + name, () -> n.rec.welcome != null || !n.rec.rejects.isEmpty());
		return n;
	}

	/** The host, its own player (which has no template, being on the host's machine), and the given players. */
	private Node startHostAndPlayer() throws Exception {
		this.env.timings.hostGoneMs = 300;
		this.env.timings.migrateWaitMs = 1500;
		this.env.timings.reconnectRetryMs = 30;
		this.env.startHost();
		return this.env.joinAndWait("Host");
	}

	/** The machine of the host dies: its network thread and its player. */
	private void killHostMachine(final Node hostPlayer) {
		hostPlayer.loop.shutdown();
		this.env.hostLoop.shutdown();
	}

	private static int hostSteps(final List<TurnData> turns, final int from) {
		int steps = 0;
		for (int i = from; i < turns.size(); i++) {
			steps += turns.get(i).steps;
		}
		return steps;
	}

	@Test
	void theNextPlayerTakesAGameOverAndEveryCommandCountsOnce() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Hosts rc = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		final Node c = join("C", rc);
		this.env.startGame(hostPlayer, a, b, c);
		final Thread sa = sender(a, 0, 150, 1);
		final Thread sb = sender(b, 1, 150, 2);
		final Thread sc = sender(c, 2, 150, 3);
		waitFor("some turns", () -> a.client.lastTurn() > 20 && b.client.lastTurn() > 20);
		final int hostId = hostPlayer.client.participantId();
		killHostMachine(hostPlayer);
		sa.join();
		sb.join();
		sc.join();
		settle(150, a, b, c);

		assertNotNull(ra.promoted, "the first player after the host takes the game over");
		assertNull(rb.promoted);
		assertNull(rc.promoted);
		final int aId = a.client.participantId();
		assertEquals(aId, b.client.hostId());
		assertEquals(aId, c.client.hostId());
		assertTrue(rb.changed.contains(aId) && rc.changed.contains(aId) && ra.changed.contains(aId));
		assertTrue(rb.migrating >= 1 && rc.migrating >= 1);
		assertConsistent(150, a, b, c);
		// the old host is gone for everybody, in the same turn
		int goneTurn = -1;
		for (final TurnData t : a.rec.turns) {
			for (final PresenceEntry e : t.presence) {
				if (e.participant == hostId && e.state == LanProtocol.PRESENCE_ABSENT) {
					goneTurn = t.n;
				}
			}
		}
		assertTrue(goneTurn > 0, "the new host's first turn says the old one is absent");
		for (final TurnData t : b.rec.turns) {
			if (t.n == goneTurn) {
				assertEquals(1, t.presence.size());
			}
		}
		assertTrue(hostSteps(a.rec.turns, goneTurn) > 0, "the game goes on");
	}

	@Test
	void turnsOnlyTheOthersHoldAreHandedToTheNewHost() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		// the next host's own link is slow: when the host dies it lacks turns B has
		final LanFaultInjector slow = new LanFaultInjector(3);
		a.loop.setFaultInjector(slow);
		this.env.startGame(hostPlayer, a, b);
		final Thread sa = sender(a, 0, 100, 1);
		final Thread sb = sender(b, 1, 100, 2);
		waitFor("turns", () -> b.client.lastTurn() > 60);
		// its network stands still for a moment: the host sends on to the others, up to its sync limit
		slow.freeze(800);
		Thread.sleep(250);
		final int bHad = b.client.lastTurn();
		killHostMachine(hostPlayer);
		sa.join();
		sb.join();
		settle(100, a, b);
		assertNotNull(ra.promoted);
		assertConsistent(100, a, b);
		assertTrue(ra.promoted.archive().size() > bHad, "its archive has what B held");
		assertTrue(ra.promoted.filledTurns() > 0, "some of them were handed over by B");
	}

	@Test
	void aLobbyGoesOnWithTheNextOwnerAndStartsAGame() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		b.client.slotRequest(3, 2, 1, 7, 60);
		waitFor("B moved", () -> rb.lobby != null && rb.lobby.slots.get(3).participantId == b.client.participantId());
		a.client.setReady(true);
		waitFor("A ready", () -> rb.lobby.slots.get(1).ready);
		killHostMachine(hostPlayer);
		waitFor("the new host", () -> ra.promoted != null);
		waitFor("B at the new host", () -> !rb.changed.isEmpty() && !ra.changed.isEmpty());
		waitFor("a new owner", () -> rb.lobby != null && rb.lobby.ownerId == a.client.participantId()
				&& rb.lobby.slots.get(0).kind == LanProtocol.SLOT_OPEN);
		assertNotNull(ra.promoted);
		// seats and choices stay as they were
		assertEquals(b.client.participantId(), rb.lobby.slots.get(3).participantId);
		assertEquals(2, rb.lobby.slots.get(3).race);
		assertEquals(60, rb.lobby.slots.get(3).handicap);
		assertTrue(rb.lobby.slots.get(1).ready, "the ready mark of A stays");
		// the lobby works: B readies and A starts
		b.client.setReady(true);
		waitFor("B ready", () -> ra.lobby != null && ra.lobby.slots.get(3).ready);
		a.client.requestStart();
		waitFor("go", () -> ra.go && rb.go);
		final Thread sa = sender(a, 0, 50, 1);
		final Thread sb = sender(b, 1, 50, 2);
		sa.join();
		sb.join();
		settle(50, a, b);
		assertConsistent(50, a, b);
	}

	@Test
	void aHostThatLeavesHandsTheGameOver() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		this.env.startGame(hostPlayer, a, b);
		waitFor("turns", () -> a.client.lastTurn() > 10);
		final int hostId = hostPlayer.client.participantId();
		hostPlayer.client.leave(LanProtocol.LEAVE_QUIT);
		waitFor("left", () -> this.env.host.presenceOf(hostId) == LanProtocol.PRESENCE_LEFT);
		final boolean[] closed = new boolean[1];
		this.env.host.retire(() -> closed[0] = true);
		waitFor("the new host", () -> ra.promoted != null && rb.changed.size() >= 1);
		final Thread sa = sender(a, 0, 40, 1);
		final Thread sb = sender(b, 1, 40, 2);
		sa.join();
		sb.join();
		settle(40, a, b);
		assertConsistent(40, a, b);
		boolean left = false;
		for (final TurnData t : a.rec.turns) {
			for (final PresenceEntry e : t.presence) {
				if (e.participant == hostId) {
					assertEquals(LanProtocol.PRESENCE_LEFT, e.state, "a leaving host is not just absent");
					left = true;
				}
			}
		}
		assertTrue(left);
		waitFor("retired", () -> closed[0]);
	}

	@Test
	void theStopOfAPausedGameSurvivesTheHostChange() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		this.env.startGame(hostPlayer, a, b);
		waitFor("turns", () -> a.client.lastTurn() > 10);
		hostPlayer.client.simPaused(true);
		a.client.simPaused(true);
		b.client.simPaused(true);
		waitFor("turns without steps", () -> {
			final List<TurnData> t = b.rec.turns;
			return t.size() > 5 && t.get(t.size() - 1).steps == 0;
		});
		killHostMachine(hostPlayer);
		waitFor("the new host", () -> ra.promoted != null && !rb.changed.isEmpty());
		final int from = a.client.lastTurn() + 1;
		waitFor("turns of the new host", () -> a.client.lastTurn() > from + 30);
		// the new host was told by the others that the game stands still, so its turns hold no steps
		int steps = 0;
		for (int i = from + 2; i < a.rec.turns.size(); i++) {
			steps += a.rec.turns.get(i).steps;
		}
		assertEquals(0, steps);
		a.client.simPaused(false);
		b.client.simPaused(false);
		waitFor("steps again", () -> a.rec.turns.get(a.rec.turns.size() - 1).steps > 0);
	}

	@Test
	void aPlayerWhoWasGoneComesBackToTheNewHostWithItsOldKey() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Hosts rc = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		final Node c = join("C", rc);
		this.env.startGame(hostPlayer, a, b, c);
		waitFor("turns", () -> a.client.lastTurn() > 20);
		final RejoinKey key = c.client.rejoinKey();
		final int cId = c.client.participantId();
		c.loop.shutdown();
		waitFor("C gone", () -> this.env.host.presenceOf(cId) >= 0);
		killHostMachine(hostPlayer);
		waitFor("the new host", () -> ra.promoted != null && !rb.changed.isEmpty());
		final LanHost host = ra.promoted;
		final LanClient.Config config = LanTestSupport.clientConfig("C");
		config.rejoinKey = key;
		final Hosts again = new Hosts();
		final LanClient.Config cfg = config;
		final Node c2 = this.env.joinAt(cfg, again, new InetSocketAddress("127.0.0.1", host.port()));
		waitFor("rejoined", () -> again.caughtUp > 0);
		assertEquals(1, again.rejoins);
		waitFor("playing", () -> host.presenceOf(cId) == LanProtocol.PRESENCE_PLAYING);
		assertEquals(host.archive().size() > 0, true);
		assertTrue(c2.client.lastTurn() >= 0);
	}

	@Test
	void aCandidateThatStillSeesTheHostSendsTheAskerBack() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Node a = join("A", ra);
		this.env.startGame(hostPlayer, a);
		waitFor("turns", () -> a.client.lastTurn() > 5);
		final int standby = a.client.standbyPort();
		assertTrue(standby > 0);
		final LanLoop loop = this.env.newLoop("asker");
		final List<LanMessage> got = new CopyOnWriteArrayList<LanMessage>();
		loop.connect(new InetSocketAddress("127.0.0.1", standby), 2000, new LanLoop.ConnectCallback() {
			@Override
			public void onConnected(final LanConnection c) {
				c.setListener(new LanConnection.Listener() {
					@Override
					public void onMessage(final LanConnection connection, final LanMessage m) {
						got.add(m);
					}

					@Override
					public void onClosed(final LanConnection connection, final String reason) {
					}
				});
				c.send(new LanMessages.MigrateHello(3, new byte[16], 5, new java.util.ArrayList<>(), 0));
			}

			@Override
			public void onFailed(final java.io.IOException error) {
			}
		});
		waitFor("an answer", () -> !got.isEmpty());
		assertTrue(got.get(0) instanceof LanMessages.Redirect);
		assertEquals(this.env.port, ((LanMessages.Redirect) got.get(0)).port);
	}

	@Test
	void whenTheNextOneIsGoneToo_theOneAfterItTakesOver() throws Exception {
		final Node hostPlayer = startHostAndPlayer();
		final Hosts ra = new Hosts();
		final Hosts rb = new Hosts();
		final Hosts rc = new Hosts();
		final Node a = join("A", ra);
		final Node b = join("B", rb);
		final Node c = join("C", rc);
		this.env.startGame(hostPlayer, a, b, c);
		final Thread sb = sender(b, 0, 80, 2);
		final Thread sc = sender(c, 1, 80, 3);
		waitFor("turns", () -> b.client.lastTurn() > 20);
		a.loop.shutdown();
		killHostMachine(hostPlayer);
		sb.join();
		sc.join();
		settle(80, b, c);
		assertNull(ra.promoted);
		assertNotNull(rb.promoted, "the second one in the order takes over");
		assertNull(rc.promoted);
		assertEquals(b.client.participantId(), c.client.hostId());
		LanTestSupport.assertConsistent(80, b, c);
	}
}
