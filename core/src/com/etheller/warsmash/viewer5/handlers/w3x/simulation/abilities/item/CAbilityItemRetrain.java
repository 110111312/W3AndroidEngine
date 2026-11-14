package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Tome of Retraining: the hero forgets every learned skill that is not in cooldown and gets the skill points back.
 * Only a hero that has learned such a skill can use it.
 */
public class CAbilityItemRetrain extends AbstractCAbilityItemInstantUse {
	public CAbilityItemRetrain(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemretrain;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId == getBaseOrderId()) {
			final CAbilityHero heroData = caster.getHeroData();
			if (heroData == null) {
				return false;
			}
			game.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
			game.unitSoundEffectEvent(caster, getAlias());
			heroData.retrain(game, caster);
			return false;
		}
		return true;
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		final CAbilityHero heroData = unit.getHeroData();
		if ((heroData == null) || !heroData.isRetrainable(game, unit)) {
			receiver.activationCheckFailed(
					CommandStringErrorKeys.ONLY_HEROES_THAT_HAVE_LEARNED_SPELLS_NOT_IN_COOLDOWN_CAN_USE_THIS_ITEM);
			return;
		}
		receiver.useOk();
	}
}
