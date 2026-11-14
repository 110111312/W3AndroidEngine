package net.warsmash.lan.protocol;

/** Tunable time limits; defaults are the protocol values, tests shrink them. */
public final class LanTimings {
	public volatile int connectTimeoutMs = 5000;
	public volatile int helloTimeoutMs = 5000;
	public volatile int keepaliveMs = 1000;
	public volatile int silenceMs = 5000;
	public volatile int loadTimeoutMs = 180000;
	public volatile int lagAfterMs = 2000;
	public volatile int absentAfterMs = 60000;
	/** How long the others have waited for someone before "Continue without them" is allowed. */
	public volatile int continueAfterMs = 30000;
	/** How long the opponents have waited before "Claim victory" is allowed. */
	public volatile int claimAfterMs = 60000;
	/** How long a client that lost its link in a running game keeps trying to come back before it gives the game up. */
	public volatile int reconnectMs = 30000;
	public volatile int reconnectRetryMs = 1000;
	/** How long a lost host is tried for again before the game moves to a new host (at once when nothing listens). */
	public volatile int hostGoneMs = 5000;
	/** How long a new host waits for the others to come to it, and how long a candidate is given to answer. */
	public volatile int migrateWaitMs = 5000;
	/** How long a game without a host looks for one before it gives up. */
	public volatile int migrateGiveUpMs = 60000;
	public volatile int announceMs = 2000;
	public volatile int gameExpiryMs = 6000;
}
