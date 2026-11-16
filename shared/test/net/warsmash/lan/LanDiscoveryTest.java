package net.warsmash.lan;

import static net.warsmash.lan.LanTestSupport.waitFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.DefaultLanNetwork;
import net.warsmash.lan.transport.LanDiscovery;
import net.warsmash.lan.transport.LanLoop;
import net.warsmash.lan.transport.LanNetwork;

class LanDiscoveryTest {
	private final LanTestSupport env = new LanTestSupport();

	@AfterEach
	void tearDown() throws Exception {
		this.env.close();
		waitFor("network threads to end", 3000, () -> LanTestSupport.liveLanThreads().isEmpty());
	}

	private static int freeUdpPort() throws Exception {
		try (DatagramSocket s = new DatagramSocket(0)) {
			return s.getLocalPort();
		}
	}

	/** Pretends the only network is loopback, so "broadcast" reaches the other test node. */
	private static final class LoopbackNetwork extends DefaultLanNetwork {
		int acquired;
		int released;

		@Override
		public List<InetAddress> broadcastAddresses() {
			try {
				return Arrays.asList(InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }));
			}
			catch (final java.net.UnknownHostException e) {
				throw new AssertionError(e);
			}
		}

		@Override
		public synchronized void acquireDiscoveryLock() {
			this.acquired++;
		}

		@Override
		public synchronized void releaseDiscoveryLock() {
			this.released++;
		}
	}

	private static final class Listener implements LanDiscovery.Listener {
		final List<LanDiscovery.Found> found = new CopyOnWriteArrayList<LanDiscovery.Found>();
		final List<Long> lost = new CopyOnWriteArrayList<Long>();

		@Override
		public void onGameFound(final LanDiscovery.Found game) {
			this.found.add(game);
		}

		@Override
		public void onGameLost(final long gameId) {
			this.lost.add(gameId);
		}
	}

	@Test
	void hostIsFoundBySearchAndAnnouncementAndDisappears() throws Exception {
		final int hostUdp = freeUdpPort();
		final int searchUdp = freeUdpPort();
		this.env.startHost();
		final LanLoop searchLoop = this.env.newLoop("searcher");
		final LoopbackNetwork hostNet = new LoopbackNetwork();
		final LoopbackNetwork searchNet = new LoopbackNetwork();
		final LanDiscovery hostDisc = new LanDiscovery(this.env.hostLoop, hostNet, hostUdp, searchUdp);
		final LanDiscovery searchDisc = new LanDiscovery(searchLoop, searchNet, searchUdp, hostUdp);
		final Listener l = new Listener();
		searchDisc.setListener(l);
		hostDisc.open();
		searchDisc.open();
		assertEquals(hostUdp, hostDisc.boundPort());
		assertEquals(1, hostNet.acquired);

		this.env.host.setDiscovery(hostDisc);
		waitFor("announcement", () -> !l.found.isEmpty());
		final LanDiscovery.Found f = l.found.get(0);
		assertEquals(this.env.host.gameId(), f.info.gameId);
		assertEquals("Test Game", f.info.gameName);
		assertEquals(this.env.port, f.info.tcpPort);
		assertEquals(LanProtocol.STATE_LOBBY, f.info.state);
		assertTrue(f.address.isLoopbackAddress());

		// lobby changes are announced right away
		final LanTestSupport.Node p = this.env.joinAndWait("Thrall");
		waitFor("host name", () -> {
			for (final LanDiscovery.Found g : searchDisc.games()) {
				if (g.info.hostName.equals("Thrall") && g.info.players == 1) {
					return true;
				}
			}
			return false;
		});
		assertEquals(1, searchDisc.games().size());

		// a SEARCH is answered even by a host that is not due to announce
		final int before = l.found.size();
		searchDisc.search();
		waitFor("reply to search", () -> l.found.size() > before);

		// a search sent to the host's own address is answered the same way
		final int beforeDirect = l.found.size();
		searchDisc.searchAt(InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }));
		waitFor("reply to a search at the host's address", () -> l.found.size() > beforeDirect);

		// and the client can join the discovered game
		final LanTestSupport.Node viaDiscovery = this.env.join("Jaina");
		waitFor("welcome", () -> viaDiscovery.rec.welcome != null);
		assertEquals(p.rec.welcome.gameId, f.info.gameId);

		this.env.host.close();
		waitFor("game gone", () -> l.lost.contains(f.info.gameId));
		assertTrue(searchDisc.games().isEmpty());
		searchDisc.close();
		hostDisc.close();
		waitFor("locks released", () -> searchNet.released == 1 && hostNet.released == 1);
	}

	@Test
	void hostsThatStopAnnouncingWithoutGoodbyeExpire() throws Exception {
		final int searchUdp = freeUdpPort();
		final LanLoop searchLoop = this.env.newLoop("s");
		final LanDiscovery searchDisc = new LanDiscovery(searchLoop, new LoopbackNetwork(), searchUdp, searchUdp);
		final Listener l = new Listener();
		searchDisc.setListener(l);
		searchDisc.open();
		final net.warsmash.lan.protocol.LanData.GameInfo info = new net.warsmash.lan.protocol.LanData.GameInfo();
		info.gameId = 42;
		info.gameName = "Gone quiet";
		info.tcpPort = 1234;
		final byte[] announce = net.warsmash.lan.protocol.LanCodec.encodeDatagram(
				new net.warsmash.lan.protocol.LanCodec.GameAnnounce(info));
		try (DatagramSocket s = new DatagramSocket()) {
			s.send(new DatagramPacket(announce, announce.length, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }),
					searchUdp));
		}
		waitFor("found", () -> !l.found.isEmpty());
		assertEquals(1234, l.found.get(0).info.tcpPort);
		waitFor("expired", 5000, () -> l.lost.contains(42L));
		assertTrue(searchDisc.games().isEmpty());
	}

	@Test
	void theAnnouncementCarriesTheHostsPortrait() throws Exception {
		final int hostUdp = freeUdpPort();
		final int searchUdp = freeUdpPort();
		this.env.startHost();
		final LanLoop searchLoop = this.env.newLoop("searcher");
		final LanDiscovery hostDisc = new LanDiscovery(this.env.hostLoop, new LoopbackNetwork(), hostUdp, searchUdp);
		final LanDiscovery searchDisc = new LanDiscovery(searchLoop, new LoopbackNetwork(), searchUdp, hostUdp);
		hostDisc.open();
		searchDisc.open();
		this.env.host.setDiscovery(hostDisc);
		final net.warsmash.lan.client.LanClient.Config config = LanTestSupport.clientConfig("Thrall");
		config.portrait = "c:BTNArthas";
		final LanTestSupport.Node owner = this.env.join(config);
		waitFor("welcome", () -> owner.rec.welcome != null);
		waitFor("the host's portrait", () -> {
			for (final LanDiscovery.Found g : searchDisc.games()) {
				if (g.info.hostName.equals("Thrall") && g.info.portrait.equals("c:BTNArthas")) {
					return true;
				}
			}
			return false;
		});
	}

	@Test
	void foreignAndBrokenDatagramsAreIgnored() throws Exception {
		final int udp = freeUdpPort();
		final LanLoop loop = this.env.newLoop("s");
		final LanDiscovery d = new LanDiscovery(loop, new LoopbackNetwork(), udp, udp);
		final Listener l = new Listener();
		d.setListener(l);
		d.open();
		final List<byte[]> junk = new ArrayList<byte[]>();
		junk.add(new byte[] { 1, 2, 3 });
		junk.add("GET / HTTP/1.1\r\n\r\n".getBytes("US-ASCII"));
		final byte[] almost = net.warsmash.lan.protocol.LanCodec.encodeDatagram(
				new net.warsmash.lan.protocol.LanCodec.GameGone(1));
		junk.add(Arrays.copyOf(almost, almost.length - 3));
		junk.add(new byte[1500]);
		try (DatagramSocket s = new DatagramSocket()) {
			for (final byte[] b : junk) {
				s.send(new DatagramPacket(b, b.length, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), udp));
			}
		}
		Thread.sleep(200);
		assertTrue(d.games().isEmpty());
		assertTrue(l.found.isEmpty());
		// still alive afterwards
		final byte[] good = net.warsmash.lan.protocol.LanCodec.encodeDatagram(new net.warsmash.lan.protocol.LanCodec.Search(1));
		try (DatagramSocket s = new DatagramSocket()) {
			s.send(new DatagramPacket(good, good.length, InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 }), udp));
		}
		Thread.sleep(50);
		assertTrue(d.games().isEmpty());
	}

	@Test
	void defaultNetworkListsBroadcastTargets() {
		final LanNetwork n = new DefaultLanNetwork();
		final List<InetAddress> b = n.broadcastAddresses();
		assertTrue(b.size() >= 1);
		assertEquals("255.255.255.255", b.get(0).getHostAddress());
	}
}
