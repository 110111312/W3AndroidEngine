package com.etheller.warsmash.parsers.jass.scope;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CDamageType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * What one hit tells its damage triggers. Every scope a trigger runs in shares the one object, so a change a
 * trigger makes (BlzSetEventDamage and the type setters) reaches the damage code whichever scope copy it was made
 * in. Only the triggers of the damaging event, which runs before armor and resistance, may change the types.
 */
public final class CDamageEvent {
	private final CUnit target;
	private final CUnit source;
	private final boolean damaging;
	private final boolean attack;
	private final boolean ranged;
	private float damage;
	private CAttackType attackType;
	private CDamageType damageType;
	private String weaponSoundType;

	public CDamageEvent(final CUnit target, final CUnit source, final boolean damaging, final boolean attack,
			final boolean ranged, final float damage, final CAttackType attackType, final CDamageType damageType,
			final String weaponSoundType) {
		this.target = target;
		this.source = source;
		this.damaging = damaging;
		this.attack = attack;
		this.ranged = ranged;
		this.damage = damage;
		this.attackType = attackType;
		this.damageType = damageType;
		this.weaponSoundType = weaponSoundType;
	}

	public CUnit getTarget() {
		return this.target;
	}

	public CUnit getSource() {
		return this.source;
	}

	public boolean isDamaging() {
		return this.damaging;
	}

	public boolean isAttack() {
		return this.attack;
	}

	public boolean isRanged() {
		return this.ranged;
	}

	public float getDamage() {
		return this.damage;
	}

	public void setDamage(final float damage) {
		this.damage = damage;
	}

	public CAttackType getAttackType() {
		return this.attackType;
	}

	public void setAttackType(final CAttackType attackType) {
		this.attackType = attackType;
	}

	public CDamageType getDamageType() {
		return this.damageType;
	}

	public void setDamageType(final CDamageType damageType) {
		this.damageType = damageType;
	}

	public String getWeaponSoundType() {
		return this.weaponSoundType;
	}

	public void setWeaponSoundType(final String weaponSoundType) {
		this.weaponSoundType = weaponSoundType;
	}

	/** The weapon type as a script sees it; the damage code carries it as the name or the sound key. */
	public CWeaponSoundTypeJass getWeaponType() {
		if (this.weaponSoundType != null) {
			for (final CWeaponSoundTypeJass type : CWeaponSoundTypeJass.values()) {
				if (this.weaponSoundType.equals(type.name()) || this.weaponSoundType.equals(type.getSoundKey())) {
					return type;
				}
			}
		}
		return CWeaponSoundTypeJass.WHOKNOWS;
	}
}
