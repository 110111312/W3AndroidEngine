package com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.core.AbilityBuilderGsonBuilder;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderFile;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.parser.AbilityBuilderParser;
import com.google.gson.Gson;

/**
 * The loader logs and skips a file it cannot parse, so a renamed or merged action class would only surface in game.
 * This parses every ability JSON directly and fails on the first unresolved type.
 */
class AbilityBuilderJsonTest {

	private static final Path ASSETS = new File("assets/abilityBehaviors").toPath();

	private static List<Path> jsonFiles() throws IOException {
		try (Stream<Path> walk = Files.walk(ASSETS)) {
			final List<Path> files = new ArrayList<>();
			walk.filter(p -> p.toString().endsWith(".json")).sorted().forEach(files::add);
			return files;
		}
	}

	private static List<AbilityBuilderParser> parse(final Gson gson, final Path file) throws IOException {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return gson.fromJson(reader, AbilityBuilderFile.class).getAbilityList();
		}
	}

	@Test
	void everyAbilityFileResolvesAllTypes() throws IOException {
		final Gson gson = AbilityBuilderGsonBuilder.create();
		final List<Path> files = jsonFiles();
		assertTrue(files.size() > 20, "ability JSON files not found at " + ASSETS.toAbsolutePath());
		int abilities = 0;
		for (final Path file : files) {
			final List<AbilityBuilderParser> list = parse(gson, file);
			assertTrue(list != null && !list.isEmpty(), file + " has no abilities");
			abilities += list.size();
		}
		assertTrue(abilities > 100, "only " + abilities + " abilities parsed");
	}

	/** Dumps the parsed trees as text; with ABILITYBUILDER_DUMP=<file> they are written there for before/after diffs. */
	@Test
	void parsedTreeIsStable() throws IOException {
		final Gson gson = AbilityBuilderGsonBuilder.create();
		final StringBuilder first = new StringBuilder();
		final StringBuilder second = new StringBuilder();
		for (final Path file : jsonFiles()) {
			first.append("# ").append(file.getFileName()).append('\n');
			second.append("# ").append(file.getFileName()).append('\n');
			dump(first, parse(gson, file), 0, new IdentityHashMap<>());
			dump(second, parse(gson, file), 0, new IdentityHashMap<>());
		}
		assertEquals(first.toString(), second.toString());
		final String target = System.getenv("ABILITYBUILDER_DUMP");
		if (target != null) {
			Files.writeString(Path.of(target), first.toString(), StandardCharsets.UTF_8);
		}
	}

	private static void dump(final StringBuilder out, final Object value, final int depth,
			final IdentityHashMap<Object, Boolean> path) {
		if (value == null) {
			out.append("null");
			return;
		}
		final Class<?> type = value.getClass();
		if (type.isPrimitive() || value instanceof Number || value instanceof CharSequence
				|| value instanceof Boolean || value instanceof Character || type.isEnum()) {
			out.append(type.isEnum() ? ((Enum<?>) value).name() : value.toString());
			return;
		}
		if (path.put(value, Boolean.TRUE) != null || depth > 200) {
			out.append("<cycle>");
			return;
		}
		try {
			if (value instanceof Collection) {
				out.append('[');
				for (final Object o : (Collection<?>) value) {
					dump(out, o, depth + 1, path);
					out.append(',');
				}
				out.append(']');
			}
			else if (value instanceof Map) {
				final List<String> entries = new ArrayList<>();
				for (final Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
					final StringBuilder k = new StringBuilder();
					dump(k, e.getKey(), depth + 1, path);
					final StringBuilder v = new StringBuilder();
					dump(v, e.getValue(), depth + 1, path);
					entries.add(k + "=" + v);
				}
				entries.sort(null);
				out.append("map").append(entries);
			}
			else if (type.isArray()) {
				out.append(type.getComponentType().getSimpleName()).append('[');
				if (type.getComponentType().isPrimitive()) {
					out.append(java.lang.reflect.Array.getLength(value));
				}
				else {
					for (final Object o : (Object[]) value) {
						dump(out, o, depth + 1, path);
						out.append(',');
					}
				}
				out.append(']');
			}
			else {
				final List<String> parts = new ArrayList<>();
				for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
					for (final Field f : c.getDeclaredFields()) {
						if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
							continue;
						}
						f.setAccessible(true);
						final StringBuilder v = new StringBuilder();
						dump(v, f.get(value), depth + 1, path);
						parts.add(f.getName() + ":" + v);
					}
				}
				parts.sort(null);
				out.append(type.getSimpleName()).append('{').append(String.join(";", parts)).append('}');
			}
		}
		catch (final IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
		finally {
			path.remove(value);
		}
	}
}
