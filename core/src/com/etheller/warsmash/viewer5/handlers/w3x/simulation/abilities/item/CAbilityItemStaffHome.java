package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnitClassification;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbstractCAbilityTypeDefinition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CEffectType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

/**
 * Staff of Preservation and Staff of Sanctuary: the target unit is teleported to the highest level town hall of the
 * user's player. Sanctuary also stuns it, protects it from magic damage and regenerates its life until it is fully healed.
 */
public class CAbilityItemStaffHome extends CAbilityTargetSpellBase {
	private final boolean sanctuary;
	private float heroDelay;
	private float unitDelay;
	private float lifePerSecond;
	private float magicDamageReduction;
	private War3ID buffId;

	public CAbilityItemStaffHome(final int handleId, final War3ID alias, final boolean sanctuary) {
		super(handleId, alias);
		this.sanctuary = sanctuary;
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		if (this.sanctuary) {
			this.heroDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_B + level, 0);
			this.unitDelay = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_C + level, 0);
			this.magicDamageReduction = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_D + level, 0);
			this.lifePerSecond = worldEditorAbility.getFieldAsFloat(AbilityFields.DATA_E + level, 0);
		}
		this.buffId = AbstractCAbilityTypeDefinition.getBuffId(worldEditorAbility, level, 0);
	}

	@Override
	public int getBaseOrderId() {
		return this.sanctuary ? OrderIds.sanctuary : OrderIds.preservation;
	}

	/** The highest level town hall of the player; of several, the one nearest to the point. */
	private static CUnit findTownHall(final CSimulation game, final int playerIndex, final float x, final float y) {
		CUnit best = null;
		for (final CUnit unit : game.getUnits()) {
			if ((unit.getPlayerIndex() == playerIndex) && !unit.isDead() && !unit.isConstructingOrUpgrading()
					&& unit.getClassifications().contains(CUnitClassification.TOWNHALL)) {
				final int level = unit.getUnitType().getLevel();
				if ((best == null) || (level > best.getUnitType().getLevel())
						|| ((level == best.getUnitType().getLevel())
								&& (unit.distanceSquaredNoCollision(x, y) < best.distanceSquaredNoCollision(x, y)))) {
					best = unit;
				}
			}
		}
		return best;
	}

	@Override
	protected void innerCheckCanTargetSpell(final CSimulation game, final CUnit unit, final int orderId,
			final com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget target,
			final AbilityTargetCheckReceiver<com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget> receiver) {
		if (target == unit) {
			receiver.orderIdNotAccepted();
			return;
		}
		if (findTownHall(game, unit.getPlayerIndex(), target.getX(), target.getY()) == null) {
			receiver.targetCheckFailed(CommandStringErrorKeys.THERE_ARE_NO_FRIENDLY_TOWN_HALLS_TO_TOWN_PORTAL_TO);
			return;
		}
		receiver.targetOk(target);
	}

	@Override
	public boolean doEffect(final CSimulation simulation, final CUnit caster, final AbilityTarget target) {
		final CUnit targetUnit = target.visit(AbilityTargetVisitor.UNIT);
		final CUnit hall = findTownHall(simulation, caster.getPlayerIndex(), target.getX(), target.getY());
		if ((targetUnit == null) || (hall == null)) {
			return false;
		}
		simulation.createTemporarySpellEffectOnUnit(caster, getAlias(), CEffectType.CASTER);
		simulation.spawnSpellEffectOnPoint(targetUnit.getX(), targetUnit.getY(), 0, getAlias(), CEffectType.SPECIAL, 0)
				.remove();
		targetUnit.order(simulation, OrderIds.stop, null);
		targetUnit.placeAroundStructure(simulation, hall);
		simulation.spawnSpellEffectOnPoint(targetUnit.getX(), targetUnit.getY(), 0, getAlias(), CEffectType.SPECIAL, 0)
				.remove();
		if (this.sanctuary) {
			for (final CAbility ability : targetUnit.getAbilities()) {
				if ((ability instanceof CBuffSanctuary) && ability.getAlias().equals(this.buffId)) {
					targetUnit.remove(simulation, (CBuffSanctuary) ability);
					break;
				}
			}
			targetUnit.add(simulation, new CBuffSanctuary(simulation.getHandleIdAllocator().createId(), this.buffId,
					this.lifePerSecond, targetUnit.isHero() ? this.heroDelay : this.unitDelay,
					this.magicDamageReduction));
		}
		return false;
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
