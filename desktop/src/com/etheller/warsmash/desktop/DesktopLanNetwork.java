package com.etheller.warsmash.desktop;

import java.net.NetworkInterface;
import java.util.Locale;

import net.warsmash.lan.transport.DefaultLanNetwork;

/** The desktop's LAN network: no locks to take, but the adapters of virtual machines carry no players. */
public final class DesktopLanNetwork extends DefaultLanNetwork {
	private static final String[] VIRTUAL = { "vmware", "virtualbox", "docker", "vboxnet", "loopback",
			"bluetooth" };

	@Override
	protected boolean acceptInterface(final NetworkInterface ni) {
		if (ni.isVirtual()) {
			return false;
		}
		final String name = (ni.getDisplayName() + " " + ni.getName()).toLowerCase(Locale.ROOT);
		for (final String marker : VIRTUAL) {
			if (name.contains(marker)) {
				return false;
			}
		}
		return true;
	}
}
