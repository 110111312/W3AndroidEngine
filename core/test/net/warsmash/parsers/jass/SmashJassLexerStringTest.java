package net.warsmash.parsers.jass;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Escapes inside string literals and the line numbers around literals that contain a line break.
 */
class SmashJassLexerStringTest {
	private static List<String> literals(final String source) throws Exception {
		final SmashJassLexer lexer = new SmashJassLexer(new StringReader(source));
		final List<String> result = new ArrayList<>();
		int token;
		while ((token = lexer.yylex()) != 0) {
			if (token == SmashJassParser.Lexer.STRING_LITERAL) {
				result.add((String) lexer.getLVal());
			}
		}
		return result;
	}

	private static String literal(final String content) throws Exception {
		final List<String> result = literals("\"" + content + "\"");
		assertEquals(1, result.size());
		return result.get(0);
	}

	@Test
	void usualEscapesAreRead() throws Exception {
		assertEquals("a\"b", literal("a\\\"b"));
		assertEquals("a\nb", literal("a\\nb"));
		assertEquals("a\tb", literal("a\\tb"));
		assertEquals("a\rb", literal("a\\rb"));
		assertEquals("a\bb", literal("a\\bb"));
		assertEquals("a\fb", literal("a\\fb"));
		assertEquals("a\\q", literal("a\\q"));
	}

	@Test
	void escapesAreReadInOnePass() throws Exception {
		// 1.29: "a\\nb" is a, a backslash, n and b; "a\\\nb" is a, a backslash, a line feed and b.
		assertEquals("a\\b", literal("a\\\\b"));
		assertEquals("a\\nb", literal("a\\\\nb"));
		assertEquals("a\\fb", literal("a\\\\fb"));
		assertEquals("a\\qb", literal("a\\\\qb"));
		assertEquals("a\\\nb", literal("a\\\\\\nb"));
		assertEquals("a\\\\nb", literal("a\\\\\\\\nb"));
		assertEquals("a\\\\", literal("a\\\\\\\\"));
		assertEquals("a\\\"b", literal("a\\\\\\\"b"));
		assertEquals("a\"\\b", literal("a\\\"\\\\b"));
		assertEquals("ab\\", literal("ab\\\\"));
		assertEquals("WC3Probes\\fog.txt", literal("WC3Probes\\\\fog.txt"));
	}

	@Test
	void realLineBreakStaysAndLineNumbersFollow() throws Exception {
		final String source = "x = \"a\nb\"\ny = \"c\"\n";
		assertEquals("a\nb", literals(source).get(0));
		final SmashJassLexer lexer = new SmashJassLexer(new StringReader(source));
		int token;
		int lastLine = -1;
		while ((token = lexer.yylex()) != 0) {
			if (token == SmashJassParser.Lexer.STRING_LITERAL) {
				lastLine = lexer.getLine();
			}
		}
		assertEquals(2, lastLine);
	}
}
