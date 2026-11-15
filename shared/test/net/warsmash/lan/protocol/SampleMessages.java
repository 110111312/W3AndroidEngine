package net.warsmash.lan.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.ChecksumGroup;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.ObserverInfo;
import net.warsmash.lan.protocol.LanData.PresenceEntry;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanData.TurnData;

/** One filled-in instance of every message type. */
public final class SampleMessages {
	private SampleMessages() {
	}

	public static byte[] bytes(final int n, final int seed) {
		final byte[] b = new byte[n];
		for (int i = 0; i < n; i++) {
			b[i] = (byte) (seed * 31 + i * 7);
		}
		return b;
	}

	public static SlotInfo slot(final int i) {
		final SlotInfo s = new SlotInfo();
		s.index = i;
		s.kind = LanProtocol.SLOT_HUMAN;
		s.participantId = i + 1;
		s.name = "Player" + i;
		s.computerLevel = 1;
		s.race = 2;
		s.team = i;
		s.color = i + 3;
		s.handicap = 80;
		s.ready = i % 2 == 0;
		s.pingMs = 12 + i;
		s.controlScheme = i % 2;
		return s;
	}

	public static LanData.MapInfo map() {
		final LanData.MapInfo m = new LanData.MapInfo();
		m.name = "Maps\\Test.w3x";
		m.title = "Test Map";
		m.playableWidth = 96;
		m.playableHeight = 128;
		m.size = 123456;
		m.sha256 = bytes(32, 9);
		m.crc32 = 0xCAFEBABE;
		return m;
	}

	public static LanMessages.LobbyState lobby() {
		final LanMessages.LobbyState l = new LanMessages.LobbyState();
		l.rev = 5;
		l.ownerId = 1;
		for (int i = 0; i < 3; i++) {
			l.slots.add(slot(i));
		}
		final ObserverInfo o = new ObserverInfo();
		o.participantId = 9;
		o.name = "Watcher";
		o.role = LanProtocol.ROLE_REFEREE;
		o.controlScheme = 1;
		o.pingMs = 33;
		l.observers.add(o);
		l.members.add(new LanData.MemberInfo(2, "192.168.1.41", 6113));
		l.options = options();
		l.map = map();
		l.syncWarning = true;
		l.portraits.put(1, "12");
		l.portraits.put(9, "3");
		return l;
	}

	public static LanData.Options options() {
		final LanData.Options o = new LanData.Options();
		o.gameName = "Thrall's Game";
		o.speed = 2;
		o.observers = LanProtocol.OBSERVERS_REFEREES;
		o.lockTeams = true;
		o.fullSharedControl = true;
		return o;
	}

	public static LanGameSetup setup() {
		final LanGameSetup g = new LanGameSetup();
		g.gameId = 0x1122334455667788L;
		g.seed = -42L;
		g.turnSteps = 2;
		g.syncLimit = 10;
		g.options = options();
		g.map = map();
		g.slots.add(slot(0));
		g.slots.add(slot(1));
		final LanData.ParticipantInfo p = new LanData.ParticipantInfo();
		p.id = 1;
		p.name = "Thrall";
		p.role = LanProtocol.ROLE_PLAYER;
		p.slot = 0;
		p.address = "192.168.1.40";
		p.listenPort = 6112;
		p.controlScheme = 1;
		g.participants.add(p);
		g.hostOrder = new int[] { 1, 2, 9 };
		g.header = bytes(20, 4);
		return g;
	}

	public static TurnData turn(final int n) {
		final List<CmdEntry> cmds = new ArrayList<CmdEntry>();
		cmds.add(new CmdEntry(0, 1, bytes(11, 1)));
		cmds.add(new CmdEntry(1, 7, bytes(0, 2)));
		final List<PresenceEntry> presence = new ArrayList<PresenceEntry>();
		presence.add(new PresenceEntry(2, LanProtocol.PRESENCE_ABSENT));
		return new TurnData(n, 2, cmds, presence);
	}

	public static List<LanMessage> all() {
		final List<LanMessage> list = new ArrayList<LanMessage>();
		final LanMessages.Hello h = new LanMessages.Hello();
		h.engineVersion = "0.4.0";
		h.buildCommit = "abc1234";
		h.officialBuild = true;
		h.dataChecksum = -77L;
		h.playerName = "Thrall";
		h.portrait = "orc";
		h.listenPort = 6113;
		h.controlScheme = 1;
		h.wantedRole = LanProtocol.ROLE_OBSERVER;
		list.add(h);
		final LanMessages.Hello hr = new LanMessages.Hello();
		hr.playerName = "Jaina";
		hr.rejoin = new LanMessages.RejoinData();
		hr.rejoin.gameId = 99;
		hr.rejoin.token = bytes(16, 3);
		hr.rejoin.haveTurns = 12;
		list.add(hr);
		final LanMessages.Welcome w = new LanMessages.Welcome();
		w.participantId = 2;
		w.slotToken = bytes(16, 5);
		w.gameId = 1234567890123L;
		w.ownerId = 1;
		w.gameState = LanProtocol.STATE_LOBBY;
		w.lobby = lobby();
		list.add(w);
		final LanMessages.Reject r = new LanMessages.Reject(LanProtocol.REJECT_NOT_HOST, "Not the host");
		r.hostVersion = "0.4.3";
		r.redirectHost = "192.168.1.41";
		r.redirectPort = 6112;
		list.add(r);
		list.add(new LanMessages.Ping(123456789L));
		list.add(new LanMessages.Pong(987654321L));
		list.add(new LanMessages.PlayerState(3, LanProtocol.CONTROL_KBM));
		list.add(lobby());
		list.add(new LanMessages.SlotRequest(2, 1, 1, 4, 70));
		list.add(new LanMessages.HostSlot(3, LanProtocol.ACTION_COMPUTER, 2));
		list.add(new LanMessages.HostSetup(3, 2, 1, 5, 80));
		list.add(new LanMessages.HostOptions(options()));
		list.add(new LanMessages.Ready(true));
		list.add(new LanMessages.Chat(LanProtocol.CHAT_ALLIES, 2, "gg wp äš"));
		list.add(new LanMessages.MapRequest(4096));
		list.add(new LanMessages.MapChunk(0, 100000, bytes(1000, 6)));
		list.add(new LanMessages.MapStatus(LanProtocol.MAP_STATUS_DOWNLOADING, 63));
		list.add(new LanMessages.StartRequest());
		list.add(new LanMessages.Countdown(5));
		list.add(new LanMessages.CountdownCancel());
		list.add(new LanMessages.Start(setup()));
		list.add(new LanMessages.LoadProgress(2, 41));
		list.add(new LanMessages.Loaded());
		list.add(new LanMessages.Go());
		list.add(new LanMessages.Leave(LanProtocol.LEAVE_QUIT));
		list.add(new LanMessages.Kicked("You were kicked from the game"));
		list.add(new LanMessages.Cmd(17, bytes(40, 8)));
		list.add(new LanMessages.TurnDone(250));
		list.add(new LanMessages.Checksum(40, 0xDEADBEEF));
		list.add(new LanMessages.AppState(LanProtocol.APP_BACKGROUND));
		list.add(new LanMessages.Vote(LanProtocol.VOTE_CLAIM_VICTORY));
		list.add(new LanMessages.Turn(turn(7)));
		list.add(new LanMessages.Lag(Arrays.asList(new LagEntry(2, LanProtocol.LAG_BACKGROUND, 2500),
				new LagEntry(3, LanProtocol.LAG_RECONNECTING, 40000))));
		list.add(new LanMessages.LagEnd());
		list.add(new LanMessages.Presence(2, LanProtocol.PRESENCE_REJOINING));
		list.add(new LanMessages.Desync(60, Arrays.asList(new ChecksumGroup(1, new int[] { 1, 2 }),
				new ChecksumGroup(2, new int[] { 3 }))));
		list.add(new LanMessages.HostChanged(2, 300));
		list.add(new LanMessages.GameEnd(0));
		list.add(new LanMessages.Resync(LanProtocol.RESYNC_DESYNC, 60));
		list.add(new LanMessages.Resync(LanProtocol.RESYNC_ACK, -1));
		list.add(new LanMessages.Rejoin(setup(), 0, 1000, 77, 2000));
		list.add(new LanMessages.CaughtUp(999, -5));
		list.add(blockOf(500, 600));
		list.add(new LanMessages.MigrateHello(2, bytes(16, 2), 410,
				Arrays.asList(new CmdEntry(1, 20, bytes(5, 1)), new CmdEntry(1, 21, bytes(6, 1))), 6113));
		list.add(new LanMessages.MigrateWelcome(411));
		list.add(new LanMessages.Redirect("192.168.1.42", 6113));
		list.add(new LanMessages.TurnFillRequest(400, 410));
		list.add(new LanMessages.TurnFill(Arrays.asList(turn(400), turn(401))));
		return list;
	}

	/** The compressed block of the turns from..to-1 (empty ones with a command now and then). */
	public static LanMessages.TurnBlock blockOf(final int from, final int to) {
		final List<LanData.TurnData> turns = new java.util.ArrayList<LanData.TurnData>();
		for (int i = from; i < to; i++) {
			turns.add(turn(i));
		}
		try {
			return LanMessages.TurnBlock.of(turns);
		}
		catch (final net.warsmash.lan.protocol.LanProtocolException e) {
			throw new IllegalStateException(e);
		}
	}
}
