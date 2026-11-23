package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

/**
 * What a ticket asks the base to do. The first three are the building plan's; {@code TRAIN} is the unit plan's (E2,
 * issue #27), and it needed nothing else to change, because {@link MacroRule} never looks at a kind -- it compares
 * numbers -- and {@link MacroPlan} holds both plans' tickets in one numbering.
 */
public enum MacroTicketKind {
	/** Any structure a worker can build: the order id is the structure's rawcode. */
	BUILD,
	/** Anything a building can research -- tech, weapon and armour levels, unit upgrades -- through its queue. */
	RESEARCH,
	/** A hall's upgrade to another structure, which is a no-order-id trick: the order id is the target's rawcode. */
	UPGRADE,
	/** One unit from a producer's queue: the order id is the unit's rawcode, and one ticket is one unit. */
	TRAIN
}
