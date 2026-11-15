package net.warsmash.lan.protocol;

import java.util.ArrayList;
import java.util.List;

import net.warsmash.lan.LanGameSetup;
import net.warsmash.lan.protocol.LanData.ChecksumGroup;
import net.warsmash.lan.protocol.LanData.CmdEntry;
import net.warsmash.lan.protocol.LanData.LagEntry;
import net.warsmash.lan.protocol.LanData.ObserverInfo;
import net.warsmash.lan.protocol.LanData.SlotInfo;
import net.warsmash.lan.protocol.LanData.TurnData;

/** All TCP messages. Every class encodes canonically and decodes with full validation. */
public final class LanMessages {
	private LanMessages() {
	}

	private abstract static class Empty extends LanMessage {
		@Override
		public void write(final ByteWriter w) {
		}
	}

	// ---------------------------------------------------------------- handshake

	public static final class Hello extends LanMessage {
		public int protocolVersion = LanProtocol.PROTOCOL_VERSION;
		public String engineVersion = "";
		public String buildCommit = "";
		public boolean officialBuild;
		public long dataChecksum;
		public String playerName = "";
		public String portrait = "";
		public int listenPort;
		public int controlScheme;
		/** ROLE_PLAYER, ROLE_OBSERVER or ROLE_REFEREE preference. */
		public int wantedRole;
		/** Present when rejoining or reconnecting to a started game. */
		public RejoinData rejoin;

		@Override
		public int type() {
			return LanProtocol.HELLO;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(LanProtocol.MAGIC).u16(this.protocolVersion);
			w.string(this.engineVersion, 32).string(this.buildCommit, 48).bool(this.officialBuild)
					.i64(this.dataChecksum).string(this.playerName, LanProtocol.MAX_PLAYER_NAME)
					.string(this.portrait, LanProtocol.MAX_PORTRAIT).u16(this.listenPort).u8(this.controlScheme).u8(this.wantedRole);
			w.bool(this.rejoin != null);
			if (this.rejoin != null) {
				this.rejoin.write(w);
			}
		}

		/**
		 * A different protocol version is decoded as far as the version field so the host can answer with a
		 * proper REJECT instead of dropping the connection.
		 */
		static Hello read(final ByteReader r) throws LanProtocolException {
			if (r.i32() != LanProtocol.MAGIC) {
				throw new LanProtocolException("bad magic");
			}
			final Hello h = new Hello();
			h.protocolVersion = r.u16();
			if (h.protocolVersion != LanProtocol.PROTOCOL_VERSION) {
				return h;
			}
			h.engineVersion = r.name(32);
			h.buildCommit = r.name(48);
			h.officialBuild = r.bool();
			h.dataChecksum = r.i64();
			h.playerName = r.name(LanProtocol.MAX_PLAYER_NAME);
			h.portrait = r.name(LanProtocol.MAX_PORTRAIT);
			h.listenPort = r.u16();
			h.controlScheme = r.u8(1);
			h.wantedRole = r.u8(2);
			if (r.bool()) {
				h.rejoin = RejoinData.read(r);
			}
			return h;
		}
	}

	public static final class RejoinData {
		public long gameId;
		public byte[] token = new byte[LanProtocol.TOKEN_BYTES];
		/** Number of turns the client already holds (a pure reconnect passes its last turn + 1). */
		public int haveTurns;

		void write(final ByteWriter w) throws LanProtocolException {
			if (this.token.length != LanProtocol.TOKEN_BYTES) {
				throw new LanProtocolException("bad token length");
			}
			w.i64(this.gameId).bytes(this.token).i32(this.haveTurns);
		}

		static RejoinData read(final ByteReader r) throws LanProtocolException {
			final RejoinData d = new RejoinData();
			d.gameId = r.i64();
			d.token = r.fixed(LanProtocol.TOKEN_BYTES);
			d.haveTurns = r.i32(0, Integer.MAX_VALUE);
			return d;
		}
	}

	public static final class Welcome extends LanMessage {
		public int participantId;
		public byte[] slotToken = new byte[LanProtocol.TOKEN_BYTES];
		public long gameId;
		public int ownerId;
		public int gameState;
		public LobbyState lobby = new LobbyState();

		@Override
		public int type() {
			return LanProtocol.WELCOME;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.slotToken.length != LanProtocol.TOKEN_BYTES) {
				throw new LanProtocolException("bad token length");
			}
			w.u8(this.participantId).bytes(this.slotToken).i64(this.gameId).u8(this.ownerId).u8(this.gameState);
			this.lobby.write(w);
		}

		static Welcome read(final ByteReader r) throws LanProtocolException {
			final Welcome m = new Welcome();
			m.participantId = r.u8();
			m.slotToken = r.fixed(LanProtocol.TOKEN_BYTES);
			m.gameId = r.i64();
			m.ownerId = r.u8();
			m.gameState = r.u8(3);
			m.lobby = LobbyState.read(r);
			return m;
		}
	}

	public static final class Reject extends LanMessage {
		public int reason;
		public String text = "";
		public String hostVersion = "";
		public String redirectHost = "";
		public int redirectPort;

		public Reject() {
		}

		public Reject(final int reason, final String text) {
			this.reason = reason;
			this.text = text;
		}

		@Override
		public int type() {
			return LanProtocol.REJECT;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.reason).string(this.text, 80).string(this.hostVersion, 32).string(this.redirectHost, 64)
					.u16(this.redirectPort);
		}

		static Reject read(final ByteReader r) throws LanProtocolException {
			final Reject m = new Reject();
			m.reason = r.u8(8);
			m.text = r.string(80);
			m.hostVersion = r.name(32);
			m.redirectHost = r.name(64);
			m.redirectPort = r.u16();
			return m;
		}
	}

	public static final class Ping extends LanMessage {
		public final long stamp;

		public Ping(final long stamp) {
			this.stamp = stamp;
		}

		@Override
		public int type() {
			return LanProtocol.PING;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i64(this.stamp);
		}
	}

	public static final class Pong extends LanMessage {
		public final long stamp;

		public Pong(final long stamp) {
			this.stamp = stamp;
		}

		@Override
		public int type() {
			return LanProtocol.PONG;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i64(this.stamp);
		}
	}

	// -------------------------------------------------------------------- lobby

	public static final class PlayerState extends LanMessage {
		/** Ignored when sent by a client; the host fills it in when relaying. */
		public final int participant;
		public final int controlScheme;

		public PlayerState(final int participant, final int controlScheme) {
			this.participant = participant;
			this.controlScheme = controlScheme;
		}

		@Override
		public int type() {
			return LanProtocol.PLAYER_STATE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.participant).u8(this.controlScheme);
		}
	}

	public static final class LobbyState extends LanMessage {
		public int rev;
		public int ownerId = LanProtocol.NO_PARTICIPANT;
		public final List<SlotInfo> slots = new ArrayList<SlotInfo>();
		public final List<ObserverInfo> observers = new ArrayList<ObserverInfo>();
		public final List<LanData.MemberInfo> members = new ArrayList<LanData.MemberInfo>();
		public LanData.Options options = new LanData.Options();
		public LanData.MapInfo map = new LanData.MapInfo();
		public boolean syncWarning;
		/** The profile portrait each participant chose, by participant id; those without one are left out. */
		public final java.util.TreeMap<Integer, String> portraits = new java.util.TreeMap<Integer, String>();

		@Override
		public int type() {
			return LanProtocol.LOBBY_STATE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.rev).u8(this.ownerId).u16(this.slots.size());
			for (final SlotInfo s : this.slots) {
				s.write(w);
			}
			w.u16(this.observers.size());
			for (final ObserverInfo o : this.observers) {
				o.write(w);
			}
			w.u16(this.members.size());
			for (final LanData.MemberInfo o : this.members) {
				o.write(w);
			}
			this.options.write(w);
			this.map.write(w);
			w.bool(this.syncWarning);
			w.u16(this.portraits.size());
			for (final java.util.Map.Entry<Integer, String> e : this.portraits.entrySet()) {
				w.u8(e.getKey()).string(e.getValue(), LanProtocol.MAX_PORTRAIT);
			}
		}

		static LobbyState read(final ByteReader r) throws LanProtocolException {
			final LobbyState m = new LobbyState();
			m.rev = r.i32(0, Integer.MAX_VALUE);
			m.ownerId = r.u8();
			int n = r.count(LanProtocol.MAX_SLOTS, 14);
			for (int i = 0; i < n; i++) {
				m.slots.add(SlotInfo.read(r));
			}
			n = r.count(LanProtocol.MAX_PARTICIPANTS, 7);
			for (int i = 0; i < n; i++) {
				m.observers.add(ObserverInfo.read(r));
			}
			n = r.count(LanProtocol.MAX_PARTICIPANTS, 5);
			for (int i = 0; i < n; i++) {
				m.members.add(LanData.MemberInfo.read(r));
			}
			m.options = LanData.Options.read(r);
			m.map = LanData.MapInfo.read(r);
			m.syncWarning = r.bool();
			n = r.count(LanProtocol.MAX_PARTICIPANTS, 2);
			for (int i = 0; i < n; i++) {
				final int id = r.u8();
				final String portrait = r.name(LanProtocol.MAX_PORTRAIT);
				if (m.portraits.put(id, portrait) != null) {
					throw new LanProtocolException("portrait listed twice");
				}
			}
			return m;
		}
	}

	public static final class SlotRequest extends LanMessage {
		public final int slot;
		public final int race;
		public final int team;
		public final int color;
		public final int handicap;

		public SlotRequest(final int slot, final int race, final int team, final int color, final int handicap) {
			this.slot = slot;
			this.race = race;
			this.team = team;
			this.color = color;
			this.handicap = handicap;
		}

		@Override
		public int type() {
			return LanProtocol.SLOT_REQUEST;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.slot).u8(this.race).u8(this.team).u8(this.color).u8(this.handicap);
		}
	}

	public static final class HostSlot extends LanMessage {
		public final int slot;
		public final int action;
		public final int level;

		public HostSlot(final int slot, final int action, final int level) {
			this.slot = slot;
			this.action = action;
			this.level = level;
		}

		@Override
		public int type() {
			return LanProtocol.HOST_SLOT;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.slot).u8(this.action).u8(this.level);
		}
	}

	/** The host sets the race, team, color and handicap of a computer's slot, as a player sets its own with a request. */
	public static final class HostSetup extends LanMessage {
		public final int slot;
		public final int race;
		public final int team;
		public final int color;
		public final int handicap;

		public HostSetup(final int slot, final int race, final int team, final int color, final int handicap) {
			this.slot = slot;
			this.race = race;
			this.team = team;
			this.color = color;
			this.handicap = handicap;
		}

		@Override
		public int type() {
			return LanProtocol.HOST_SETUP;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.slot).u8(this.race).u8(this.team).u8(this.color).u8(this.handicap);
		}
	}

	public static final class HostOptions extends LanMessage {
		public final LanData.Options options;

		public HostOptions(final LanData.Options options) {
			this.options = options;
		}

		@Override
		public int type() {
			return LanProtocol.HOST_OPTIONS;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			this.options.write(w);
		}
	}

	public static final class Ready extends LanMessage {
		public final boolean ready;

		public Ready(final boolean ready) {
			this.ready = ready;
		}

		@Override
		public int type() {
			return LanProtocol.READY;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.bool(this.ready);
		}
	}

	public static final class Chat extends LanMessage {
		public final int channel;
		/** Ignored when sent by a client. */
		public final int from;
		public final String text;

		public Chat(final int channel, final int from, final String text) {
			this.channel = channel;
			this.from = from;
			this.text = text;
		}

		@Override
		public int type() {
			return LanProtocol.CHAT;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.channel).u8(this.from).string(this.text, LanProtocol.MAX_CHAT);
		}
	}

	public static final class MapRequest extends LanMessage {
		public final int offset;

		public MapRequest(final int offset) {
			this.offset = offset;
		}

		@Override
		public int type() {
			return LanProtocol.MAP_REQUEST;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.offset);
		}
	}

	public static final class MapChunk extends LanMessage {
		public final int offset;
		public final int total;
		public final byte[] data;

		public MapChunk(final int offset, final int total, final byte[] data) {
			this.offset = offset;
			this.total = total;
			this.data = data;
		}

		@Override
		public int type() {
			return LanProtocol.MAP_CHUNK;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.data.length > LanProtocol.MAX_MAP_CHUNK) {
				throw new LanProtocolException("map chunk too long");
			}
			w.i32(this.offset).i32(this.total).bytes(this.data);
		}

		@Override
		public int sizeHint() {
			return 10 + this.data.length;
		}
	}

	public static final class MapStatus extends LanMessage {
		public final int status;
		public final int percent;

		public MapStatus(final int status, final int percent) {
			this.status = status;
			this.percent = percent;
		}

		@Override
		public int type() {
			return LanProtocol.MAP_STATUS;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.status).u8(this.percent);
		}
	}

	public static final class StartRequest extends Empty {
		@Override
		public int type() {
			return LanProtocol.START_REQUEST;
		}
	}

	public static final class Countdown extends LanMessage {
		public final int seconds;

		public Countdown(final int seconds) {
			this.seconds = seconds;
		}

		@Override
		public int type() {
			return LanProtocol.COUNTDOWN;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.seconds);
		}
	}

	public static final class CountdownCancel extends Empty {
		@Override
		public int type() {
			return LanProtocol.COUNTDOWN_CANCEL;
		}
	}

	public static final class Start extends LanMessage {
		public final LanGameSetup setup;

		public Start(final LanGameSetup setup) {
			this.setup = setup;
		}

		@Override
		public int type() {
			return LanProtocol.START;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			this.setup.write(w);
		}
	}

	public static final class LoadProgress extends LanMessage {
		/** Ignored when sent by a client; the host fills it in when relaying. */
		public final int participant;
		public final int percent;

		public LoadProgress(final int participant, final int percent) {
			this.participant = participant;
			this.percent = percent;
		}

		@Override
		public int type() {
			return LanProtocol.LOAD_PROGRESS;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.participant).u8(this.percent);
		}
	}

	public static final class Loaded extends Empty {
		@Override
		public int type() {
			return LanProtocol.LOADED;
		}
	}

	public static final class Go extends Empty {
		@Override
		public int type() {
			return LanProtocol.GO;
		}
	}

	public static final class Leave extends LanMessage {
		public final int reason;

		public Leave(final int reason) {
			this.reason = reason;
		}

		@Override
		public int type() {
			return LanProtocol.LEAVE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.reason);
		}
	}

	public static final class Kicked extends LanMessage {
		public final String reason;

		public Kicked(final String reason) {
			this.reason = reason;
		}

		@Override
		public int type() {
			return LanProtocol.KICKED;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.string(this.reason, 80);
		}
	}

	// --------------------------------------------------------------------- game

	public static final class Cmd extends LanMessage {
		public final int seq;
		public final byte[] bytes;

		public Cmd(final int seq, final byte[] bytes) {
			this.seq = seq;
			this.bytes = bytes;
		}

		@Override
		public int type() {
			return LanProtocol.CMD;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.bytes.length > LanProtocol.MAX_COMMAND_BYTES) {
				throw new LanProtocolException("command too long");
			}
			w.i32(this.seq).bytes(this.bytes);
		}
	}

	public static final class TurnDone extends LanMessage {
		public final int turn;

		public TurnDone(final int turn) {
			this.turn = turn;
		}

		@Override
		public int type() {
			return LanProtocol.TURN_DONE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.turn);
		}
	}

	public static final class Checksum extends LanMessage {
		public final int tick;
		public final int crc;

		public Checksum(final int tick, final int crc) {
			this.tick = tick;
			this.crc = crc;
		}

		@Override
		public int type() {
			return LanProtocol.CHECKSUM;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.tick).i32(this.crc);
		}
	}

	public static final class AppState extends LanMessage {
		public final int state;

		public AppState(final int state) {
			this.state = state;
		}

		@Override
		public int type() {
			return LanProtocol.STATE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.state);
		}
	}

	public static final class Vote extends LanMessage {
		public final int vote;

		public Vote(final int vote) {
			this.vote = vote;
		}

		@Override
		public int type() {
			return LanProtocol.VOTE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.vote);
		}
	}

	public static final class Turn extends LanMessage {
		public final TurnData turn;

		public Turn(final TurnData turn) {
			this.turn = turn;
		}

		@Override
		public int type() {
			return LanProtocol.TURN;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			this.turn.write(w);
		}
	}

	public static final class Lag extends LanMessage {
		public final List<LagEntry> entries;

		public Lag(final List<LagEntry> entries) {
			this.entries = entries;
		}

		@Override
		public int type() {
			return LanProtocol.LAG;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u16(this.entries.size());
			for (final LagEntry e : this.entries) {
				w.u8(e.participant).u8(e.reason).i32(e.waitedMs);
			}
		}
	}

	public static final class LagEnd extends Empty {
		@Override
		public int type() {
			return LanProtocol.LAG_END;
		}
	}

	public static final class Presence extends LanMessage {
		public final int participant;
		public final int state;

		public Presence(final int participant, final int state) {
			this.participant = participant;
			this.state = state;
		}

		@Override
		public int type() {
			return LanProtocol.PRESENCE;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.participant).u8(this.state);
		}
	}

	public static final class Desync extends LanMessage {
		public final int tick;
		public final List<ChecksumGroup> groups;

		public Desync(final int tick, final List<ChecksumGroup> groups) {
			this.tick = tick;
			this.groups = groups;
		}

		@Override
		public int type() {
			return LanProtocol.DESYNC;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.tick).u16(this.groups.size());
			for (final ChecksumGroup g : this.groups) {
				w.i32(g.crc).u16(g.participants.length);
				for (final int p : g.participants) {
					w.u8(p);
				}
			}
		}
	}

	public static final class Resync extends LanMessage {
		public final int kind;
		public final int tick;

		public Resync(final int kind, final int tick) {
			this.kind = kind;
			this.tick = tick;
		}

		@Override
		public int type() {
			return LanProtocol.RESYNC;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.kind).i32(this.tick);
		}
	}

	public static final class HostChanged extends LanMessage {
		public final int newHost;
		public final int firstTurn;

		public HostChanged(final int newHost, final int firstTurn) {
			this.newHost = newHost;
			this.firstTurn = firstTurn;
		}

		@Override
		public int type() {
			return LanProtocol.HOST_CHANGED;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.newHost).i32(this.firstTurn);
		}
	}

	public static final class GameEnd extends LanMessage {
		public final int reason;

		public GameEnd(final int reason) {
			this.reason = reason;
		}

		@Override
		public int type() {
			return LanProtocol.GAME_END;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u8(this.reason);
		}
	}

	// ------------------------------------------------------------------- rejoin

	public static final class Rejoin extends LanMessage {
		public final LanGameSetup setup;
		public final int turnsFrom;
		public final int totalTurns;
		/** The last command number the host holds for the seat: a client that started over numbers on from it. */
		public final int lastSeq;
		/** The simulation steps of those {@code totalTurns} turns: how far the game has to be played through. */
		public final int totalSteps;

		public Rejoin(final LanGameSetup setup, final int turnsFrom, final int totalTurns, final int lastSeq,
				final int totalSteps) {
			this.setup = setup;
			this.turnsFrom = turnsFrom;
			this.totalTurns = totalTurns;
			this.lastSeq = lastSeq;
			this.totalSteps = totalSteps;
		}

		@Override
		public int type() {
			return LanProtocol.REJOIN;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			this.setup.write(w);
			w.i32(this.turnsFrom).i32(this.totalTurns).i32(this.lastSeq).i32(this.totalSteps);
		}
	}

	public static final class CaughtUp extends LanMessage {
		public final int turn;
		public final int crc;

		public CaughtUp(final int turn, final int crc) {
			this.turn = turn;
			this.crc = crc;
		}

		@Override
		public int type() {
			return LanProtocol.CAUGHT_UP;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.turn).i32(this.crc);
		}
	}

	/**
	 * A run of consecutive turns, compressed together: the way a rejoining client is sent the game so far. The turns
	 * are written one after another and deflated; a block never inflates to more than {@link LanProtocol#MAX_BLOCK_RAW}.
	 */
	public static final class TurnBlock extends LanMessage {
		public final List<TurnData> turns;
		private final int rawLength;
		private final byte[] packed;

		private TurnBlock(final List<TurnData> turns, final int rawLength, final byte[] packed) {
			this.turns = turns;
			this.rawLength = rawLength;
			this.packed = packed;
		}

		/** The block of these consecutive turns, or null when they do not fit a frame compressed. */
		public static TurnBlock of(final List<TurnData> turns) throws LanProtocolException {
			if (turns.isEmpty() || turns.size() > LanProtocol.MAX_BLOCK_TURNS) {
				return null;
			}
			final ByteWriter raw = new ByteWriter(LanProtocol.MAX_BLOCK_RAW);
			for (final TurnData t : turns) {
				t.write(raw);
			}
			final java.util.zip.Deflater d = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
			try {
				d.setInput(raw.toByteArray());
				d.finish();
				final byte[] out = new byte[LanProtocol.MAX_BLOCK_PACKED + 1];
				int n = 0;
				while (!d.finished() && n < out.length) {
					n += d.deflate(out, n, out.length - n);
				}
				if (!d.finished() || n > LanProtocol.MAX_BLOCK_PACKED) {
					return null;
				}
				return new TurnBlock(turns, raw.size(), java.util.Arrays.copyOf(out, n));
			}
			finally {
				d.end();
			}
		}

		/** Bytes this block takes on the wire. */
		public int packedLength() {
			return this.packed.length;
		}

		@Override
		public int type() {
			return LanProtocol.TURN_BLOCK;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u16(this.turns.size()).i32(this.rawLength).bytes(this.packed);
		}

		@Override
		public int sizeHint() {
			return 8 + this.packed.length;
		}

		static TurnBlock read(final ByteReader r) throws LanProtocolException {
			final int count = r.u16();
			final int rawLen = r.i32(0, LanProtocol.MAX_BLOCK_RAW);
			final byte[] packed = r.bytes(LanProtocol.MAX_BLOCK_PACKED);
			if (count == 0 || count > LanProtocol.MAX_BLOCK_TURNS || (long) count * 9 > rawLen) {
				throw new LanProtocolException("bad turn block");
			}
			final byte[] raw = new byte[rawLen];
			final java.util.zip.Inflater inf = new java.util.zip.Inflater();
			try {
				inf.setInput(packed);
				int n = 0;
				while (n < rawLen && !inf.finished()) {
					final int got = inf.inflate(raw, n, rawLen - n);
					if (got == 0 && (inf.needsInput() || inf.needsDictionary())) {
						break;
					}
					n += got;
				}
				// The data has to end exactly where the header says: more or less is a damaged or hostile block.
				if (n != rawLen || !inf.finished()) {
					throw new LanProtocolException("turn block does not match its size");
				}
			}
			catch (final java.util.zip.DataFormatException e) {
				throw new LanProtocolException("turn block damaged: " + e.getMessage());
			}
			finally {
				inf.end();
			}
			final ByteReader in = new ByteReader(raw);
			final List<TurnData> turns = new ArrayList<TurnData>(Math.min(count, 256));
			for (int i = 0; i < count; i++) {
				final TurnData t = TurnData.read(in);
				if (!turns.isEmpty() && t.n != turns.get(turns.size() - 1).n + 1) {
					throw new LanProtocolException("turn block is not consecutive");
				}
				turns.add(t);
			}
			in.expectEnd();
			return new TurnBlock(turns, rawLen, packed);
		}
	}

	// ---------------------------------------------------------------- migration

	public static final class MigrateHello extends LanMessage {
		public final int participantId;
		public final byte[] token;
		public final int lastTurn;
		public final List<CmdEntry> pending;
		/** The port this participant listens on, for the case it is the next one to take the game over. */
		public final int listenPort;

		public MigrateHello(final int participantId, final byte[] token, final int lastTurn,
				final List<CmdEntry> pending) {
			this(participantId, token, lastTurn, pending, 0);
		}

		public MigrateHello(final int participantId, final byte[] token, final int lastTurn,
				final List<CmdEntry> pending, final int listenPort) {
			this.participantId = participantId;
			this.token = token;
			this.lastTurn = lastTurn;
			this.pending = pending;
			this.listenPort = listenPort;
		}

		@Override
		public int type() {
			return LanProtocol.MIGRATE_HELLO;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			if (this.token.length != LanProtocol.TOKEN_BYTES) {
				throw new LanProtocolException("bad token length");
			}
			w.u8(this.participantId).bytes(this.token).i32(this.lastTurn).u16(this.listenPort)
					.u16(this.pending.size());
			for (final CmdEntry c : this.pending) {
				c.write(w);
			}
		}
	}

	public static final class MigrateWelcome extends LanMessage {
		public final int firstTurn;

		public MigrateWelcome(final int firstTurn) {
			this.firstTurn = firstTurn;
		}

		@Override
		public int type() {
			return LanProtocol.MIGRATE_WELCOME;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.firstTurn);
		}
	}

	public static final class Redirect extends LanMessage {
		public final String host;
		public final int port;

		public Redirect(final String host, final int port) {
			this.host = host;
			this.port = port;
		}

		@Override
		public int type() {
			return LanProtocol.REDIRECT;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.string(this.host, 64).u16(this.port);
		}
	}

	public static final class TurnFillRequest extends LanMessage {
		public final int from;
		public final int to;

		public TurnFillRequest(final int from, final int to) {
			this.from = from;
			this.to = to;
		}

		@Override
		public int type() {
			return LanProtocol.TURN_FILL_REQUEST;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.i32(this.from).i32(this.to);
		}
	}

	public static final class TurnFill extends LanMessage {
		public final List<TurnData> turns;

		public TurnFill(final List<TurnData> turns) {
			this.turns = turns;
		}

		@Override
		public int type() {
			return LanProtocol.TURN_FILL;
		}

		@Override
		public void write(final ByteWriter w) throws LanProtocolException {
			w.u16(this.turns.size());
			for (final TurnData t : this.turns) {
				t.write(w);
			}
		}
	}

	// ----------------------------------------------------------------- decoding

	/** Decodes one body of the given type; the reader must be fully consumed afterwards. */
	static LanMessage read(final int type, final ByteReader r) throws LanProtocolException {
		switch (type) {
		case LanProtocol.HELLO: {
			final Hello h = Hello.read(r);
			if (h.protocolVersion == LanProtocol.PROTOCOL_VERSION) {
				r.expectEnd();
			}
			else {
				r.skipRest();
			}
			return h;
		}
		case LanProtocol.WELCOME:
			return end(Welcome.read(r), r);
		case LanProtocol.REJECT:
			return end(Reject.read(r), r);
		case LanProtocol.PING:
			return end(new Ping(r.i64()), r);
		case LanProtocol.PONG:
			return end(new Pong(r.i64()), r);
		case LanProtocol.PLAYER_STATE:
			return end(new PlayerState(r.u8(), r.u8(1)), r);
		case LanProtocol.LOBBY_STATE:
			return end(LobbyState.read(r), r);
		case LanProtocol.SLOT_REQUEST:
			return end(new SlotRequest(r.u8(), r.u8(), r.u8(), r.u8(), r.u8(100)), r);
		case LanProtocol.HOST_SLOT:
			return end(new HostSlot(r.u8(), r.u8(4), r.u8(2)), r);
		case LanProtocol.HOST_SETUP:
			return end(new HostSetup(r.u8(), r.u8(), r.u8(), r.u8(), r.u8(100)), r);
		case LanProtocol.HOST_OPTIONS:
			return end(new HostOptions(LanData.Options.read(r)), r);
		case LanProtocol.READY:
			return end(new Ready(r.bool()), r);
		case LanProtocol.CHAT:
			return end(new Chat(r.u8(2), r.u8(), r.string(LanProtocol.MAX_CHAT)), r);
		case LanProtocol.MAP_REQUEST:
			return end(new MapRequest(r.i32(0, LanData.MapInfo.MAX_SIZE)), r);
		case LanProtocol.MAP_CHUNK: {
			final int offset = r.i32(0, LanData.MapInfo.MAX_SIZE);
			final int total = r.i32(0, LanData.MapInfo.MAX_SIZE);
			return end(new MapChunk(offset, total, r.bytes(LanProtocol.MAX_MAP_CHUNK)), r);
		}
		case LanProtocol.MAP_STATUS:
			return end(new MapStatus(r.u8(2), r.u8(100)), r);
		case LanProtocol.START_REQUEST:
			return end(new StartRequest(), r);
		case LanProtocol.COUNTDOWN:
			return end(new Countdown(r.u8()), r);
		case LanProtocol.COUNTDOWN_CANCEL:
			return end(new CountdownCancel(), r);
		case LanProtocol.START:
			return end(new Start(LanGameSetup.read(r)), r);
		case LanProtocol.LOAD_PROGRESS:
			return end(new LoadProgress(r.u8(), r.u8(100)), r);
		case LanProtocol.LOADED:
			return end(new Loaded(), r);
		case LanProtocol.GO:
			return end(new Go(), r);
		case LanProtocol.LEAVE:
			return end(new Leave(r.u8(1)), r);
		case LanProtocol.KICKED:
			return end(new Kicked(r.string(80)), r);
		case LanProtocol.CMD:
			return end(new Cmd(r.i32(1, Integer.MAX_VALUE), r.bytes(LanProtocol.MAX_COMMAND_BYTES)), r);
		case LanProtocol.TURN_DONE:
			return end(new TurnDone(r.i32(0, Integer.MAX_VALUE)), r);
		case LanProtocol.CHECKSUM:
			return end(new Checksum(r.i32(0, Integer.MAX_VALUE), r.i32()), r);
		case LanProtocol.STATE:
			return end(new AppState(r.u8(3)), r);
		case LanProtocol.VOTE:
			return end(new Vote(r.u8(1)), r);
		case LanProtocol.TURN:
			return end(new Turn(TurnData.read(r)), r);
		case LanProtocol.LAG: {
			final int n = r.count(LanProtocol.MAX_PARTICIPANTS, 6);
			final List<LagEntry> list = new ArrayList<LagEntry>(n);
			for (int i = 0; i < n; i++) {
				list.add(new LagEntry(r.u8(), r.u8(3), r.i32(0, Integer.MAX_VALUE)));
			}
			return end(new Lag(list), r);
		}
		case LanProtocol.LAG_END:
			return end(new LagEnd(), r);
		case LanProtocol.PRESENCE:
			return end(new Presence(r.u8(), r.u8(3)), r);
		case LanProtocol.DESYNC: {
			final int tick = r.i32(0, Integer.MAX_VALUE);
			final int n = r.count(LanProtocol.MAX_PARTICIPANTS, 6);
			final List<ChecksumGroup> groups = new ArrayList<ChecksumGroup>(n);
			for (int i = 0; i < n; i++) {
				final int crc = r.i32();
				final int pc = r.count(LanProtocol.MAX_PARTICIPANTS, 1);
				final int[] ids = new int[pc];
				for (int j = 0; j < pc; j++) {
					ids[j] = r.u8();
				}
				groups.add(new ChecksumGroup(crc, ids));
			}
			return end(new Desync(tick, groups), r);
		}
		case LanProtocol.HOST_CHANGED:
			return end(new HostChanged(r.u8(), r.i32(0, Integer.MAX_VALUE)), r);
		case LanProtocol.GAME_END:
			return end(new GameEnd(r.u8()), r);
		case LanProtocol.RESYNC:
			return end(new Resync(r.u8(3), r.i32(-1, Integer.MAX_VALUE)), r);
		case LanProtocol.REJOIN: {
			final LanGameSetup setup = LanGameSetup.read(r);
			return end(new Rejoin(setup, r.i32(0, Integer.MAX_VALUE), r.i32(0, Integer.MAX_VALUE),
					r.i32(0, Integer.MAX_VALUE), r.i32(0, Integer.MAX_VALUE)), r);
		}
		case LanProtocol.CAUGHT_UP:
			return end(new CaughtUp(r.i32(0, Integer.MAX_VALUE), r.i32()), r);
		case LanProtocol.TURN_BLOCK:
			return end(TurnBlock.read(r), r);
		case LanProtocol.MIGRATE_HELLO: {
			final int id = r.u8();
			final byte[] token = r.fixed(LanProtocol.TOKEN_BYTES);
			final int lastTurn = r.i32(-1, Integer.MAX_VALUE);
			final int listenPort = r.u16();
			final int n = r.count(LanProtocol.MAX_COMMANDS_PER_TURN * 64, 7);
			final List<CmdEntry> pending = new ArrayList<CmdEntry>(Math.min(n, 64));
			for (int i = 0; i < n; i++) {
				pending.add(CmdEntry.read(r));
			}
			return end(new MigrateHello(id, token, lastTurn, pending, listenPort), r);
		}
		case LanProtocol.MIGRATE_WELCOME:
			return end(new MigrateWelcome(r.i32(0, Integer.MAX_VALUE)), r);
		case LanProtocol.REDIRECT:
			return end(new Redirect(r.name(64), r.u16()), r);
		case LanProtocol.TURN_FILL_REQUEST:
			return end(new TurnFillRequest(r.i32(0, Integer.MAX_VALUE), r.i32(0, Integer.MAX_VALUE)), r);
		case LanProtocol.TURN_FILL: {
			final int n = r.count(4096, 9);
			final List<TurnData> turns = new ArrayList<TurnData>(Math.min(n, 64));
			for (int i = 0; i < n; i++) {
				turns.add(TurnData.read(r));
			}
			return end(new TurnFill(turns), r);
		}
		default:
			throw new LanProtocolException("unknown message type " + type);
		}
	}

	private static LanMessage end(final LanMessage m, final ByteReader r) throws LanProtocolException {
		r.expectEnd();
		return m;
	}
}
