package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.Arrays;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;

/**
 * What a unit's sight found out about the terrain and sight blockers around it, kept while the unit stands in the same
 * fog cell with the same sight and nothing that cuts sight has changed. Whether a cell is reached also depends on what
 * the other units of the player already show, so only that part is checked again at every fog update.
 */
final class CUnitVisionStamp {
	/** Larger sights are not kept: the notes would be larger than what they save. */
	static final int MAX_SIGHT_CELLS = 32;

	private int cellX;
	private int cellY;
	private int sightCells = -1;
	private int height;
	private boolean flying;
	private int blockerVersion;
	/** Per cell of the sight: unknown, blocked or open, filled when the cell is first looked at. */
	private byte[] cardinal = new byte[0];
	private byte[] diagonal = new byte[0];

	/** Empties the notes unless they were taken under the same conditions. */
	void match(final int cellX, final int cellY, final int sightCells, final int height, final boolean flying,
			final int blockerVersion) {
		if ((this.sightCells == sightCells) && (this.cellX == cellX) && (this.cellY == cellY)
				&& (this.height == height) && (this.flying == flying) && (this.blockerVersion == blockerVersion)) {
			return;
		}
		if (this.sightCells != sightCells) {
			this.cardinal = new byte[sightCells * 4];
			this.diagonal = new byte[sightCells * sightCells * 4];
		}
		else {
			Arrays.fill(this.cardinal, CPlayerFogOfWar.SIGHT_UNKNOWN);
			Arrays.fill(this.diagonal, CPlayerFogOfWar.SIGHT_UNKNOWN);
		}
		this.cellX = cellX;
		this.cellY = cellY;
		this.sightCells = sightCells;
		this.height = height;
		this.flying = flying;
		this.blockerVersion = blockerVersion;
	}

	byte[] cardinalNotes() {
		return this.cardinal;
	}

	byte[] diagonalNotes() {
		return this.diagonal;
	}
}
