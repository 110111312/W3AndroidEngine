package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/**
 * BlzTriggerRegisterPlayerSyncEvent: the data one player sent under a prefix, which every machine gets in the same
 * step.
 */
public class CPlayerSyncEvent extends CPlayerEvent {
	private final GlobalScope globalScope;
	private final CPlayerJass player;
	private final String prefix;

	public CPlayerSyncEvent(final GlobalScope globalScope, final CPlayerJass player, final Trigger trigger,
			final String prefix) {
		super(globalScope, player, trigger, JassGameEventsWar3.EVENT_PLAYER_SYNC_DATA, null);
		this.globalScope = globalScope;
		this.player = player;
		this.prefix = prefix == null ? "" : prefix;
	}

	public void fireSync(final String sentPrefix, final String data) {
		if (this.prefix.equals(sentPrefix)) {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.playerSyncScope(getTrigger(),
					this.player, sentPrefix, data);
			this.globalScope.queueTrigger(null, null, getTrigger(), scope, scope);
		}
	}
}
