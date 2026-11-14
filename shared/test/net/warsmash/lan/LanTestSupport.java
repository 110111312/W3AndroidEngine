package net.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.TurnData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.transport.LanLoop;

/** Hosts and clients on loopback, one loop (network thread) per node, all closed by {@link #close()}. */
public final class LanTestSupport implements AutoCloseable {
	public static final String VERSION = "test";
	public static final long DATA = 1234;

	/** Collects every event a client receives. */
	public static class Recorder extends LanClient.Listener {
		public final List<TurnData> turns = new CopyOnWriteArrayList<TurnData>();
		public final List<LanMessages.Reject> rejects = new CopyOnWriteArrayList<LanMessages.Reject>();
		public final List<LanMessages.Desync> desyncs = new CopyOnWriteArrayList<LanMessages.Desync>();
		public final List<String> disconnects = new CopyOnWriteArrayList<String>();
		/** Each resync the client was told of: {kind, tick}. */
		public final List<int[]> resyncs = new CopyOnWriteArrayList<int[]>();
		public final List<LanMessages.Chat> chats = new CopyOnWriteArrayList<LanMessages.Chat>();
		public final List<Integer> countdowns = new CopyOnWriteArrayList<Integer>();
		public final List<String> kicks = new CopyOnWriteArrayList<String>();
		public volatile LanMessages.Welcome welcome;
		public volatile LanMessages.LobbyState lobby;
		public volatile LanGameSetup setup;
		public volatile boolean go;
		public volatile boolean gameEnded;
		public volatile int lagEvents;
		public volatile int lagEnds;
		public volatile int caughtUp;
		public volatile int rejoins;
		public volatile int reconnecting;
		public volatile int reconnected;

		@Override
		public void onWelcome(final LanMessages.Welcome w) {
			this.welcome = w;
		}

		@Override
		public void onLobby(final LanMessages.LobbyState l) {
			this.lobby = l;
		}

		@Override
		public void onChat(final LanMessages.Chat c) {
			this.chats.add(c);
		}

		@Override
		public void onReject(final LanMessages.Reject r) {
			this.rejects.add(r);
		}

		@Override
		public void onKicked(final String reason) {
			this.kicks.add(reason);
		}

		@Override
		public void onCountdown(final int seconds) {
			this.countdowns.add(seconds);
		}

		@Override
		public void onStart(final LanGameSetup s) {
			this.setup = s;
		}

		@Override
		public void onGo() {
			this.go = true;
		}

		@Override
		public void onTurn(final TurnData t) {
			this.turns.add(t);
		}

		@Override
		public void onLag(final List<LanData.LagEntry> entries) {
			this.lagEvents++;
		}

		@Override
		public void onLagEnd() {
			this.lagEnds++;
		}

		@Override
		public void onDesync(final LanMessages.Desync d) {
			this.desyncs.add(d);
		}

		@Override
		public void onRejoin(final LanGameSetup s, final int from, final int total) {
			this.rejoins++;
		}

		@Override
		public void onCaughtUp() {
			this.caughtUp++;
		}

		@Override
		public void onResync(final int kind, final int tick) {
			this.resyncs.add(new int[] { kind, tick });
		}

		@Override
		public void onReconnecting() {
			this.reconnecting++;
		}

		@Override
		public void onReconnected() {
			this.reconnected++;
		}

		@Override
		public void onGameEnd(final int reason) {
			this.gameEnded = true;
		}

		@Override
		public void onDisconnected(final String reason) {
			this.disconnects.add(reason);
		}
	}

	public static final class Node {
		public final LanLoop loop;
		public final LanClient client;
		public final Recorder rec;

		Node(final LanLoop loop, final LanClient client, final Recorder rec) {
			this.loop = loop;
			this.client = client;
			this.rec = rec;
		}
	}

	private static int counter;

	private final List<LanLoop> loops = new ArrayList<LanLoop>();
	public LanLoop hostLoop;
	public LanHost host;
	public int port;
	public final LanTimings timings = fastTimings();

	public static LanTimings fastTimings() {
		final LanTimings t = new LanTimings();
		t.connectTimeoutMs = 2000;
		t.helloTimeoutMs = 800;
		t.keepaliveMs = 100;
		t.silenceMs = 2000;
		t.loadTimeoutMs = 5000;
		t.lagAfterMs = 300;
		t.absentAfterMs = 5000;
		t.continueAfterMs = 300;
		t.claimAfterMs = 600;
		t.announceMs = 100;
		t.gameExpiryMs = 600;
		return t;
	}

	public static LanHost.Config hostConfig() {
		final LanHost.Config c = new LanHost.Config();
		c.engineVersion = VERSION;
		c.buildCommit = "abc";
		c.dataChecksum = DATA;
		c.options.gameName = "Test Game";
		c.options.observers = LanProtocol.OBSERVERS_REFEREES;
		c.map.name = "Maps\\Test.w3x";
		c.map.size = 1000;
		c.maxSlots = 4;
		c.port = 0;
		c.turnIntervalMs = 10;
		c.countdownMs = 0;
		return c;
	}

	public LanLoop newLoop(final String role) throws IOException {
		final LanLoop l = new LanLoop("lan-net-test-" + role + "-" + (counter++), this.timings);
		l.start();
		this.loops.add(l);
		return l;
	}

	public LanHost startHost(final LanHost.Config config) throws IOException {
		this.hostLoop = newLoop("host");
		this.host = new LanHost(this.hostLoop, config);
		this.port = this.host.open();
		return this.host;
	}

	public LanHost startHost() throws IOException {
		return startHost(hostConfig());
	}

	public static LanClient.Config clientConfig(final String name) {
		final LanClient.Config c = new LanClient.Config();
		c.engineVersion = VERSION;
		c.buildCommit = "abc";
		c.dataChecksum = DATA;
		c.playerName = name;
		c.autoLoad = true;
		return c;
	}

	public InetSocketAddress address() {
		return new InetSocketAddress("127.0.0.1", this.port);
	}

	public Node join(final String name) throws IOException {
		return join(clientConfig(name));
	}

	public Node join(final LanClient.Config config) throws IOException {
		return join(config, new Recorder());
	}

	public Node join(final LanClient.Config config, final Recorder rec) throws IOException {
		final LanLoop loop = newLoop("client-" + config.playerName);
		final LanClient client = new LanClient(loop, config, rec);
		client.connect(address());
		return new Node(loop, client, rec);
	}

	public Node joinAt(final LanClient.Config config, final Recorder rec, final InetSocketAddress address)
			throws IOException {
		final LanLoop loop = newLoop("client-" + config.playerName);
		final LanClient client = new LanClient(loop, config, rec);
		client.connect(address);
		return new Node(loop, client, rec);
	}

	public Node joinAndWait(final String name) throws IOException {
		final Node n = join(name);
		waitFor("welcome of " + name, () -> n.rec.welcome != null || !n.rec.rejects.isEmpty());
		return n;
	}

	/**
	 * Wall-clock limits are multiplied by this: the tests poll for conditions, so a passing run is not slower, but a
	 * busy machine no longer turns slow scheduling into a failure. A real hang still ends at the scaled limit.
	 */
	public static final int TIME_SCALE = Math.max(1, Integer.getInteger("lan.test.timeScale", 6));

	public static long scaled(final long ms) {
		return ms * TIME_SCALE;
	}

	public static void waitFor(final String what, final BooleanSupplier condition) {
		waitFor(what, 10000, condition);
	}

	public static void waitFor(final String what, final long ms, final BooleanSupplier condition) {
		final long end = System.nanoTime() + scaled(ms) * 1000000L;
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() > end) {
				throw new AssertionError("timed out waiting for " + what);
			}
			try {
				Thread.sleep(2);
			}
			catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new AssertionError("interrupted");
			}
		}
	}

	/** Everybody ready, the first participant starts, and all clients reach GO. */
	public void startGame(final Node... nodes) {
		for (final Node n : nodes) {
			n.client.setReady(true);
		}
		waitFor("all ready", () -> {
			final LanMessages.LobbyState l = nodes[0].rec.lobby;
			if (l == null) {
				return false;
			}
			int ready = 0;
			for (final LanData.SlotInfo s : l.slots) {
				if (s.kind == LanProtocol.SLOT_HUMAN && s.ready) {
					ready++;
				}
			}
			return ready == nodes.length;
		});
		nodes[0].client.requestStart();
		for (final Node n : nodes) {
			waitFor("go for " + n.client.participantId(), () -> n.rec.go);
		}
	}

	public static byte[] command(final int client, final int counter) {
		return new byte[] { (byte) client, (byte) (counter >>> 8), (byte) counter, (byte) (counter * 7 + client) };
	}

	/**
	 * Checks what lockstep promises: every node saw the same gap-free turn sequence, and each client's commands
	 * appear exactly once, in order, with their original bytes.
	 */
	public static void assertConsistent(final int commandsPerClient, final Node... nodes) {
		int limit = Integer.MAX_VALUE;
		for (final Node n : nodes) {
			limit = Math.min(limit, n.rec.turns.size());
		}
		for (int i = 0; i < limit; i++) {
			final TurnData ref = nodes[0].rec.turns.get(i);
			assertTrue(ref.n == i, "turn numbers must be gap free");
			for (final Node n : nodes) {
				final TurnData t = n.rec.turns.get(i);
				assertTrue(t.n == i, "turn numbers must be gap free");
				assertTrue(sameTurn(ref, t), "turn " + i + " differs between nodes");
			}
		}
		for (int c = 0; c < nodes.length; c++) {
			final int slot = slotOf(nodes[c]);
			int expectedSeq = 1;
			for (final TurnData t : nodes[0].rec.turns) {
				for (final CmdEntry e : t.cmds) {
					if (e.slot == slot) {
						assertTrue(e.seq == expectedSeq, "client " + c + " expected seq " + expectedSeq + " got " + e.seq
								+ " in turn " + t.n);
						assertTrue(java.util.Arrays.equals(e.bytes, command(c, expectedSeq)),
								"bytes of command " + expectedSeq + " changed");
						expectedSeq++;
					}
				}
			}
			assertTrue(expectedSeq - 1 == commandsPerClient,
					"client " + c + " commands in turns: " + (expectedSeq - 1) + " expected " + commandsPerClient);
		}
	}

	public static int slotOf(final Node n) {
		for (final LanData.ParticipantInfo p : n.client.setup().participants) {
			if (p.id == n.client.participantId()) {
				return p.slot;
			}
		}
		throw new AssertionError("participant not in setup");
	}

	private static boolean sameTurn(final TurnData a, final TurnData b) {
		try {
			return java.util.Arrays.equals(a.toBytes(), b.toBytes());
		}
		catch (final net.warsmash.lan.protocol.LanProtocolException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public void close() {
		for (final LanLoop l : this.loops) {
			l.shutdown();
		}
		if (this.host != null && this.host.archive() != null) {
			this.host.archive().close();
		}
		this.loops.clear();
	}

	public static List<String> liveLanThreads() {
		final List<String> names = new ArrayList<String>();
		for (final Thread t : Thread.getAllStackTraces().keySet()) {
			if (t.isAlive() && t.getName().startsWith("lan-")) {
				names.add(t.getName());
			}
		}
		return names;
	}
}
