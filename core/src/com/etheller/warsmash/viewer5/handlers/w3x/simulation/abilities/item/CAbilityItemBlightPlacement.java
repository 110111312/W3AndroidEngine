package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;

/**
 * Sacrificial Skull: blight (or, with Data B off, no blight) grows around the target point, by the expansion amount
 * (Data A) every interval (the ability's duration) up to the area.
 */
public class CAbilityItemBlightPlacement extends CAbilityPointTargetSpellBase {
	private float areaOfEffect;
	private boolean createsBlight;
	private float expansionAmount;
	private float expansionInterval;

	public CAbilityItemBlightPlacement(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.areaOfEffect = worldEditorAbility.getFieldAsFloat(AbilityFields.AREA_OF_EFFECT + level, 0);
		this.createsBlight = worldEditorAbility.getFieldAsBoolean(AbilityFields.DATA_B + level, 0);
		this.expansionAmount = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_A + level, 0);
		this.expansionInterval = worldEditorAbility.getFieldAsFloat(AbilityFields.DURATION + level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds._allmodblightplacementsacrificialskullpassivesdetectionalsoflyersubmerge;
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		final BlightGrowth growth = new BlightGrowth(target.getX(), target.getY(), this.areaOfEffect,
				this.expansionAmount, this.createsBlight);
		growth.grow(simulation);
		if (!growth.isDone() && (this.expansionInterval > 0)) {
			growth.setTimeoutTime(this.expansionInterval * 2);
			growth.setRepeats(true);
			growth.start(simulation);
		}
		else {
			simulation.setBlight(target.getX(), target.getY(), this.areaOfEffect, this.createsBlight);
		}
		return false;
	}

	private static final class BlightGrowth extends CTimer {
		private final float x;
		private final float y;
		private final float area;
		private final float amount;
		private final boolean blighted;
		private float radius;
		private int steps;

		private BlightGrowth(final float x, final float y, final float area, final float amount,
				final boolean blighted) {
			this.x = x;
			this.y = y;
			this.area = area;
			this.amount = amount;
			this.blighted = blighted;
		}

		private boolean isDone() {
			return (this.radius >= this.area) || (this.amount <= 0);
		}

		// Measured: the blight starts on the corner nearest to the point, and the interval's expansions are carried
		// out two at a time, so every second interval it grows by two amounts (192 after the first of them).
		private void grow(final CSimulation simulation) {
			if (this.amount <= 0) {
				this.radius = this.area;
			}
			else if (this.steps > 0) {
				this.radius = Math.min(this.area, ((2 * this.steps) + 1) * this.amount);
			}
			this.steps++;
			simulation.setBlight(this.x, this.y, this.radius, this.blighted);
		}

		@Override
		public void onFire(final CSimulation simulation) {
			grow(simulation);
			if (isDone()) {
				kill(simulation);
			}
		}
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
