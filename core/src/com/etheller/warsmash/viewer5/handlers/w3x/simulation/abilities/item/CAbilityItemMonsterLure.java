package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CBuffTimedLife;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Monster Lure: a ward (Unit ID) that lives for the ability's duration. After the activation delay (Data B), every
 * lure interval (Data C) it draws all the neutral hostile creeps in the area to itself.
 */
public class CAbilityItemMonsterLure extends CAbilityPointTargetSpellBase {
	private War3ID wardTypeId;
	private War3ID buffId;
	private float activationDelay;
	private float lureInterval;
	private float areaOfEffect;

	public CAbilityItemMonsterLure(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.wardTypeId = War3ID.fromString(worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + level, 0));
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
		this.activationDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
		this.lureInterval = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemmonsterlure;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		// Measured on 1.29: the ward belongs to the neutral passive player, so the creeps it draws do not attack it.
		final CUnit ward = simulation.createUnitSimple(this.wardTypeId, simulation.getPlayers().size() - 1,
				target.getX(), target.getY(), caster.getFacing());
		if (ward == null) {
			return false;
		}
		ward.addClassification(CUnitClassification.SUMMONED);
		simulation.unitSummoned(caster, ward);
		ward.add(simulation, new CBuffTimedLife(simulation.getHandleIdAllocator().createId(), this.buffId,
				getDuration(), false));
		simulation.createTemporarySpellEffectOnUnit(ward, getAlias(), CEffectType.TARGET);
		final CTimer lureTimer = new CTimer() {
			@Override
			public void onFire(final CSimulation game) {
				if (ward.isDead()) {
					kill(game);
					return;
				}
				lure(game, ward);
			}
		};
		lureTimer.setTimeoutTime(Math.max(0.1f, this.lureInterval));
		// Measured on 1.29: the first pull comes one interval after the activation delay.
		lureTimer.startRepeatingTimerWithDelay(simulation, this.activationDelay + this.lureInterval);
		return false;
	}

	private void lure(final CSimulation game, final CUnit ward) {
		game.getWorldCollision().enumUnitsInRange(ward.getX(), ward.getY(), this.areaOfEffect, (enumUnit) -> {
			if (game.isNeutralAggressive(enumUnit.getPlayerIndex()) && !enumUnit.isBuilding() && !enumUnit.isDead()
					&& !enumUnit.isHidden() && !enumUnit.isMovementDisabled()) {
				enumUnit.order(game, OrderIds.move, new AbilityPointTarget(ward.getX(), ward.getY()));
			}
			return false;
		});
	}

	@Override
	public boolean isMagic() {
		return false;
	}

	@Override
	public CAbilityCategory getAbilityCategory() {
		return CAbilityCategory.ITEM;
	}
}
