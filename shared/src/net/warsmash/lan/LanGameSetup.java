package net.warsmash.lan;

import java.util.ArrayList;
import java.util.List;

import net.warsmash.lan.protocol.ByteReader;
import net.warsmash.lan.protocol.ByteWriter;
import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanProtocol;
import net.warsmash.lan.protocol.LanProtocolException;

/** Everything every participant needs to start (or rejoin) the same game: the START message content. */
public final class LanGameSetup {
	public long gameId;
	public long seed;
	/** Simulation steps per turn. */
	public int turnSteps = 2;
	/** A turn is released only if every active participant acknowledged at least turn - syncLimit. */
	public int syncLimit = 10;
	public LanData.Options options = new LanData.Options();
	public LanData.MapInfo map = new LanData.MapInfo();
	public final List<LanData.SlotInfo> slots = new ArrayList<LanData.SlotInfo>();
	public final List<LanData.ParticipantInfo> participants = new ArrayList<LanData.ParticipantInfo>();
	/** Candidate order for host migration: participant ids, players first. */
	public int[] hostOrder = new int[0];
	/** Opaque replay header fields filled in by the engine. */
	public byte[] header = new byte[0];

	public void write(final ByteWriter w) throws LanProtocolException {
		w.i64(this.gameId).i64(this.seed).u8(this.turnSteps).u8(this.syncLimit);
		this.options.write(w);
		this.map.write(w);
		w.u16(this.slots.size());
		for (final LanData.SlotInfo s : this.slots) {
			s.write(w);
		}
		w.u16(this.participants.size());
		for (final LanData.ParticipantInfo p : this.participants) {
			p.write(w);
		}
		w.u16(this.hostOrder.length);
		for (final int id : this.hostOrder) {
			w.u8(id);
		}
		if (this.header.length > LanProtocol.MAX_HEADER_EXTRA) {
			throw new LanProtocolException("header too long");
		}
		w.bytes(this.header);
	}

	public static LanGameSetup read(final ByteReader r) throws LanProtocolException {
		final LanGameSetup g = new LanGameSetup();
		g.gameId = r.i64();
		g.seed = r.i64();
		g.turnSteps = r.u8();
		g.syncLimit = r.u8();
		if (g.turnSteps < 1 || g.syncLimit < 1) {
			throw new LanProtocolException("bad turn parameters");
		}
		g.options = LanData.Options.read(r);
		g.map = LanData.MapInfo.read(r);
		int n = r.count(LanProtocol.MAX_SLOTS, 14);
		for (int i = 0; i < n; i++) {
			g.slots.add(LanData.SlotInfo.read(r));
		}
		n = r.count(LanProtocol.MAX_PARTICIPANTS, 8);
		for (int i = 0; i < n; i++) {
			g.participants.add(LanData.ParticipantInfo.read(r));
		}
		n = r.count(LanProtocol.MAX_PARTICIPANTS, 1);
		g.hostOrder = new int[n];
		for (int i = 0; i < n; i++) {
			g.hostOrder[i] = r.u8();
		}
		g.header = r.bytes(LanProtocol.MAX_HEADER_EXTRA);
		return g;
	}

	public LanData.ParticipantInfo participant(final int id) {
		for (final LanData.ParticipantInfo p : this.participants) {
			if (p.id == id) {
				return p;
			}
		}
		return null;
	}

	/**
	 * The slot the turns name for what the participant sends: its seat for a player, for a referee a number above
	 * every seat (its place in the participants), and {@link LanProtocol#NO_SLOT} for anyone who sends nothing.
	 */
	public int commandSlot(final int id) {
		for (int i = 0; i < this.participants.size(); i++) {
			final LanData.ParticipantInfo p = this.participants.get(i);
			if (p.id != id) {
				continue;
			}
			if (p.role == LanProtocol.ROLE_PLAYER) {
				return p.slot;
			}
			return p.role == LanProtocol.ROLE_REFEREE ? LanProtocol.WATCHER_SLOT_BASE + i : LanProtocol.NO_SLOT;
		}
		return LanProtocol.NO_SLOT;
	}

	/** The participant whose commands the turns name with this slot, or null. */
	public LanData.ParticipantInfo commandSender(final int slot) {
		if (slot >= LanProtocol.WATCHER_SLOT_BASE) {
			final int i = slot - LanProtocol.WATCHER_SLOT_BASE;
			return i < this.participants.size() ? this.participants.get(i) : null;
		}
		for (final LanData.ParticipantInfo p : this.participants) {
			if (p.role == LanProtocol.ROLE_PLAYER && p.slot == slot) {
				return p;
			}
		}
		return null;
	}
}
