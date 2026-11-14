package com.etheller.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.protocol.LanTimings;
import net.warsmash.lan.transport.LanNetwork;

class LanSessionLocksTest {
	private static final class CountingNetwork implements LanNetwork {
		int sessionAcquired;
		int sessionReleased;

		@Override
		public boolean isNetworkAvailable() {
			return true;
		}

		@Override
		public List<InetAddress> localAddresses() {
			return Collections.emptyList();
		}

		@Override
		public List<InetAddress> broadcastAddresses() {
			return Collections.emptyList();
		}

		@Override
		public void acquireDiscoveryLock() {
		}

		@Override
		public void releaseDiscoveryLock() {
		}

		@Override
		public void acquireSessionLock() {
			this.sessionAcquired++;
		}

		@Override
		public void releaseSessionLock() {
			this.sessionReleased++;
		}
	}

	@Test
	void sessionLockIsHeldFromTheFirstConnectionAndReleasedOnce() throws Exception {
		final CountingNetwork network = new CountingNetwork();
		final LanSession session = new LanSession(new LanTimings(), network);
		session.search(new net.warsmash.lan.transport.LanDiscovery.Listener() {
			@Override
			public void onGameFound(final net.warsmash.lan.transport.LanDiscovery.Found game) {
			}

			@Override
			public void onGameLost(final long gameId) {
			}
		});
		assertEquals(1, network.sessionAcquired);
		session.stopSearch();
		session.close();
		session.close();
		assertEquals(1, network.sessionReleased);
	}

	@Test
	void aSessionThatNeverConnectedTakesNoLock() {
		final CountingNetwork network = new CountingNetwork();
		new LanSession(new LanTimings(), network).close();
		assertEquals(0, network.sessionAcquired);
		assertEquals(0, network.sessionReleased);
	}
}
