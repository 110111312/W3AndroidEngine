package com.etheller.warsmash.input.touch;

/** What lies under a screen point, as far as a touch order cares. */
public enum PointKind {
	/** Open ground, or anything a plain left click leaves alone. */
	GROUND,
	/** A unit the player controls: a tap selects it. */
	OWN_UNIT,
	/** A unit the player is hostile to. */
	ENEMY,
	/** A tree or a gold mine. */
	RESOURCE,
	/** An item lying on the ground. */
	ITEM,
	/** Any other unit or object that a tap would select: allies, shops, neutral units. */
	OTHER,
	/** A button, the minimap, an overlay or anything else that is not the world. */
	INTERFACE
}
