package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.Objects;

import com.etheller.warsmash.util.War3ID;

/**
 * One line of a plan: a thing to do, and nothing about how it is going.
 * <p>
 * The ticket is deliberately dumb. What is happening to it -- whether it is affordable, whether it is stuck, whether an
 * order for it is already out -- is recomputed from the simulation every pass and lives in {@link TicketState}, never
 * here. That is what lets the same list survive a reload and lets E3 edit it while the executor is running.
 * <p>
 * There is no number: a ticket's number is its position in {@link MacroPlan}, so "done tickets disappear and the
 * numbers behind slide down" costs no code at all. Identity is worth keeping in mind -- two tickets for the same
 * building are two different things, so the executor keys its bookkeeping on object identity, not on equality.
 */
public final class MacroTicket {
	private final MacroTicketKind kind;
	private final War3ID rawcode;

	public MacroTicket(final MacroTicketKind kind, final War3ID rawcode) {
		if ((kind == null) || (rawcode == null)) {
			throw new IllegalArgumentException("a ticket needs a kind and a rawcode");
		}
		this.kind = kind;
		this.rawcode = rawcode;
	}

	public static MacroTicket build(final War3ID rawcode) {
		return new MacroTicket(MacroTicketKind.BUILD, rawcode);
	}

	public static MacroTicket research(final War3ID rawcode) {
		return new MacroTicket(MacroTicketKind.RESEARCH, rawcode);
	}

	public static MacroTicket upgrade(final War3ID rawcode) {
		return new MacroTicket(MacroTicketKind.UPGRADE, rawcode);
	}

	public MacroTicketKind getKind() {
		return this.kind;
	}

	public War3ID getRawcode() {
		return this.rawcode;
	}

	@Override
	public String toString() {
		return this.kind + " " + this.rawcode.asStringValue();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.kind, this.rawcode);
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MacroTicket)) {
			return false;
		}
		final MacroTicket other = (MacroTicket) obj;
		return (this.kind == other.kind) && this.rawcode.equals(other.rawcode);
	}
}
