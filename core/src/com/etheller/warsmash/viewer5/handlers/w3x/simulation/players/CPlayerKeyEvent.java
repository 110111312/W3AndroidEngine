package com.etheller.warsmash.viewer5.handlers.w3x.simulation.players;

import com.etheller.interpreter.ast.scope.GlobalScope;
import com.etheller.interpreter.ast.scope.trigger.Trigger;
import com.etheller.warsmash.parsers.jass.scope.CommonTriggerExecutionScope;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.JassGameEventsWar3;

/** BlzTriggerRegisterPlayerKeyEvent: one key pressed or released with exactly the given meta keys held. */
public class CPlayerKeyEvent extends CPlayerEvent {
	private final GlobalScope globalScope;
	private final CPlayerJass player;
	private final int key;
	private final int metaKey;

	public CPlayerKeyEvent(final GlobalScope globalScope, final CPlayerJass player, final Trigger trigger,
			final int key, final int metaKey, final boolean keyDown) {
		super(globalScope, player, trigger,
				keyDown ? JassGameEventsWar3.EVENT_PLAYER_KEY_DOWN : JassGameEventsWar3.EVENT_PLAYER_KEY_UP, null);
		this.globalScope = globalScope;
		this.player = player;
		this.key = key;
		this.metaKey = metaKey;
	}

	public void fireKey(final int pressedKey, final int heldMetaKeys, final boolean down) {
		if ((this.key == pressedKey) && (this.metaKey == heldMetaKeys)) {
			final CommonTriggerExecutionScope scope = CommonTriggerExecutionScope.playerKeyScope(getTrigger(),
					this.player, pressedKey, heldMetaKeys, down);
			this.globalScope.queueTrigger(null, null, getTrigger(), scope, scope);
		}
	}
}
