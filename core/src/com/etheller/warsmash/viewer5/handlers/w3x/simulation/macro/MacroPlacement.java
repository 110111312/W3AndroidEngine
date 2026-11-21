package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.build.AbstractCAbilityBuild;

/**
 * Where a building goes when the player did not say: a ring walk out from the base, as the computer AI does it.
 * <p>
 * Copied from {@code AIConstruction.searchLocation} because that is the only working answer to "where does an
 * unrequested building go": rings of growing radius, each starting at a preferred angle and alternating around it, the
 * first footprint that is free and has a cell of room around it. Two things are copied exactly, since a difference here
 * shows up as a building that cannot be placed: the point is cut to the build grid with the engine's own rounding
 * ({@code AbstractCAbilityBuild.roundToBuildGrid}) before it is tested, and the test is asked both at the point and one
 * {@code BUILDING_GAP} to each side.
 * <p>
 * Everything that decides <em>where</em> to start the walk -- the base, the mine to keep clear of, the map's centre --
 * belongs to the caller, because this stays a pure function of numbers.
 */
public final class MacroPlacement {
	/** How far apart the rings are, in world units, and how far apart the points on a ring. */
	private static final float RADIUS_STEP = 64f;
	private static final float ARC_STEP = 96f;

	private MacroPlacement() {
	}

	/**
	 * @param centerX          the base the walk starts from
	 * @param centerY          the base the walk starts from
	 * @param minRadius        how far out the first ring is, which keeps the building off the hall itself
	 * @param maxRadius        how far out the walk gives up
	 * @param preferredAngle   the direction of the first candidate of every ring, in radians
	 * @param pathingCellsWide the footprint's pathing width in cells, for the grid cut
	 * @param pathingCellsHigh the footprint's pathing height in cells, for the grid cut
	 * @param buildingGap      how much room around the footprint counts as free
	 * @param needsRoomAround  false for a building that is meant to be built onto something
	 * @return the point, or null when every candidate out to {@code maxRadius} is taken
	 */
	public static float[] findSite(final float centerX, final float centerY, final float minRadius,
			final float maxRadius, final float preferredAngle, final int pathingCellsWide, final int pathingCellsHigh,
			final float buildingGap, final boolean needsRoomAround, final SiteProbe probe) {
		for (float radius = minRadius; radius <= maxRadius; radius += RADIUS_STEP) {
			final int steps = Math.max(8, (int) ((2 * Math.PI * radius) / ARC_STEP));
			for (int step = 0; step < steps; step++) {
				// Alternating sides of the preferred angle, so the building lands on either side of the path from the
				// mine rather than always on the side an angle happens to name.
				final int side = ((step & 1) == 0) ? 1 : -1;
				final double angle = preferredAngle + (side * ((step + 1) / 2) * ((2 * Math.PI) / steps));
				final float x = AbstractCAbilityBuild.roundToBuildGrid(
						centerX + (float) (StrictMath.cos(angle) * radius), pathingCellsWide);
				final float y = AbstractCAbilityBuild.roundToBuildGrid(
						centerY + (float) (StrictMath.sin(angle) * radius), pathingCellsHigh);
				if (probe.obstructed(x, y)) {
					continue;
				}
				if (needsRoomAround && (probe.obstructed(x + buildingGap, y) || probe.obstructed(x - buildingGap, y)
						|| probe.obstructed(x, y + buildingGap) || probe.obstructed(x, y - buildingGap))) {
					continue;
				}
				return new float[] { x, y };
			}
		}
		return null;
	}
}
