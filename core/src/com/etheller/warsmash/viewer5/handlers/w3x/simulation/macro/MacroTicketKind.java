package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

/**
 * What a ticket asks the base to do. The three kinds here are the ones the building plan can carry; the unit plan
 * (E2, issue #27) adds its own kind and nothing else has to change, because {@link MacroRule} never looks at a kind.
 */
public enum MacroTicketKind {
	/** Any structure a worker can build: the order id is the structure's rawcode. */
	BUILD,
	/** Anything a building can research -- tech, weapon and armour levels, unit upgrades -- through its queue. */
	RESEARCH,
	/** A hall's upgrade to another structure, which is a no-order-id trick: the order id is the target's rawcode. */
	UPGRADE
}
