package com.etheller.warsmash.viewer5.handlers.blp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;

import com.etheller.warsmash.util.ImageUtils;
import com.etheller.warsmash.util.Log;
import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public class BlpTexture extends RawOpenGLTextureResource {

	public BlpTexture(final ModelViewer viewer, final ResourceHandler handler, final String extension,
			final PathSolver pathSolver, final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
	}

	@Override
	protected void lateLoad() {

	}

	@Override
	protected void load(final InputStream src, final Object options) {
		BufferedImage img;
		try {
			final byte[] bytes = IOUtils.toByteArray(src);
			final ByteBuffer data = ByteBuffer.wrap(bytes);
			final int limit = this.viewer.getTextureSizeLimit();
			final boolean dds = DdsDecoder.isDds(data);
			if (this.viewer.isTextureCompression() && (dds || BlpDecoder.isBlp(data))) {
				final CompressedMips blocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit,
						() -> dds ? DdsDecoder.decode(data, limit, true).getLevels()
								: BlpDecoder.decode(data, limit, true).getLevels(),
						dds && DdsDecoder.isNormalMap(data));
				if (blocks != null) {
					update(blocks);
					return;
				}
			}
			if (dds) {
				// A missing .blp can resolve to its .dds. Classic data stores the same colors in
				// both, so the .dds takes the plain RGBA format of the .blp below.
				update(DdsDecoder.decode(data, this.viewer.getTextureSizeLimit(), true).getLevels(), false);
				return;
			}
			if (BlpDecoder.isBlp(data)) {
				// The ImageIO plugin reads these colors as linear and the sRGB texture format
				// undoes that, so the raw colors in a plain RGBA texture match it.
				update(BlpDecoder.decode(data, this.viewer.getTextureSizeLimit(), true).getLevels(), false);
				return;
			}
			if (!ImageUtils.IMAGE_IO_AVAILABLE) {
				Log.warn("No decoder for this BLP on this platform, texture left empty");
				return;
			}
			img = ImageIO.read(new ByteArrayInputStream(data.array()));
			update(img, true);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}