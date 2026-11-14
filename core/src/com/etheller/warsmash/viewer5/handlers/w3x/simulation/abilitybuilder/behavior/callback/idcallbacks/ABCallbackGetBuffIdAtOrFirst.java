package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.idcallbacks;

import java.util.List;
import java.util.Map;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABLocalStoreKeys;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.types.impl.CAbilityTypeAbilityBuilderLevelData;

public class ABCallbackGetBuffIdAtOrFirst extends ABIDCallback {

	private int index;

	@SuppressWarnings("unchecked")
	@Override
	public War3ID callback(CSimulation game, CUnit caster, Map<String, Object> localStore, final int castId) {
		List<War3ID> buffs = ((List<CAbilityTypeAbilityBuilderLevelData>) localStore.get(ABLocalStoreKeys.LEVELDATA))
				.get(((int) localStore.get(ABLocalStoreKeys.CURRENTLEVEL)) - 1).getBuffs();

		if (buffs != null && buffs.size() > this.index) {
			return buffs.get(this.index);
		}
		if (buffs != null && !buffs.isEmpty()) {
			return buffs.get(0);
		}
		return null;
	}

}
