package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.graphics.Texture;
import com.etheller.warsmash.datasources.DataSource;
import com.etheller.warsmash.util.ImageUtils;

/**
 * The circular copies of the icons the tray draws.
 * <p>
 * Warcraft's ability and item art is square and stays square on disk, so the circle is a mask cut into the pixels once,
 * at the tray's own copy. It has to be its own copy: {@code GameUI.loadTexture} keeps one {@link Texture} per path for
 * the whole map, and the same icon is drawn by the command card, the tooltips, the item slots and the portrait — a
 * circle written into that texture would round the corners of every one of them.
 * <p>
 * A copy is 64 by 64 at most for a command button, so a handful of them costs little, and the last of a map's copies is
 * released with the map's other textures by {@code SessionResources}.
 */
final class HeroTrayIcons {
	/** How soft the edge of the circle is, in pixels of the source icon. */
	private static final float FEATHER_PX = 1.5f;

	private final DataSource dataSource;
	private final Map<String, Texture> masked = new HashMap<>();
	private final Set<String> unreadable = new HashSet<>();

	HeroTrayIcons(final DataSource dataSource) {
		this.dataSource = dataSource;
	}

	/** @return the icon cut to a circle, or null when the file is not there or cannot be read */
	Texture masked(final String path) {
		if ((path == null) || path.isEmpty()) {
			return null;
		}
		final Texture cached = this.masked.get(path);
		if (cached != null) {
			return cached;
		}
		if (this.unreadable.contains(path)) {
			return null;
		}
		final Texture built = build(path);
		if (built == null) {
			this.unreadable.add(path);
		}
		else {
			this.masked.put(path, built);
		}
		return built;
	}

	private Texture build(final String path) {
		try {
			final ImageUtils.RgbaImage image = ImageUtils.getAnyExtensionRgbaImage(this.dataSource, withBlp(path),
					"texture", true);
			final int width = image.getWidth();
			final int height = image.getHeight();
			if ((width <= 0) || (height <= 0)) {
				return null;
			}
			final ByteBuffer source = image.getPixels().duplicate();
			final ByteBuffer pixels = ByteBuffer.allocate(width * height * 4);
			final float centreX = width / 2f;
			final float centreY = height / 2f;
			// Half a feather inside the edge of a square icon, so no part of the circle is cut off.
			final float radius = (Math.min(width, height) / 2f) - (FEATHER_PX / 2f);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					final int index = ((y * width) + x) * 4;
					final float distance = (float) Math.hypot((x + 0.5f) - centreX, (y + 0.5f) - centreY);
					final float coverage = Math.max(0f,
							Math.min(1f, ((radius - distance) / FEATHER_PX) + 0.5f));
					pixels.put(index, source.get(index));
					pixels.put(index + 1, source.get(index + 1));
					pixels.put(index + 2, source.get(index + 2));
					pixels.put(index + 3, (byte) Math.round((source.get(index + 3) & 0xff) * coverage));
				}
			}
			return ImageUtils.getTexture(width, height, pixels);
		}
		catch (final Exception exc) {
			// An icon that is not in this data set is not worth a report per frame; the tray draws the ring alone.
			return null;
		}
	}

	/** The loaders want the archive's own name, and the data usually gives the path without the extension. */
	private static String withBlp(final String path) {
		if (path.toLowerCase().endsWith(".blp")) {
			return path;
		}
		final int dot = path.lastIndexOf('.');
		return ((dot > path.lastIndexOf('\\')) && (dot > path.lastIndexOf('/'))) ? path.substring(0, dot) + ".blp"
				: path + ".blp";
	}
}
