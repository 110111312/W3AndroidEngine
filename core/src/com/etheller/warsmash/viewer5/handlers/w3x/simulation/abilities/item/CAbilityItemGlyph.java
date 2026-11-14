package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.item;

import java.util.ArrayList;
import java.util.List;

import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.definitions.impl.AbilityFields;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * Glyph of Fortification and Glyph of Ultravision: every level of the ability sets the upgrade of its unit id (Data A
 * levels) for the player of the user, which improves buildings or the night vision of all of the player's units.
 */
public class CAbilityItemGlyph extends AbstractCAbilityItemInstantUse {
	private final List<War3ID> upgrades = new ArrayList<>();
	private final List<Integer> upgradeLevels = new ArrayList<>();

	public CAbilityItemGlyph(final int handleId, final War3ID alias) {
		super(handleId, alias);
	}

	@Override
	public void populateData(final GameObject worldEditorAbility, final int level) {
		this.upgrades.clear();
		this.upgradeLevels.clear();
		final int levels = Math.max(1, worldEditorAbility.getFieldAsInteger(AbilityFields.LEVELS, 0));
		for (int index = 1; index <= levels; index++) {
			final String upgradeCode = worldEditorAbility.getFieldAsString(AbilityFields.UNIT_ID + index, 0);
			if ((upgradeCode == null) || (upgradeCode.length() != 4)) {
				continue;
			}
			this.upgrades.add(War3ID.fromString(upgradeCode));
			this.upgradeLevels.add(worldEditorAbility.getFieldAsInteger(AbilityFields.DATA_A + index, 0));
		}
	}

	@Override
	public int getBaseOrderId() {
		return OrderIds.itemultravisionglyph;
	}

	@Override
	public boolean checkBeforeQueue(final CSimulation game, final CUnit caster, final int playerIndex,
			final int orderId, final boolean autoOrder, final AbilityTarget target) {
		if (orderId != getBaseOrderId()) {
			return true;
		}
		final CPlayer player = game.getPlayer(caster.getPlayerIndex());
		for (int i = 0; i < this.upgrades.size(); i++) {
			player.setTechResearched(game, this.upgrades.get(i), this.upgradeLevels.get(i));
		}
		game.unitSoundEffectEvent(caster, getAlias());
		return false;
	}
}
