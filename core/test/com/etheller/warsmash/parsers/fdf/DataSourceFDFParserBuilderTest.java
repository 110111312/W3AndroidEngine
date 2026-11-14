package com.etheller.warsmash.parsers.fdf;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.fdfparser.FDFParser;

/**
 * The menu and the game read the same interface files: a file is parsed once, and again only when its bytes change.
 */
class DataSourceFDFParserBuilderTest {
	@Test
	void sameBytesGiveTheSameTreeAndChangedBytesAreParsedAgain() {
		final Text first = new Text("// one\n");
		final FDFParser.ProgramContext tree = new DataSourceFDFParserBuilder(first).buildProgram("Test.fdf");
		assertSame(tree, new DataSourceFDFParserBuilder(new Text("// one\n")).buildProgram("Test.fdf"));
		assertNotSame(tree, new DataSourceFDFParserBuilder(new Text("// two\n")).buildProgram("Test.fdf"));
	}

	private static final class Text implements DataSource {
		private final String text;

		private Text(final String text) {
			this.text = text;
		}

		@Override
		public InputStream getResourceAsStream(final String filepath) {
			return new ByteArrayInputStream(this.text.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public File getFile(final String filepath) {
			return null;
		}

		@Override
		public File getDirectory(final String filepath) {
			return null;
		}

		@Override
		public ByteBuffer read(final String path) {
			return ByteBuffer.wrap(this.text.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public boolean has(final String filepath) {
			return true;
		}

		@Override
		public Collection<String> getListfile() {
			return Collections.emptyList();
		}

		@Override
		public void close() {
		}
	}
}
