package com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro;

/**
 * Supply is a status line, not a ticket, and it outranks every number.
 * <p>
 * The food building is not something the player puts in the list: it is picked from the player's own build list by its
 * food stat ({@link LiveBaseDefaults#supplyType}) and built when the army is about to hit the cap, one at a time, never
 * past the map's ceiling. Two consequences worth naming, because they are the whole reason this is a rule and not a
 * ticket:
 * <ul>
 * <li><b>it keeps the cap ahead of the army, not behind it</b> -- the test is the army plus a headroom of
 * {@link #SUPPLY_HEADROOM} food against the cap, so the farm is ordered while there is still room to train, not once a
 * train has already been refused. {@code incomingFoodDemand} is what the army is about to ask for on top of that (the
 * unit plan's next train, once E2 exists; zero today);</li>
 * <li><b>a dead farm rebuilds itself</b> -- the food cap is the sum of the living farms, so a farm dying drops the cap
 * under the food already used and the same test fires. There is no "rebuild" branch to write.</li>
 * </ul>
 * The cap used for the ceiling test is the raw one ({@code CPlayer.getFoodCap}), not the limited one: the limited value
 * is clamped to the ceiling and replaced outright by the no-food-limit cheat, and either would make the ceiling test
 * answer the wrong question.
 */
public final class MacroSupplyRule {
	/**
	 * How far ahead of the cap the supply line starts building.
	 * <p>
	 * Seven is the melee AI's own number -- {@code Scripts\elf.ai:551} asks for another moon well when
	 * {@code c_food_used + 7 > c_food_made} -- and it is headroom rather than a threshold at the cap for the reason the
	 * bot has it there: a farm begun at the cap finishes after the train that needed it was already refused.
	 */
	public static final int SUPPLY_HEADROOM = 7;

	private MacroSupplyRule() {
	}

	public static boolean wanted(final boolean typeKnown, final boolean typeStuck, final int foodUsed,
			final int incomingFoodDemand, final int foodCap, final int foodCapLimit, final int foodCapCeiling,
			final int foodMade, final boolean paused, final int supplyInFlight) {
		if (paused || !typeKnown || typeStuck) {
			// Nothing to build it with, or the player paused the line: supply keeps out of the way entirely.
			return false;
		}
		if (foodMade <= 0) {
			return false;
		}
		if (supplyInFlight > 0) {
			// At most one at a time: the cap it makes only arrives when it finishes, so a second would be blind.
			return false;
		}
		if ((foodCap + foodMade) > foodCapCeiling) {
			// The map's ceiling: the next farm would go past what this map allows, so it stops here.
			return false;
		}
		return (foodUsed + incomingFoodDemand + SUPPLY_HEADROOM) > foodCapLimit;
	}
}
