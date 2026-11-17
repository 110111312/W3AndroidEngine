package com.etheller.warsmash.viewer5.handlers.w3x;

import com.etheller.warsmash.util.ArgbImage;
import com.etheller.warsmash.viewer5.handlers.mdx.MdxModel;

public interface MdxAssetLoader {
	public MdxModel loadModelMdx(final String path);

	/** A model with nothing in it, for a type whose data names no model file at all. */
	public MdxModel emptyModel();

	public ArgbImage loadPathingTexture(String pathingTexture);
}
