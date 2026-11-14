package net.warsmash.lan.protocol;

public final class LanProtocol {
	private LanProtocol() {
	}

	public static final int MAGIC = 0x5733414C; // ASCII W3AL
	public static final int PROTOCOL_VERSION = 2;
	public static final int DEFAULT_PORT = 6112;
	public static final int PORT_TRIES = 8;

	public static final int FRAME_HEADER = 5;
	public static final int MAX_FRAME_BODY = 64 * 1024;
	public static final int MAX_DATAGRAM = 1200;

	public static final int MAX_PLAYER_NAME = 15;
	public static final int MAX_GAME_NAME = 31;
	public static final int MAX_CHAT = 255;
	public static final int MAX_SLOTS = 24;
	public static final int MAX_PARTICIPANTS = 24;
	public static final int MAX_CONNECTIONS = MAX_PARTICIPANTS + 8;
	public static final int MAX_COMMAND_BYTES = 2048;
	public static final int MAX_COMMANDS_PER_TURN = 64;
	public static final int MAX_COMMAND_BYTES_PER_TURN = 8 * 1024;
	public static final int MAX_TURN_BODY = 48 * 1024;
	public static final int MAX_MAP_CHUNK = 16 * 1024;
	public static final int MAX_HEADER_EXTRA = 1024;
	public static final int TOKEN_BYTES = 16;
	public static final int SHA256_BYTES = 32;
	public static final int MAX_OUT_QUEUE_BYTES = 16 * 1024 * 1024;
	/** Turns in one TURN_BLOCK, and the bytes they take before compression. */
	public static final int MAX_BLOCK_TURNS = 1024;
	public static final int MAX_BLOCK_RAW = 1024 * 1024;
	/** The compressed part of a TURN_BLOCK has to fit a frame with its small header. */
	public static final int MAX_BLOCK_PACKED = 60000;

	public static final int NO_PARTICIPANT = 255;
	public static final int NO_SLOT = 255;

	// message types
	public static final int HELLO = 1;
	public static final int WELCOME = 2;
	public static final int REJECT = 3;
	public static final int PING = 4;
	public static final int PONG = 5;
	public static final int PLAYER_STATE = 10;
	public static final int LOBBY_STATE = 11;
	public static final int SLOT_REQUEST = 12;
	public static final int HOST_SLOT = 13;
	public static final int HOST_OPTIONS = 14;
	public static final int READY = 15;
	public static final int CHAT = 16;
	public static final int MAP_REQUEST = 17;
	public static final int MAP_CHUNK = 18;
	public static final int MAP_STATUS = 19;
	public static final int START_REQUEST = 20;
	public static final int COUNTDOWN = 21;
	public static final int COUNTDOWN_CANCEL = 22;
	public static final int START = 23;
	public static final int LOAD_PROGRESS = 24;
	public static final int LOADED = 25;
	public static final int GO = 26;
	public static final int LEAVE = 27;
	public static final int KICKED = 28;
	public static final int HOST_SETUP = 29;
	public static final int CMD = 40;
	public static final int TURN_DONE = 41;
	public static final int CHECKSUM = 42;
	public static final int STATE = 43;
	public static final int VOTE = 44;
	public static final int TURN = 45;
	public static final int LAG = 46;
	public static final int LAG_END = 47;
	public static final int PRESENCE = 48;
	public static final int DESYNC = 49;
	public static final int HOST_CHANGED = 50;
	public static final int GAME_END = 51;
	/** Host to a participant: its game differs from the others' and is to be played again; the participant answers with an ACK. */
	public static final int RESYNC = 52;
	public static final int REJOIN = 60;
	public static final int CAUGHT_UP = 61;
	public static final int TURN_BLOCK = 62;
	public static final int MIGRATE_HELLO = 70;
	public static final int MIGRATE_WELCOME = 71;
	public static final int REDIRECT = 72;
	public static final int TURN_FILL_REQUEST = 73;
	public static final int TURN_FILL = 74;

	// datagram types
	public static final int UDP_SEARCH = 1;
	public static final int UDP_GAME_INFO = 2;
	public static final int UDP_GAME_GONE = 3;

	// reject reasons
	public static final int REJECT_VERSION = 1;
	public static final int REJECT_DATA = 2;
	public static final int REJECT_FULL = 3;
	public static final int REJECT_STARTED = 4;
	public static final int REJECT_KICKED = 5;
	public static final int REJECT_BAD_NAME = 6;
	public static final int REJECT_NOT_HOST = 7;
	public static final int REJECT_BAD_HELLO = 8;

	// RESYNC kinds
	/** The participant's sums are not the majority's at that step. */
	public static final int RESYNC_DESYNC = 0;
	/** The participant holds turns the game does not have, or lacks turns it needs. */
	public static final int RESYNC_TURNS = 1;
	/** The participant cannot stay in sync with this game; it is absent from now on. */
	public static final int RESYNC_GIVE_UP = 2;
	/** The participant dropped its game and its sums since; sent by it. */
	public static final int RESYNC_ACK = 3;

	// game state
	public static final int STATE_LOBBY = 0;
	public static final int STATE_LOADING = 1;
	public static final int STATE_IN_GAME = 2;
	public static final int STATE_ENDED = 3;

	// roles
	public static final int ROLE_PLAYER = 0;
	public static final int ROLE_OBSERVER = 1;
	public static final int ROLE_REFEREE = 2;

	/**
	 * What turns name as the slot of a referee's command: this plus its place in the start's participants. Real slots
	 * stay below it.
	 */
	public static final int WATCHER_SLOT_BASE = 32;

	/**
	 * The place of a participant in the order the game's hosts follow: players by seat, then the referees, then the
	 * observers, each by seat or, without one, by id.
	 */
	public static int hostKey(final int role, final int slot, final int id) {
		final int rank = role == ROLE_PLAYER ? 0 : role == ROLE_REFEREE ? 1 : 2;
		return (rank * 10000) + (slot == NO_SLOT ? 1000 + id : slot);
	}

	// slot kinds
	public static final int SLOT_OPEN = 0;
	public static final int SLOT_CLOSED = 1;
	public static final int SLOT_HUMAN = 2;
	public static final int SLOT_COMPUTER = 3;

	// SLOT_REQUEST targets besides real slot indices
	public static final int REQUEST_OBSERVER = 254;
	public static final int REQUEST_REFEREE = 253;

	// HOST_SLOT actions
	public static final int ACTION_OPEN = 0;
	public static final int ACTION_CLOSE = 1;
	public static final int ACTION_COMPUTER = 2;
	public static final int ACTION_KICK = 3;
	/** Kicks a watcher; the message's slot is then the watcher's participant id. */
	public static final int ACTION_KICK_WATCHER = 4;

	public static final int CONTROL_TOUCH = 0;
	public static final int CONTROL_KBM = 1;

	public static final int OBSERVERS_NONE = 0;
	public static final int OBSERVERS_OBSERVERS = 1;
	public static final int OBSERVERS_REFEREES = 2;

	public static final int CHAT_ALL = 0;
	public static final int CHAT_ALLIES = 1;
	public static final int CHAT_OBSERVERS = 2;

	public static final int PRESENCE_PLAYING = 0;
	public static final int PRESENCE_ABSENT = 1;
	public static final int PRESENCE_REJOINING = 2;
	public static final int PRESENCE_LEFT = 3;

	public static final int LAG_LAGGING = 0;
	public static final int LAG_BACKGROUND = 1;
	public static final int LAG_RECONNECTING = 2;
	public static final int LAG_MIGRATING = 3;

	public static final int APP_FOREGROUND = 0;
	public static final int APP_BACKGROUND = 1;
	/**
	 * Reported in STATE by every player's game when its simulation stands still (a player's pause, a script's
	 * PauseGame) and when it runs again; the host sends turns without steps while any game stands.
	 */
	public static final int SIM_PAUSED = 2;
	public static final int SIM_RESUMED = 3;

	public static final int VOTE_CONTINUE_WITHOUT = 0;
	public static final int VOTE_CLAIM_VICTORY = 1;

	public static final int LEAVE_NORMAL = 0;
	public static final int LEAVE_QUIT = 1;

	public static final int MAP_STATUS_HAVE = 0;
	public static final int MAP_STATUS_DOWNLOADING = 1;
	public static final int MAP_STATUS_FAILED = 2;
}
