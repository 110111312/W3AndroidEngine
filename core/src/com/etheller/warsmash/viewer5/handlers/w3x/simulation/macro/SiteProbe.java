package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

/**
 * "Is this footprint taken?" and nothing else.
 * <p>
 * The site search is the AI's ring walk ({@code AIConstruction.searchLocation}), which asks the pathing grid and the
 * world collision whether a point is free. Those two are engine objects the search has no business knowing about, so
 * the search asks this instead and the executor supplies it. That is what makes the ring walk testable: a test can
 * refuse the first n candidates with a lambda and see which one the search settles on.
 */
public interface SiteProbe {
	/** True when a building of the type being placed could not stand at this point and be reached. */
	boolean obstructed(float x, float y);
}
