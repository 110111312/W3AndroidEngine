package com.etheller.warsmash.datasources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.launcher.LauncherOptions;
import com.etheller.warsmash.units.DataTable;
import com.etheller.warsmash.util.StringBundle;

class RenderScaleTest {
	@Test
	void fullSizeWithoutASetting() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		assertEquals(100, GraphicsSettings.renderScalePercent(ini));
		assertEquals(1f, GraphicsSettings.renderScale(ini));
	}

	@Test
	void settingIsKeptWithinTheRange() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 0, 70);
		assertEquals(70, GraphicsSettings.renderScalePercent(ini));
		assertEquals(0.7f, GraphicsSettings.renderScale(ini), 1e-6f);
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 0, 10);
		assertEquals(50, GraphicsSettings.renderScalePercent(ini));
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 0, 400);
		assertEquals(100, GraphicsSettings.renderScalePercent(ini));
	}

	@Test
	void unreadableValueMeansFullSize() {
		final DataTable ini = new DataTable(StringBundle.EMPTY);
		GraphicsSettings.apply(ini, GraphicsSet.CLASSIC, 0);
		ini.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_RENDER_SCALE, "fast");
		assertEquals(100, GraphicsSettings.renderScalePercent(ini));
		ini.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_RENDER_SCALE, "85 %");
		assertEquals(85, GraphicsSettings.renderScalePercent(ini));
		ini.get(GraphicsSettings.SECTION).setField(GraphicsSettings.KEY_RENDER_SCALE, "85%");
		assertEquals(85, GraphicsSettings.renderScalePercent(ini));
	}

	@Test
	void choicesAreTheNearestOffered() {
		assertEquals(0, GraphicsSettings.renderScaleChoice(100));
		assertEquals(2, GraphicsSettings.renderScaleChoice(70));
		assertEquals(3, GraphicsSettings.renderScaleChoice(55));
		assertEquals(1, GraphicsSettings.renderScaleChoice(80));
		assertEquals(0, LauncherOptions.frameCapChoice(0));
		assertEquals(1, LauncherOptions.frameCapChoice(30));
		assertEquals(2, LauncherOptions.frameCapChoice(120));
		assertEquals(0, LauncherOptions.frameCapChoice(20));
	}
}
