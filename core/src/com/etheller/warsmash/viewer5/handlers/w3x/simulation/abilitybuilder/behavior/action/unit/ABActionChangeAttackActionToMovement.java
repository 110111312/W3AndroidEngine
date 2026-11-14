package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.action.unit;

import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.behavior.callback.unitcallbacks.ABUnitCallback;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.ABAction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorNextBehaviorVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.BehaviorTargetVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CRangedBehavior;

public class ABActionChangeAttackActionToMovement implements ABAction {

	private ABUnitCallback unit;

	@Override
	public void runAction(CSimulation game, CUnit caster, Map<String, Object> localStore, final int castId) {
		CUnit targetUnit = caster;
		if (unit != null) {
			targetUnit = this.unit.callback(game, caster, localStore, castId);
		}
		// Measured on 1.29: an invisible unit drops a fight it took up by itself and walks back to its spot; only an
		// attack it was ordered to make is carried on as a walk to the target.
		if (targetUnit.isFightingByItself() && ((targetUnit.getCurrentBehavior().getBehaviorCategory() == CBehaviorCategory.ATTACK)
				|| (targetUnit.getCurrentBehavior().getBehaviorCategory() == CBehaviorCategory.MOVEMENT
						&& (targetUnit.getCurrentBehavior().visit(BehaviorNextBehaviorVisitor.INSTANCE) != null)
						&& (targetUnit.getCurrentBehavior().visit(BehaviorNextBehaviorVisitor.INSTANCE)
								.getBehaviorCategory() == CBehaviorCategory.ATTACK)))) {
			targetUnit.performDefaultBehavior(game);
		}
		else if (targetUnit.getCurrentBehavior().getBehaviorCategory() == CBehaviorCategory.ATTACK) {
			targetUnit.beginBehavior(game,
					targetUnit.getMoveBehavior().reset(targetUnit.getMoveBehavior().getHighlightOrderId(),
							targetUnit.getCurrentBehavior().visit(BehaviorTargetVisitor.INSTANCE)), true);
		} else
			if (targetUnit.getCurrentBehavior().getBehaviorCategory() == CBehaviorCategory.MOVEMENT) {
				CRangedBehavior next = targetUnit.getCurrentBehavior().visit(BehaviorNextBehaviorVisitor.INSTANCE);
				if (next != null && next.getBehaviorCategory() == CBehaviorCategory.ATTACK) {
					targetUnit.beginBehavior(game,
							targetUnit.getMoveBehavior().reset(targetUnit.getMoveBehavior().getHighlightOrderId(),
									next.visit(BehaviorTargetVisitor.INSTANCE)), true);
				}
			}
	}

}
