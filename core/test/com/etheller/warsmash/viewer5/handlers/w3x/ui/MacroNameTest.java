package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * What the panel calls a thing. The command card's tooltip is markup -- a colour code and the verb of the button that
 * builds or trains it -- and the panel has one line to put a name in, so the markup has to come off first.
 */
class MacroNameTest {
	@Test
	void aBuildButtonsTooltipIsTheNameOfWhatItMakes() {
		assertEquals("Moon Well", MacroPanel.nameIn("Create |cffffcc00Moon Well|r"));
	}

	@Test
	void theVerbComesOffWhetherItIsCapitalisedOrNot() {
		assertEquals("Archer", MacroPanel.nameIn("train Archer"));
		assertEquals("Strength of the Moon", MacroPanel.nameIn("Research Strength of the Moon"));
	}

	@Test
	void anUpgradeThatSaysUpgradeToLosesThePreposition() {
		assertEquals("Strength of the Wild", MacroPanel.nameIn("Upgrade to Strength of the Wild"));
	}

	@Test
	void aNameThatIsAlreadyANameIsLeftAlone() {
		assertEquals("Ancient of War", MacroPanel.nameIn("Ancient of War"));
	}

	@Test
	void colourCodesInTheMiddleAreRemovedRatherThanTruncatingTheName() {
		assertEquals("Druid of the Claw", MacroPanel.nameIn("Create |cffffcc00Druid|r of the Claw"));
	}
}
