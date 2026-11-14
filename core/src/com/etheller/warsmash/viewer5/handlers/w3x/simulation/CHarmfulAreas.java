package com.etheller.warsmash.viewer5.handlers.w3x.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.targeting.AbilityPointTarget;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehavior;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorAttack;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorCategory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.behaviors.CBehaviorHoldPosition;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.timers.CTimer;

/**
 * The ground effects that hurt the units standing in them (Flame Strike, Blizzard, Inferno, Starfall, Death and
 * Decay), and the way a computer player's units step out of them.
 *
 * Measured on 1.29: a unit of a computer player without an AI script that stands in such an area once it has hurt
 * for a moment walks 700 away, and does so again when it is still in it after it got there; it leaves away from the
 * caster of another player's spell, and goes on towards the centre of its own spell.
 */
public class CHarmfulAreas {
	private static final float ESCAPE_DISTANCE = 700;
	private static final int THINK_TICKS = CTimer.wholeSteps(1.0f / 3);

	public static final class Area {
		private final CUnit source;
		private final float x;
		private final float y;
		private final float radius;
		private int hurtTick;
		private final int firstHurtTick;
		private final int endTick;
		private final boolean hurtOnly;

		private Area(final CUnit source, final float x, final float y, final float radius, final int hurtTick,
				final int endTick, final boolean hurtOnly) {
			this.source = source;
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.hurtTick = hurtTick;
			this.firstHurtTick = hurtTick - 1;
			this.endTick = endTick;
			this.hurtOnly = hurtOnly;
		}
	}

	private static final class Escape {
		private int nextThinkTick;
		private float directionX;
		private float directionY;
		private boolean escaping;
		private boolean returning;
		private int returnTick;
		private float homeX;
		private float homeY;
		private int restoreSpeed = -1;
	}

	private final List<Area> areas = new ArrayList<>();
	private final Map<CUnit, Escape> escapes = new HashMap<>();
	private int pruneTick = -1;

	public Area add(final CSimulation game, final CUnit source, final float x, final float y, final float radius,
			final float hurtDelay, final float duration) {
		return add(game, source, x, y, radius, hurtDelay, duration, false);
	}

	/**
	 * An area whose damage spares some units (Starfall hurts only what its caster sees) sends away only the units it
	 * hurt.
	 */
	public Area add(final CSimulation game, final CUnit source, final float x, final float y, final float radius,
			final float hurtDelay, final float duration, final boolean hurtOnly) {
		final int tick = game.getGameTurnTick();
		// A unit that is hit answers the hit on its next update, so the area sends nobody away before that.
		final Area area = new Area(source, x, y, radius, tick + CTimer.wholeSteps(hurtDelay) + 1,
				tick + CTimer.wholeSteps(duration), hurtOnly);
		this.areas.add(area);
		return area;
	}

	/** An area that makes nobody leave until {@link #arm} says when its damage starts to send units away. */
	public Area addUnarmed(final CSimulation game, final CUnit source, final float x, final float y,
			final float radius, final float duration) {
		final Area area = add(game, source, x, y, radius, 0, duration, false);
		area.hurtTick = Integer.MAX_VALUE;
		return area;
	}

	public void arm(final CSimulation game, final Area area, final float hurtDelay) {
		area.hurtTick = game.getGameTurnTick() + CTimer.wholeSteps(hurtDelay);
	}

	/** Whether the unit stands in an area of this source, whose damage draws no answer or run from it. */
	public boolean hurtsUnit(final CSimulation game, final CUnit unit, final CUnit source) {
		final int tick = game.getGameTurnTick();
		for (final Area area : this.areas) {
			if ((area.source == source) && (tick < area.endTick) && !source.isRemovedFromGame() && !source.isDead()
					&& ((StrictMath.hypot(unit.getX() - area.x, unit.getY() - area.y)
							- unit.getUnitType().getCollisionSize()) <= area.radius)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Called every tick for a computer player's unit; orders it out of the area it stands in.
	 */
	public void stepOut(final CSimulation game, final CUnit unit) {
		if (this.areas.isEmpty() && this.escapes.isEmpty()) {
			return;
		}
		final int tick = game.getGameTurnTick();
		if (this.areas.isEmpty() && (tick != this.pruneTick)) {
			this.pruneTick = tick;
			this.escapes.keySet().removeIf((gone) -> gone.isDead() || gone.isRemovedFromGame());
		}
		final Escape escape = this.escapes.get(unit);
		if ((escape != null) && (tick < escape.nextThinkTick)) {
			return;
		}
		Area inside = null;
		for (final Iterator<Area> iterator = this.areas.iterator(); iterator.hasNext();) {
			final Area area = iterator.next();
			if ((tick >= area.endTick) || area.source.isRemovedFromGame() || area.source.isDead()) {
				// Measured: a spell goes with its caster; a unit that is removed or dies leaves no area behind (a
				// caster that is only ordered to stop leaves it).
				iterator.remove();
			}
			else if ((tick >= area.hurtTick) && (inside == null)
					&& (!area.hurtOnly || (unit.aggro.lastHurtTick >= area.firstHurtTick))
					&& ((StrictMath.hypot(unit.getX() - area.x, unit.getY() - area.y)
							- unit.getUnitType().getCollisionSize()) <= area.radius)) {
				inside = area;
			}
		}
		final CBehavior behavior = unit.getCurrentBehavior();
		final boolean idle = (behavior == null) || (behavior.getBehaviorCategory() == CBehaviorCategory.IDLE);
		if (inside == null) {
			if (escape != null) {
				if (escape.escaping && !escape.returning && idle) {
					// Measured: a unit that walked the whole way out walks back to where it stood.
					escape.returning = true;
					escape.returnTick = tick;
					unit.order(game, OrderIds.attack, new AbilityPointTarget(escape.homeX, escape.homeY), true);
				}
				else if (!escape.escaping || !(escape.escaping && (behavior != null)
						&& (behavior.getBehaviorCategory() == CBehaviorCategory.MOVEMENT))) {
					if (escape.restoreSpeed >= 0) {
						unit.setSpeed(escape.restoreSpeed);
					}
					this.escapes.remove(unit);
				}
			}
			return;
		}
		if (behavior != null) {
			final CBehaviorCategory category = behavior.getBehaviorCategory();
			// Measured: a unit that has a target to fight keeps at it, and one on hold position never leaves.
			if ((category == CBehaviorCategory.SPELL) || (category == CBehaviorCategory.BUILD)
					|| (category == CBehaviorCategory.ATTACK) || (behavior instanceof CBehaviorHoldPosition)
					|| ((behavior == unit.moveBehavior) && (unit.moveBehavior.getRangedBehavior() instanceof CBehaviorAttack))) {
				return;
			}
		}
		// Measured: walking back into the area it fled, the unit runs again only after it is hurt once more.
		if ((escape != null) && escape.returning && (unit.aggro.lastHurtTick < escape.returnTick)) {
			return;
		}
		final Escape current = (escape != null) ? escape : new Escape();
		this.escapes.put(unit, current);
		current.nextThinkTick = tick + THINK_TICKS;
		if (current.returning) {
			current.returning = false;
			current.escaping = false;
		}
		if (current.escaping && (behavior != null) && (behavior.getBehaviorCategory() == CBehaviorCategory.MOVEMENT)) {
			return;
		}
		if (!current.escaping) {
			current.escaping = true;
			// Measured: a unit whose speed was set to 0 still walks out of the area.
			if ((unit.getSpeed() <= 0) && !unit.isMovementDisabled() && (unit.getUnitType().getSpeed() > 0)) {
				current.restoreSpeed = unit.getSpeed();
				unit.setSpeed(unit.getUnitType().getSpeed());
			}
			current.homeX = unit.getX();
			current.homeY = unit.getY();
			float fromX = unit.getX() - inside.source.getX();
			float fromY = unit.getY() - inside.source.getY();
			if (inside.source == unit) {
				fromX = inside.x - unit.getX();
				fromY = inside.y - unit.getY();
			}
			final float length = (float) StrictMath.hypot(fromX, fromY);
			if (length < 1) {
				fromX = (float) StrictMath.cos(Math.toRadians(unit.getFacing()));
				fromY = (float) StrictMath.sin(Math.toRadians(unit.getFacing()));
			}
			else {
				fromX /= length;
				fromY /= length;
			}
			current.directionX = fromX;
			current.directionY = fromY;
		}
		unit.order(game, OrderIds.move, new AbilityPointTarget(unit.getX() + (current.directionX * ESCAPE_DISTANCE),
				unit.getY() + (current.directionY * ESCAPE_DISTANCE)), true);
	}
}
