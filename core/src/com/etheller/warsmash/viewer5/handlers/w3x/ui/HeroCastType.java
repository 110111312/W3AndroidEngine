package com.etheller.warsmash.viewer5.handlers.w3x.ui;

/**
 * The four ways a hero-mode skill is cast. This is a design decision, not data: the ability data says which target an
 * ability accepts, but a cone and a rain of ice both accept a point, so the difference between them is a table in
 * {@link HeroCastTypes} rather than something read out of the game files.
 */
enum HeroCastType {
	/** Fires at the hero: Fan of Knives, an aura, a self heal. A tap casts it. */
	NO_TARGET,
	/** Fires at a unit: Shadow Strike, a heal, a hex. A tap chooses it, a second tap on the target casts it. */
	SINGLE_TARGET,
	/** Leaves the hero along a line: Breath of Fire, Carrion Swarm. Dragging aims it, and the preview is a line. */
	DIRECTION,
	/** Lands on a place: Blizzard, Blink, Flame Strike. Dragging aims it, and the preview is a circle. */
	GROUND
}
