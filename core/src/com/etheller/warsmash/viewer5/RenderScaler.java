package com.etheller.warsmash.viewer5;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.math.Rectangle;
import com.etheller.warsmash.util.Log;

/**
 * Draws the world into a render target smaller than the screen and stretches the result over the world's rectangle,
 * for devices that cannot fill every pixel of their screen at full speed. The interface is drawn afterwards on the
 * screen itself, so it stays sharp; the world camera keeps the full rectangle, so a click maps to the same place.
 */
public final class RenderScaler {
	/** The framebuffer the world is drawn into now, for code that must come back to it from another one. */
	private static int currentTarget;

	private int framebuffer;
	private int colorBuffer;
	private int depthBuffer;
	private int width;
	private int height;
	private boolean failed;

	public static int currentTarget() {
		return currentTarget;
	}

	/**
	 * Starts drawing into a target of the given part of the full size.
	 *
	 * @return false when the device cannot make the target, which leaves the drawing on the screen
	 */
	public boolean begin(final int fullWidth, final int fullHeight, final float scale) {
		if (this.failed) {
			return false;
		}
		final int wantedWidth = Math.max(1, (int) Math.ceil(fullWidth * scale));
		final int wantedHeight = Math.max(1, (int) Math.ceil(fullHeight * scale));
		final GL30 gl = Gdx.gl30;
		if ((this.framebuffer == 0) || (wantedWidth != this.width) || (wantedHeight != this.height)) {
			dispose();
			this.framebuffer = gl.glGenFramebuffer();
			this.colorBuffer = gl.glGenRenderbuffer();
			this.depthBuffer = gl.glGenRenderbuffer();
			gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, this.colorBuffer);
			gl.glRenderbufferStorage(GL20.GL_RENDERBUFFER, GL30.GL_RGBA8, wantedWidth, wantedHeight);
			gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, this.depthBuffer);
			gl.glRenderbufferStorage(GL20.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, wantedWidth, wantedHeight);
			gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, 0);
			gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, this.framebuffer);
			gl.glFramebufferRenderbuffer(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_RENDERBUFFER,
					this.colorBuffer);
			gl.glFramebufferRenderbuffer(GL20.GL_FRAMEBUFFER, GL20.GL_DEPTH_ATTACHMENT, GL20.GL_RENDERBUFFER,
					this.depthBuffer);
			final int status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
			gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
			if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
				Log.error("The render scale target is incomplete (" + status + "); drawing at full size");
				this.failed = true;
				dispose();
				return false;
			}
			this.width = wantedWidth;
			this.height = wantedHeight;
		}
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, this.framebuffer);
		currentTarget = this.framebuffer;
		return true;
	}

	/**
	 * Goes back to the screen and stretches the drawn part into the world's rectangle.
	 *
	 * @param world the world camera's rectangle
	 * @param scale the scale the scenes drew with, see {@link Scene#scaled}
	 */
	public void end(final Rectangle world, final float scale) {
		final GL30 gl = Gdx.gl30;
		currentTarget = 0;
		gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, 0);
		final int sourceX = Scene.scaled(world.x, scale);
		final int sourceY = Scene.scaled(world.y, scale);
		final int sourceWidth = Math.max(1, Scene.scaled(world.width, scale));
		final int sourceHeight = Math.max(1, Scene.scaled(world.height, scale));
		final int x = (int) world.x;
		final int y = (int) world.y;
		gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.framebuffer);
		gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
		gl.glDisable(GL20.GL_SCISSOR_TEST);
		gl.glColorMask(true, true, true, true);
		gl.glBlitFramebuffer(sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight, x, y,
				x + (int) world.width, y + (int) world.height, GL20.GL_COLOR_BUFFER_BIT, GL20.GL_LINEAR);
		gl.glEnable(GL20.GL_SCISSOR_TEST);
		gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
	}

	public void dispose() {
		if (currentTarget == this.framebuffer) {
			currentTarget = 0;
		}
		if (this.framebuffer != 0) {
			Gdx.gl30.glDeleteFramebuffer(this.framebuffer);
			Gdx.gl30.glDeleteRenderbuffer(this.colorBuffer);
			Gdx.gl30.glDeleteRenderbuffer(this.depthBuffer);
		}
		this.framebuffer = 0;
		this.colorBuffer = 0;
		this.depthBuffer = 0;
		this.width = 0;
		this.height = 0;
	}
}
