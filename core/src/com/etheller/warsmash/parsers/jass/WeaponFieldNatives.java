package com.etheller.warsmash.parsers.jass;

import java.util.List;

import com.etheller.interpreter.ast.function.ConvertedConstant;
import com.etheller.interpreter.ast.function.JassFunction;
import com.etheller.interpreter.ast.util.JassProgram;
import com.etheller.interpreter.ast.value.BooleanJassValue;
import com.etheller.interpreter.ast.value.IntegerJassValue;
import com.etheller.interpreter.ast.value.JassValue;
import com.etheller.interpreter.ast.value.RealJassValue;
import com.etheller.interpreter.ast.value.StringJassValue;
import com.etheller.interpreter.ast.value.visitor.BooleanJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.IntegerJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.ObjectJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.RealJassValueVisitor;
import com.etheller.interpreter.ast.value.visitor.StringJassValueVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.CAttackType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackInstant;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileBounce;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileLine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.combat.attacks.CUnitAttackMissileSplash;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CAttackTypeJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CWeaponSoundTypeJass;

/**
 * The unit weapon field natives (BlzGetUnitWeaponIntegerField and its real, boolean and string kinds and the
 * setters): the numbers of one attack of one unit. The weapon index counts from 0, as the fields of the editor
 * name the first weapon 1. A weapon the unit does not have, or a field the weapon's kind does not have (a missile
 * speed on a melee attack), reads 0, false or an empty string and a set of it returns false.
 */
final class WeaponFieldNatives {
	private final JassProgram program;

	private WeaponFieldNatives(final JassProgram program) {
		this.program = program;
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final WeaponFieldNatives natives = new WeaponFieldNatives(program);
		natives.registerAll();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	/** The four letters of a field constant as one number ('ua1d' reads as the weapon-independent 'ua*d'). */
	private static String fieldId(final List<JassValue> arguments, final int index) {
		final Object constant = WeaponFieldNatives.<Object>object(arguments, index);
		if (!(constant instanceof ConvertedConstant)) {
			return "";
		}
		final int value = ((ConvertedConstant) constant).getValue();
		return "" + (char) ((value >> 24) & 0xFF) + (char) ((value >> 16) & 0xFF) + (char) ((value >> 8) & 0xFF)
				+ (char) (value & 0xFF);
	}

	private static CUnitAttack weapon(final CUnit unit, final int index) {
		if ((unit == null) || unit.isRemovedFromGame() || (index < 0)) {
			return null;
		}
		final List<CUnitAttack> attacks = unit.getUnitSpecificAttacks();
		return (attacks == null) || (index >= attacks.size()) ? null : attacks.get(index);
	}

	private static int attackTypeIndex(final CAttackType type) {
		for (int i = 0; i < CAttackTypeJass.VALUES.length; i++) {
			if (CAttackTypeJass.VALUES[i] == type) {
				return i;
			}
		}
		return 0;
	}

	private static int weaponSoundIndex(final String weaponSound) {
		for (final CWeaponSoundTypeJass type : CWeaponSoundTypeJass.values()) {
			if ((weaponSound != null) && weaponSound.equals(type.getSoundKey())) {
				return type.ordinal();
			}
		}
		return 0;
	}

	private Integer readInteger(final CUnitAttack attack, final String id) {
		switch (id) {
		case "ua1d":
		case "ua2d":
			return attack.getDamageDice();
		case "ua1b":
		case "ua2b":
			return attack.getDamageBase();
		case "ua1s":
		case "ua2s":
			return attack.getDamageSidesPerDie();
		case "utc1":
		case "utc2":
			return attack instanceof CUnitAttackMissileBounce
					? ((CUnitAttackMissileBounce) attack).getMaximumNumberOfTargets() : null;
		case "ua1t":
		case "ua2t":
			return attackTypeIndex(attack.getAttackType());
		case "ucs1":
		case "ucs2":
			return weaponSoundIndex(attack.getWeaponSound());
		case "ua1p":
		case "ua2p":
			return attack instanceof CUnitAttackMissileSplash
					? ((CUnitAttackMissileSplash) attack).getAreaOfEffectTargets().size() : null;
		default:
			return null;
		}
	}

	private boolean writeInteger(final CUnitAttack attack, final String id, final int value) {
		switch (id) {
		case "ua1d":
		case "ua2d":
			attack.setDamageDice(value);
			return true;
		case "ua1b":
		case "ua2b":
			attack.setDamageBase(value - attack.getPrimaryAttributePermanentDamageBonus());
			return true;
		case "ua1s":
		case "ua2s":
			attack.setDamageSidesPerDie(value);
			return true;
		case "utc1":
		case "utc2":
			if (attack instanceof CUnitAttackMissileBounce) {
				((CUnitAttackMissileBounce) attack).setMaximumNumberOfTargets(value);
				return true;
			}
			return false;
		case "ua1t":
		case "ua2t":
			if ((value >= 0) && (value < CAttackTypeJass.VALUES.length)) {
				attack.setAttackType(CAttackTypeJass.VALUES[value]);
				return true;
			}
			return false;
		case "ucs1":
		case "ucs2":
			final CWeaponSoundTypeJass[] sounds = CWeaponSoundTypeJass.values();
			if ((value >= 0) && (value < sounds.length)) {
				attack.setWeaponSound(sounds[value].getSoundKey());
				return true;
			}
			return false;
		default:
			return false;
		}
	}

	private Float readReal(final CUnitAttack attack, final String id) {
		switch (id) {
		case "ubs1":
		case "ubs2":
			return attack.getAnimationBackswingPoint();
		case "udp1":
		case "udp2":
			return attack.getAnimationDamagePoint();
		case "ua1c":
		case "ua2c":
			return attack.getCooldownTimeBase();
		case "ua1r":
		case "ua2r":
			return (float) attack.getRange();
		case "udl1":
		case "udl2":
			if (attack instanceof CUnitAttackMissileBounce) {
				return ((CUnitAttackMissileBounce) attack).getDamageLossFactor();
			}
			return null;
		case "uhd1":
		case "uhd2":
			return attack instanceof CUnitAttackMissileSplash ? ((CUnitAttackMissileSplash) attack).getDamageFactorMedium()
					: null;
		case "uqd1":
		case "uqd2":
			return attack instanceof CUnitAttackMissileSplash ? ((CUnitAttackMissileSplash) attack).getDamageFactorSmall()
					: null;
		case "usd1":
		case "usd2":
			return attack instanceof CUnitAttackMissileLine ? ((CUnitAttackMissileLine) attack).getDamageSpillDistance()
					: null;
		case "usr1":
		case "usr2":
			return attack instanceof CUnitAttackMissileLine ? ((CUnitAttackMissileLine) attack).getDamageSpillRadius()
					: null;
		case "ua1z":
		case "ua2z":
			return attack instanceof CUnitAttackMissile ? (float) ((CUnitAttackMissile) attack).getProjectileSpeed()
					: null;
		case "uma1":
		case "uma2":
			return attack instanceof CUnitAttackMissile ? ((CUnitAttackMissile) attack).getProjectileArc() : null;
		case "ua1f":
		case "ua2f":
			return attack instanceof CUnitAttackMissileSplash
					? (float) ((CUnitAttackMissileSplash) attack).getAreaOfEffectFullDamage() : null;
		case "ua1h":
		case "ua2h":
			return attack instanceof CUnitAttackMissileSplash
					? (float) ((CUnitAttackMissileSplash) attack).getAreaOfEffectMediumDamage() : null;
		case "ua1q":
		case "ua2q":
			return attack instanceof CUnitAttackMissileSplash
					? (float) ((CUnitAttackMissileSplash) attack).getAreaOfEffectSmallDamage() : null;
		default:
			return null;
		}
	}

	private boolean writeReal(final CUnitAttack attack, final String id, final float value) {
		switch (id) {
		case "ubs1":
		case "ubs2":
			attack.setAnimationBackswingPoint(value);
			return true;
		case "udp1":
		case "udp2":
			attack.setAnimationDamagePoint(value);
			return true;
		case "ua1c":
		case "ua2c":
			attack.setCooldownTime(value);
			return true;
		case "ua1r":
		case "ua2r":
			attack.setRange((int) value);
			return true;
		case "udl1":
		case "udl2":
			if (attack instanceof CUnitAttackMissileBounce) {
				((CUnitAttackMissileBounce) attack).setDamageLossFactor(value);
				return true;
			}
			return false;
		case "uhd1":
		case "uhd2":
			if (attack instanceof CUnitAttackMissileSplash) {
				((CUnitAttackMissileSplash) attack).setDamageFactorMedium(value);
				return true;
			}
			return false;
		case "uqd1":
		case "uqd2":
			if (attack instanceof CUnitAttackMissileSplash) {
				((CUnitAttackMissileSplash) attack).setDamageFactorSmall(value);
				return true;
			}
			return false;
		case "usd1":
		case "usd2":
			if (attack instanceof CUnitAttackMissileLine) {
				((CUnitAttackMissileLine) attack).setDamageSpillDistance(value);
				return true;
			}
			return false;
		case "usr1":
		case "usr2":
			if (attack instanceof CUnitAttackMissileLine) {
				((CUnitAttackMissileLine) attack).setDamageSpillRadius(value);
				return true;
			}
			return false;
		case "ua1z":
		case "ua2z":
			if (attack instanceof CUnitAttackMissile) {
				((CUnitAttackMissile) attack).setProjectileSpeed((int) value);
				return true;
			}
			return false;
		case "uma1":
		case "uma2":
			if (attack instanceof CUnitAttackMissile) {
				((CUnitAttackMissile) attack).setProjectileArc(value);
				return true;
			}
			return false;
		case "ua1f":
		case "ua2f":
			if (attack instanceof CUnitAttackMissileSplash) {
				((CUnitAttackMissileSplash) attack).setAreaOfEffectFullDamage((int) value);
				return true;
			}
			return false;
		case "ua1h":
		case "ua2h":
			if (attack instanceof CUnitAttackMissileSplash) {
				((CUnitAttackMissileSplash) attack).setAreaOfEffectMediumDamage((int) value);
				return true;
			}
			return false;
		case "ua1q":
		case "ua2q":
			if (attack instanceof CUnitAttackMissileSplash) {
				((CUnitAttackMissileSplash) attack).setAreaOfEffectSmallDamage((int) value);
				return true;
			}
			return false;
		default:
			return false;
		}
	}

	private Boolean readBoolean(final CUnitAttack attack, final String id) {
		switch (id) {
		case "uwu1":
		case "uwu2":
			return attack.isShowUI();
		case "umh1":
		case "umh2":
			return attack instanceof CUnitAttackMissile ? ((CUnitAttackMissile) attack).isProjectileHomingEnabled()
					: null;
		default:
			return null;
		}
	}

	private boolean writeBoolean(final CUnitAttack attack, final String id, final boolean value) {
		switch (id) {
		case "uwu1":
		case "uwu2":
			attack.setShowUI(value);
			return true;
		case "umh1":
		case "umh2":
			if (attack instanceof CUnitAttackMissile) {
				((CUnitAttackMissile) attack).setProjectileHomingEnabled(value);
				return true;
			}
			return false;
		default:
			return false;
		}
	}

	private String readString(final CUnitAttack attack, final String id) {
		if (id.startsWith("ua1m") || id.startsWith("ua2m")) {
			if (attack instanceof CUnitAttackMissile) {
				return ((CUnitAttackMissile) attack).getProjectileArt();
			}
			if (attack instanceof CUnitAttackInstant) {
				return ((CUnitAttackInstant) attack).getProjectileArt();
			}
		}
		return null;
	}

	private boolean writeString(final CUnitAttack attack, final String id, final String value) {
		if (id.startsWith("ua1m") || id.startsWith("ua2m")) {
			if (attack instanceof CUnitAttackMissile) {
				((CUnitAttackMissile) attack).setProjectileArt(value);
				return true;
			}
			if (attack instanceof CUnitAttackInstant) {
				((CUnitAttackInstant) attack).setProjectileArt(value);
				return true;
			}
		}
		return false;
	}

	private void registerAll() {
		add("BlzGetUnitWeaponIntegerField", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0),
					arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final Integer value = attack == null ? null : readInteger(attack, fieldId(arguments, 1));
			return IntegerJassValue.of(value == null ? 0 : value);
		});
		add("BlzSetUnitWeaponIntegerField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final boolean changed = (attack != null) && writeInteger(attack, fieldId(arguments, 1),
					arguments.get(3).visit(IntegerJassValueVisitor.getInstance()));
			if (changed) {
				unit.notifyAttacksChanged();
			}
			return BooleanJassValue.of(changed);
		});
		add("BlzGetUnitWeaponRealField", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0),
					arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final Float value = attack == null ? null : readReal(attack, fieldId(arguments, 1));
			return RealJassValue.of(value == null ? 0 : value);
		});
		add("BlzSetUnitWeaponRealField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final boolean changed = (attack != null) && writeReal(attack, fieldId(arguments, 1),
					arguments.get(3).visit(RealJassValueVisitor.getInstance()).floatValue());
			if (changed) {
				unit.notifyAttacksChanged();
			}
			return BooleanJassValue.of(changed);
		});
		add("BlzGetUnitWeaponBooleanField", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0),
					arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final Boolean value = attack == null ? null : readBoolean(attack, fieldId(arguments, 1));
			return BooleanJassValue.of(value != null && value);
		});
		add("BlzSetUnitWeaponBooleanField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final boolean changed = (attack != null) && writeBoolean(attack, fieldId(arguments, 1),
					arguments.get(3).visit(BooleanJassValueVisitor.getInstance()));
			if (changed) {
				unit.notifyAttacksChanged();
			}
			return BooleanJassValue.of(changed);
		});
		add("BlzGetUnitWeaponStringField", (arguments, globalScope, triggerScope) -> {
			final CUnitAttack attack = weapon(object(arguments, 0),
					arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final String value = attack == null ? null : readString(attack, fieldId(arguments, 1));
			return new StringJassValue(value == null ? "" : value);
		});
		add("BlzSetUnitWeaponStringField", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			final CUnitAttack attack = weapon(unit, arguments.get(2).visit(IntegerJassValueVisitor.getInstance()));
			final boolean changed = (attack != null) && writeString(attack, fieldId(arguments, 1),
					arguments.get(3).visit(StringJassValueVisitor.getInstance()));
			if (changed) {
				unit.notifyAttacksChanged();
			}
			return BooleanJassValue.of(changed);
		});
	}
}
