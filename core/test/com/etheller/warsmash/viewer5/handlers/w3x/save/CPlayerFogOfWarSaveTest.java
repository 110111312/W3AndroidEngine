package com.etheller.warsmash.viewer5.handlers.w3x.save;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.parsers.w3x.wpm.War3MapWpm;
import com.etheller.warsmash.viewer5.handlers.w3x.environment.PathingGrid;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CFogMaskSettings;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.vision.CPlayerFogOfWar;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.trigger.enumtypes.CFogState;

/**
 * The fog of war a save was written with is the fog of war the loaded game is played with: what the player had explored
 * comes back, and the cells the terrain and the minimap read keep following the simulation afterwards.
 */
class CPlayerFogOfWarSaveTest {
	private static final int SIZE = 256;
	private static final String FOG_CLASS = CPlayerFogOfWar.class.getName();

	private static final CFogMaskSettings SETTINGS = new CFogMaskSettings() {
		@Override
		public boolean isFogMaskEnabled() {
			return true;
		}

		@Override
		public boolean isFogEnabled() {
			return true;
		}

		@Override
		public byte getFogStateFromSettings(final byte state) {
			return state;
		}
	};

	/**
	 * What {@link SavedGameRules} does for the fog: one named external per player, stored in place because the loading
	 * game already has the object and only its fields come back.
	 */
	private static final class FogRules implements SavedObjectGraph.Rules {
		private final Map<String, Object> externals = new HashMap<>();

		@Override
		public String externalName(final Object object) {
			return object instanceof CPlayerFogOfWar ? "fog:0" : null;
		}

		@Override
		public boolean isInPlace(final Object object) {
			return object instanceof CPlayerFogOfWar;
		}

		@Override
		public boolean isDropped(final Object object) {
			return false;
		}

		@Override
		public Object replace(final Object object) {
			return object;
		}

		@Override
		public Object resolveExternal(final String name) {
			return this.externals.get(name);
		}

		@Override
		public void reportDropped(final Class<?> holder, final String field, final Object object) {
		}
	}

	/** Holds the fog so the graph has a root that is not the fog itself. */
	static final class Holder {
		CPlayerFogOfWar fog;
	}

	/** A save of one fog, as the two byte streams a saved game file carries. */
	private static final class Save {
		private final byte[] table;
		private final byte[] bodies;

		private Save(final CPlayerFogOfWar fog) throws IOException {
			final Holder holder = new Holder();
			holder.fog = fog;
			final ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
			try (DataOutputStream bodies = new DataOutputStream(bodyBytes)) {
				final SavedObjectGraph.Writer writer = new SavedObjectGraph.Writer(new FogRules(), bodies);
				writer.writeRoot(holder);
				this.table = writer.finishTable();
			}
			this.bodies = bodyBytes.toByteArray();
		}

		private CPlayerFogOfWar load(final PathingGrid grid) throws IOException {
			SavedObjectGraph.checkClassesMatch(this.table);
			final FogRules rules = new FogRules();
			final CPlayerFogOfWar fresh = new CPlayerFogOfWar(grid);
			rules.externals.put("fog:0", fresh);
			final Holder loaded;
			try (DataInputStream bodies = new DataInputStream(new ByteArrayInputStream(this.bodies))) {
				loaded = (Holder) new SavedObjectGraph.Reader(rules, this.table, bodies).readRoot();
			}
			assertSame(fresh, loaded.fog, "the load puts the save into the running game's fog object");
			return loaded.fog;
		}
	}

	private static PathingGrid grid() throws IOException {
		final War3MapWpm wpm = new War3MapWpm(null);
		wpm.getSize()[0] = SIZE;
		wpm.getSize()[1] = SIZE;
		wpm.setPathing(new short[SIZE * SIZE]);
		return new PathingGrid(wpm, new float[] { 0, 0 });
	}

	private static CPlayerFogOfWar saved() throws IOException {
		final CPlayerFogOfWar fog = new CPlayerFogOfWar(grid());
		fog.setVisible(10, 10, CFogState.VISIBLE);
		fog.setVisible(20, 20, CFogState.FOGGED);
		return fog;
	}

	/** The field names of the given class as the save's class table lists them. */
	private static Set<String> storedFieldNames(final byte[] table, final String className) throws IOException {
		final DataInputStream in = new DataInputStream(new ByteArrayInputStream(table));
		in.readInt();
		final int classCount = in.readInt();
		for (int i = 0; i < classCount; i++) {
			final String name = in.readUTF();
			final int fieldCount = in.readInt();
			final Set<String> names = new LinkedHashSet<>();
			for (int j = 0; j < fieldCount; j++) {
				final String[] parts = in.readUTF().split("#", 3);
				names.add(parts[1]);
			}
			if (className.equals(name)) {
				return names;
			}
		}
		throw new AssertionError(className + " is not in the save's class table");
	}

	/** A class table as a save of the given version of this engine wrote it, for the check a load makes first. */
	private static byte[] classTable(final String className, final String... fieldNames) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeInt(1);
			out.writeInt(1);
			out.writeUTF(className);
			out.writeInt(fieldNames.length);
			for (final String fieldName : fieldNames) {
				out.writeUTF(className + "#" + fieldName);
			}
		}
		return bytes.toByteArray();
	}

	@Test
	void exploredCellsComeBackAfterALoad() throws IOException {
		final CPlayerFogOfWar loaded = new Save(saved()).load(grid());

		assertEquals(CFogState.VISIBLE, loaded.getFogState(SETTINGS, 10, 10), "a visible cell stays visible");
		assertEquals(CFogState.FOGGED, loaded.getFogState(SETTINGS, 20, 20), "a fogged cell stays fogged");
		assertEquals(CFogState.MASKED, loaded.getFogState(SETTINGS, 40, 40), "an unexplored cell stays masked");
		assertArrayEquals(saved().getFogOfWarBuffer().array(), loaded.getFogOfWarBuffer().array(),
				"the cells the terrain and the minimap read are the cells that were saved");
	}

	@Test
	void theMaskKeepsFollowingTheSimulationAfterALoad() throws IOException {
		final CPlayerFogOfWar fog = saved();
		final Save save = new Save(fog);
		final Save again = new Save(fog);
		assertArrayEquals(save.table, again.table, "the same fog saves to the same class table");

		final CPlayerFogOfWar loaded = save.load(grid());
		loaded.setVisible(50, 50, CFogState.VISIBLE);

		final int revealed = (50 * loaded.getWidth()) + 50;
		assertEquals(0, loaded.getFogOfWarBuffer().array()[revealed],
				"a cell the units reveal after the load is visible to whatever reads the fog buffer");
		final int stayed = (60 * loaded.getWidth()) + 60;
		assertEquals(-1, loaded.getFogOfWarBuffer().array()[stayed], "and a cell they did not is still masked");
	}

	@Test
	void theBufferIsNotPartOfTheSave() throws IOException {
		final Set<String> names = storedFieldNames(new Save(saved()).table, FOG_CLASS);

		assertTrue(names.containsAll(Arrays.asList("width", "height", "fogOfWar", "detection")),
				"the mask itself is stored: " + names);
		assertFalse(names.contains("fogOfWarBuffer"),
				"the renderer's view of the mask is derived, not stored next to it: " + names);
	}

	@Test
	void aSaveThatStillHoldsTheBufferIsNotRefused() throws IOException {
		final byte[] old = classTable(FOG_CLASS, "width#int", "height#int", "fogOfWar#[B", "detection#[B",
				"fogOfWarBuffer#java.nio.ByteBuffer");

		SavedObjectGraph.checkClassesMatch(old);
	}
}
