package net.warsmash.lan;

import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.warsmash.lan.LanTestSupport.Node;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.LanFaultInjector;

/** Pause, the votes of a stalled game, and the claim of a victory, against a real host on loopback. */
class LanPresenceTest {
	private final LanTestSupport env = new LanTestSupport();

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private static int lastSteps(final Node n) {
		return n.rec.turns.isEmpty() ? -1 : n.rec.turns.get(n.rec.turns.size() - 1).steps;
	}

	private static void ownTeam(final Node n, final int team) {
		LanData.SlotInfo mine = null;
		for (final LanData.SlotInfo s : n.rec.lobby.slots) {
			if (s.participantId == n.client.participantId()) {
				mine = s;
			}
		}
		n.client.slotRequest(mine.index, mine.race, team, mine.color, 100);
		waitFor("team of " + n.client.participantId(), () -> {
			for (final LanData.SlotInfo s : n.rec.lobby.slots) {
				if (s.participantId == n.client.participantId()) {
					return s.team == team;
				}
			}
			return false;
		});
	}

	@Test
	void aGameThatStandsStillGetsTurnsWithoutSteps() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		waitFor("steps", () -> a.rec.turns.size() > 5 && lastSteps(a) > 0);
		a.client.simPaused(true);
		waitFor("turns without steps", () -> lastSteps(a) == 0 && lastSteps(b) == 0);
		final int at = a.rec.turns.size();
		waitFor("they keep coming", () -> a.rec.turns.size() > at + 2);
		a.client.sendCommand(LanTestSupport.command(0, 1));
		waitFor("the command comes in a turn without steps", () -> {
			for (final LanData.TurnData t : a.rec.turns) {
				if (t.steps == 0 && !t.cmds.isEmpty()) {
					return true;
				}
			}
			return false;
		});
		a.client.simPaused(false);
		waitFor("steps again", () -> lastSteps(a) > 0 && lastSteps(b) > 0);
	}

	@Test
	void theOthersContinueWithoutAStalledOwnerByMajority() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		final Node c = this.env.joinAndWait("C");
		this.env.startGame(a, b, c);
		final LanFaultInjector f = new LanFaultInjector(3);
		a.loop.setFaultInjector(f);
		f.freeze(60000);
		waitFor("lag", () -> b.rec.lagEvents > 0 && c.rec.lagEvents > 0);
		Thread.sleep(400);
		b.client.vote(LanProtocol.VOTE_CONTINUE_WITHOUT);
		Thread.sleep(500);
		assertEquals(0, b.rec.lagEnds, "one of two players who are not waited for is not a majority");
		c.client.vote(LanProtocol.VOTE_CONTINUE_WITHOUT);
		waitFor("lag ended", () -> b.rec.lagEnds > 0);
		assertEquals(LanProtocol.PRESENCE_ABSENT, this.env.host.presenceOf(a.client.participantId()));
	}

	@Test
	void aVoteBeforeTheWaitIsLongEnoughIsIgnored() throws Exception {
		this.env.timings.continueAfterMs = 5000;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(4);
		b.loop.setFaultInjector(f);
		f.freeze(60000);
		waitFor("lag", () -> a.rec.lagEvents > 0);
		a.client.vote(LanProtocol.VOTE_CONTINUE_WITHOUT);
		Thread.sleep(600);
		assertEquals(0, a.rec.lagEnds);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(b.client.participantId()));
	}

	@Test
	void claimingVictoryNeedsAllOpponentsGoneForLongEnough() throws Exception {
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.setReady(true);
		ownTeam(a, 1);
		ownTeam(b, 2);
		this.env.startGame(a, b);
		final LanFaultInjector f = new LanFaultInjector(5);
		b.loop.setFaultInjector(f);
		f.freeze(60000);
		waitFor("lag", () -> a.rec.lagEvents > 0);
		a.client.vote(LanProtocol.VOTE_CLAIM_VICTORY);
		Thread.sleep(250);
		assertEquals(LanProtocol.PRESENCE_PLAYING, this.env.host.presenceOf(b.client.participantId()),
				"claimed before the claim time");
		Thread.sleep(700);
		a.client.vote(LanProtocol.VOTE_CLAIM_VICTORY);
		waitFor("opponent left", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_LEFT);
		waitFor("the game goes on without the opponent", () -> a.rec.lagEnds > 0);
		boolean leftInTurn = false;
		for (final LanData.TurnData t : a.rec.turns) {
			for (final LanData.PresenceEntry p : t.presence) {
				leftInTurn |= p.state == LanProtocol.PRESENCE_LEFT && p.participant == b.client.participantId();
			}
		}
		assertTrue(leftInTurn, "the departure travels inside the turn stream");
	}

	@Test
	void teammatesCannotClaimVictoryOverEachOther() throws Exception {
		this.env.timings.absentAfterMs = 500;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.setReady(true);
		ownTeam(a, 1);
		ownTeam(b, 1);
		this.env.startGame(a, b);
		b.client.close();
		waitFor("absent", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_ABSENT);
		a.client.vote(LanProtocol.VOTE_CLAIM_VICTORY);
		Thread.sleep(500);
		assertFalse(this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_LEFT);
	}

	@Test
	void anAbsentOpponentCanBeClaimedAtOnce() throws Exception {
		this.env.timings.absentAfterMs = 700;
		this.env.startHost();
		final Node a = this.env.joinAndWait("A");
		final Node b = this.env.joinAndWait("B");
		a.client.setReady(true);
		ownTeam(a, 1);
		ownTeam(b, 2);
		this.env.startGame(a, b);
		waitFor("traffic", () -> a.client.lastTurn() > 5);
		b.client.close();
		waitFor("absent", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_ABSENT);
		a.client.vote(LanProtocol.VOTE_CLAIM_VICTORY);
		waitFor("left", () -> this.env.host.presenceOf(b.client.participantId()) == LanProtocol.PRESENCE_LEFT);
	}
}
