package com.etheller.warsmash.launcher.lan;

import java.io.File;

import com.etheller.warsmash.lan.LanGameStart;
import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;
import com.etheller.warsmash.launcher.LaunchRequest;
import com.etheller.warsmash.launcher.LauncherLog;
import com.etheller.warsmash.viewer5.handlers.w3x.ui.MenuUI;

import net.warsmash.lan.client.LanClient;

/** The engine's side of a LAN game the launcher's lobby started: the map this device has, and the host's go. */
public final class LanLaunch {
	private LanLaunch() {
	}

	/** @return false when there is no session or the map is not on this device */
	public static boolean start(final MenuUI menu, final LaunchRequest request) {
		final LanSession session = LanSessions.current();
		final LanClient client = session == null ? null : session.client();
		if ((client == null) || (client.setup() == null)) {
			LauncherLog.w("No LAN game to start");
			return false;
		}
		final File map = client.mapFile();
		if (map == null) {
			LauncherLog.w("The LAN game's map is not on this device");
			return false;
		}
		final LanGameStart start = new LanGameStart(client, client.setup(), map.getAbsolutePath());
		// Out of step with the others: this machine's game is dropped and played again from the first turn.
		start.setRestarter(() -> {
			if (!menu.restartRunningGame(() -> start(menu, request))) {
				LauncherLog.w("No game to start over");
			}
		});
		session.addListener(new LanClient.Listener() {
			@Override
			public void onGo() {
				start.onGo();
			}

			@Override
			public void onLoadProgress(final int participant, final int percent) {
				start.onLoadProgress(participant, percent);
			}
		});
		return menu.startLan(start);
	}
}
