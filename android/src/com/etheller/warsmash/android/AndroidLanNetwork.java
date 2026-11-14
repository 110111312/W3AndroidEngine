package com.etheller.warsmash.android;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import net.warsmash.lan.transport.DefaultLanNetwork;

/**
 * LAN play on Android: the multicast lock for the game search, the Wi-Fi and CPU locks and the foreground service for
 * the length of a session, and the addresses of the networks the phone is on. A phone that is the hotspot has no
 * Network of its own for it, so the interfaces are read too: its access point interface is where the other players
 * are, and its broadcast address is where the search has to go.
 */
public final class AndroidLanNetwork extends DefaultLanNetwork {
	private static final String TAG = "WarsmashLan";
	private static final String[] MOBILE_DATA = { "rmnet", "ccmni", "pdp", "tun", "dummy", "v4-", "ipsec" };

	private final Context context;
	private WifiManager.MulticastLock multicastLock;
	private WifiManager.WifiLock wifiLock;
	private PowerManager.WakeLock cpuLock;
	private int discoveryHolds;
	private int sessionHolds;

	public AndroidLanNetwork(final Context context) {
		this.context = context.getApplicationContext();
	}

	@Override
	protected boolean acceptInterface(final NetworkInterface ni) {
		final String name = ni.getName().toLowerCase(Locale.ROOT);
		for (final String prefix : MOBILE_DATA) {
			if (name.startsWith(prefix)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<InetAddress> localAddresses() {
		final List<InetAddress> result = super.localAddresses();
		for (final LinkAddress link : linkAddresses()) {
			if (link.getAddress().isSiteLocalAddress() && !result.contains(link.getAddress())) {
				result.add(link.getAddress());
			}
		}
		return result;
	}

	@Override
	public List<InetAddress> broadcastAddresses() {
		final List<InetAddress> result = super.broadcastAddresses();
		for (final LinkAddress link : linkAddresses()) {
			final InetAddress broadcast = broadcastOf(link.getAddress(), link.getPrefixLength());
			if ((broadcast != null) && !result.contains(broadcast)) {
				result.add(broadcast);
			}
		}
		return result;
	}

	/** The IPv4 addresses of the Wi-Fi and Ethernet networks the system routes through. */
	@SuppressWarnings("deprecation")
	private List<LinkAddress> linkAddresses() {
		final List<LinkAddress> result = new ArrayList<LinkAddress>();
		try {
			final ConnectivityManager manager = this.context.getSystemService(ConnectivityManager.class);
			for (final Network network : manager.getAllNetworks()) {
				final NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
				final LinkProperties properties = manager.getLinkProperties(network);
				if ((capabilities == null) || (properties == null)
						|| !(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
								|| capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
					continue;
				}
				for (final LinkAddress link : properties.getLinkAddresses()) {
					if (link.getAddress() instanceof Inet4Address) {
						result.add(link);
					}
				}
			}
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "Cannot read the networks", e);
		}
		return result;
	}

	@Override
	public synchronized void acquireDiscoveryLock() {
		if (this.discoveryHolds++ == 0) {
			try {
				final WifiManager wifi = this.context.getSystemService(WifiManager.class);
				this.multicastLock = wifi.createMulticastLock("warsmash:lan-search");
				this.multicastLock.setReferenceCounted(false);
				this.multicastLock.acquire();
			}
			catch (final RuntimeException e) {
				Log.w(TAG, "Cannot take the multicast lock", e);
			}
		}
	}

	@Override
	public synchronized void releaseDiscoveryLock() {
		if ((this.discoveryHolds > 0) && (--this.discoveryHolds == 0) && (this.multicastLock != null)) {
			if (this.multicastLock.isHeld()) {
				this.multicastLock.release();
			}
			this.multicastLock = null;
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public synchronized void acquireSessionLock() {
		if (this.sessionHolds++ > 0) {
			return;
		}
		try {
			// Low latency counts only while the app is on the screen; the lock is the best the system offers there.
			final int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
					: WifiManager.WIFI_MODE_FULL_HIGH_PERF;
			this.wifiLock = this.context.getSystemService(WifiManager.class).createWifiLock(mode, "warsmash:lan-game");
			this.wifiLock.setReferenceCounted(false);
			this.wifiLock.acquire();
			this.cpuLock = this.context.getSystemService(PowerManager.class)
					.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "warsmash:lan-game");
			this.cpuLock.setReferenceCounted(false);
			this.cpuLock.acquire();
		}
		catch (final RuntimeException e) {
			Log.w(TAG, "Cannot take the Wi-Fi or CPU lock", e);
		}
		try {
			LanSessionService.start(this.context);
		}
		catch (final RuntimeException e) {
			// Android 12+ refuses it when the app is not on the screen; the game still plays.
			Log.w(TAG, "Cannot start the session service", e);
		}
	}

	@Override
	public synchronized void releaseSessionLock() {
		if ((this.sessionHolds == 0) || (--this.sessionHolds > 0)) {
			return;
		}
		if ((this.wifiLock != null) && this.wifiLock.isHeld()) {
			this.wifiLock.release();
		}
		if ((this.cpuLock != null) && this.cpuLock.isHeld()) {
			this.cpuLock.release();
		}
		this.wifiLock = null;
		this.cpuLock = null;
		LanSessionService.stop(this.context);
	}
}
