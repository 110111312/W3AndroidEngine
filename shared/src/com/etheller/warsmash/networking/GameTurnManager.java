package com.etheller.warsmash.networking;

import com.etheller.warsmash.util.Log;

public interface GameTurnManager {
	int getLatestCompletedTurn();

	void turnCompleted(int gameTurnTick);

	void framesSkipped(float skippedCount);

	/**
	 * Called each frame while the game is paused. A game that takes its turns from a host has to go on taking them,
	 * because the command that ends the pause comes in one.
	 */
	default void whilePaused() {
	}

	GameTurnManager PAUSED = new GameTurnManager() {
		@Override
		public int getLatestCompletedTurn() {
			return Integer.MIN_VALUE;
		}

		@Override
		public void turnCompleted(final int gameTurnTick) {
			Log.warn("got turnCompleted(" + gameTurnTick + ") while paused !!");
		}

		@Override
		public void framesSkipped(final float skippedCount) {
		}
	};

	GameTurnManager LOCAL = new GameTurnManager() {
		@Override
		public int getLatestCompletedTurn() {
			return Integer.MAX_VALUE;
		}

		@Override
		public void turnCompleted(final int gameTurnTick) {
		}

		@Override
		public void framesSkipped(final float skippedCount) {
		}
	};

}
