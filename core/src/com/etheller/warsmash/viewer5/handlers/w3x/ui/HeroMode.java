package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.input.touch.HeroStick;
import com.etheller.warsmash.input.touch.HeroStickOrderEmitter;
import com.etheller.warsmash.input.touch.HeroTouchHost;
import com.etheller.warsmash.input.touch.TouchSettings;
import com.etheller.warsmash.lan.LockstepTurnManager;
import com.etheller.warsmash.parsers.fdf.datamodel.FramePoint;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.parsers.fdf.frames.AbstractUIFrame;
import com.etheller.warsmash.parsers.fdf.frames.SetPoint;
import com.etheller.warsmash.parsers.fdf.frames.SimpleStatusBarFrame;
import com.etheller.warsmash.parsers.fdf.frames.UIFrame;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.RenderWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CWidgetFilterFunction;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CWidgetAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgAbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.ExternStringMsgTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.PointAbilityTargetCheckReceiver;
import com.etheller.warsmash.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
	/** An aim: the line a direction skill would leave along, or the circle a ground skill would land in. */
	private static final Color AIM_COLOR = new Color(0.5f, 0.85f, 1f, 0.6f);
	private static final Color AIM_LINE_COLOR = new Color(0.5f, 0.85f, 1f, 0.85f);
	/** The ring that shows how far a chosen target can be, drawn around the hero. */
	private static final Color AIM_RANGE_COLOR = new Color(1f, 1f, 1f, 0.22f);
	/** A ground skill that declares no area still shows a small circle, so that the player sees where it lands. */
	private static final float AIM_MIN_AREA = 48f;
	private static final int STICK_SEGMENTS = 48;
	private static final int KNOB_SEGMENTS = 28;
	private static final int AIM_SEGMENTS = 40;
	/** The upper button bar's distance from the minimap, in design pixels. */
	private static final float BUTTON_BAR_GAP = 10f;
	/**
	 * How long the search for a hero runs. The map's script makes the hero just before the first frame, so this only
	 * has to cover a hero that a cinematic or a timer creates (or one restored from a saved game, which the simulation
	 * adds over its first steps).
	 */
	private static final float HERO_SEARCH_SECONDS = 8f;

	private final MeleeUI ui;
	private final TouchSettings settings;
	private final HeroStick stick;
	private final HeroStickOrderEmitter emitter;
	private final HeroTray tray;
	private final AbilityPointTarget orderTarget = new AbilityPointTarget(0, 0);
	private final Vector3 aimLocation = new Vector3();
	private final Vector3 heroLocation = new Vector3();
	private final Vector3 worldScratch = new Vector3();
	private final Vector2 screenScratch = new Vector2();
	private final Vector2 heroUi = new Vector2();
	private final Vector2 aimUi = new Vector2();
	private final Vector2 uiPoint = new Vector2();
	private final Vector2 heroUi2 = new Vector2();
	private final Vector2 aimUi2 = new Vector2();
	private final float[] clampedAim = new float[2];
	private boolean resolved;
	private boolean active;
	private float searchTime;
	private CUnit hero;
	private RenderUnit heroRender;
	private CAbilityHero heroAbility;
	private CAbilityInventory inventory;
	private boolean cameraLocked;
	private boolean looking;
	private boolean moving;
	private float worldPerScreenPixel = 1f;
	/** The skill a finger is aiming right now, or -1. */
	private int aimingSlot = -1;
	/** A single-target skill that is waiting for the tap on its target, or -1. */
	private int armedSlot = -1;

	HeroMode(final MeleeUI ui, final TouchSettings settings) {
		this.ui = ui;
		this.settings = settings;
		this.stick = new HeroStick(settings);
		this.emitter = new HeroStickOrderEmitter(settings);
		this.tray = new HeroTray(ui);
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
			resolve(deltaTime);
		}
		if (!this.active) {
			return;
		}
		updateTray();
		updateCamera();
		updateStick();
	}

	// ------------------------------------------------------------------ resolution

	private void resolve(final float deltaTime) {
		if (!canPlayHeroMode()) {
			this.resolved = true;
			return;
		}
		if (this.hero == null) {
			this.hero = findHero();
			if (this.hero == null) {
				this.searchTime += deltaTime;
				if (this.searchTime >= HERO_SEARCH_SECONDS) {
					// No hero on this map for the local player: the console stays, as the issue asks.
					this.resolved = true;
					Log.info("Hero mode: no hero for player " + this.ui.localPlayer.getId()
							+ " in this map, the console stays");
				}
				return;
			}
			// The hero is decided now; only her render peer is left, and that appears with the frame that draws her.
			this.resolved = true;
			Log.info("Hero mode: following " + this.hero.getName() + " (" + this.hero.getTypeId() + ") of player "
					+ this.hero.getPlayerIndex());
		}
		final RenderUnit renderHero = this.ui.war3MapViewer.getRenderPeer(this.hero);
		if (renderHero == null) {
			return;
		}
		this.heroRender = renderHero;
		this.active = true;
		this.heroAbility = findHeroAbility();
		measureWorldPerScreenPixel();
		applyHeroLayout();
		lockCamera();
	}

	private CAbilityHero findHeroAbility() {
		for (final CAbility ability : this.hero.getAbilities()) {
			if (ability instanceof CAbilityHero) {
				return (CAbilityHero) ability;
			}
		}
		return null;
	}

	/**
	 * The player's heroes, in the order the map made them.
	 * <p>
	 * The player's own hero list is only filled by {@code CSinulation.createUnit}, that is by the script's CreateUnit;
	 * a hero placed in the map, or one restored from a saved game, is never added to it, and the campaigns are built
	 * that way. So the player's units are read as well, which is the same rule the original game uses: a hero is a
	 * hero because the player owns it, not because of how it came to exist.
	 */
	private CUnit findHero() {
		final int localIndex = this.ui.localPlayer.getId();
		final List<CUnit> candidates = new ArrayList<>(this.ui.localPlayer.getHeroes());
		// Including the units the script made this step: they join the simulation's unit list only at the next one.
		for (final CUnit unit : this.ui.war3MapViewer.simulation.getUnitsIncludingNew()) {
			if ((unit.getPlayerIndex() == localIndex) && unit.isHero() && !unit.isIllusion()
					&& !candidates.contains(unit)) {
				candidates.add(unit);
			}
		}
		candidates.sort(Comparator.comparingInt(CUnit::getHandleId));
		return HeroResolver.firstHero(candidates, (unit) -> !unit.isDead());
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
		liftOutOfConsole(this.ui.resourceBar);
		liftOutOfConsole(this.ui.upperButtonBar);
		placeUpperButtonBar();
		updateInterfaceVisibility(true);
	}

	/**
	 * The console is hidden outright, and a hidden frame draws none of its children, so the two bars hero mode keeps
	 * have to belong to the root instead. Their set points still name the console, which is laid out as before; only
	 * the drawing was lost with it.
	 */
	private void liftOutOfConsole(final UIFrame frame) {
		if (frame == null) {
			return;
		}
		((AbstractUIFrame) this.ui.consoleUI).remove(frame);
		// First among the root's children, so the dialogs and the console frames still draw above them.
		this.ui.rootFrame.add(0, frame);
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
		final float y = this.ui.minimapPanel.heroInterfaceTop()
				- (BUTTON_BAR_GAP * this.ui.heightRatioCorrection);
		((AbstractRenderableFrame) bar).clearFramePointAssignments();
		// Hung off the root's bottom left, so the point is the world position and the wide viewport's letterbox on the
		// left and right is part of it.
		((AbstractRenderableFrame) bar).addSetPoint(new SetPoint(FramePoint.TOPLEFT, this.ui.rootFrame,
				FramePoint.BOTTOMLEFT, x, y));
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
		// The clock is not the console's: it hangs off the root frame and tells the player what the world does, so it
		// stays. Its own art is a dial small enough for the corner it was made for.
		this.ui.timeIndicator.setVisible(show);
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
		// Walking away from an aim gives it up: the stick and an aim are two intentions at once.
		this.aimingSlot = -1;
		this.armedSlot = -1;
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
		if (!canTakeTouches()) {
			return false;
		}
		// The minimap is a look-around too: a drag on it walks the camera across the map, since the camera is the
		// hero's and the game would pull a tap straight back. A tap is no drag, so it does nothing, as it should on a
		// screen with no selection to order and no camera to send anywhere.
		if (isOverMinimap(screenX, screenY) || (isRightHalf(screenX) && !isOverInterface(screenX, screenY))) {
			this.looking = true;
			releaseCamera();
			return true;
		}
		return false;
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

	/** The minimap and the tray keep their own touches, and so does every button of the interface: the stick takes the rest. */
	private boolean isOverInterface(final float screenX, final float screenY) {
		return isOverMinimap(screenX, screenY) || isOverTray(screenX, screenY) || this.ui.input
				.isOverClickableUi(Math.round(screenX), Math.round(screenY));
	}

	private boolean isOverTray(final float screenX, final float screenY) {
		final Vector2 point = toUi(screenX, screenY, this.uiPoint);
		return this.tray.contains(point.x, point.y);
	}

	private boolean isOverMinimap(final float screenX, final float screenY) {
		MeleeUI.screenCoordsVector.set(screenX, screenY);
		final Vector2 point = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector);
		return this.ui.meleeUIMinimap.containsMouse(point.x, point.y);
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
		// The thumb is in screen space, where y grows downwards; the map's y grows upwards.
		this.orderTarget.y = this.hero.getY() - (directionY * ahead);
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

	// ------------------------------------------------------------------ the tray

	/**
	 * The tray's corner is the interface's, not the screen's: the 4:3 box is centred in a wider viewport, so the world's
	 * right edge is not x = worldWidth. The dp are the phone's, turned into world units by the projection the stick is
	 * drawn with, so both scale together on any screen.
	 */
	private void layoutTray() {
		final float density = (Gdx.graphics == null) ? 1f : Gdx.graphics.getDensity();
		final float right = this.ui.minimapPanel.heroInterfaceLeft() + this.ui.uiViewport.getWorldWidth();
		final float bottom = this.ui.minimapPanel.heroInterfaceTop() - this.ui.uiViewport.getWorldHeight();
		this.tray.layout(right, bottom, this.worldPerScreenPixel * density);
	}

	private void updateTray() {
		layoutTray();
		if (!canTakeTouches()) {
			// A dead hero, a fade, or a cinematic: nothing is being aimed and nothing is chosen.
			this.aimingSlot = -1;
			this.armedSlot = -1;
		}
		if (this.heroAbility == null) {
			this.heroAbility = findHeroAbility();
		}
		if (this.heroAbility == null) {
			return;
		}
		this.inventory = this.hero.getInventoryData();
		this.tray.refresh(this.hero, this.heroAbility, this.inventory);
	}

	/** Screen pixels to UI world units, in the vector given: the viewport unprojects in place. */
	private Vector2 toUi(final float screenX, final float screenY, final Vector2 out) {
		out.set(screenX, screenY);
		return this.ui.uiViewport.unproject(out);
	}

	// ------------------------------------------------------------------ the tray's touches

	@Override
	public boolean beginSkill(final float screenX, final float screenY) {
		if (!canTakeTouches()) {
			return false;
		}
		final Vector2 point = toUi(screenX, screenY, this.uiPoint);
		final int slot = this.tray.skillAt(point.x, point.y);
		if (slot < 0) {
			// The items and the attack button act on the lift, so a drag that starts on them belongs to nobody.
			return (this.tray.itemAt(point.x, point.y) >= 0) || this.tray.attackAt(point.x, point.y);
		}
		this.aimingSlot = slot;
		this.armedSlot = -1;
		updateAim(screenX, screenY);
		return true;
	}

	@Override
	public void dragSkill(final float screenX, final float screenY) {
		if (this.aimingSlot >= 0) {
			updateAim(screenX, screenY);
		}
	}

	@Override
	public void endSkill(final float screenX, final float screenY) {
		final int slot = this.aimingSlot;
		if (slot < 0) {
			return;
		}
		this.aimingSlot = -1;
		final Vector2 point = toUi(screenX, screenY, this.uiPoint);
		if (this.tray.stillOnSlot(slot, point.x, point.y)) {
			// Back on the button it started from: the player changed their mind, as on the command card.
			return;
		}
		updateAim(screenX, screenY);
		releaseSkill(slot, screenX, screenY);
	}

	@Override
	public void cancelSkill() {
		this.aimingSlot = -1;
		this.armedSlot = -1;
	}

	@Override
	public boolean tap(final float screenX, final float screenY) {
		if (!this.active) {
			return false;
		}
		final Vector2 point = toUi(screenX, screenY, this.uiPoint);
		for (int i = 0; i < HeroTray.SKILL_SLOTS; i++) {
			if (this.tray.onLearnBadge(i, point.x, point.y)) {
				learnSkill(i);
				return true;
			}
		}
		final int slot = this.tray.skillAt(point.x, point.y);
		if (slot >= 0) {
			tappedSkill(slot);
			return true;
		}
		final int item = this.tray.itemAt(point.x, point.y);
		if (item >= 0) {
			useItem(item);
			return true;
		}
		if (this.tray.attackAt(point.x, point.y)) {
			attackNearestEnemy();
			return true;
		}
		if (this.armedSlot >= 0) {
			final int armed = this.armedSlot;
			final CWidget target = pickTargetFor(armed, screenX, screenY);
			if (target != null) {
				this.armedSlot = -1;
				castAtWidget(armed, target);
			}
			// A tap on nobody keeps the skill chosen: the player is probably still finding their target.
			return true;
		}
		return tapGroundItem(screenX, screenY);
	}

	// ------------------------------------------------------------------ casting

	/** A tap on a skill's circle: a no-target skill fires, a single-target one is chosen, the rest need a drag. */
	private void tappedSkill(final int slot) {
		final HeroTray.SkillSlot state = this.tray.skills[slot];
		if (state.skill == null) {
			return;
		}
		if (state.level <= 0) {
			// An unlearned slot buys its first level when there is a point to spend, exactly as the "+" would.
			learnSkill(slot);
			return;
		}
		switch (castTypeOf(slot)) {
		case NO_TARGET:
			this.armedSlot = -1;
			castNoTarget(slot);
			break;
		case SINGLE_TARGET:
			this.armedSlot = (this.armedSlot == slot) ? -1 : slot;
			break;
		default:
			// A direction or a ground skill is aimed by dragging; a tap has no direction to give it.
			break;
		}
	}

	/** The finger let go of a skill it was aiming: cast it the way its type is cast, or leave it chosen. */
	private void releaseSkill(final int slot, final float screenX, final float screenY) {
		final HeroTray.SkillSlot state = this.tray.skills[slot];
		if (state.skill == null) {
			return;
		}
		if (state.level <= 0) {
			learnSkill(slot);
			return;
		}
		switch (castTypeOf(slot)) {
		case NO_TARGET:
			castNoTarget(slot);
			break;
		case SINGLE_TARGET: {
			final CWidget target = pickTargetFor(slot, screenX, screenY);
			if (target != null) {
				castAtWidget(slot, target);
			}
			else {
				// Released on nobody: the skill stays chosen, so that a second tap on a target casts it.
				this.armedSlot = slot;
			}
			break;
		}
		case DIRECTION:
		case GROUND:
			castAtPoint(slot, this.aimLocation.x, this.aimLocation.y);
			break;
		}
	}

	/**
	 * Where the aim points now, in map coordinates: the thumb's point pulled back onto the spell's cast range. The
	 * clamp is the tray's own decision — the engine would accept a point out of range and walk the hero there, which
 * on a phone means a mis-tap sends her across the map.
	 */
	private void updateAim(final float screenX, final float screenY) {
		this.ui.war3MapViewer.getClickLocation(this.aimLocation, Math.round(screenX), Math.round(screenY), true, true);
		final int slot = this.aimingSlot;
		final CAbility ability = (slot >= 0) ? this.tray.skills[slot].ability : null;
		final float range = (ability == null) ? 0f : HeroTray.castRangeOf(ability);
		HeroAim.clamp(this.hero.getX(), this.hero.getY(), this.aimLocation.x, this.aimLocation.y, range,
				this.clampedAim);
		this.aimLocation.x = this.clampedAim[0];
		this.aimLocation.y = this.clampedAim[1];
	}

	/** How a slot is cast: the table's answer, or what the spell accepts when the table does not know it. */
	private HeroCastType castTypeOf(final int slot) {
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return HeroCastType.NO_TARGET;
		}
		final int orderId = HeroTray.orderIdOf(ability);
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		final int playerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		return HeroCastTypes.of(ability, new HeroCastTypes.TargetProbe() {
			@Override
			public boolean acceptsNoTarget() {
				final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver
						.<Void>getInstance().reset();
				ability.checkCanTargetNoTarget(simulation, HeroMode.this.hero, playerIndex, orderId, false, receiver);
				return receiver.isTargetable();
			}

			@Override
			public boolean acceptsPoint() {
				HeroMode.this.orderTarget.x = HeroMode.this.hero.getX();
				HeroMode.this.orderTarget.y = HeroMode.this.hero.getY();
				final BooleanAbilityTargetCheckReceiver<AbilityPointTarget> receiver = BooleanAbilityTargetCheckReceiver
						.getInstance();
				receiver.reset();
				ability.checkCanTarget(simulation, HeroMode.this.hero, playerIndex, orderId, false,
						HeroMode.this.orderTarget, receiver);
				return receiver.isTargetable();
			}
		});
	}

	/** The learn order is the skill's own rawcode, which the hero ability reads as "learn this" and nothing else. */
	private void learnSkill(final int slot) {
		final War3ID skill = this.tray.skills[slot].skill;
		if ((skill == null) || (this.heroAbility == null)) {
			return;
		}
		final ExternStringMsgAbilityActivationReceiver receiver = ExternStringMsgAbilityActivationReceiver.INSTANCE
				.reset();
		this.heroAbility.checkCanUse(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), skill.getValue(), false, receiver);
		if (!receiver.isUseOk()) {
			reportError(receiver.getExternStringKey());
			return;
		}
		this.ui.unitOrderListener.issueImmediateOrder(this.hero.getHandleId(), this.heroAbility.getHandleId(),
				skill.getValue(), false);
	}

	private void castNoTarget(final int slot) {
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return;
		}
		final int orderId = HeroTray.orderIdOf(ability);
		if (!canUseNow(ability, orderId)) {
			return;
		}
		final BooleanAbilityTargetCheckReceiver<Void> target = BooleanAbilityTargetCheckReceiver.<Void>getInstance()
				.reset();
		ability.checkCanTargetNoTarget(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, target);
		if (!target.isTargetable()) {
			return;
		}
		this.ui.unitOrderListener.issueImmediateOrder(this.hero.getHandleId(), ability.getHandleId(), orderId, false);
	}

	private void castAtPoint(final int slot, final float x, final float y) {
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return;
		}
		final int orderId = HeroTray.orderIdOf(ability);
		if (!canUseNow(ability, orderId)) {
			return;
		}
		this.orderTarget.x = x;
		this.orderTarget.y = y;
		final ExternStringMsgTargetCheckReceiver<AbilityPointTarget> receiver = ExternStringMsgTargetCheckReceiver
				.<AbilityPointTarget>getInstance().reset();
		ability.checkCanTarget(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, this.orderTarget, receiver);
		final AbilityPointTarget target = receiver.getTarget();
		if (target == null) {
			reportError(receiver.getExternStringKey());
			return;
		}
		this.ui.unitOrderListener.issuePointOrder(this.hero.getHandleId(), ability.getHandleId(), orderId, target.x,
				target.y, false);
	}

	private void castAtWidget(final int slot, final CWidget target) {
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return;
		}
		final int orderId = HeroTray.orderIdOf(ability);
		if (!canUseNow(ability, orderId)) {
			return;
		}
		final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
		receiver.reset();
		ability.checkCanTarget(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, target, receiver);
		final CWidget resolved = receiver.getTarget();
		if (resolved == null) {
			return;
		}
		this.ui.unitOrderListener.issueTargetOrder(this.hero.getHandleId(), ability.getHandleId(), orderId,
				resolved.getHandleId(), false);
	}

	private boolean canUseNow(final CAbility ability, final int orderId) {
		final ExternStringMsgAbilityActivationReceiver receiver = ExternStringMsgAbilityActivationReceiver.INSTANCE
				.reset();
		ability.checkCanUse(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, receiver);
		if (receiver.isUseOk()) {
			return true;
		}
		reportError(receiver.getExternStringKey());
		return false;
	}

	/**
	 * The unit under the finger that a slot may be cast on. The filter asks the spell itself, so a heal takes an ally
	 * and a strike takes an enemy without the tray knowing which is which.
	 */
	private CWidget pickTargetFor(final int slot, final float screenX, final float screenY) {
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return null;
		}
		final int orderId = HeroTray.orderIdOf(ability);
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		final int playerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final RenderWidget picked = this.ui.war3MapViewer.rayPickUnit(screenX, screenY, new CWidgetFilterFunction() {
			@Override
			public boolean call(final CWidget widget) {
				if ((widget == null) || widget.isDead()) {
					return false;
				}
				final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
				receiver.reset();
				ability.checkCanTarget(simulation, HeroMode.this.hero, playerIndex, orderId, false, widget, receiver);
				return receiver.getTarget() != null;
			}
		});
		return (picked == null) ? null : picked.getSimulationWidget();
	}

	/** An item on the ground: a smart order on the item itself, since a point order there walks past it. */
	private boolean tapGroundItem(final float screenX, final float screenY) {
		final RenderWidget picked = this.ui.war3MapViewer.rayPickUnit(screenX, screenY, new CWidgetFilterFunction() {
			@Override
			public boolean call(final CWidget widget) {
				return widget instanceof CItem;
			}
		});
		if ((picked == null) || !(picked.getSimulationWidget() instanceof CItem)) {
			return false;
		}
		issueSmartOrderOn(picked.getSimulationWidget());
		return true;
	}

	private void useItem(final int button) {
		final HeroTray.ItemButton state = this.tray.items[button];
		final CAbilityInventory inventoryData = this.inventory;
		if ((inventoryData == null) || (state.inventorySlot < 0)) {
			return;
		}
		final int orderId = OrderIds.itemUseFirst(this.ui.war3MapViewer.getGameData().isUseNineItemInventory())
				+ state.inventorySlot;
		final ExternStringMsgAbilityActivationReceiver receiver = ExternStringMsgAbilityActivationReceiver.INSTANCE
				.reset();
		inventoryData.checkCanUse(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, receiver);
		if (!receiver.isUseOk()) {
			reportError(receiver.getExternStringKey());
			return;
		}
		final BooleanAbilityTargetCheckReceiver<Void> noTarget = BooleanAbilityTargetCheckReceiver.<Void>getInstance()
				.reset();
		inventoryData.checkCanTargetNoTarget(this.ui.war3MapViewer.simulation, this.hero,
				this.ui.war3MapViewer.getLocalPlayerIndex(), orderId, false, noTarget);
		if (!noTarget.isTargetable()) {
			// An item that wants a target (a scroll on a unit) is not something hero mode aims yet.
			Log.info("Hero mode: item in slot " + state.inventorySlot + " needs a target; not used");
			return;
		}
		this.ui.unitOrderListener.issueImmediateOrder(this.hero.getHandleId(), inventoryData.getHandleId(), orderId,
				false);
	}

	/** The attack button: an order on the nearest enemy, so that a hero can be picked out of the crowd. */
	private void attackNearestEnemy() {
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		final int playerIndex = this.ui.war3MapViewer.getLocalPlayerIndex();
		final float range = Math.max(400f, this.hero.getUnitType().getDefaultAcquisitionRange());
		CUnit nearest = null;
		double best = Double.MAX_VALUE;
		for (final CUnit unit : simulation.getUnits()) {
			if ((unit == this.hero) || unit.isDead() || unit.isHidden() || (unit.getPlayerIndex() == playerIndex)) {
				continue;
			}
			final double distance = this.hero.distance(unit);
			if ((distance <= range) && (distance < best)) {
				best = distance;
				nearest = unit;
			}
		}
		if (nearest == null) {
			return;
		}
		issueSmartOrderOn(nearest);
	}

	/**
	 * The smart order a right click makes, found the way the console finds it: the last of the hero's abilities that
	 * accepts the order on this target wins, so an enemy is attacked, an item is picked up and a tree is harvested by
	 * the same call.
	 */
	private void issueSmartOrderOn(final CWidget target) {
		CAbility abilityToUse = null;
		CWidget targetToUse = null;
		for (final CAbility ability : this.hero.getAbilities()) {
			final CWidgetAbilityTargetCheckReceiver receiver = CWidgetAbilityTargetCheckReceiver.INSTANCE;
			receiver.reset();
			ability.checkCanTarget(this.ui.war3MapViewer.simulation, this.hero,
					this.ui.war3MapViewer.getLocalPlayerIndex(), OrderIds.smart, false, target, receiver);
			final CWidget resolved = receiver.getTarget();
			if (resolved != null) {
				abilityToUse = ability;
				targetToUse = resolved;
			}
		}
		if (abilityToUse != null) {
			this.ui.unitOrderListener.issueTargetOrder(this.hero.getHandleId(), abilityToUse.getHandleId(),
					OrderIds.smart, targetToUse.getHandleId(), false);
		}
	}

	private void reportError(final String externStringKey) {
		if ((externStringKey != null) && !externStringKey.isEmpty()) {
			this.ui.messages.showInterfaceError(this.ui.war3MapViewer.getLocalPlayerIndex(), externStringKey);
		}
	}

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

	/** The tray, the aim on top of it, and the stick: each swaps the batch for its own shapes and back. */
	void render(final SpriteBatch batch, final ShapeRenderer shapes) {
		if (!this.active) {
			return;
		}
		if (this.ui.cinematics.isInterfaceShown()) {
			this.tray.render(batch, shapes, this.armedSlot);
			drawAim(batch, shapes);
		}
		if (!this.stick.isActive()) {
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
		// The stick is kept in screen pixels; the UI space it is drawn in has y growing upwards.
		final float knobY = originY - (this.stick.getKnobOffsetY() * perPixel);

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

	// ------------------------------------------------------------------ aim drawing

	/**
	 * What the thumb is aiming: a circle where a ground skill would land, a line for a direction, and for a single
	 * target the ring of how far the target may be. Map units become UI units by projecting them, because the camera's
	 * zoom is what decides how large a game unit is on the screen.
	 */
	private void drawAim(final SpriteBatch batch, final ShapeRenderer shapes) {
		final int slot = this.aimingSlot;
		if (slot < 0) {
			return;
		}
		final CAbility ability = this.tray.skills[slot].ability;
		if (ability == null) {
			return;
		}
		final HeroCastType type = castTypeOf(slot);
		if (type == HeroCastType.NO_TARGET) {
			return;
		}
		final float range = HeroTray.castRangeOf(ability);
		uiOfWorld(this.hero.getX(), this.hero.getY(), this.heroUi);
		uiOfWorld(this.aimLocation.x, this.aimLocation.y, this.aimUi);
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Line);
		switch (type) {
		case DIRECTION:
			shapes.setColor(AIM_LINE_COLOR);
			shapes.line(this.heroUi.x, this.heroUi.y, this.aimUi.x, this.aimUi.y);
			break;
		case SINGLE_TARGET:
			if (range > 0f) {
				shapes.setColor(AIM_RANGE_COLOR);
				shapes.circle(this.heroUi.x, this.heroUi.y,
						uiLength(this.hero.getX(), this.hero.getY(), range), AIM_SEGMENTS);
			}
			shapes.setColor(AIM_COLOR);
			shapes.circle(this.aimUi.x, this.aimUi.y, this.tray.skillRadius() * 0.3f, 12);
			break;
		case GROUND:
		default:
			shapes.setColor(AIM_COLOR);
			shapes.circle(this.aimUi.x, this.aimUi.y,
					uiLength(this.aimLocation.x, this.aimLocation.y,
							Math.max(AIM_MIN_AREA, HeroTray.areaOfEffectOf(ability))),
					AIM_SEGMENTS);
			break;
		}
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		batch.begin();
	}

	/** A map point in UI world units, at the ground height of the point. */
	private void uiOfWorld(final float gameX, final float gameY, final Vector2 out) {
		final float groundZ = this.ui.war3MapViewer.terrain.getGroundHeight(gameX, gameY);
		this.worldScratch.set(gameX, gameY, groundZ);
		this.ui.war3MapViewer.worldScene.camera.worldToScreen(this.screenScratch, this.worldScratch);
		out.set(this.screenScratch);
		this.ui.uiViewport.unproject(out);
	}

	/** How long a length of game units is on the screen, in UI world units, measured where it is drawn. */
	private float uiLength(final float gameX, final float gameY, final float gameLength) {
		uiOfWorld(gameX, gameY, this.heroUi2);
		uiOfWorld(gameX + gameLength, gameY, this.aimUi2);
		return Math.abs(this.aimUi2.x - this.heroUi2.x);
	}
}
