package net.warsmash.lan.transport;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** Plain-Java implementation (desktop and tests); the locks are no-ops. */
public class DefaultLanNetwork implements LanNetwork {
	/** Whether the interface may carry LAN play; platforms leave out the ones that cannot (mobile data, virtual adapters). */
	protected boolean acceptInterface(final NetworkInterface ni) {
		return true;
	}

	@Override
	public boolean isNetworkAvailable() {
		return !localAddresses().isEmpty();
	}

	@Override
	public List<InetAddress> localAddresses() {
		final List<InetAddress> result = new ArrayList<InetAddress>();
		try {
			final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			if (ifaces != null) {
				for (final NetworkInterface ni : Collections.list(ifaces)) {
					if (!ni.isUp() || ni.isLoopback() || !acceptInterface(ni)) {
						continue;
					}
					for (final InetAddress a : Collections.list(ni.getInetAddresses())) {
						if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
							result.add(a);
						}
					}
				}
			}
		}
		catch (final java.io.IOException e) {
			LanLog.warn("cannot enumerate interfaces", e);
		}
		return result;
	}

	@Override
	public List<InetAddress> broadcastAddresses() {
		final List<InetAddress> result = new ArrayList<InetAddress>();
		try {
			result.add(InetAddress.getByAddress(new byte[] { -1, -1, -1, -1 }));
			final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
			if (ifaces != null) {
				for (final NetworkInterface ni : Collections.list(ifaces)) {
					if (!ni.isUp() || ni.isLoopback() || !acceptInterface(ni)) {
						continue;
					}
					for (final InterfaceAddress ia : ni.getInterfaceAddresses()) {
						// Android reports no broadcast address for some interfaces; the prefix gives it.
						final InetAddress b = ia.getBroadcast() != null ? ia.getBroadcast()
								: broadcastOf(ia.getAddress(), ia.getNetworkPrefixLength());
						if (b != null && !result.contains(b)) {
							result.add(b);
						}
					}
				}
			}
		}
		catch (final java.io.IOException e) {
			LanLog.warn("cannot enumerate interfaces", e);
		}
		return result;
	}

	/** The broadcast address of the subnet the address and prefix length make, or null for a non-IPv4 address. */
	public static InetAddress broadcastOf(final InetAddress address, final int prefixLength) {
		if (!(address instanceof Inet4Address) || prefixLength < 1 || prefixLength > 31) {
			return null;
		}
		final byte[] bytes = address.getAddress();
		final int mask = -1 << (32 - prefixLength);
		final int value = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8)
				| (bytes[3] & 0xFF);
		final int broadcast = value | ~mask;
		try {
			return InetAddress.getByAddress(new byte[] { (byte) (broadcast >>> 24), (byte) (broadcast >>> 16),
					(byte) (broadcast >>> 8), (byte) broadcast });
		}
		catch (final java.net.UnknownHostException e) {
			return null;
		}
	}

	@Override
	public void acquireDiscoveryLock() {
	}

	@Override
	public void releaseDiscoveryLock() {
	}

	@Override
	public void acquireSessionLock() {
	}

	@Override
	public void releaseSessionLock() {
	}
}
