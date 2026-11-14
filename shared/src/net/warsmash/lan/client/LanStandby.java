package net.warsmash.lan.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanMessage;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.transport.LanConnection;
import net.warsmash.lan.transport.LanLoop;

/**
 * The listening socket every participant keeps while it plays, so that it can become the host when the host is gone.
 * Until then it sends whoever comes to the host (a player asking for a host that may not be gone at all), and while
 * this participant itself has not yet found out whether its host is gone it holds the one who asked. Once the
 * participant is the host, what comes in goes to the host. Loop thread of the client.
 */
final class LanStandby {
	/** What the standby asks the client it belongs to. */
	interface Owner {
		/** Whether the client has a working link to its host. */
		boolean hostAlive();

		/** Where the client's host is, for a redirect; null when not known. */
		InetSocketAddress hostAddress();

		/** Whether the client has lost its host and has not yet found a new one (or become one itself). */
		boolean searching();
	}

	private static final class Held {
		final LanConnection connection;
		final LanMessage first;
		final LanLoop.Timer timer;

		Held(final LanConnection connection, final LanMessage first, final LanLoop.Timer timer) {
			this.connection = connection;
			this.first = first;
			this.timer = timer;
		}
	}

	private final LanLoop loop;
	private final Owner owner;
	private LanLoop.ServerHandle server;
	private LanHost host;
	private final List<Held> held = new ArrayList<Held>();

	LanStandby(final LanLoop loop, final Owner owner) {
		this.loop = loop;
		this.owner = owner;
	}

	/** Starts listening; returns the port. */
	int open(final int port, final int tries) throws IOException {
		this.server = this.loop.listen(port, tries, new LanLoop.AcceptListener() {
			@Override
			public void onAccept(final LanConnection c) {
				accepted(c);
			}
		});
		return this.server.port();
	}

	int port() {
		return this.server == null ? 0 : this.server.port();
	}

	LanLoop.ServerHandle server() {
		return this.server;
	}

	void close() {
		if (this.server != null) {
			this.server.close();
			this.server = null;
		}
	}

	private void accepted(final LanConnection c) {
		final LanLoop.Timer hello = this.loop.schedule(this.loop.timings().helloTimeoutMs, new Runnable() {
			@Override
			public void run() {
				c.close("hello timeout");
			}
		});
		c.setListener(new LanConnection.Listener() {
			private boolean first = true;

			@Override
			public void onMessage(final LanConnection connection, final LanMessage m) {
				if (!this.first) {
					return;
				}
				this.first = false;
				hello.cancel();
				firstMessage(connection, m);
			}

			@Override
			public void onClosed(final LanConnection connection, final String reason) {
				hello.cancel();
				for (final Held h : new ArrayList<Held>(LanStandby.this.held)) {
					if (h.connection == connection) {
						h.timer.cancel();
						LanStandby.this.held.remove(h);
					}
				}
			}
		});
	}

	private void firstMessage(final LanConnection c, final LanMessage m) {
		if (this.host != null) {
			this.host.adopt(c, m);
			return;
		}
		if (!(m instanceof LanMessages.Hello) && !(m instanceof LanMessages.MigrateHello)) {
			c.close("expected HELLO");
			return;
		}
		if (this.owner.hostAlive() || !this.owner.searching()) {
			redirect(c);
			return;
		}
		// Asked before this participant knows whether its own host is really gone: answered once it does.
		final Held[] self = new Held[1];
		final LanLoop.Timer timer = this.loop.schedule(
				this.loop.timings().hostGoneMs + this.loop.timings().migrateWaitMs + 1000, new Runnable() {
					@Override
					public void run() {
						LanStandby.this.held.remove(self[0]);
						c.close("no host yet");
					}
				});
		self[0] = new Held(c, m, timer);
		this.held.add(self[0]);
	}

	private void redirect(final LanConnection c) {
		final InetSocketAddress a = this.owner.hostAddress();
		if (a == null || a.getAddress() == null) {
			c.close("no host");
			return;
		}
		c.send(new LanMessages.Redirect(a.getAddress().getHostAddress(), a.getPort()));
		c.closeAfterFlush("redirected");
	}

	/** This participant is the host now: those that waited go to it. */
	void promoted(final LanHost promoted) {
		this.host = promoted;
		for (final Held h : new ArrayList<Held>(this.held)) {
			h.timer.cancel();
			promoted.adopt(h.connection, h.first);
		}
		this.held.clear();
	}

	/** The host turned out not to be gone (or another participant took over): those that waited are sent to it. */
	void hostFound() {
		for (final Held h : new ArrayList<Held>(this.held)) {
			h.timer.cancel();
			redirect(h.connection);
		}
		this.held.clear();
	}
}
