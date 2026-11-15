package com.etheller.warsmash.viewer5.handlers.blp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.apache.commons.io.IOUtils;

import com.etheller.warsmash.viewer5.ModelViewer;
import com.etheller.warsmash.viewer5.PathSolver;
import com.etheller.warsmash.viewer5.RawOpenGLTextureResource;
import com.etheller.warsmash.viewer5.handlers.ResourceHandler;

public class DdsTexture extends RawOpenGLTextureResource {

	public DdsTexture(final ModelViewer viewer, final ResourceHandler handler, final String extension,
			final PathSolver pathSolver, final String fetchUrl) {
		super(viewer, extension, pathSolver, fetchUrl, handler);
	}

	@Override
	protected void lateLoad() {

	}

	@Override
	protected void load(final InputStream src, final Object options) {
		try {
			final byte[] bytes = IOUtils.toByteArray(src);
			final ByteBuffer file = ByteBuffer.wrap(bytes);
			final int limit = this.viewer.getTextureSizeLimit();
			if (this.viewer.isTextureCompression()) {
				final CompressedMips blocks = TextureTranscoder.transcode(this.fetchUrl, bytes, limit,
						() -> DdsDecoder.decode(file, limit, true).getLevels(), DdsDecoder.isNormalMap(file));
				if (blocks != null) {
					update(blocks);
					return;
				}
			}
			final DdsDecoder image = DdsDecoder.decode(file, limit, true);
			update(image.getLevels(), false);
		}
		catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
