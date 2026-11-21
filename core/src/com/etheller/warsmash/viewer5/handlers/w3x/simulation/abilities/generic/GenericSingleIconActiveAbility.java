package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.Aliased;

public interface GenericSingleIconActiveAbility extends CLevelingAbility, SingleOrderAbility, Aliased {

	boolean isToggleOn();

	/**
	 * Whether the ability is acting on the unit right now — a stance held, a meld in place — as opposed to merely being
	 * available. A toggle's own switch is the default; an ability that takes effect without being a toggle says so by
	 * overriding this, so that its button can show that it is on.
	 */
	default boolean isRunning() {
		return isToggleOn();
	}

	boolean isAutoCastOn();

	int getAutoCastOnOrderId();

	int getAutoCastOffOrderId();

	int getUIGoldCost();

	int getUILumberCost();
	
	int getUIFoodCost();

	int getUIManaCost();

	int getUsesRemaining();

	float getUIAreaOfEffect();

}
