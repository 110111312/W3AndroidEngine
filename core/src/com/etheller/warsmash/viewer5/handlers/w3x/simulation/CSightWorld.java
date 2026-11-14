package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

/**
 * What a fog update reads of the world besides the pathing grid: the terrain that cuts sight, and the fog settings.
 */
public interface CSightWorld extends CFogMaskSettings {
	int getTerrainHeight(float x, float y);

	boolean isTerrainRomp(float x, float y);

	boolean isTerrainWater(float x, float y);
}
