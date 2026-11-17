package com.etheller.warsmash.datasources;

import java.nio.IntBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;

/**
 * What the OpenGL driver tells about the GPU's memory, for the default texture size limit of a desktop. Only NVIDIA
 * and AMD drivers tell; elsewhere the GPU is judged by its name, see
 * {@link GraphicsSettings#desktopDefaultTextureSize}.
 */
public final class GpuMemory {
	/** GL_NVX_gpu_memory_info: the dedicated video memory in kilobytes. */
	private static final int GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX = 0x9047;
	/** GL_ATI_meminfo: the free memory of the texture pool in kilobytes (the first of four values). */
	private static final int TEXTURE_FREE_MEMORY_ATI = 0x87FC;

	private GpuMemory() {
	}

	/** The default texture size limit of this desktop's GPU for the HD and DE graphics; needs the GL thread. */
	public static int desktopDefaultTextureSize() {
		if (Gdx.gl == null) {
			return GraphicsSettings.desktopDefaultTextureSize(null, 0);
		}
		return GraphicsSettings.desktopDefaultTextureSize(Gdx.gl.glGetString(GL20.GL_RENDERER), dedicatedMegabytes());
	}

	/** The memory of the GPU in megabytes as the driver tells it, 0 when it does not. */
	private static long dedicatedMegabytes() {
		try {
			final IntBuffer values = BufferUtils.newIntBuffer(4);
			if (Gdx.graphics.supportsExtension("GL_NVX_gpu_memory_info")) {
				Gdx.gl.glGetIntegerv(GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX, values);
				return values.get(0) >> 10;
			}
			if (Gdx.graphics.supportsExtension("GL_ATI_meminfo")) {
				Gdx.gl.glGetIntegerv(TEXTURE_FREE_MEMORY_ATI, values);
				return values.get(0) >> 10;
			}
		}
		catch (final RuntimeException e) {
			// A driver that rejects the query is judged by its name.
		}
		finally {
			Gdx.gl.glGetError();
		}
		return 0;
	}
}
