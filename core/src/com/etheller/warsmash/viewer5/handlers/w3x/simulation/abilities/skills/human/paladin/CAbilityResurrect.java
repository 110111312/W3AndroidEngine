package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.human.paladin;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

public class CAbilityResurrect extends CAbilityNoTargetSpellBase {
	private float areaOfEffect;
	private int numberOfCorpsesRaised;

	public CAbilityResurrect(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.resurrection;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.numberOfCorpsesRaised = worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	private List<CUnit> findCorpses(final CSimulation simulation, final CUnit caster) {
		final List<CUnit> unitsToResurrect = new ArrayList<>(numberOfCorpsesRaised);
		simulation.getWorldCollision().enumCorpsesInRange(caster.getX(), caster.getY(), this.areaOfEffect,
				(enumUnit) -> {
					if (unitsToResurrect.size() < numberOfCorpsesRaised) {
						if (enumUnit.canBeTargetedBy(simulation, caster, getTargetsAllowed())) {
							unitsToResurrect.add(enumUnit);
						}
						return false;
					}
					else {
						return true;
					}
				});
		return unitsToResurrect;
	}

	/**
	 * Measured (1.29): the scroll of an item is refused while no corpse it could resurrect is near the user.
	 */
	@Override
	protected void innerCheckCanUseSpell(final CSimulation game, final CUnit unit, final int orderId,
			final AbilityActivationReceiver receiver) {
		if ((getItem() != null) && findCorpses(game, unit).isEmpty()) {
			receiver.activationCheckFailed(CommandStringErrorKeys.THERE_ARE_NO_USABLE_CORPSES_NEARBY);
			return;
		}
		super.innerCheckCanUseSpell(game, unit, orderId, receiver);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		for (final CUnit unit : findCorpses(simulation, caster)) {
			unit.resurrect(simulation);
			simulation.createTemporarySpellEffectOnUnit(unit, getAlias(), CEffectType.TARGET);
		}
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		return false;
	}
}
