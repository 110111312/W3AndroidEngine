package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.AbstractCBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CDamageFlags;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.listeners.CUnitAttackFinalDamageTakenModificationListener;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuff;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.unit.StateModBuffType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.SimulationRenderComponent;

/**
 * Staff of Sanctuary: the unit is stunned; after the regeneration delay it regains life every second until it is fully
 * healed, and the stun ends with the healing. Meanwhile the unit takes no magic damage (the magic damage reduction
 * of the ability, a reduction of 1 or more takes all of it).
 */
public class CBuffSanctuary extends AbstractCBuff implements CUnitAttackFinalDamageTakenModificationListener {
	private final StateModBuff stunBuff = new StateModBuff(StateModBuffType.STUN, 1);
	private final float lifePerSecond;
	private final float magicDamageReduction;
	private final int delayTicks;
	private int ticksSinceAdd;
	private SimulationRenderComponent fx;

	public CBuffSanctuary(final int handleId, final War3ID alias, final float lifePerSecond,
			final float delaySeconds, final float magicDamageReduction) {
		super(handleId, alias, alias);
		this.lifePerSecond = lifePerSecond;
		this.magicDamageReduction = magicDamageReduction;
		this.delayTicks = CTimer.ceilSteps(delaySeconds);
	}

	@Override
	public void onAdd(final CSimulation game, final CUnit unit) {
		unit.addStateModBuff(this.stunBuff);
		unit.computeUnitState(game, this.stunBuff.getBuffType());
		unit.addFinalDamageTakenModificationListener(this);
		this.fx = game.createPersistentSpellEffectOnUnit(unit, getAlias(), CEffectType.TARGET);
	}

	@Override
	public void onRemove(final CSimulation game, final CUnit unit) {
		unit.removeStateModBuff(this.stunBuff);
		unit.computeUnitState(game, this.stunBuff.getBuffType());
		unit.removeFinalDamageTakenModificationListener(this);
		if (this.fx != null) {
			this.fx.remove();
			this.fx = null;
		}
	}

	@Override
	public void onTick(final CSimulation game, final CUnit unit) {
		this.ticksSinceAdd++;
		if (this.ticksSinceAdd < this.delayTicks) {
			return;
		}
		if (unit.getLife() >= unit.getMaximumLife()) {
			unit.remove(game, this);
			return;
		}
		unit.heal(game, this.lifePerSecond * WarsmashConstants.SIMULATION_STEP_TIME);
		if (unit.getLife() >= unit.getMaximumLife()) {
			unit.remove(game, this);
		}
	}

	@Override
	public float onDamage(final CSimulation simulation, final CUnit attacker, final CUnit target,
			final CDamageFlags flags, final CAttackType attackType, final CDamageType damageType,
			final float previousDamage) {
		if (damageType.isMagic()) {
			return previousDamage * Math.max(0, 1 - this.magicDamageReduction);
		}
		return previousDamage;
	}

	@Override
	public void onDeath(final CSimulation game, final CUnit cUnit) {
		cUnit.remove(game, this);
	}

	@Override
	public void onCancelFromQueue(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId) {
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target) {
		return null;
	}

	@Override
	public CBehavior begin(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget point) {
		return null;
	}

	@Override
	public CBehavior beginNoTarget(final CSimulation game, final CUnit caster, final int playerIndex, final int orderId,
			final boolean autoOrder) {
		return null;
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final CWidget target, final AbilityTargetCheckReceiver<CWidget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTarget(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final boolean autoOrder, final AbilityPointTarget target,
			final AbilityTargetCheckReceiver<AbilityPointTarget> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	public void checkCanTargetNoTarget(final CSimulation game, final CUnit unit, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTargetCheckReceiver<Void> receiver) {
		receiver.orderIdNotAccepted();
	}

	@Override
	protected void innerCheckCanUse(final CSimulation game, final CUnit unit, final int playerIndex, final int orderId,
			final AbilityActivationReceiver receiver) {
		receiver.notAnActiveAbility();
	}

	@Override
	public float getDurationMax() {
		return 0;
	}

	@Override
	public float getDurationRemaining(final CSimulation game, final CUnit unit) {
		return 0;
	}

	@Override
	public boolean isTimedLifeBar() {
		return false;
	}

	@Override
	public boolean isPositive() {
		return true;
	}
}
