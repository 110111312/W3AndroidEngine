package com.etheller.warsmash.launcher.lan;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The LAN pages' words are looked up by name, so a name the strings file lacks shows as the name itself; and the
 * release build keeps only the strings its code names or the keep rules list, so the LAN names must be kept too.
 */
class LanTextKeysTest {
	private static final Pattern NAME_IN_CODE = Pattern
			.compile("\"(lan_[a-z0-9_]+)\"|R\\.string\\.(lan_[a-z0-9_]+)|@string/(lan_[a-z0-9_]+)");
	private static final Pattern NAME_IN_STRINGS = Pattern.compile("<string name=\"(lan_[a-z0-9_]+)\"");

	private static Path root() {
		Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("android/res/values/strings.xml"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new AssertionError("the project root with android/res/values/strings.xml was not found");
	}

	private static Set<String> namesUsed(final Path root) throws IOException {
		final Set<String> names = new TreeSet<>();
		for (final String dir : new String[] { "core/src", "android/src", "desktop/src", "android/res/layout" }) {
			final Path base = root.resolve(dir);
			if (!Files.isDirectory(base)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(base)) {
				for (final Path file : (Iterable<Path>) files
						.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".xml"))::iterator) {
					final Matcher m = NAME_IN_CODE.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
					while (m.find()) {
						names.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
					}
				}
			}
		}
		return names;
	}

	@Test
	void everyLanNameInTheCodeIsInTheStringsFile() throws IOException {
		final Path root = root();
		final Set<String> used = namesUsed(root);
		assertTrue(used.size() > 20, "the LAN names were not found in the code: " + used);
		final Set<String> defined = new TreeSet<>();
		try (Stream<Path> files = Files.list(root.resolve("android/res/values"))) {
			for (final Path file : (Iterable<Path>) files.filter(
					p -> p.getFileName().toString().startsWith("strings") && p.toString().endsWith(".xml"))::iterator) {
				final Matcher m = NAME_IN_STRINGS.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
				while (m.find()) {
					defined.add(m.group(1));
				}
			}
		}
		final Set<String> missing = new TreeSet<>(used);
		missing.removeAll(defined);
		assertTrue(missing.isEmpty(), "LAN names missing from the strings files: " + missing);
	}

	@Test
	void theReleaseBuildKeepsTheLanStrings() throws IOException {
		final String keep = new String(Files.readAllBytes(root().resolve("android/res/raw/launcher_keep.xml")),
				StandardCharsets.UTF_8);
		assertTrue(keep.contains("@string/lan_*"), "launcher_keep.xml must keep @string/lan_*");
	}
}
