package com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability;

import java.util.function.UnaryOperator;

import com.badlogic.gdx.graphics.Texture;

public class IconUI {
	private IconTexture icon;
	private IconTexture iconDisabled;
	private int buttonPositionX;
	private int buttonPositionY;
	private String toolTip;
	private String uberTip;
	/** Reads the object data the text refers to; null when the text is final. Run on first use, not for every icon. */
	private UnaryOperator<String> uberTipParser;
	private final char hotkey;

	public IconUI(final IconTexture icon, final IconTexture iconDisabled, final int buttonPositionX, final int buttonPositionY,
			final String toolTip, final String uberTip, final char hotkey) {
		this.icon = icon;
		this.iconDisabled = iconDisabled;
		this.buttonPositionX = buttonPositionX;
		this.buttonPositionY = buttonPositionY;
		this.toolTip = toolTip;
		this.uberTip = uberTip;
		this.hotkey = hotkey;
	}

	/**
	 * @param uberTip       the text as the data has it
	 * @param uberTipParser fills in the values the text refers to when the tip is first asked for, or null
	 */
	public IconUI(final IconTexture icon, final IconTexture iconDisabled, final int buttonPositionX,
			final int buttonPositionY, final String toolTip, final String uberTip,
			final UnaryOperator<String> uberTipParser, final char hotkey) {
		this(icon, iconDisabled, buttonPositionX, buttonPositionY, toolTip, uberTip, hotkey);
		this.uberTipParser = uberTipParser;
	}

	public Texture getIcon() {
		return this.icon.get();
	}

	public Texture getIconDisabled() {
		return this.iconDisabled.get();
	}

	public int getButtonPositionX() {
		return this.buttonPositionX;
	}

	public int getButtonPositionY() {
		return this.buttonPositionY;
	}

	public String getToolTip() {
		return this.toolTip;
	}

	public String getUberTip() {
		if (this.uberTipParser != null) {
			final UnaryOperator<String> parser = this.uberTipParser;
			this.uberTipParser = null;
			this.uberTip = parser.apply(this.uberTip);
		}
		return this.uberTip;
	}

	public char getHotkey() {
		return this.hotkey;
	}

	public String getIconPath() {
		return this.icon.getPath();
	}

	public void setIcon(final IconTexture icon, final IconTexture iconDisabled) {
		this.icon = icon;
		this.iconDisabled = iconDisabled;
	}

	public void setButtonPositionX(final int buttonPositionX) {
		this.buttonPositionX = buttonPositionX;
	}

	public void setButtonPositionY(final int buttonPositionY) {
		this.buttonPositionY = buttonPositionY;
	}

	public void setToolTip(final String toolTip) {
		this.toolTip = toolTip;
	}

	public void setUberTip(final String uberTip) {
		this.uberTipParser = null;
		this.uberTip = uberTip;
	}
}
