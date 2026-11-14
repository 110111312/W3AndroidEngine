package com.etheller.warsmash.launcher;

import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;

/**
 * The one rule that only one game runs at a time. While this machine is in a LAN lobby, hosts one, or plays one, no
 * other game starts; both platforms ask it inside the single place they start a game, so no page can forget to.
 */
public final class LaunchGuard {
	public static final int REASON_IN_LOBBY = 1;
	public static final int REASON_HOSTING = 2;
	/** A LAN game is asked for that is not the one of the current session (or there is none). */
	public static final int REASON_NO_SESSION = 3;

	/** Why a game cannot start, and the LAN session in the way (null for {@link #REASON_NO_SESSION}). */
	public static final class Blocked {
		public final int reason;
		public final LanSession lanSession;

		Blocked(final int reason, final LanSession lanSession) {
			this.reason = reason;
			this.lanSession = lanSession;
		}

		public boolean hosting() {
			return this.reason == REASON_HOSTING;
		}
	}

	private LaunchGuard() {
	}

	/** @return null when the game may start */
	public static Blocked check(final LaunchRequest request) {
		return check(request, LanSessions.current());
	}

	static Blocked check(final LaunchRequest request, final LanSession current) {
		if (LaunchRequest.SOURCE_LAN.equals(request.source())) {
			return (current != null) && (request.lanSession == current) ? null
					: new Blocked(REASON_NO_SESSION, current);
		}
		if (current == null) {
			return null;
		}
		return new Blocked(current.host() != null ? REASON_HOSTING : REASON_IN_LOBBY, current);
	}
}
