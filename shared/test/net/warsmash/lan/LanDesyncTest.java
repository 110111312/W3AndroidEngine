package net.warsmash.lan;

import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.LanTestSupport.Recorder;
import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanFaultInjector;

/** Players whose sums differ: the minority plays the game again from the host's archive, the others go on. */
@Timeout(360)
class LanDesyncTest {
	private final LanTestSupport env = new LanTestSupport();

	private static final class Hosts extends Recorder {
		volatile LanHost promoted;

		@Override
		public void onPromoted(final LanHost host) {
			this.promoted = host;
		}

		LanHost promotedHost() {
			return this.promoted;
		}
	}

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private static void report(final int tick, final Node... nodes) {
		for (final Node n : nodes) {
			n.client.checksum(tick, tick * 3);
		}
	}

	private static boolean hasResync(final Recorder rec, final int kind) {
		for (final int[] r : rec.resyncs) {
			if (r[0] == kind) {
				return true;
			}
		}
		return false;
	}

	@Test
	void theMinorityOfThreePlaysTheGameAgainAndTheOthersAreNotTouched() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		report(20, a, b, c);
		a.client.checksum(40, 120);
		b.client.checksum(40, 120);
		c.client.checksum(40, 999);
		waitFor("the minority is told", () -> hasResync(c.rec, LanProtocol.RESYNC_DESYNC));
		assertEquals(40, c.rec.resyncs.get(0)[1]);
		assertTrue(a.rec.resyncs.isEmpty(), "the majority goes on");
		assertTrue(b.rec.resyncs.isEmpty(), "the majority goes on");
		// everybody is told of the difference, it is the minority that starts over
		waitFor("everyone told", () -> !a.rec.desyncs.isEmpty() && !b.rec.desyncs.isEmpty()
				&& !c.rec.desyncs.isEmpty());
		// the game the minority drops is replaced: the turns come from the first one and it is counted in again
		waitFor("turns again", () -> c.rec.rejoins > 0);
		c.client.resumeTurns();
		waitFor("caught up", () -> c.rec.caughtUp > 0);
		waitFor("playing", () -> this.env.host.presenceOf(c.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(a.client.participantId()));
		// its new run agrees with the game: no second difference
		c.client.checksum(20, 60);
		c.client.checksum(40, 120);
		report(60, a, b, c);
		Thread.sleep(300);
		assertEquals(1, a.rec.desyncs.size());
		assertEquals(1, c.rec.resyncs.size());
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(c.client.participantId()));
		assertEquals(0, c.rec.turns.get(0).n, "the turns it holds are the game's, from the first");
	}

	@Test
	void aTieGoesToTheHostsGroup() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		a.client.checksum(20, 1);
		b.client.checksum(20, 2);
		waitFor("the guest is told", () -> hasResync(b.rec, LanProtocol.RESYNC_DESYNC));
		assertTrue(a.rec.resyncs.isEmpty(), "the first player is the owner of the game");
	}

	@Test
	void theLateSumsOfADroppedGameAreNotHeldAgainstTheNewOne() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		report(20, a, b, c);
		a.client.checksum(40, 120);
		b.client.checksum(40, 120);
		c.client.checksum(40, 999);
		waitFor("told", () -> hasResync(c.rec, LanProtocol.RESYNC_DESYNC));
		// the dropped game sent more before it noticed: the client holds them back
		c.client.checksum(60, 12345);
		c.client.checksum(80, 12345);
		c.client.isConnected();
		c.client.resumeTurns();
		waitFor("caught up", () -> c.rec.caughtUp > 0);
		report(60, a, b);
		report(80, a, b);
		c.client.checksum(60, 180);
		c.client.checksum(80, 240);
		Thread.sleep(300);
		assertTrue(c.rec.disconnects.isEmpty());
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(c.client.participantId()));
		assertEquals(1, c.rec.resyncs.size());
	}

	@Test
	void theSameDifferenceInTheNewRunGivesTheParticipantUp() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final LanClient.Config cc = LanTestSupport.clientConfig("C");
		cc.autoCaughtUp = false;
		final Node c = this.env.join(cc);
		waitFor("welcome of C", () -> c.rec.welcome != null);
		this.env.startGame(a, b, c);
		report(20, a, b, c);
		a.client.checksum(40, 120);
		b.client.checksum(40, 120);
		c.client.checksum(40, 999);
		waitFor("told", () -> hasResync(c.rec, LanProtocol.RESYNC_DESYNC));
		c.client.resumeTurns();
		// the game played again from the first turn comes to the same wrong sum
		c.client.checksum(40, 999);
		waitFor("given up", () -> hasResync(c.rec, LanProtocol.RESYNC_GIVE_UP));
		waitFor("absent", () -> this.env.host.presenceOf(c.client.participantId()) == LanProtocol.PRESENCE_ABSENT);
		waitFor("link closed", () -> !c.rec.disconnects.isEmpty());
		// the others play on
		final int at = a.client.lastTurn();
		waitFor("turns go on", () -> a.client.lastTurn() > at + 10 && b.client.lastTurn() > at + 10);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(a.client.participantId()));
	}

	@Test
	void theStepItWasOutOfStepAtAgainAfterItIsCountedInGivesItUp() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		report(20, a, b, c);
		a.client.checksum(40, 120);
		b.client.checksum(40, 120);
		c.client.checksum(40, 999);
		waitFor("told", () -> hasResync(c.rec, LanProtocol.RESYNC_DESYNC));
		c.client.resumeTurns();
		waitFor("caught up", () -> c.rec.caughtUp > 0);
		waitFor("playing", () -> this.env.host.presenceOf(c.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		c.client.checksum(40, 999);
		waitFor("given up", () -> hasResync(c.rec, LanProtocol.RESYNC_GIVE_UP));
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(a.client.participantId()));
	}

	@Test
	void aParticipantThatHoldsTurnsNobodyHasStartsOverAtTheNewHost() throws Exception {
		this.env.timings.hostGoneMs = 300;
		this.env.timings.migrateWaitMs = 1500;
		this.env.timings.reconnectRetryMs = 30;
		final LanHost.Config hc = LanTestSupport.hostConfig();
		this.env.startHost(hc);
		final Node hostPlayer = this.env.joinAndWait("Host");
		final Hosts ra = new Hosts();
		final Recorder rc = new Recorder();
		final LanClient.Config ca = candidate("A");
		final LanClient.Config cc = candidate("C");
		final Node a = this.env.join(ca, ra);
		waitFor("welcome A", () -> ra.welcome != null);
		final Node c = this.env.join(cc, rc);
		waitFor("welcome C", () -> rc.welcome != null);
		final LanFaultInjector slowA = new LanFaultInjector(3);
		a.loop.setFaultInjector(slowA);
		this.env.startGame(hostPlayer, a, c);
		waitFor("turns", () -> c.client.lastTurn() > 40);
		slowA.freeze(800);
		Thread.sleep(250);
		final LanFaultInjector stillC = new LanFaultInjector(4);
		c.loop.setFaultInjector(stillC);
		stillC.freeze(4000);
		final int cHeld = c.client.lastTurn();
		hostPlayer.loop.shutdown();
		this.env.hostLoop.shutdown();
		waitFor("the new host", () -> ra.promotedHost() != null);
		final LanHost host = ra.promotedHost();
		assertTrue(cHeld > 0);
		waitFor("the late one is told", 30000, () -> hasResync(rc, LanProtocol.RESYNC_TURNS));
		c.client.resumeTurns();
		waitFor("caught up", 30000, () -> rc.caughtUp > 0);
		assertNotNull(host);
		waitFor("playing", 10000,
				() -> host.presenceOf(c.client.participantId()) == LanProtocol.PRESENCE_PLAYING);
		assertFalse(rc.resyncs.isEmpty());
	}

	private LanClient.Config candidate(final String name) {
		final LanClient.Config c = LanTestSupport.clientConfig(name);
		c.hostTemplate = LanTestSupport.hostConfig();
		c.reconnectMs = 20000;
		return c;
	}
}
