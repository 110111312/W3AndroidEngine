package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

import java.util.List;

import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CUnitRace;

/**
 * The order a base builds in when nothing goes wrong, one list per race.
 * <p>
 * Read off the melee AI's opening -- {@code Scripts\elf.ai:456-500}, {@code Scripts_human.ai}, {@code Scripts_orc.ai},
 * {@code Scripts_undead.ai}, all inside the MPQ's {@code basic_opening} branch -- and then cut down to what belongs in
 * a building plan. The order is theirs; the contents are not a copy:
 * <ul>
 * <li><b>units are gone</b> -- they are the unit plan's tickets (E2), not this list's;</li>
 * <li><b>supply buildings are gone</b> -- the AI sprinkles farms and moon wells through its opening because it has no
 * separate supply concept; ours is a standing rule that keeps the cap ahead of the army, so a farm here would be the
 * same decision made twice;</li>
 * <li><b>hall upgrades and researches are inline</b> -- the AI treats "teeth into a keep" as one more step of the same
 * sequence, which is what makes the list readable: production first, then the tier that unlocks more, then what the
 * tier-2 buildings research.</li>
 * </ul>
 * A ticket that arrives before its requirement is met is not a bug and not a stall: the ability gate reports the
 * missing requirement, the number is passed over while it waits, and it runs once a lower number has supplied the
 * requirement. That is why the lists can be one flat sequence instead of a tree.
 * <p>
 * The lists stop at "one of each". The AI's own order is the same shape -- it never asks for a second barracks -- and a
 * plan the player edits is better served by a list that ends than one that repeats.
 * <p>
 * Every call builds fresh tickets. The executor keys its per-ticket bookkeeping on object identity, so two games
 * sharing one list would share the sites and refusals recorded against it.
 */
public final class RaceBuildOrder {
	private RaceBuildOrder() {
	}

	/**
	 * The order for this race, in the sequence the base should work through it. Empty for a race this table does not
	 * cover (naga, creeps), which leaves the base with its supply line and nothing else to do by itself.
	 */
	public static List<MacroTicket> forRace(final CUnitRace race) {
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

	private static List<MacroTicket> human() {
		return List.of(
				MacroTicket.build(id("halt")), // Altar of Kings
				MacroTicket.build(id("hbar")), // Barracks
				MacroTicket.build(id("hbla")), // Blacksmith
				MacroTicket.build(id("hvlt")), // Arcane Vault
				MacroTicket.build(id("hlum")), // Lumber Mill
				MacroTicket.upgrade(id("hkee")), // Keep
				MacroTicket.research(id("Rhde")), // Defend
				MacroTicket.research(id("Rhar")), // armor plating
				MacroTicket.research(id("Rhme")), // melee weapons
				MacroTicket.build(id("harm")), // Workshop
				MacroTicket.upgrade(id("hcas")), // Castle
				MacroTicket.research(id("Rhra")), // ranged weapons
				MacroTicket.build(id("hars")), // Arcane Sanctum
				MacroTicket.research(id("Rhlh")), // lumber harvesting
				MacroTicket.build(id("hgra"))); // Gryphon Aviary
	}

	private static List<MacroTicket> orc() {
		return List.of(
				MacroTicket.build(id("oalt")), // Altar of Storms
				MacroTicket.build(id("obar")), // Barracks
				MacroTicket.build(id("ofor")), // Forge
				MacroTicket.research(id("Rome")), // melee weapons
				MacroTicket.build(id("ovln")), // Voodoo Lounge
				MacroTicket.research(id("Rora")), // ranged weapons
				MacroTicket.research(id("Roar")), // armor
				MacroTicket.upgrade(id("ostr")), // Stronghold
				MacroTicket.research(id("Rosp")), // Spiked Barricades
				MacroTicket.research(id("Ropg")), // Pillage
				MacroTicket.build(id("osld")), // Spirit Lodge
				MacroTicket.build(id("obea")), // Beastiary
				MacroTicket.upgrade(id("ofrt")), // Fortress
				MacroTicket.build(id("otto"))); // Tauren Totem
	}

	private static List<MacroTicket> undead() {
		return List.of(
				MacroTicket.build(id("usep")), // Crypt
				MacroTicket.build(id("uaod")), // Altar of Darkness
				MacroTicket.research(id("Ruac")), // Cannibalize
				MacroTicket.build(id("ugrv")), // Graveyard
				MacroTicket.upgrade(id("unp1")), // Halls of the Dead
				MacroTicket.build(id("utom")), // Tomb of Relics
				MacroTicket.research(id("Ruwb")), // Web
				MacroTicket.build(id("uslh")), // Slaughterhouse
				MacroTicket.build(id("utod")), // Temple of the Damned
				MacroTicket.upgrade(id("unp2")), // Black Citadel
				MacroTicket.build(id("usap")), // Sacrificial Pit
				MacroTicket.build(id("ubon"))); // Boneyard
	}

	private static List<MacroTicket> nightElf() {
		return List.of(
				MacroTicket.build(id("eate")), // Altar of Elders
				MacroTicket.build(id("eaom")), // Ancient of War
				MacroTicket.build(id("eden")), // Ancient of Wonders
				MacroTicket.build(id("edob")), // Hunter's Hall
				MacroTicket.upgrade(id("etoa")), // Tree of Ages
				MacroTicket.research(id("Resm")), // Strength of the Moon
				MacroTicket.research(id("Rema")), // Moon Armor
				MacroTicket.research(id("Resw")), // Strength of the Wild
				MacroTicket.research(id("Rerh")), // Reinforced Hides
				MacroTicket.build(id("eaoe")), // Ancient of Lore
				MacroTicket.research(id("Redc")), // Druid of the Claw training
				MacroTicket.upgrade(id("etoe")), // Tree of Eternity
				MacroTicket.build(id("eaow")), // Ancient of Wind
				MacroTicket.build(id("edos"))); // Chimaera Roost
	}

	private static War3ID id(final String rawcode) {
		return War3ID.fromString(rawcode);
	}
}
