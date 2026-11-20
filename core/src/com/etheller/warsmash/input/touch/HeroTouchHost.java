package com.etheller.warsmash.input.touch;

/**
 * What the touch adapter may ask a screen that is playing hero mode. A screen that is not in hero mode answers null
 * from {@link TouchTarget#heroTouch()}, and then every gesture keeps its RTS meaning.
 * <p>
 * The movements are told apart by where the thumb lands, not by a control the player aims at: the left region is the
 * stick, the right half looks around, and the interface's own buttons keep their taps in both.
 */
public interface HeroTouchHost {
	/**
	 * The thumb has landed and might be the movement stick.
	 *
	 * @return true when the point is in the stick's region, so the drag belongs to it and to nothing else
	 */
	boolean beginStick(float screenX, float screenY);

	void dragStick(float screenX, float screenY);

	/** The thumb lifted, or the press was cancelled: the hero stops where the stick left it. */
	void endStick();

	/**
	 * The thumb has landed and might be a look-around.
	 *
	 * @return true when the point is in the right half, so the camera takes the drag
	 */
	boolean beginLook(float screenX, float screenY);

	void endLook();

	// ------------------------------------------------------------------ the skill tray

	/**
	 * The finger has landed on a skill, an item or the attack button.
	 *
	 * @return true when the point is on one of them, so the press belongs to the tray and not to a look-around
	 */
	boolean beginSkill(float screenX, float screenY);

	/** The finger that began on a button moved: a direction is being aimed, or a ground point dragged. */
	void dragSkill(float screenX, float screenY);

	/** The finger lifted: cast what was aimed, unless it came back onto the button it started from. */
	void endSkill(float screenX, float screenY);

	/** The press was cancelled: nothing is cast. */
	void cancelSkill();

	/**
	 * A tap anywhere, offered before the console sees it.
	 *
	 * @return true when the tray took it — a skill, an item or a level — so no world click is synthesized
	 */
	boolean tap(float screenX, float screenY);
}
