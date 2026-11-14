package net.warsmash.lan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import net.warsmash.lan.transport.DefaultLanNetwork;

class BroadcastAddressTest {
	private static String broadcast(final String address, final int prefix) throws Exception {
		final InetAddress b = DefaultLanNetwork.broadcastOf(InetAddress.getByName(address), prefix);
		return b == null ? null : b.getHostAddress();
	}

	@Test
	void subnetBroadcastFromPrefix() throws Exception {
		assertEquals("192.168.1.255", broadcast("192.168.1.20", 24));
		assertEquals("192.168.43.255", broadcast("192.168.43.1", 24));
		assertEquals("10.255.255.255", broadcast("10.1.2.3", 8));
		assertEquals("192.168.3.255", broadcast("192.168.2.7", 23));
		assertEquals("172.20.0.7", broadcast("172.20.0.6", 31));
	}

	@Test
	void noBroadcastWithoutAPrefixOrForIpv6() throws Exception {
		assertNull(broadcast("192.168.1.20", 32));
		assertNull(broadcast("192.168.1.20", 0));
		assertNull(broadcast("fe80::1", 64));
	}
}
