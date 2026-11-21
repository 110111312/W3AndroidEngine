package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

/**
 * Where one ticket stands this pass, as the rule set needs to see it.
 * <p>
 * The three answers are independent and that is the point: {@code stuck} is "waiting will never help", {@code
 * affordable} is "the price is there", {@code pending} is "an order is already out". A ticket that is none of them is
 * ready, and the rule set hands back exactly one of those per pass.
 */
public final class TicketState {
	private final int number;
	private final MacroTicket ticket;
	private final Stuck stuck;
	private final boolean affordable;
	private final boolean pending;

	public TicketState(final int number, final MacroTicket ticket, final Stuck stuck, final boolean affordable,
			final boolean pending) {
		this.number = number;
		this.ticket = ticket;
		this.stuck = stuck;
		this.affordable = affordable && (stuck == null);
		this.pending = pending;
	}

	public static TicketState ready(final int number, final MacroTicket ticket) {
		return new TicketState(number, ticket, null, true, false);
	}

	public static TicketState waiting(final int number, final MacroTicket ticket) {
		return new TicketState(number, ticket, null, false, false);
	}

	public static TicketState pending(final int number, final MacroTicket ticket) {
		return new TicketState(number, ticket, null, true, true);
	}

	public static TicketState stuck(final int number, final MacroTicket ticket, final Stuck stuck) {
		return new TicketState(number, ticket, stuck, false, false);
	}

	/** The ticket's position in its own plan, which is its number. */
	public int getNumber() {
		return this.number;
	}

	public MacroTicket getTicket() {
		return this.ticket;
	}

	/** The reason it can never run, or null. */
	public Stuck getStuck() {
		return this.stuck;
	}

	public boolean isStuck() {
		return this.stuck != null;
	}

	public boolean isAffordable() {
		return this.affordable;
	}

	/** An order for this ticket is already out, so the number stays open without a second purchase. */
	public boolean isPending() {
		return this.pending;
	}

	/** It can be bought right now. */
	public boolean isReady() {
		return (this.stuck == null) && this.affordable && !this.pending;
	}

	@Override
	public String toString() {
		return "#" + this.number + " " + this.ticket + (this.stuck != null ? " stuck(" + this.stuck + ")"
				: (this.pending ? " pending" : (this.affordable ? " ready" : " waiting")));
	}
}
