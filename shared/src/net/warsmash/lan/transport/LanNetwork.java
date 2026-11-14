package net.warsmash.lan.transport;

import java.net.InetAddress;
import java.util.List;

/** What the platform contributes to LAN play: addresses, reachability and the power/radio locks that keep a session alive. */
public interface LanNetwork {
	boolean isNetworkAvailable();

	/** Own site-local addresses, for display and for telling other participants where to reach this one. */
	List<InetAddress> localAddresses();

	/** Destinations for discovery datagrams (limited broadcast plus the broadcast address of every interface). */
	List<InetAddress> broadcastAddresses();

	/** Held while searching or announcing (Android MulticastLock). */
	void acquireDiscoveryLock();

	void releaseDiscoveryLock();

	/** Held from lobby to the end of the session (Android WifiLock and foreground service). */
	void acquireSessionLock();

	void releaseSessionLock();
}
