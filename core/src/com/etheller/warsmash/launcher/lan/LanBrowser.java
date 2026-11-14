package com.etheller.warsmash.launcher.lan;

import java.io.IOException;
import java.util.List;

import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.transport.LanDiscovery;
import net.warsmash.lan.transport.LanLoop;
import net.warsmash.lan.transport.LanNetwork;

/** The search for games on the network while the Join Game page is shown: its own thread and socket, closed after. */
public final class LanBrowser {
	private static final int SEARCH_MS = 2500;

	private final LanLoop loop;
	private final LanDiscovery discovery;
	private final LanNetwork network;
	private long lastSearch;

	public LanBrowser(final LanNetwork network, final LanTimings timings) throws IOException {
		this(network, timings, LanProtocol.DEFAULT_PORT, LanProtocol.DEFAULT_PORT);
	}

	public LanBrowser(final LanNetwork network, final LanTimings timings, final int bindPort, final int targetPort)
			throws IOException {
		this.network = network;
		this.loop = new LanLoop("lan-browse", timings);
		this.discovery = new LanDiscovery(this.loop, network, bindPort, targetPort);
	}

	/** Starts the thread and the first search; the discovery socket is open when this returns. */
	public void start() throws IOException {
		this.loop.start();
		try {
			this.discovery.open();
		}
		catch (final IOException e) {
			this.loop.shutdown();
			throw e;
		}
		this.discovery.search();
		this.lastSearch = System.currentTimeMillis();
	}

	/** Asks these hosts directly (the games this device can rejoin were last played there). */
	public void probe(final java.util.Collection<java.net.InetAddress> hosts) {
		for (final java.net.InetAddress host : hosts) {
			this.discovery.searchAt(host);
		}
	}

	/** Searches again when it is time to; the hosts answer, and announce themselves every two seconds anyway. */
	public void poll(final long now) {
		if ((now - this.lastSearch) >= SEARCH_MS) {
			this.lastSearch = now;
			this.discovery.search();
		}
	}

	public List<LanDiscovery.Found> games() {
		return this.discovery.games();
	}

	public void close() {
		this.loop.shutdown();
	}

	public LanNetwork network() {
		return this.network;
	}
}
