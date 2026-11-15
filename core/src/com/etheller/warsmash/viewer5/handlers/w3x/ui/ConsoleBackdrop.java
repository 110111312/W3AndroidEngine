package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.BackdropFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;

/**
 * The console's black backdrop, left open where the unit portrait is. The portrait is a scene drawn before the
 * interface, so the backdrop drawn over it would hide the model; the portrait scene clears its own area to black.
 */
final class ConsoleBackdrop extends AbstractUIFrame {
	private final BackdropFrame backdrop;
	private final MeleeUIInfoPanel infoPanel;
	private final Rectangle opening = new Rectangle();

	ConsoleBackdrop(final BackdropFrame backdrop, final MeleeUIInfoPanel infoPanel, final UIFrame parent) {
		super(backdrop.getName() + "Opening", parent);
		this.backdrop = backdrop;
		this.infoPanel = infoPanel;
		add(backdrop);
	}

	@Override
	protected void internalRender(final SpriteBatch batch, final BitmapFont baseFont, final GlyphLayout glyphLayout) {
		final Texture background = this.backdrop.getBackground();
		if (!this.infoPanel.isPortraitShown() || (background == null)) {
			super.internalRender(batch, baseFont, glyphLayout);
			return;
		}
		if (!this.backdrop.isVisible()) {
			return;
		}
		final Rectangle bounds = this.backdrop.getRenderBounds();
		final Rectangle portrait = this.infoPanel.portraitBounds(this.opening);
		final float left = bounds.x;
		final float bottom = bounds.y;
		final float right = bounds.x + bounds.width;
		final float top = bounds.y + bounds.height;
		final float openLeft = Math.max(left, Math.min(right, portrait.x));
		final float openRight = Math.max(openLeft, Math.min(right, portrait.x + portrait.width));
		final float openBottom = Math.max(bottom, Math.min(top, portrait.y));
		final float openTop = Math.max(openBottom, Math.min(top, portrait.y + portrait.height));
		batch.draw(background, left, bottom, openLeft - left, top - bottom);
		batch.draw(background, openRight, bottom, right - openRight, top - bottom);
		batch.draw(background, openLeft, bottom, openRight - openLeft, openBottom - bottom);
		batch.draw(background, openLeft, openTop, openRight - openLeft, top - openTop);
	}
}
