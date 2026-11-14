package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.lan.LanSession;
import com.etheller.warsmash.lan.LanSessions;

import net.warsmash.lan.host.LanHost;
import net.warsmash.lan.protocol.LanTimings;

class LaunchGuardTest {
	private static LanSession session() {
		return new LanSession(new LanTimings());
	}

	private static LanSession hostingSession() throws Exception {
		final LanSession s = session();
		final LanHost.Config config = new LanHost.Config();
		config.port = 0;
		config.portTries = 1;
		s.openHost(config);
		return s;
	}

	private static LaunchRequest request(final String source, final LanSession lan) {
		final LaunchRequest r = new LaunchRequest();
		switch (source) {
		case LaunchRequest.SOURCE_CAMPAIGN:
			r.campaign = 2;
			r.mission = 1;
			break;
		case LaunchRequest.SOURCE_CUSTOM:
			r.customMap = "Maps\\Test.w3x";
			break;
		case LaunchRequest.SOURCE_LOAD:
			r.saveFile = "Save1";
			break;
		case LaunchRequest.SOURCE_REPLAY:
			r.replayFile = "Replay.w3g";
			break;
		case LaunchRequest.SOURCE_LAN:
			return LaunchRequest.lan(lan, "map.w3x", "");
		default:
			throw new AssertionError("a source the test does not know: " + source);
		}
		assertEquals(source, r.source());
		return r;
	}

	private static Set<String> allSources() throws IllegalAccessException {
		final Set<String> sources = new HashSet<>();
		for (final Field f : LaunchRequest.class.getFields()) {
			if (f.getName().startsWith("SOURCE_") && Modifier.isStatic(f.getModifiers())) {
				sources.add((String) f.get(null));
			}
		}
		return sources;
	}

	@Test
	void everySourceHasARequestHere() throws Exception {
		final Set<String> sources = allSources();
		assertTrue(sources.size() >= 5);
		for (final String source : sources) {
			assertEquals(source, request(source, session()).source());
		}
	}

	@Test
	void withoutASessionEveryOtherGameStarts() throws Exception {
		for (final String source : allSources()) {
			if (!LaunchRequest.SOURCE_LAN.equals(source)) {
				assertNull(LaunchGuard.check(request(source, null), null), source);
			}
		}
	}

	@Test
	void inALobbyNoOtherGameStarts() throws Exception {
		final LanSession lobby = session();
		for (final String source : allSources()) {
			if (LaunchRequest.SOURCE_LAN.equals(source)) {
				continue;
			}
			final LaunchGuard.Blocked blocked = LaunchGuard.check(request(source, null), lobby);
			assertNotNull(blocked, source);
			assertEquals(LaunchGuard.REASON_IN_LOBBY, blocked.reason, source);
			assertSame(lobby, blocked.lanSession);
		}
	}

	@Test
	void hostingSaysSo() throws Exception {
		final LanSession hosting = hostingSession();
		try {
			final LaunchGuard.Blocked blocked = LaunchGuard.check(request(LaunchRequest.SOURCE_CUSTOM, null), hosting);
			assertEquals(LaunchGuard.REASON_HOSTING, blocked.reason);
			assertTrue(blocked.hosting());
		}
		finally {
			hosting.close();
		}
	}

	@Test
	void theLanGameOfTheSameSessionStarts() throws Exception {
		final LanSession lobby = session();
		assertNull(LaunchGuard.check(request(LaunchRequest.SOURCE_LAN, lobby), lobby));
	}

	@Test
	void aLanGameOfAnotherSessionOrNoneDoesNot() throws Exception {
		final LanSession lobby = session();
		final LanSession other = session();
		assertEquals(LaunchGuard.REASON_NO_SESSION,
				LaunchGuard.check(request(LaunchRequest.SOURCE_LAN, other), lobby).reason);
		assertEquals(LaunchGuard.REASON_NO_SESSION,
				LaunchGuard.check(request(LaunchRequest.SOURCE_LAN, lobby), null).reason);
	}

	@Test
	void theProcessSessionIsTheOneAsked() throws Exception {
		final LanSession lobby = session();
		LanSessions.begin(lobby);
		try {
			assertNotNull(LaunchGuard.check(request(LaunchRequest.SOURCE_CAMPAIGN, null)));
			assertNull(LaunchGuard.check(request(LaunchRequest.SOURCE_LAN, lobby)));
		}
		finally {
			LanSessions.end(lobby);
		}
		assertNull(LaunchGuard.check(request(LaunchRequest.SOURCE_CAMPAIGN, null)));
	}
}
