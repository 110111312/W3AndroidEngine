package com.etheller.warsmash.launcher.lan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.etheller.warsmash.launcher.ButtonFlash;

import net.warsmash.lan.protocol.LanData;
import net.warsmash.lan.protocol.LanMessages;
import net.warsmash.lan.protocol.LanProtocol;

/**
 * Tells the launcher's LAN button when the lobby changed while another page was shown: the button flashes like the
 * game's Quests button until the LAN page is opened. Chat only puts a still dot on it; a changed ready mark or ping
 * of someone else is not worth a look.
 */
public final class LanAttention {
	/** What changed in the lobby. */
	public enum Event {
		JOINED(true),
		LEFT(true),
		HOST_CHANGED(true),
		MAP_CHANGED(true),
		OPTIONS_CHANGED(true),
		SLOTS_CHANGED(true),
		MAP_DOWNLOADED(true),
		MAP_FAILED(true),
		OWN_READY_CLEARED(true),
		COUNTDOWN(true),
		CHAT(false),
		READY_OR_PING(false);

		/** Whether it makes the button flash. */
		public final boolean flashes;

		Event(final boolean flashes) {
			this.flashes = flashes;
		}
	}

	private boolean pageShown;
	private boolean unseen;
	private boolean chatDot;
	private boolean reducedMotion;
	private float time = ButtonFlash.OFF;

	/** Whether the LAN page is the one on screen; opening it ends the flash. */
	public void setPageShown(final boolean shown) {
		this.pageShown = shown;
		if (shown) {
			clear();
		}
	}

	public void setReducedMotion(final boolean reducedMotion) {
		this.reducedMotion = reducedMotion;
	}

	/** A lobby event; ignored while the LAN page is on screen. */
	public void onEvent(final Event event) {
		if (this.pageShown) {
			return;
		}
		if (event.flashes) {
			if (!this.unseen) {
				this.unseen = true;
				this.time = 0;
			}
		}
		else if (event == Event.CHAT) {
			this.chatDot = true;
		}
	}

	/** Ends the flash and the dot: the lobby is looked at, or gone. */
	public void clear() {
		this.unseen = false;
		this.chatDot = false;
		this.time = ButtonFlash.OFF;
	}

	/** The lobby changed while the LAN page was not shown. */
	public boolean isUnseen() {
		return this.unseen;
	}

	public boolean hasChatDot() {
		return this.chatDot;
	}

	/** Whether the button blinks (unseen changes, and the player has not asked for no motion). */
	public boolean isFlashing() {
		return this.unseen && !this.reducedMotion;
	}

	public void update(final float deltaTime) {
		if (ButtonFlash.isRunning(this.time)) {
			this.time += deltaTime;
		}
	}

	/** Whether the button is drawn highlighted as with the mouse over it. */
	public boolean isLit() {
		if (!this.unseen) {
			return false;
		}
		return this.reducedMotion || ButtonFlash.isLit(this.time);
	}

	/** The spoken text for a screen reader while the button flashes. */
	public String description() {
		return this.unseen ? "Lobby changed" : null;
	}

	/**
	 * The events that turn one lobby state into the next. The first state a player sees is not a change.
	 *
	 * @param me this player's participant id
	 * @param ownReadyAsked whether this player just asked to change its own ready mark
	 */
	public static List<Event> diff(final LanMessages.LobbyState before, final LanMessages.LobbyState after,
			final int me, final boolean ownReadyAsked) {
		final List<Event> events = new ArrayList<>();
		if (before == null) {
			return events;
		}
		final boolean[] humansBefore = humans(before);
		final boolean[] humansNow = humans(after);
		for (int id = 0; id < humansNow.length; id++) {
			if (humansNow[id] && !humansBefore[id] && (id != me)) {
				events.add(Event.JOINED);
				break;
			}
		}
		for (int id = 0; id < humansBefore.length; id++) {
			if (humansBefore[id] && !humansNow[id] && (id != me)) {
				events.add(Event.LEFT);
				break;
			}
		}
		if (before.ownerId != after.ownerId) {
			events.add(Event.HOST_CHANGED);
		}
		if (!Arrays.equals(before.map.sha256, after.map.sha256) || !before.map.name.equals(after.map.name)) {
			events.add(Event.MAP_CHANGED);
		}
		if (optionsChanged(before.options, after.options) || (before.syncWarning != after.syncWarning)) {
			events.add(Event.OPTIONS_CHANGED);
		}
		if (slotsChanged(before, after, me)) {
			events.add(Event.SLOTS_CHANGED);
		}
		final LanData.SlotInfo mineBefore = slotOf(before, me);
		final LanData.SlotInfo mineNow = slotOf(after, me);
		if ((mineBefore != null) && (mineNow != null) && mineBefore.ready && !mineNow.ready && !ownReadyAsked) {
			events.add(Event.OWN_READY_CLEARED);
		}
		if (events.isEmpty() && othersReadyOrPingChanged(before, after, me)) {
			events.add(Event.READY_OR_PING);
		}
		return events;
	}

	private static boolean[] humans(final LanMessages.LobbyState state) {
		final boolean[] ids = new boolean[256];
		for (final LanData.SlotInfo s : state.slots) {
			if ((s.kind == LanProtocol.SLOT_HUMAN) && (s.participantId < ids.length)) {
				ids[s.participantId] = true;
			}
		}
		for (final LanData.ObserverInfo o : state.observers) {
			if (o.participantId < ids.length) {
				ids[o.participantId] = true;
			}
		}
		return ids;
	}

	private static boolean optionsChanged(final LanData.Options a, final LanData.Options b) {
		return !a.gameName.equals(b.gameName) || (a.speed != b.speed) || (a.observers != b.observers)
				|| (a.lockTeams != b.lockTeams) || (a.teamsTogether != b.teamsTogether)
				|| (a.fullSharedControl != b.fullSharedControl) || (a.randomRaces != b.randomRaces);
	}

	private static LanData.SlotInfo slotOf(final LanMessages.LobbyState state, final int participant) {
		for (final LanData.SlotInfo s : state.slots) {
			if ((s.kind == LanProtocol.SLOT_HUMAN) && (s.participantId == participant)) {
				return s;
			}
		}
		return null;
	}

	/** Open / closed / computer, someone moved, or another player's team, color or race changed. */
	private static boolean slotsChanged(final LanMessages.LobbyState a, final LanMessages.LobbyState b, final int me) {
		if (a.slots.size() != b.slots.size()) {
			return true;
		}
		for (int i = 0; i < a.slots.size(); i++) {
			final LanData.SlotInfo x = a.slots.get(i);
			final LanData.SlotInfo y = b.slots.get(i);
			if ((x.kind != y.kind) || (x.participantId != y.participantId) || (x.computerLevel != y.computerLevel)) {
				return true;
			}
			final boolean own = (y.kind == LanProtocol.SLOT_HUMAN) && (y.participantId == me);
			if (!own && ((x.race != y.race) || (x.team != y.team) || (x.color != y.color)
					|| (x.handicap != y.handicap))) {
				return true;
			}
		}
		return false;
	}

	private static boolean othersReadyOrPingChanged(final LanMessages.LobbyState a, final LanMessages.LobbyState b,
			final int me) {
		for (int i = 0; (i < a.slots.size()) && (i < b.slots.size()); i++) {
			final LanData.SlotInfo x = a.slots.get(i);
			final LanData.SlotInfo y = b.slots.get(i);
			if ((y.participantId != me) && ((x.ready != y.ready) || (x.pingMs != y.pingMs)
					|| (x.controlScheme != y.controlScheme))) {
				return true;
			}
		}
		return false;
	}
}
