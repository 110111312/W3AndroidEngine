package com.etheller.warsmash.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyEditorTest {
	private static HotkeyCatalog.Command command(final String section, final String key, final int x, final int y) {
		return new HotkeyCatalog.Command(section, CustomKeys.HOTKEY, section, key, x, y, null, "", "");
	}

	private static HotkeyCatalog.Card card(final HotkeyCatalog.Command... commands) {
		final HotkeyCatalog.Card card = new HotkeyCatalog.Card("Commands");
		for (final HotkeyCatalog.Command command : commands) {
			card.commands.add(command);
		}
		return card;
	}

	@Test
	void defaultsComeFromTheDataAndTheLayout(@TempDir final Path folder) {
		final HotkeyCatalog.Command move = command("CmdMove", "M", 0, 0);
		final KeyEditor editor = new KeyEditor(new CustomKeys(false), folder.resolve("CustomKeys.txt").toFile());
		assertEquals("M", editor.keyOf(move));
		assertFalse(editor.changed(move));
		editor.keys().setGrid(true);
		assertEquals("Q", editor.keyOf(move));
		assertEquals("Q", editor.defaultKeyOf(move));
	}

	@Test
	void bindingKeepsOnlyWhatDiffersFromTheDefault(@TempDir final Path folder) throws IOException {
		final File file = folder.resolve("CustomKeys.txt").toFile();
		final HotkeyCatalog.Command move = command("CmdMove", "M", 0, 0);
		final KeyEditor editor = new KeyEditor(new CustomKeys(false), file);
		assertTrue(editor.bind(move, "G"));
		assertEquals("G", editor.keyOf(move));
		assertTrue(editor.changed(move));
		assertTrue(file.isFile());
		assertTrue(editor.bind(move, "M"));
		assertFalse(editor.changed(move));
		assertFalse(file.exists());
	}

	@Test
	void restoreAndResetGoBackToDefaults(@TempDir final Path folder) {
		final File file = folder.resolve("CustomKeys.txt").toFile();
		final HotkeyCatalog.Command move = command("CmdMove", "M", 0, 0);
		final HotkeyCatalog.Command stop = command("CmdStop", "S", 1, 0);
		final KeyEditor editor = new KeyEditor(new CustomKeys(false), file);
		editor.bind(move, "G");
		editor.bind(stop, "T");
		assertTrue(editor.restore(move));
		assertEquals("M", editor.keyOf(move));
		assertEquals("T", editor.keyOf(stop));
		assertTrue(editor.anyChanged());
		assertTrue(editor.resetAll());
		assertEquals("S", editor.keyOf(stop));
		assertFalse(editor.anyChanged());
		assertFalse(file.exists());
	}

	@Test
	void sameKeyOnOneCardIsAConflict() {
		final HotkeyCatalog.Command move = command("CmdMove", "M", 0, 0);
		final HotkeyCatalog.Command stop = command("CmdStop", "S", 1, 0);
		final HotkeyCatalog.Card card = card(move, stop);
		final KeyEditor editor = new KeyEditor(new CustomKeys(false), null);
		assertTrue(editor.conflicts(card).isEmpty());
		editor.bind(stop, "M");
		assertEquals(2, editor.conflicts(card).size());
		assertTrue(editor.conflicts(card).contains(move.id()));
		editor.restore(stop);
		assertTrue(editor.conflicts(card).isEmpty());
	}

	@Test
	void keysSurviveSavingAndReading(@TempDir final Path folder) throws IOException {
		final File file = folder.resolve("CustomKeys.txt").toFile();
		final HotkeyCatalog.Command move = command("CmdMove", "M", 0, 0);
		final HotkeyCatalog.Command learn = new HotkeyCatalog.Command("AHhb", CustomKeys.RESEARCH, "Holy Light", "T", 0, 0,
				null, "", "");
		final KeyEditor editor = new KeyEditor(new CustomKeys(false), file);
		editor.bind(move, "G");
		editor.bind(learn, "J");
		final String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		assertTrue(text.contains("[CmdMove]"));
		assertTrue(text.contains("Researchhotkey=J"));
		final CustomKeys read = CustomKeys.read(file, false);
		assertEquals("G", read.own("CmdMove", CustomKeys.HOTKEY));
		assertEquals("J", read.own("AHhb", CustomKeys.RESEARCH));
		assertNull(read.own("AHhb", CustomKeys.HOTKEY));
	}

	@Test
	void lettersDigitsAndPunctuationBind() {
		assertEquals("A", KeyEditor.keyOfChar('a'));
		assertEquals("7", KeyEditor.keyOfChar('7'));
		assertEquals("-", KeyEditor.keyOfChar('-'));
		assertEquals("[", KeyEditor.keyOfChar('['));
		assertEquals("44", KeyEditor.keyOfChar(','));
		assertEquals(",", CustomKeys.shown(KeyEditor.keyOfChar(',')));
		assertNull(KeyEditor.keyOfChar(' '));
		assertNull(KeyEditor.keyOfChar('~'));
		assertNull(KeyEditor.keyOfChar('\n'));
	}

	@Test
	void androidKeyCodesBindByTheirPlaceOnTheKeyboard() {
		assertEquals("A", KeyEditor.keyOfAndroidCode(29));
		assertEquals("G", KeyEditor.keyOfAndroidCode(35));
		assertEquals("Z", KeyEditor.keyOfAndroidCode(54));
		assertEquals("0", KeyEditor.keyOfAndroidCode(7));
		assertEquals("9", KeyEditor.keyOfAndroidCode(16));
		assertEquals("5", KeyEditor.keyOfAndroidCode(149));
		assertNull(KeyEditor.keyOfAndroidCode(111));
		assertNull(KeyEditor.keyOfAndroidCode(62));
		assertNull(KeyEditor.keyOfAndroidCode(28));
		assertNull(KeyEditor.keyOfAndroidCode(55));
	}
}
