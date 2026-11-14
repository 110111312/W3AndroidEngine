package net.warsmash.lan;

/** Where a device keeps the keys of the games it can come back to; they have to outlive the app. */
public interface RejoinStore {
	/** The key of that game, or null. */
	RejoinKey load(long gameId);

	void save(RejoinKey key);

	void clear(long gameId);

	/** Every key held, for looking for those games without a search. */
	default java.util.List<RejoinKey> all() {
		return java.util.Collections.emptyList();
	}
}
