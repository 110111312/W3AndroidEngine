package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.input.touch.HeroStick;
import com.etheller.warsmash.input.touch.HeroStickOrderEmitter;
import com.etheller.warsmash.input.touch.HeroTouchHost;
import com.etheller.warsmash.input.touch.TouchSettings;
import com.etheller.warsmash.lan.LockstepTurnManager;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;

/**
 * Hero mode: the interface the phone plays. A hero, a camera on her, a floating stick in the left thumb's region, a
 * look-around on the right, and the console not built for this map at all — see {@code docs/hero-mode.md}.
 * <p>
 * The mode is resolved once, on the first frame after the map's script has run: the map script creates the units
 * after the interface is constructed, so the hero cannot be a constructor argument. Until a hero exists nothing here
 * is active and the RTS console plays, which is also what a map with no hero for the local player keeps.
 * <p>
 * Every movement the stick makes is a point order through {@link MeleeUI#unitOrderListener}, exactly as a
 * right-click is, so a replay and a LAN peer apply the same thing the player saw.
 */
final class HeroMode implements HeroTouchHost {
	private static final Color STICK_RING_COLOR = new Color(1f, 1f, 1f, 0.45f);
	private static final Color STICK_DEAD_ZONE_COLOR = new Color(1f, 1f, 1f, 0.18f);
	private static final Color STICK_KNOB_COLOR = new Color(1f, 1f, 1f, 0.55f);
	private static final int STICK_SEGMENTS = 48;
	private static final int KNOB_SEGMENTS = 28;
	/** The upper button bar's distance from the minimap, in design pixels. */
	private static final float BUTTON_BAR_GAP = 10f;

	private final MeleeUI ui;
	private final TouchSettings settings;
	private final HeroStick stick;
	private final HeroStickOrderEmitter emitter;
	private final AbilityPointTarget orderTarget = new AbilityPointTarget(0, 0);
	private boolean resolved;
	private boolean active;
	private CUnit hero;
	private RenderUnit heroRender;
	private boolean cameraLocked;
	private boolean looking;
	private boolean moving;
	private float worldPerScreenPixel = 1f;

	HeroMode(final MeleeUI ui, final TouchSettings settings) {
		this.ui = ui;
		this.settings = settings;
		this.stick = new HeroStick(settings);
		this.emitter = new HeroStickOrderEmitter(settings);
	}

	boolean isActive() {
		return this.active;
	}

	RenderUnit getHeroRender() {
		return this.heroRender;
	}

	/** Called once a frame, before the interface draws. */
	void update(final float deltaTime) {
		if (!this.resolved) {
			resolve();
		}
		if (!this.active) {
			return;
		}
		updateCamera();
		updateStick();
	}

	// ------------------------------------------------------------------ resolution

	private void resolve() {
		this.resolved = true;
		if (!canPlayHeroMode()) {
			return;
		}
		final CUnit hero = HeroResolver.firstHero(this.ui.localPlayer.getHeroes(), (unit) -> !unit.isDead());
		if (hero == null) {
			// No hero on this map for the local player: the console stays, as the issue asks.
			return;
		}
		final RenderUnit renderHero = this.ui.war3MapViewer.getRenderPeer(hero);
		if (renderHero == null) {
			return;
		}
		this.hero = hero;
		this.heroRender = renderHero;
		this.active = true;
		measureWorldPerScreenPixel();
		applyHeroLayout();
		lockCamera();
	}

	/**
	 * Replays keep the RTS interface (they have their own panel and no hero of their own to be), and a networked game
	 * would need the orders to travel before hero mode can be its shape.
	 */
	private boolean canPlayHeroMode() {
		if (this.ui.war3MapViewer.isReplayPlayback()) {
			return false;
		}
		return !(this.ui.war3MapViewer.getGameTurnManager() instanceof LockstepTurnManager);
	}

	private void measureWorldPerScreenPixel() {
		MeleeUI.screenCoordsVector.set(0f, 0f);
		final float x0 = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector).x;
		MeleeUI.screenCoordsVector.set(100f, 0f);
		final float x100 = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector).x;
		this.worldPerScreenPixel = (x100 - x0) / 100f;
	}

	// ------------------------------------------------------------------ the interface

	private void applyHeroLayout() {
		this.ui.minimapPanel.placeMinimapTopLeft(this.ui.meleeUIMinimap);
		placeUpperButtonBar();
		updateInterfaceVisibility(true);
	}

	/**
	 * The quest log, the chat, the alliance dialog and the game menu hang off the upper button bar, which the console
	 * anchored to the top left. The minimap now owns that corner, so the bar moves to the minimap's right.
	 */
	private void placeUpperButtonBar() {
		final UIFrame bar = this.ui.upperButtonBar;
		if (bar == null) {
			return;
		}
		final float x = this.ui.minimapPanel.heroMinimapRight()
				+ (BUTTON_BAR_GAP * this.ui.widthRatioCorrection);
		final float y = this.ui.minimapPanel.heroMinimapTopMargin();
		((AbstractRenderableFrame) bar).clearFramePointAssignments();
		((AbstractRenderableFrame) bar).addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.rootFrame,
				FramePoint.TOPLEFT, x, -y));
		bar.positionBounds(this.ui.rootFrame, this.ui.uiViewport);
	}

	/**
	 * The console's own show/hide, with hero mode's answer: the console never comes back, the minimap, the resources
	 * and the button bar follow the interface's fades, and the dialogs are separate surfaces that keep working.
	 */
	void updateInterfaceVisibility(final boolean show) {
		this.ui.consoleUI.setVisible(false);
		if (this.ui.consoleBackdrop != null) {
			this.ui.consoleBackdrop.setVisible(false);
		}
		this.ui.resourceBar.setVisible(show);
		this.ui.meleeUIMinimap.setVisible(show);
		if (this.ui.upperButtonBar != null) {
			this.ui.upperButtonBar.setVisible(show);
		}
		this.ui.minimapButtonBar.setVisible(false);
		this.ui.smashSimpleInfoPanel.setVisible(false);
		this.ui.smashCommandButtons.setVisible(false);
		this.ui.timeIndicator.setVisible(false);
		if (this.ui.heroBar != null) {
			this.ui.heroBar.setVisible(false);
		}
		final UIFrame portrait = this.ui.rootFrame.getFrameByName("UnitPortrait", 0);
		if (portrait != null) {
			portrait.setVisible(false);
		}
		if (this.ui.inventoryCover != null) {
			this.ui.inventoryCover.setVisible(false);
		}
		this.ui.infoPanel.positionPortrait();
		this.ui.updateWorldFrameViewport();
	}

	// ------------------------------------------------------------------ the hero's own bar

	/**
	 * Health and mana above the hero, always, in place of the hover bar the console draws — the player has no portrait
	 * to read them from any more. The mana bar is the console's blue one, the same code the hero bar uses.
	 */
	void positionOverheadBar() {
		if (!this.active || (this.hero == null) || this.hero.isDead()) {
			return;
		}
		final SimpleStatusBarFrame lifeBar = this.ui.tooltips.getHpBar();
		this.ui.tooltips.positionHealthBar(lifeBar, this.heroRender, 1f);
		if (this.hero.getMaximumMana() > 0) {
			final SimpleStatusBarFrame manaBar = this.ui.tooltips.getHpBar();
			this.ui.tooltips.positionManaBar(manaBar, this.heroRender, 1f);
		}
	}

	// ------------------------------------------------------------------ the stick

	@Override
	public boolean beginStick(final float screenX, final float screenY) {
		if (!canTakeTouches() || isRightHalf(screenX) || isOverInterface(screenX, screenY)) {
			return false;
		}
		this.stick.begin(screenX, screenY);
		this.emitter.reset();
		this.moving = false;
		return true;
	}

	@Override
	public void dragStick(final float screenX, final float screenY) {
		this.stick.move(screenX, screenY);
	}

	@Override
	public void endStick() {
		if (!this.stick.isActive()) {
			return;
		}
		this.stick.end();
		if (this.moving) {
			this.moving = false;
			issueStopOrder();
		}
		this.emitter.reset();
	}

	@Override
	public boolean beginLook(final float screenX, final float screenY) {
		if (!canTakeTouches() || !isRightHalf(screenX) || isOverInterface(screenX, screenY)) {
			return false;
		}
		this.looking = true;
		releaseCamera();
		return true;
	}

	@Override
	public void endLook() {
		this.looking = false;
	}

	private boolean canTakeTouches() {
		return this.active && this.ui.userControlEnabled && !this.hero.isDead();
	}

	private boolean isRightHalf(final float screenX) {
		MeleeUI.screenCoordsVector.set(screenX, 0f);
		return this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector).x >= (this.ui.uiViewport.getMinWorldWidth()
				/ 2f);
	}

	/** The minimap keeps its own touches, and so does every button of the interface: the stick only takes the rest. */
	private boolean isOverInterface(final float screenX, final float screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		final Vector2 point = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		return this.ui.meleeUIMinimap.containsMouse(point.x, point.y)
				|| this.ui.input.isOverClickableUi(Math.round(screenX), Math.round(screenY));
	}

	private void updateStick() {
		if (!this.stick.isActive()) {
			return;
		}
		if (!canTakeTouches()) {
			endStick();
			return;
		}
		if (!this.stick.hasDirection()) {
			// The thumb is back inside the dead zone: the hero stands, and her idle auto-acquire may take a target.
			if (this.moving) {
				this.moving = false;
				issueStopOrder();
			}
			this.emitter.reset();
			return;
		}
		this.moving = true;
		issueMoveOrder(this.stick.getDirectionX(), this.stick.getDirectionY(), TimeUtils.millis());
	}

	private void issueMoveOrder(final float directionX, final float directionY, final long nowMs) {
		if (!this.emitter.tick(directionX, directionY, nowMs)) {
			return;
		}
		final float ahead = this.settings.stickOrderAhead;
		this.orderTarget.x = this.hero.getX() + (directionX * ahead);
		this.orderTarget.y = this.hero.getY() + (directionY * ahead);
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		final PointAbilityTargetCheckReceiver targetReceiver = PointAbilityTargetCheckReceiver.INSTANCE;
		// The order is the right-click's smart order, found the way the console finds it, so the game agrees with the
		// screen; a hero has no cell the pointer could be over, so the point is all there is.
		for (final CAbility ability : this.hero.getAbilities()) {
			ability.checkCanUse(this.ui.war3MapViewer.simulation, this.hero, localPlayerIndex, OrderIds.smart, false,
					activationReceiver.reset());
			if (!activationReceiver.isOk()) {
				continue;
			}
			ability.checkCanTarget(this.ui.war3MapViewer.simulation, this.hero, localPlayerIndex, OrderIds.smart,
					false, this.orderTarget, targetReceiver.reset());
			final AbilityPointTarget target = targetReceiver.getTarget();
			if (target != null) {
				this.ui.unitOrderListener.issuePointOrder(this.hero.getHandleId(), ability.getHandleId(),
						OrderIds.smart, target.x, target.y, false);
				return;
			}
		}
	}

	private void issueStopOrder() {
		final int localPlayerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final BooleanAbilityActivationReceiver activationReceiver = BooleanAbilityActivationReceiver.INSTANCE;
		final BooleanAbilityTargetCheckReceiver<Void> targetReceiver = BooleanAbilityTargetCheckReceiver.getInstance();
		int abilityHandleId = 0;
		for (final CAbility ability : this.hero.getAbilities()) {
			ability.checkCanUse(this.ui.war3MapViewer.simulation, this.hero, localPlayerIndex, OrderIds.stop, false,
					activationReceiver.reset());
			if (!activationReceiver.isOk()) {
				continue;
			}
			ability.checkCanTargetNoTarget(this.ui.war3MapViewer.simulation, this.hero, localPlayerIndex,
					OrderIds.stop, false, targetReceiver.reset());
			if (targetReceiver.isTargetable()) {
				abilityHandleId = ability.getHandleId();
			}
		}
		// Stop is issued without an ability when none carries it, exactly as the script's own order native does.
		this.ui.unitOrderListener.issueImmediateOrder(this.hero.getHandleId(), abilityHandleId, OrderIds.stop, false);
	}

	// ------------------------------------------------------------------ the camera

	private void updateCamera() {
		if (this.looking) {
			return;
		}
		final boolean shouldLock = !this.hero.isDead() && this.ui.cinematics.isInterfaceShown();
		if (shouldLock == this.cameraLocked) {
			return;
		}
		if (shouldLock) {
			lockCamera();
		}
		else {
			releaseCamera();
		}
	}

	private void lockCamera() {
		this.ui.cameraManager.setTargetController(this.heroRender, 0f, 0f, false);
		this.cameraLocked = true;
	}

	/** A look-around, a cinematic and a dead hero all need the camera to be the game's for a while. */
	private void releaseCamera() {
		this.ui.cameraManager.setTargetController(null, 0f, 0f, false);
		this.cameraLocked = false;
	}

	// ------------------------------------------------------------------ drawing

	/** Two rings and a knob, in UI space: no texture, no asset, and the ring is the only thing that is drawn. */
	void render(final SpriteBatch batch, final ShapeRenderer shapes) {
		if (!this.active || !this.stick.isActive()) {
			return;
		}
		MeleeUI.screenCoordsVector.set(this.stick.getOriginX(), this.stick.getOriginY());
		final Vector2 origin = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		final float originX = origin.x;
		final float originY = origin.y;
		final float perPixel = this.worldPerScreenPixel;
		final float radius = this.settings.stickRadiusPx * perPixel;
		final float deadZone = this.settings.stickDeadZonePx * perPixel;
		final float knobRadius = this.settings.stickKnobRadiusPx * perPixel;
		final float knobX = originX + (this.stick.getKnobOffsetX() * perPixel);
		final float knobY = originY + (this.stick.getKnobOffsetY() * perPixel);

		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Line);
		shapes.setColor(STICK_RING_COLOR);
		shapes.circle(originX, originY, radius, STICK_SEGMENTS);
		shapes.setColor(STICK_DEAD_ZONE_COLOR);
		shapes.circle(originX, originY, deadZone, STICK_SEGMENTS);
		shapes.end();
		shapes.begin(ShapeType.Filled);
		shapes.setColor(STICK_KNOB_COLOR);
		shapes.circle(knobX, knobY, knobRadius, KNOB_SEGMENTS);
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		batch.begin();
	}
}
