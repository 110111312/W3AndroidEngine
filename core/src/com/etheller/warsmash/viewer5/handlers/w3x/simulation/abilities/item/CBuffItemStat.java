package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimed;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.NonStackingStatBuff;

/**
 * A timed positive buff from an item that changes one stat while it lasts (Scroll of Protection, Scroll of Speed).
 */
public class CBuffItemStat extends CBuffTimed {
	private final NonStackingStatBuff[] statBuffs;

	public CBuffItemStat(final int handleId, final War3ID alias, final float duration,
			final NonStackingStatBuff... statBuffs) {
		super(handleId, alias, alias, duration);
		this.statBuffs = statBuffs;
	}

	@Override
	protected void onBuffAdd(final CSimulation game, final CUnit unit) {
		for (final NonStackingStatBuff statBuff : this.statBuffs) {
			unit.addNonStackingStatBuff(game, statBuff);
		}
	}

	@Override
	protected void onBuffRemove(final CSimulation game, final CUnit unit) {
		for (final NonStackingStatBuff statBuff : this.statBuffs) {
			unit.removeNonStackingStatBuff(game, statBuff);
		}
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return true;
	}

	@Override
	public boolean isDispellable() {
		return true;
	}

	@Override
	public boolean isMagic() {
		return true;
	}
}
