package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

/** Receives what a player did to a script frame, as the command brought it to this machine. */
public interface CFrameEventHandler {
	void frameEvent(CPlayer player, int frameId, int eventType, float value, String text);
}
