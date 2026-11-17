package com.etheller.warsmash.parsers.jass;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.badlogic.gdx.graphics.Color;
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
import com.etheller.warsmash.units.GameObject;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.util.WarsmashConstants;
import com.etheller.warsmash.viewer5.handlers.w3x.War3MapViewer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbilityDisableType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.util.CAbilityAuraBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.template.AbstractCAbilityAbilityBuilderAuraTemplate;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayerJass;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.sound.CSound;

/**
 * Reforged natives on units, items, text and the game environment that need little of their own: the height of a
 * unit, counted pauses, resetting an attack, aura switches, ability cooldowns as numbers or percent, skins,
 * tag text, the terrain fog, and the ones that only concern a client the engine does not have (a video
 * recorder, the test harness of the original's developers).
 */
final class ReforgedMiscNatives {
	private static final Pattern TAG = Pattern.compile("<([^<>,]+),([^<>,]+)(?:,([^<>]*))?>");

	private final JassProgram program;
	private final War3MapViewer viewer;
	private final CSimulation simulation;
	private int minShadowPointLights;

	private ReforgedMiscNatives(final JassProgram program, final War3MapViewer viewer) {
		this.program = program;
		this.viewer = viewer;
		this.simulation = viewer.simulation;
	}

	static void register(final JassProgram program, final War3MapViewer viewer) {
		final ReforgedMiscNatives natives = new ReforgedMiscNatives(program, viewer);
		natives.registerUnits();
		natives.registerCooldowns();
		natives.registerSkins();
		natives.registerText();
		natives.registerEnvironment();
		natives.registerClientOnly();
		natives.registerTriggerState();
		natives.registerWithoutCounterpart();
	}

	private void add(final String name, final JassFunction function) {
		this.program.getJassNativeManager().createNative(name, function);
	}

	private static <T> T object(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(ObjectJassValueVisitor.<T>getInstance());
	}

	private static String string(final List<JassValue> arguments, final int index) {
		final JassValue value = arguments.get(index);
		return value == null ? null : value.visit(StringJassValueVisitor.getInstance());
	}

	private static int integer(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(IntegerJassValueVisitor.getInstance());
	}

	private static float real(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(RealJassValueVisitor.getInstance()).floatValue();
	}

	private static boolean bool(final List<JassValue> arguments, final int index) {
		return arguments.get(index).visit(BooleanJassValueVisitor.getInstance());
	}

	private static int constant(final List<JassValue> arguments, final int index) {
		final Object value = ReforgedMiscNatives.<Object>object(arguments, index);
		return value instanceof ConvertedConstant ? ((ConvertedConstant) value).getValue() : 0;
	}

	private static CUnit inGame(final CUnit unit) {
		return (unit == null) || unit.isRemovedFromGame() ? null : unit;
	}

	private void registerUnits() {
		// The height the unit stands at in the world: the ground under it and its fly height.
		add("BlzGetUnitZ", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return RealJassValue.ZERO;
			}
			final float ground = this.viewer.terrain == null ? 0
					: this.viewer.terrain.getGroundHeight(unit.getX(), unit.getY());
			return RealJassValue.of(ground + unit.getFlyHeight());
		});
		add("BlzPauseUnitEx", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = object(arguments, 0);
			if (unit != null) {
				unit.pauseCounted(bool(arguments, 1), this.simulation);
			}
			return null;
		});
		// The weapon index is the script's; the unit keeps one attack timer, which this makes ready.
		add("BlzResetUnitAttack", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setCooldownEndTime(this.simulation.getGameTurnTick());
			}
			return null;
		});
		// Aura abilities stop giving their effect while disabled; the ones from items and buffs count as well.
		add("BlzUnitEnableAuras", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final boolean enable = bool(arguments, 1);
				for (final CAbility ability : unit.getAbilities()) {
					if ((ability instanceof CAbilityAuraBase)
							|| (ability instanceof AbstractCAbilityAbilityBuilderAuraTemplate)) {
						ability.setDisabled(!enable, CAbilityDisableType.DISABLED_EFFECT);
					}
				}
			}
			return null;
		});
	}

	private static War3ID cooldownKey(final CUnit unit, final War3ID alias) {
		final CAbility ability = unit.getAbilityByRawcode(alias);
		return ability == null ? alias : ability.getCode();
	}

	/** The cooldown that is running under this ability, by its alias or the code it is made from. */
	private War3ID runningKey(final CUnit unit, final War3ID alias) {
		final War3ID code = cooldownKey(unit, alias);
		return unit.getCooldownRemainingTicks(this.simulation, code) > unit.getCooldownRemainingTicks(this.simulation,
				alias) ? code : alias;
	}

	private float remainingSeconds(final CUnit unit, final War3ID alias) {
		return unit.getCooldownRemainingTicks(this.simulation, runningKey(unit, alias))
				* WarsmashConstants.SIMULATION_STEP_TIME;
	}

	private float lengthSeconds(final CUnit unit, final War3ID alias) {
		return unit.getCooldownLengthDisplayTicks(this.simulation, runningKey(unit, alias))
				* WarsmashConstants.SIMULATION_STEP_TIME;
	}

	private void setRemaining(final CUnit unit, final War3ID alias, final float seconds) {
		unit.beginCooldown(this.simulation, runningKey(unit, alias), Math.max(0f, seconds));
	}

	/** The cooldown natives read the running cooldown, and a change starts one of the new remaining time. */
	private void registerCooldowns() {
		// Percent runs 0 to 100, like the other percent natives (GetUnitLifePercent).
		add("BlzGetUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return RealJassValue.ZERO;
			}
			final War3ID alias = new War3ID(integer(arguments, 1));
			final float length = lengthSeconds(unit, alias);
			return RealJassValue.of(length <= 0 ? 0 : (remainingSeconds(unit, alias) * 100f) / length);
		});
		add("BlzSetUnitAbilityCooldownRemaining", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				setRemaining(unit, new War3ID(integer(arguments, 1)), real(arguments, 2));
			}
			return null;
		});
		add("BlzSetUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias, (lengthSeconds(unit, alias) * real(arguments, 2)) / 100f);
			}
			return null;
		});
		add("BlzAdjustUnitAbilityCooldownRemaining", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias, remainingSeconds(unit, alias) + real(arguments, 2));
			}
			return null;
		});
		add("BlzAdjustUnitAbilityCooldownPercent", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				final War3ID alias = new War3ID(integer(arguments, 1));
				setRemaining(unit, alias,
						remainingSeconds(unit, alias) + ((lengthSeconds(unit, alias) * real(arguments, 2)) / 100f));
			}
			return null;
		});
	}

	private void registerSkins() {
		add("BlzGetUnitSkin", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit == null) {
				return IntegerJassValue.ZERO;
			}
			final War3ID skin = unit.getScriptSkinId();
			return IntegerJassValue.of((skin != null ? skin : unit.getTypeId()).getValue());
		});
		// A unit keeps the skin it is given and reads it back; the engine draws one model per unit type, so the
		// picture of a unit does not change.
		add("BlzSetUnitSkin", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setScriptSkinId(new War3ID(integer(arguments, 1)));
			}
			return null;
		});
		add("BlzGetItemSkin", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			return IntegerJassValue.of(item == null ? 0 : item.getSkinId().getValue());
		});
		add("BlzSetItemSkin", (arguments, globalScope, triggerScope) -> {
			final CItem item = object(arguments, 0);
			if (item != null) {
				final War3ID skin = new War3ID(integer(arguments, 1));
				if (this.viewer.setItemSkin(item, skin)) {
					item.setSkinId(skin);
				}
			}
			return null;
		});
	}

	private GameObject abilityData(final String alias) {
		try {
			return this.viewer.getAllObjectData().getAbilities().get(War3ID.fromString(alias));
		}
		catch (final RuntimeException exc) {
			return null;
		}
	}

	private static String number(final float value) {
		return Math.abs(value - Math.round(value)) < 0.0005f ? Integer.toString(Math.round(value))
				: Float.toString(Math.round(value * 100f) / 100f);
	}

	/**
	 * ParseTags: every {@code <ability,column>} of the text becomes the value of that column of the ability data
	 * (Dur1, DataA2, Cool1, ...); a third part "%" gives the value times 100 with a percent sign. Text that
	 * is no tag stays as it is.
	 */
	private void registerText() {
		add("ParseTags", (arguments, globalScope, triggerScope) -> {
			final String tagged = string(arguments, 0);
			if (tagged == null) {
				return new StringJassValue("");
			}
			final Matcher matcher = TAG.matcher(tagged);
			final StringBuffer result = new StringBuffer();
			while (matcher.find()) {
				final GameObject data = abilityData(matcher.group(1).trim());
				String replacement = matcher.group(0);
				if (data != null) {
					final String value = data.getField(matcher.group(2).trim());
					if ((value != null) && !value.isEmpty()) {
						float parsed = Float.NaN;
						try {
							parsed = Float.parseFloat(value.trim());
						}
						catch (final NumberFormatException exc) {
							// Text values are put in as they are.
						}
						if (Float.isNaN(parsed)) {
							replacement = value;
						}
						else if ((matcher.group(3) != null) && matcher.group(3).trim().equals("%")) {
							replacement = number(parsed * 100f) + "%";
						}
						else {
							replacement = number(parsed);
						}
					}
				}
				matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
			}
			matcher.appendTail(result);
			return new StringJassValue(result.toString());
		});
		add("BlzForceHasPlayer", (arguments, globalScope, triggerScope) -> {
			final List<CPlayerJass> force = object(arguments, 0);
			final CPlayer player = object(arguments, 1);
			return BooleanJassValue.of((force != null) && (player != null) && force.contains(player));
		});
		// The data of this engine is read for one language folder; its text is the one of the English game.
		add("BlzGetLocale", (arguments, globalScope, triggerScope) -> new StringJassValue("enUS"));
		add("BlzConvertColor", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(((integer(arguments, 0) & 0xFF) << 24) | ((integer(arguments, 1) & 0xFF) << 16)
						| ((integer(arguments, 2) & 0xFF) << 8) | (integer(arguments, 3) & 0xFF)));
	}

	private void registerEnvironment() {
		// The one terrain fog of the renderer: the z range and the linear range both set its range.
		add("BlzSetTerrainFogStyle", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.setStyleByIndex(constant(arguments, 0) + 1);
			return null;
		});
		add("BlzSetTerrainFogZStart", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.start = real(arguments, 0);
			return null;
		});
		add("BlzSetTerrainFogZEnd", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.end = real(arguments, 0);
			return null;
		});
		add("BlzSetTerrainFogLinearStart", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.start = real(arguments, 0);
			return null;
		});
		add("BlzSetTerrainFogLinearEnd", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.end = real(arguments, 0);
			return null;
		});
		add("BlzSetTerrainFogDensity", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.density = real(arguments, 0);
			return null;
		});
		add("BlzSetTerrainFogColor", (arguments, globalScope, triggerScope) -> {
			this.viewer.worldScene.fogSettings.color = new Color(real(arguments, 0), real(arguments, 1),
					real(arguments, 2), 1f);
			return null;
		});
		add("StartSoundEx", (arguments, globalScope, triggerScope) -> {
			final CSound sound = object(arguments, 0);
			// A fade in is not supported: the sound starts at its full volume.
			if (sound != null) {
				sound.start(this.simulation.getGameTurnTick());
			}
			return null;
		});
	}

	private void ignored(final String reason, final String... names) {
		for (final String name : names) {
			add(name, (arguments, globalScope, triggerScope) -> null);
		}
		if (reason.isEmpty()) {
			throw new IllegalStateException();
		}
	}

	/** The threads a trigger has running or sleeping, counting the one that asks. */
	private List<com.etheller.interpreter.ast.execution.JassThread> threadsOf(
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger,
			final com.etheller.interpreter.ast.scope.GlobalScope globalScope) {
		final List<com.etheller.interpreter.ast.execution.JassThread> found = new java.util.ArrayList<>();
		final List<com.etheller.interpreter.ast.execution.JassThread> all = new java.util.ArrayList<>(
				globalScope.getThreads());
		all.addAll(globalScope.getNewThreads());
		for (final com.etheller.interpreter.ast.execution.JassThread thread : all) {
			if ((thread.triggerScope != null) && (thread.triggerScope.getTriggeringTrigger() == trigger)
					&& !thread.isHalted()) {
				found.add(thread);
			}
		}
		return found;
	}

	private void registerTriggerState() {
		add("BlzTriggerIsRunning", (arguments, globalScope, triggerScope) -> {
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = object(arguments, 0);
			if (trigger == null) {
				return BooleanJassValue.FALSE;
			}
			return BooleanJassValue.of((triggerScope.getTriggeringTrigger() == trigger)
					|| !threadsOf(trigger, globalScope).isEmpty());
		});
		// The actions of the trigger end where they are: sleeping ones are halted, the running one stops at its
		// next instruction.
		add("BlzTriggerInterrupt", (arguments, globalScope, triggerScope) -> {
			final com.etheller.interpreter.ast.scope.trigger.Trigger trigger = object(arguments, 0);
			if (trigger != null) {
				for (final com.etheller.interpreter.ast.execution.JassThread thread : threadsOf(trigger,
						globalScope)) {
					thread.halt();
				}
			}
			return null;
		});
		add("AllowHeroGlowOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setHeroGlowAllowed(true);
			}
			return null;
		});
		add("DisallowHeroGlowOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			if (unit != null) {
				unit.setHeroGlowAllowed(false);
			}
			return null;
		});
		add("HeroGlowIsAllowedOnUnit", (arguments, globalScope, triggerScope) -> {
			final CUnit unit = inGame(object(arguments, 0));
			return BooleanJassValue.of((unit != null) && unit.isHeroGlowAllowed());
		});
	}

	/**
	 * Settings of features this engine does not have: HD water, height fog, point light shadows, the hero glow
	 * model, the minimap picture, camera blockers and the sky and terrain switches. A map that sets them runs on;
	 * the switches that read back (point light count) keep what was set.
	 */
	private void registerWithoutCounterpart() {
		ignored("no height fog in the renderer", "BlzSetTerrainFogHeightStart", "BlzSetTerrainFogHeightEnd");
		ignored("no HD water in the renderer", "SetHDWaterParams", "BlzSetHDWaterColor", "BlzSetHDWaterColorOverride",
				"BlzSetHDWaterVertexDisplacement", "BlzSetHDWaterMinOpacity", "BlzSetHDWaterMaxOpacity",
				"BlzSetHDWaterReflectivity", "BlzSetHDWaterEmissivity", "BlzSetHDWaterEdgeSoftness",
				"BlzSetHDWaterWaveStrength", "BlzSetHDWaterEnvMapStrength");
		ignored("portrait lights come from the portrait model", "SetPortraitLight");
		ignored("music keeps playing when the window loses focus on this engine", "BlzPauseThematicMusicOnFocusLost");
		ignored("no camera blockers in the camera of this engine", "EnableCameraBlocker", "AddCameraBlocker");
		ignored("terrain and sky are drawn as long as the map is", "BlzShowTerrain", "BlzShowSkyBox");
		ignored("no checkpoint saves in this engine", "SetMaxCheckpointSaves");
		add("BlzChangeMinimapTerrainTex", (arguments, globalScope, triggerScope) -> BooleanJassValue.FALSE);
		add("BlzSetMinShadowCastingPointLightCount", (arguments, globalScope, triggerScope) -> {
			this.minShadowPointLights = integer(arguments, 0);
			return null;
		});
		add("BlzGetMinShadowCastingPointLightCount", (arguments, globalScope, triggerScope) -> IntegerJassValue
				.of(this.minShadowPointLights));
	}

	/** What only the client of the original has something to do with: a video recorder and its test harness. */
	private void registerClientOnly() {
		ignored("no video recorder in this engine", "BlzStartRecording", "BlzEndRecording");
		ignored("quality assurance test harness of the original", "AutomationSetTestType",
				"AutomationTestingFinished");
	}
}
