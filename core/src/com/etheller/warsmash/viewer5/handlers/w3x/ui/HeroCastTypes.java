package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.Set;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityNoTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilityUnitOrPointTargetSpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveFlexTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveNoTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActivePointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.CAbilityAbilityBuilderActiveUnitTarget;

/**
 * Which {@link HeroCastType} a skill is, decided by its class and by a short list of rawcodes.
 * <p>
 * The class says what the spell accepts as a target, which is most of the answer: the engine has one base per target
 * shape. What the class cannot say is how the spell is <em>shaped</em> — a cone, a line and a rain of ice all accept a
 * point — and the ability data has no field for it either. So the handful of spells whose effect leaves the hero along
 * a direction are named here by rawcode, and a direction can be added the day one lands without touching the tray.
 * <p>
 * A spell that is neither in the list nor one of the four targets is asked what it accepts, in
 * {@link #of(CAbility, TargetProbe)}.
 */
final class HeroCastTypes {
	/** Spells whose effect leaves the hero along a line or a cone: the ability data cannot say this, so it is listed. */
	private static final Set<War3ID> DIRECTION = Set.of(War3ID.fromString("ANbf"), // Breath of Fire, a cone
			War3ID.fromString("AUcs")); // Carrion Swarm, a line

	/** A spell that is not one of the four known bases, asked what it will accept. */
	interface TargetProbe {
		boolean acceptsNoTarget();

		boolean acceptsPoint();
	}

	private HeroCastTypes() {
	}

	static HeroCastType of(final CAbility ability, final TargetProbe probe) {
		final HeroCastType byClass = ofClass(ability.getClass(), ability.getAlias());
		if (byClass != null) {
			return byClass;
		}
		if (probe.acceptsNoTarget()) {
			return HeroCastType.NO_TARGET;
		}
		return probe.acceptsPoint() ? HeroCastType.GROUND : HeroCastType.NO_TARGET;
	}

	/**
	 * @return the type the class settles, or null when the class says nothing — a JASS-built ability, or another part
	 *         of the engine's zoo — so the caller can ask the spell instead
	 */
	static HeroCastType ofClass(final Class<?> abilityClass, final War3ID rawcode) {
		if ((rawcode != null) && DIRECTION.contains(rawcode)) {
			return HeroCastType.DIRECTION;
		}
		// The four bases are siblings, so the order between them only matters for a class that is not one of them.
		if (CAbilityNoTargetSpellBase.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.NO_TARGET;
		}
		if (CAbilityTargetSpellBase.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.SINGLE_TARGET;
		}
		if (CAbilityUnitOrPointTargetSpellBase.class.isAssignableFrom(abilityClass)) {
			// Accepts either, so the tap on a unit is the one that makes sense; a tap on the ground would be a cast
			// the player did not aim.
			return HeroCastType.SINGLE_TARGET;
		}
		if (CAbilityPointTargetSpellBase.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.GROUND;
		}
		// A JASS-built ability says what it accepts in its behaviour definition, and that is which class it gets:
		// Shadow Strike, for one, is a unit target the ability builder gave this class.
		if (CAbilityAbilityBuilderActiveNoTarget.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.NO_TARGET;
		}
		if (CAbilityAbilityBuilderActiveUnitTarget.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.SINGLE_TARGET;
		}
		if (CAbilityAbilityBuilderActiveFlexTarget.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.SINGLE_TARGET;
		}
		if (CAbilityAbilityBuilderActivePointTarget.class.isAssignableFrom(abilityClass)) {
			return HeroCastType.GROUND;
		}
		return null;
	}
}
