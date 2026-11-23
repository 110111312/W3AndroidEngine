package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The army a base trains when nothing goes wrong, one list per race.
 *
 * <p>
 * Read off the melee AI's own orders -- {@code Scripts\Melee_V0_Scripts_elf.ai}, {@code _human.ai}, {@code _orc.ai},
 * {@code _undead.ai}, which is what {@code Blizzard.j}'s {@code MeleeStartingAI} picks; the same family
 * {@link RaceBuildOrder} was read from. Each line is one unit type the script asks for, at the largest cumulative
 * total it asks for, in the order the script first asks for it.
 *
 * <ul>
 * <li><b>totals, not increments</b> -- the script's {@code SetBuildUnit(total, TYPE)} is "have this many", so the
 * largest number it ever names for a type is that type's place in the army;</li>
 * <li><b>one line per type</b> -- the script's branches (a food pool split between archers and dryads, "second mine"
 * extras) ask for the same type more than once; the union is what a full base ends up with;</li>
 * <li><b>workers first</b> -- every script trains workers before soldiers, which is why the base's worker tickets are
 * seeded ahead of its buildings; a worker is one unit of income for the rest of the mission;</li>
 * <li><b>no heroes</b> -- the script's hero is a rawcode picked at runtime and the hero is never a ticket.</li>
 * </ul>
 *
 * The numbers are the bot's, not a balance opinion: this is what the computer player builds when it is left alone, and a
 * player who wants something else edits the list. Nothing here is per-campaign -- a mission's own tech limits drop the
 * lines it forbids, exactly as they do for the building plan.
 */
public final class RaceUnitOrder {
	/**
	 * One unit type's place in the army.
	 *
	 * @param count  how many the bot asks to have, in total
	 * @param worker whether this is the race's worker: seeded before the buildings rather than after them
	 */
	public record Line(War3ID rawcode, int count, boolean worker) {
	}

	private RaceUnitOrder() {
	}

	/**
	 * The army for this race, in the sequence a base should train it. Empty for a race this table does not cover
	 * (naga, creeps), which leaves the plan with the building list and nothing to train by itself.
	 */
	public static List<Line> forRace(final CUnitRace race) {
		if (race == null) {
			return List.of();
		}
		switch (race) {
		case HUMAN:
			return human();
		case ORC:
			return orc();
		case UNDEAD:
			return undead();
		case NIGHTELF:
			return nightElf();
		default:
			return List.of();
		}
	}

	private static List<Line> human() {
		return List.of(
				new Line(id("hpea"), 13, true), // Peasant
				new Line(id("hfoo"), 6, false), // Footman
				new Line(id("hrif"), 4, false), // Rifleman
				new Line(id("hkni"), 4, false), // Knight
				new Line(id("hmtm"), 2, false), // Mortar Team
				new Line(id("hmpr"), 3, false), // Priest
				new Line(id("hsor"), 2, false), // Sorceress
				new Line(id("hgyr"), 2, false), // Flying Machine
				new Line(id("hgry"), 2, false)); // Gryphon Rider
	}

	private static List<Line> orc() {
		return List.of(
				new Line(id("opeo"), 10, true), // Peon
				new Line(id("ogru"), 5, false), // Grunt
				new Line(id("ohun"), 6, false), // Headhunter
				new Line(id("orai"), 4, false), // Raider
				new Line(id("owyv"), 3, false), // Wind Rider
				new Line(id("ocat"), 1, false), // Demolisher
				new Line(id("oshm"), 2, false), // Shaman
				new Line(id("odoc"), 1, false), // Witch Doctor
				new Line(id("okod"), 1, false), // Kodo Beast
				new Line(id("otau"), 2, false)); // Tauren
	}

	private static List<Line> undead() {
		return List.of(
				new Line(id("uaco"), 5, true), // Acolyte
				new Line(id("ugho"), 9, false), // Ghoul
				new Line(id("ucry"), 4, false), // Crypt Fiend
				new Line(id("ugar"), 6, false), // Gargoyle
				new Line(id("uabo"), 4, false), // Abomination
				new Line(id("umtw"), 2, false), // Meat Wagon
				new Line(id("ufro"), 1, false), // Frost Wyrm
				new Line(id("unec"), 3, false), // Necromancer
				new Line(id("uban"), 2, false)); // Banshee
	}

	private static List<Line> nightElf() {
		return List.of(
				new Line(id("ewsp"), 11, true), // Wisp
				new Line(id("earc"), 6, false), // Archer
				new Line(id("esen"), 6, false), // Huntress
				new Line(id("edry"), 4, false), // Dryad
				new Line(id("edoc"), 3, false), // Druid of the Claw
				new Line(id("edot"), 2, false), // Druid of the Talon
				new Line(id("ebal"), 2, false), // Glaive Thrower
				new Line(id("echm"), 2, false)); // Chimaera
	}

	private static War3ID id(final String rawcode) {
		return War3ID.fromString(rawcode);
	}
}
