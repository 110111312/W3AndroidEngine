package com.etheller.warsmash.lan;

import com.etheller.warsmash.input.touch.ControlScheme;

import net.warsmash.lan.client.LanClient;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.transport.DefaultLanNetwork;
import net.warsmash.lan.transport.LanNetwork;

/**
 * The LAN session of this process, if any: the launcher's pages and the game find it here. A process plays one LAN
 * game at a time.
 */
public final class LanSessions {
	private static volatile LanSession current;
	private static volatile LanNetwork network = new DefaultLanNetwork();
	private static volatile net.warsmash.lan.RejoinStore rejoinStore;

	private LanSessions() {
	}

	/** The platform's network, which sessions made without one use. */
	public static LanNetwork network() {
		return network;
	}

	public static void setNetwork(final LanNetwork platform) {
		network = platform;
	}

	/** Where this device keeps the keys of the games it can rejoin; null (the default) keeps none. */
	public static net.warsmash.lan.RejoinStore rejoinStore() {
		return rejoinStore;
	}

	public static void setRejoinStore(final net.warsmash.lan.RejoinStore store) {
		rejoinStore = store;
	}

	/** The app left or came back to the screen (called from the platform's activity). */
	public static void appState(final boolean background) {
		final LanSession session = current;
		if (session != null) {
			session.appState(background);
		}
	}

	public static LanSession current() {
		return current;
	}

	/** Makes the session the process's; the one it replaces is closed. */
	public static synchronized void begin(final LanSession session) {
		final LanSession old = current;
		current = session;
		session.setRejoinStore(rejoinStore);
		if ((old != null) && (old != session)) {
			old.close();
		}
	}

	/** The scheme as the protocol numbers it. */
	public static int protocolScheme(final ControlScheme scheme) {
		return scheme == ControlScheme.MOUSE_KEYBOARD ? LanProtocol.CONTROL_KBM : LanProtocol.CONTROL_TOUCH;
	}

	/**
	 * The player changed the control scheme in Options: the others in the lobby (or the game) are told, so the mark
	 * beside the name follows at once. Without a session nothing happens.
	 */
	public static void onControlSchemeChanged(final ControlScheme scheme) {
		final LanSession session = current;
		final LanClient client = session == null ? null : session.client();
		if (client != null) {
			client.playerState(protocolScheme(scheme));
		}
	}

	/**
	 * Done with the session: a host whose game goes on for other players hands it over (see
	 * {@link LanSession#retire}) and closes then; any other session closes.
	 */
	public static synchronized void end(final LanSession session) {
		if (current == session) {
			current = null;
		}
		if (!session.retire()) {
			session.close();
		}
	}

	/** Closes the session at once, whatever its game. */
	public static synchronized void endNow(final LanSession session) {
		if (current == session) {
			current = null;
		}
		session.close();
	}
}
