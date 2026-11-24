package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.ItemUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CItem;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CReleaseCheats;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CSimulation;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.CUnit;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.CAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.GetAbilityByRawcodeVisitor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.GenericSingleIconActiveAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.generic.SingleOrderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.hero.CAbilityHero;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.inventory.CAbilityInventory;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.skills.CAbilitySpellBase;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilities.types.CAbilityType;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.abilitybuilder.ability.AbilityBuilderAbility;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.data.CAbilityData;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.orders.OrderIds;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.AbilityActivationReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.BooleanAbilityTargetCheckReceiver;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.util.CommandStringErrorKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The hero-mode tray: four round skill buttons on an outer arc, the big attack button and up to four usable items
 * inside it, all in a corner-centred cluster under the right thumb. The layout, in dp, is
 * {@code docs/images/hero-tray-layout.png}; the design is in {@code docs/hero-mode.md}.
 * <p>
 * The tray owns the geometry, the circular icon copies and the state each button is drawn from — level, cooldown, "not
 * enough mana", "cannot be used now". What a tap or a drag of a button <em>does</em> belongs to {@link HeroMode}, which
 * asks {@link #skillAt}, {@link #itemAt}, {@link #attackAt} and {@link #onLearnBadge} where the finger is.
 */
final class HeroTray {
	static final int SKILL_SLOTS = HeroSkillSlots.SLOT_COUNT;
	static final int ITEM_BUTTONS = HeroItemSlots.MAX_BUTTONS;
	/** Extra skills the row can hold: whatever positions the item buttons of the row leave free. */
	static final int EXTRA_BUTTONS = HeroItemSlots.ROW_SLOTS;

	// Everything below is dp measured from the interface's bottom-right corner, and matches the drawn layout. They
	// came down a sixth from the picture's numbers after the first look on a 480 dpi phone, where 52 dp circles read
	// as heavy. The picture is regenerated from these.
	private static final float SKILL_RADIUS = 22f;
	private static final float SKILL_RING = 149f;
	/** Slot 1 to slot 4, in data order: the last one is the ultimate, and it is the top-right of the arc. */
	private static final float[] SKILL_ANGLE = { 172f, 148f, 124f, 100f };
	private static final float ATTACK_RADIUS = 29f;
	private static final float ATTACK_RING = 82.5f;
	private static final float ATTACK_ANGLE = 133.7f;
	private static final float ITEM_RADIUS = 15f;
	/** The two item buttons of the inner arc, on either side of the attack button. */
	private static final float ITEM_INNER_RING = 100f;
	private static final float[] ITEM_INNER_ANGLE = { 104f, 165.7f };
	/** Further usable items, and then the hero's extra skills, in a row along the bottom edge, left of the inner arc. */
	private static final float ITEM_ROW_Y = 20f;
	private static final float[] ITEM_ROW_X = { 214f, 264f, 314f };
	/** The "+" sits on its slot's ring, up and to the right, so that the icon it belongs to still casts. */
	private static final float BADGE_OFFSET = 15f;
	private static final float BADGE_RADIUS = 11f;
	/** The border the buttons are drawn with, and the ring it is cut out of. */
	private static final float BORDER = 2.5f;
	private static final int SEGMENTS = 40;
	private static final int WEDGE_SEGMENTS = 24;

	private static final Color RING_COLOR = new Color(1f, 1f, 1f, 0.8f);
	private static final Color RING_ARMED_COLOR = new Color(0.5f, 0.85f, 1f, 1f);
	private static final Color BORDER_DISABLED_COLOR = new Color(0.55f, 0.55f, 0.55f, 0.75f);
	private static final Color COOLDOWN_COLOR = new Color(0f, 0f, 0f, 0.55f);
	private static final Color NO_MANA_TINT = new Color(0.35f, 0.45f, 1f, 0.62f);
	private static final Color DISABLED_TINT = new Color(0.4f, 0.4f, 0.4f, 0.7f);
	/** A skill the hero has not bought: flat grey, so it reads as "not mine yet" beside the bought ones. */
	private static final Color UNLEARNED_TINT = new Color(0.32f, 0.32f, 0.32f, 0.62f);
	private static final Color BADGE_COLOR = new Color(0.29f, 0.8f, 0.42f, 1f);
	private static final Color BADGE_PLUS_COLOR = new Color(0.03f, 0.14f, 0.06f, 1f);

	/** One skill button: what it is, how it is drawn, and where it is in UI world units. */
	static final class SkillSlot {
		War3ID skill;
		CAbility ability;
		int level;
		int maxLevel;
		boolean learnable;
		boolean unlearned;
		boolean disabled;
		boolean notEnoughMana;
		float cooldown;
		float cooldownMax;
		Texture icon;
		float x;
		float y;
	}

	/** One usable item: the inventory slot behind it, its icon, and how many uses it has left. */
	static final class ItemButton {
		int inventorySlot = -1;
		Texture icon;
		int charges;
		boolean disabled;
		float cooldown;
		float cooldownMax;
		float x;
		float y;
	}

	/**
	 * A skill the four arc slots have no room for — a hero's Shadow Meld is the case in hand — drawn in the row after
	 * the items and cast by a tap, exactly as the original command card kept a place for it.
	 */
	static final class ExtraButton {
		CAbility ability;
		Texture icon;
		boolean disabled;
		boolean notEnoughMana;
		/** On now, like a meld or a toggle left running: the button says so instead of looking idle. */
		boolean running;
		float cooldown;
		float cooldownMax;
		float x;
		float y;
	}

	private final MeleeUI ui;
	private final HeroTrayIcons icons;
	private final ActivationState activation = new ActivationState();
	private final GlyphLayout textLayout = new GlyphLayout();

	final SkillSlot[] skills = new SkillSlot[SKILL_SLOTS];
	final ItemButton[] items = new ItemButton[ITEM_BUTTONS];
	final ExtraButton[] extras = new ExtraButton[EXTRA_BUTTONS];
	private int itemButtonCount;
	int extraButtonCount;
	private Texture attackIcon;

	/** UI world units per dp: the screen density times the projection's world units per screen pixel. */
	private float unit = 1f;
	private float interfaceRight;
	private float interfaceBottom;
	private float attackX;
	private float attackY;
	private float skillRadius;
	private float itemRadius;
	private float attackRadius;
	private BitmapFont font;
	private int fontPixelSize = -1;

	HeroTray(final MeleeUI ui) {
		this.ui = ui;
		this.icons = new HeroTrayIcons(ui.dataSource);
		for (int i = 0; i < SKILL_SLOTS; i++) {
			this.skills[i] = new SkillSlot();
		}
		for (int i = 0; i < ITEM_BUTTONS; i++) {
			this.items[i] = new ItemButton();
		}
		for (int i = 0; i < EXTRA_BUTTONS; i++) {
			this.extras[i] = new ExtraButton();
		}
	}

	// ------------------------------------------------------------------ geometry

	/**
	 * Places the whole tray against the interface's bottom-right corner.
	 *
	 * @param right  the interface's right edge, in UI world units
	 * @param bottom the interface's bottom edge, in UI world units
	 * @param unit   UI world units per dp
	 */
	void layout(final float right, final float bottom, final float unit) {
		this.interfaceRight = right;
		this.interfaceBottom = bottom;
		this.unit = unit;
		this.skillRadius = SKILL_RADIUS * unit;
		this.itemRadius = ITEM_RADIUS * unit;
		this.attackRadius = ATTACK_RADIUS * unit;
		// A phone's dp are not the drawing's: at 480 dpi the lowest skill of the arc hangs a few pixels over the
		// bottom edge, and art cut by a screen edge is not a design decision. Whatever overflows moves the cluster in.
		final float[] shift = fitShift(right, bottom, unit);
		final float insideRight = right - shift[0];
		final float insideBottom = bottom + shift[1];
		for (int i = 0; i < SKILL_SLOTS; i++) {
			final float[] point = onRing(insideRight, insideBottom, unit, SKILL_RING, SKILL_ANGLE[i]);
			this.skills[i].x = point[0];
			this.skills[i].y = point[1];
		}
		final float[] attack = onRing(insideRight, insideBottom, unit, ATTACK_RING, ATTACK_ANGLE);
		this.attackX = attack[0];
		this.attackY = attack[1];
		for (int i = 0; i < ITEM_BUTTONS; i++) {
			if (i < HeroItemSlots.INNER_ARC_BUTTONS) {
				final float[] point = onRing(insideRight, insideBottom, unit, ITEM_INNER_RING, ITEM_INNER_ANGLE[i]);
				this.items[i].x = point[0];
				this.items[i].y = point[1];
			}
			else {
				this.items[i].x = insideRight - (ITEM_ROW_X[i - HeroItemSlots.INNER_ARC_BUTTONS] * unit);
				this.items[i].y = insideBottom + (ITEM_ROW_Y * unit);
			}
		}
		// The extra skills come after the items in the row, so they start where the item buttons of the row end.
		final int firstExtraColumn = HeroItemSlots.rowItemCount(this.itemButtonCount);
		for (int i = 0; i < EXTRA_BUTTONS; i++) {
			final int column = Math.min(firstExtraColumn + i, ITEM_ROW_X.length - 1);
			this.extras[i].x = insideRight - (ITEM_ROW_X[column] * unit);
			this.extras[i].y = insideBottom + (ITEM_ROW_Y * unit);
		}
	}

	/**
	 * How far the cluster has to move in from a corner to keep every circle of it inside the interface, as
	 * {@code {in from the right, up from the bottom}}.
	 */
	static float[] fitShift(final float right, final float bottom, final float unit) {
		float overRight = 0f;
		float overBottom = 0f;
		for (int i = 0; i < SKILL_SLOTS; i++) {
			final float[] point = onRing(right, bottom, unit, SKILL_RING, SKILL_ANGLE[i]);
			overRight = Math.max(overRight, point[0] + (SKILL_RADIUS * unit) - right);
			overBottom = Math.max(overBottom, bottom - point[1] + (SKILL_RADIUS * unit));
		}
		final float[] attack = onRing(right, bottom, unit, ATTACK_RING, ATTACK_ANGLE);
		overRight = Math.max(overRight, attack[0] + (ATTACK_RADIUS * unit) - right);
		overBottom = Math.max(overBottom, bottom - attack[1] + (ATTACK_RADIUS * unit));
		for (int i = 0; i < HeroItemSlots.INNER_ARC_BUTTONS; i++) {
			final float[] point = onRing(right, bottom, unit, ITEM_INNER_RING, ITEM_INNER_ANGLE[i]);
			overRight = Math.max(overRight, point[0] + (ITEM_RADIUS * unit) - right);
			overBottom = Math.max(overBottom, bottom - point[1] + (ITEM_RADIUS * unit));
		}
		for (int i = 0; i < ITEM_ROW_X.length; i++) {
			final float x = right - (ITEM_ROW_X[i] * unit);
			final float y = bottom + (ITEM_ROW_Y * unit);
			overRight = Math.max(overRight, x + (ITEM_RADIUS * unit) - right);
			overBottom = Math.max(overBottom, bottom - y + (ITEM_RADIUS * unit));
		}
		return new float[] { Math.max(0f, overRight), Math.max(0f, overBottom) };
	}

	/**
	 * The highest point the cluster draws at, in dp above the interface's bottom edge, badge included.
	 * <p>
	 * The macro panel's two round buttons hang above the tray, so this is what keeps them off it. The topmost thing the
	 * cluster puts on the screen is not a circle but the ultimate's learn badge, drawn up and to the right of its slot.
	 */
	static float clusterTopDp() {
		float top = 0f;
		for (int i = 0; i < SKILL_SLOTS; i++) {
			final float slot = (float) (Math.sin(Math.toRadians(SKILL_ANGLE[i])) * SKILL_RING);
			top = Math.max(top, slot + SKILL_RADIUS);
			top = Math.max(top, slot + BADGE_OFFSET + BADGE_RADIUS);
		}
		top = Math.max(top, (float) (Math.sin(Math.toRadians(ATTACK_ANGLE)) * ATTACK_RING) + ATTACK_RADIUS);
		for (int i = 0; i < HeroItemSlots.INNER_ARC_BUTTONS; i++) {
			top = Math.max(top, (float) (Math.sin(Math.toRadians(ITEM_INNER_ANGLE[i])) * ITEM_INNER_RING) + ITEM_RADIUS);
		}
		return Math.max(top, ITEM_ROW_Y + ITEM_RADIUS);
	}

	/**
	 * A point a ring's radius from the corner, at an angle of degrees measured from the interface's right edge going
	 * up, which is the way the layout picture reads: every button of the cluster is left of the corner and above it,
	 * so every angle is between 90 and 180.
	 */
	static float[] onRing(final float right, final float bottom, final float unit, final float ring,
			final float degrees) {
		final double radians = Math.toRadians(degrees);
		return new float[] { right + ((float) (Math.cos(radians) * ring * unit)),
				bottom + ((float) (Math.sin(radians) * ring * unit)) };
	}

	// ------------------------------------------------------------------ hit tests, in UI world units

	/** @return the skill slot under the point, or -1 */
	int skillAt(final float x, final float y) {
		for (int i = 0; i < SKILL_SLOTS; i++) {
			if ((this.skills[i].skill != null)
					&& inside(this.skills[i].x, this.skills[i].y, this.skillRadius, x, y)) {
				return i;
			}
		}
		return -1;
	}

	/** @return the item button under the point, or -1 */
	int itemAt(final float x, final float y) {
		for (int i = 0; i < this.itemButtonCount; i++) {
			if (inside(this.items[i].x, this.items[i].y, this.itemRadius, x, y)) {
				return i;
			}
		}
		return -1;
	}

	/** @return the extra skill button under the point, or -1 */
	int extraAt(final float x, final float y) {
		for (int i = 0; i < this.extraButtonCount; i++) {
			if (inside(this.extras[i].x, this.extras[i].y, this.itemRadius, x, y)) {
				return i;
			}
		}
		return -1;
	}

	/** Whether the point is anywhere on the cluster, which is what keeps the stick and the camera off it. */
	boolean contains(final float x, final float y) {
		if ((skillAt(x, y) >= 0) || (itemAt(x, y) >= 0) || (extraAt(x, y) >= 0) || attackAt(x, y)) {
			return true;
		}
		for (int i = 0; i < SKILL_SLOTS; i++) {
			if (onLearnBadge(i, x, y)) {
				return true;
			}
		}
		return false;
	}

	boolean attackAt(final float x, final float y) {
		return inside(this.attackX, this.attackY, this.attackRadius, x, y);
	}

	/** The "+" is its own target: the circle it sits on casts the skill. */
	boolean onLearnBadge(final int slot, final float x, final float y) {
		if ((slot < 0) || (slot >= SKILL_SLOTS) || !this.skills[slot].learnable) {
			return false;
		}
		return inside(this.skills[slot].x + (BADGE_OFFSET * this.unit),
				this.skills[slot].y + (BADGE_OFFSET * this.unit), BADGE_RADIUS * this.unit, x, y);
	}

	/** Whether a finger that started on this slot is still on it: dragging back in cancels the aim. */
	boolean stillOnSlot(final int slot, final float x, final float y) {
		return (slot >= 0) && (slot < SKILL_SLOTS)
				&& inside(this.skills[slot].x, this.skills[slot].y, this.skillRadius, x, y);
	}

	private static boolean inside(final float centreX, final float centreY, final float radius, final float x,
			final float y) {
		final float dx = x - centreX;
		final float dy = y - centreY;
		return ((dx * dx) + (dy * dy)) <= (radius * radius);
	}

	float skillRadius() {
		return this.skillRadius;
	}

	// ------------------------------------------------------------------ state

	/**
	 * Reads the hero's skills and her inventory so that the next frame draws what is true now. Called every frame
	 * while hero mode plays: a level, a cooldown and the mana all change without telling anyone.
	 */
	void refresh(final CUnit hero, final CAbilityHero heroAbility, final CAbilityInventory inventory) {
		final CSimulation simulation = this.ui.war3MapViewer.simulation;
		final int playerIndex = this.ui.localPlayer.getId();
		final CAbilityData abilityData = simulation.getAbilityData();
		final List<War3ID> available = HeroSkillSlots.slots(heroAbility.getSkillsAvailable());
		final int skillPoints = heroAbility.getSkillPoints();
		for (int i = 0; i < SKILL_SLOTS; i++) {
			final SkillSlot slot = this.skills[i];
			final War3ID skill = (i < available.size()) ? available.get(i) : null;
			if ((skill == null) || !skill.equals(slot.skill)) {
				resetSlot(slot, skill);
			}
			if (skill == null) {
				continue;
			}
			slot.level = hero.getAbilityLevel(skill);
			slot.unlearned = HeroSkillSlots.isUnlearned(slot.level);
			final CAbilityType<?> levelData = abilityData.getAbilityType(skill);
			slot.maxLevel = (levelData != null) ? levelData.getLevelCount() : Math.max(1, slot.level);
			final AbilityUI abilityUI = this.ui.war3MapViewer.getAbilityDataUI().getUI(skill);
			slot.icon = iconOf(abilityUI, slot.level);
			slot.ability = hero.getAbility(GetAbilityByRawcodeVisitor.getInstance().reset(skill));
			slot.learnable = false;
			if ((slot.maxLevel <= 0) || (slot.level < slot.maxLevel)) {
				this.activation.reset();
				heroAbility.checkCanUse(simulation, hero, playerIndex, skill.getValue(), false, this.activation);
				slot.learnable = this.activation.canUse && (skillPoints > 0);
			}
			slot.disabled = false;
			slot.notEnoughMana = false;
			slot.cooldown = 0f;
			slot.cooldownMax = 0f;
			if (slot.ability != null) {
				this.activation.reset();
				slot.ability.checkCanUse(simulation, hero, playerIndex, orderIdOf(slot.ability), false,
						this.activation);
				slot.disabled = this.activation.disabled;
				// The engine answers one question and stops: `CAbilitySpellBase.innerCheckCanUse` reports the cooldown
				// and only then, with the cooldown gone, the mana. A cast that empties the hero's pool has both true in
				// the same frame, so the tray asks the mana itself rather than wait for the sweep to end.
				slot.notEnoughMana = this.activation.notEnoughMana || !affordable(hero, slot.ability);
				slot.cooldown = this.activation.cooldownRemaining;
				slot.cooldownMax = this.activation.cooldownMax;
			}
		}
		refreshItems(hero, inventory, simulation, playerIndex);
		refreshExtras(hero, simulation, playerIndex, available);
	}

	private static void resetSlot(final SkillSlot slot, final War3ID skill) {
		slot.skill = skill;
		slot.level = 0;
		slot.maxLevel = 0;
		slot.learnable = false;
		slot.unlearned = false;
		slot.disabled = false;
		slot.notEnoughMana = false;
		slot.cooldown = 0f;
		slot.cooldownMax = 0f;
		slot.ability = null;
		slot.icon = null;
	}

	/**
	 * The icon of a skill at the level it is at. An unlearned skill shows its first level's icon rather than nothing,
	 * so that a slot is recognisable before it is bought; the "level 1" it shows on that icon is the level the hero
	 * would get.
	 */
	private Texture iconOf(final AbilityUI abilityUI, final int level) {
		if (abilityUI == null) {
			return null;
		}
		final int index = Math.max(0, Math.min(level - 1, Math.max(0, abilityUI.getLevelCount() - 1)));
		final IconUI iconUI = abilityUI.getOnIconUI(index);
		return (iconUI == null) ? null : this.icons.masked(iconUI.getIconPath());
	}

	private void refreshItems(final CUnit hero, final CAbilityInventory inventory, final CSimulation simulation,
			final int playerIndex) {
		if (inventory == null) {
			clearItems();
			return;
		}
		final int capacity = inventory.getItemCapacity();
		final boolean[] usable = new boolean[capacity];
		for (int slot = 0; slot < capacity; slot++) {
			final CItem item = inventory.getItemInSlot(slot);
			usable[slot] = (item != null) && item.isActivelyUsed();
		}
		final int[] slots = HeroItemSlots.buttons(usable);
		final boolean nine = this.ui.war3MapViewer.getGameData().isUseNineItemInventory();
		for (int i = 0; i < ITEM_BUTTONS; i++) {
			final ItemButton button = this.items[i];
			if (i >= slots.length) {
				button.inventorySlot = -1;
				button.icon = null;
				button.disabled = false;
				continue;
			}
			final CItem item = inventory.getItemInSlot(slots[i]);
			button.inventorySlot = slots[i];
			final ItemUI itemUI = (item == null) ? null
					: this.ui.war3MapViewer.getAbilityDataUI().getItemUI(item.getTypeId());
			final IconUI iconUI = (itemUI == null) ? null : itemUI.getIconUI();
			button.icon = (iconUI == null) ? null : this.icons.masked(iconUI.getIconPath());
			button.charges = (item == null) ? 0 : item.getCharges();
			this.activation.reset();
			inventory.checkCanUse(simulation, hero, playerIndex, OrderIds.itemUseFirst(nine) + slots[i], false,
					this.activation);
			button.disabled = this.activation.disabled || this.activation.outOfStock;
			button.cooldown = this.activation.cooldownRemaining;
			button.cooldownMax = this.activation.cooldownMax;
		}
		this.itemButtonCount = slots.length;
	}

	private void clearItems() {
		for (int i = 0; i < ITEM_BUTTONS; i++) {
			this.items[i].inventorySlot = -1;
			this.items[i].icon = null;
		}
		this.itemButtonCount = 0;
	}

	/**
	 * The skills the four arc slots have no room for, in the row after the items. What gets a button is decided the way
	 * the console card decided it: a single-icon ability the hero actually casts, that no item is already carrying
	 * behind an item button, and that the arc is not already showing. The four arc slots keep their order and their
	 * passives; this is everything else the hero's data put on her.
	 */
	private void refreshExtras(final CUnit hero, final CSimulation simulation, final int playerIndex,
			final List<War3ID> shownInArc) {
		final int capacity = Math.min(EXTRA_BUTTONS, HeroItemSlots.freeRowSlots(this.itemButtonCount));
		final List<War3ID> taken = new ArrayList<>();
		int count = 0;
		for (final CAbility ability : hero.getAbilities()) {
			if (count >= capacity) {
				break;
			}
			if (!(ability instanceof GenericSingleIconActiveAbility) || (ability.getItem() != null)
					|| !ability.isIconShowing()) {
				// Not something a thumb casts: a passive, one of the engine's own abilities, or an item's ability,
				// which the item buttons already carry.
				continue;
			}
			final War3ID skill = ability.getAlias();
			if (shownInArc.contains(skill) || taken.contains(skill)) {
				continue;
			}
			taken.add(skill);
			final ExtraButton button = this.extras[count++];
			button.ability = ability;
			button.icon = iconOf(this.ui.war3MapViewer.getAbilityDataUI().getUI(skill), hero.getAbilityLevel(skill));
			this.activation.reset();
			ability.checkCanUse(simulation, hero, playerIndex, orderIdOf(ability), false, this.activation);
			button.disabled = this.activation.disabled || !canBeTargetedNow(hero, simulation, playerIndex, ability);
			button.notEnoughMana = this.activation.notEnoughMana || !affordable(hero, ability);
			button.cooldown = this.activation.cooldownRemaining;
			button.cooldownMax = this.activation.cooldownMax;
			button.running = isRunning(hero, ability);
		}
		for (int i = count; i < EXTRA_BUTTONS; i++) {
			clearExtra(this.extras[i]);
		}
		this.extraButtonCount = count;
	}

	/**
	 * Whether the ability would fire on the spot right now. Only asked of the abilities the tray can recognise as
	 * no-target by their class: for the others the answer is "no" whatever the state, and the button has to stay a
	 * button so that a tap can arm it. This is what greys Shadow Meld by day, when the engine refuses the order and has
	 * no string to show.
	 */
	private boolean canBeTargetedNow(final CUnit hero, final CSimulation simulation, final int playerIndex,
			final CAbility ability) {
		if (HeroCastTypes.ofClass(ability.getClass(), ability.getAlias()) != HeroCastType.NO_TARGET) {
			return true;
		}
		final BooleanAbilityTargetCheckReceiver<Void> receiver = BooleanAbilityTargetCheckReceiver.<Void>getInstance()
				.reset();
		ability.checkCanTargetNoTarget(simulation, hero, playerIndex, orderIdOf(ability), false, receiver);
		return receiver.isTargetable();
	}

	/** Whether the ability is doing something right now — melded, a toggle left on — rather than waiting for a tap. */
	private static boolean isRunning(final CUnit hero, final CAbility ability) {
		if ((ability instanceof GenericSingleIconActiveAbility)
				&& ((GenericSingleIconActiveAbility) ability).isRunning()) {
			return true;
		}
		return (hero.getCurrentBehavior() != null)
				&& (orderIdOf(ability) == hero.getCurrentBehavior().getHighlightOrderId());
	}

	private static void clearExtra(final ExtraButton button) {
		button.ability = null;
		button.icon = null;
		button.disabled = false;
		button.notEnoughMana = false;
		button.running = false;
		button.cooldown = 0f;
		button.cooldownMax = 0f;
	}

	/**
	 * Whether the hero can pay for a cast right now, cooldown or no cooldown. The comparison is the engine's own, made
	 * against the same cheat-adjusted cost the engine would charge.
	 */
	private boolean affordable(final CUnit hero, final CAbility ability) {
		final int cost = CReleaseCheats.manaCost(this.ui.war3MapViewer.simulation, hero, manaCostOf(ability));
		return !HeroSkillSlots.cannotAfford(hero.getMana(), cost);
	}

	/** What the data says a cast costs, before the cheats that make casts free. */
	static int manaCostOf(final CAbility ability) {
		if (ability instanceof GenericSingleIconActiveAbility) {
			return ((GenericSingleIconActiveAbility) ability).getUIManaCost();
		}
		return 0;
	}

	private Texture attackIcon() {
		if (this.attackIcon == null) {
			final IconUI iconUI = this.ui.war3MapViewer.getAbilityDataUI().getAttackUI();
			this.attackIcon = (iconUI == null) ? null : this.icons.masked(iconUI.getIconPath());
		}
		return this.attackIcon;
	}

	/** The order a skill's own button would carry on the command card; the tray casts through it. */
	static int orderIdOf(final CAbility ability) {
		if (ability instanceof SingleOrderAbility) {
			return ((SingleOrderAbility) ability).getBaseOrderId();
		}
		return ability.getAlias().getValue();
	}

	/**
	 * A spell's cast range. Only the concrete spell classes carry one — it is per level, repopulated from the data
	 * every time the ability levels — so this is the same downcast the engine's own creep casting does.
	 */
	static float castRangeOf(final CAbility ability) {
		if (ability instanceof CAbilitySpellBase) {
			return ((CAbilitySpellBase) ability).getCastRange();
		}
		if (ability instanceof AbilityBuilderAbility) {
			return ((AbilityBuilderAbility) ability).getCastRange();
		}
		return 0f;
	}

	/** The radius of the circle drawn while a ground skill is aimed, or 0 when the spell declares no area. */
	static float areaOfEffectOf(final CAbility ability) {
		if (ability instanceof GenericSingleIconActiveAbility) {
			final float area = ((GenericSingleIconActiveAbility) ability).getUIAreaOfEffect();
			return Float.isNaN(area) ? 0f : area;
		}
		return 0f;
	}

	// ------------------------------------------------------------------ drawing

	/** Icons, then the rings over them, then the numbers: three passes, so the batch is swapped twice. */
	void render(final SpriteBatch batch, final ShapeRenderer shapes, final int armedSlot) {
		drawIcons(batch);
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		Gdx.gl.glEnable(GL20.GL_BLEND);
		// One filled pass: the border is a ring of triangles rather than a line, because a line is always one pixel
		// wide and reads as an accident next to the art it is supposed to frame.
		shapes.begin(ShapeType.Filled);
		for (int i = 0; i < SKILL_SLOTS; i++) {
			if (this.skills[i].skill == null) {
				continue;
			}
			shapes.setColor((i == armedSlot) ? RING_ARMED_COLOR
					: this.skills[i].unlearned ? BORDER_DISABLED_COLOR : RING_COLOR);
			drawBorder(shapes, this.skills[i].x, this.skills[i].y, this.skillRadius);
		}
		shapes.setColor(RING_COLOR);
		drawBorder(shapes, this.attackX, this.attackY, this.attackRadius);
		for (int i = 0; i < this.itemButtonCount; i++) {
			shapes.setColor(this.items[i].disabled ? BORDER_DISABLED_COLOR : RING_COLOR);
			drawBorder(shapes, this.items[i].x, this.items[i].y, this.itemRadius);
		}
		for (int i = 0; i < this.extraButtonCount; i++) {
			final ExtraButton button = this.extras[i];
			// The blue ring is the "this is on" of the tray: an extra skill that is melding or armed for a target.
			shapes.setColor(button.disabled ? BORDER_DISABLED_COLOR
					: (button.running || ((SKILL_SLOTS + i) == armedSlot)) ? RING_ARMED_COLOR : RING_COLOR);
			drawBorder(shapes, button.x, button.y, this.itemRadius);
		}
		for (int i = 0; i < this.itemButtonCount; i++) {
			drawCooldown(shapes, this.items[i].x, this.items[i].y, this.itemRadius, this.items[i].cooldown,
					this.items[i].cooldownMax);
		}
		for (int i = 0; i < this.extraButtonCount; i++) {
			drawCooldown(shapes, this.extras[i].x, this.extras[i].y, this.itemRadius, this.extras[i].cooldown,
					this.extras[i].cooldownMax);
		}
		for (int i = 0; i < SKILL_SLOTS; i++) {
			drawCooldown(shapes, this.skills[i].x, this.skills[i].y, this.skillRadius, this.skills[i].cooldown,
					this.skills[i].cooldownMax);
			if (this.skills[i].learnable) {
				drawBadge(shapes, this.skills[i]);
			}
		}
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		batch.begin();
		drawNumbers(batch);
	}

	/** A ring {@link #BORDER} thick around a button, drawn as a fan of quads. */
	private void drawBorder(final ShapeRenderer shapes, final float x, final float y, final float radius) {
		final float thickness = BORDER * this.unit;
		final float inner = radius - thickness;
		for (int i = 0; i < SEGMENTS; i++) {
			final double a = (Math.PI * 2d * i) / SEGMENTS;
			final double b = (Math.PI * 2d * (i + 1)) / SEGMENTS;
			final float cosA = (float) Math.cos(a);
			final float sinA = (float) Math.sin(a);
			final float cosB = (float) Math.cos(b);
			final float sinB = (float) Math.sin(b);
			shapes.triangle(x + (cosA * radius), y + (sinA * radius), x + (cosB * radius), y + (sinB * radius),
					x + (cosB * inner), y + (sinB * inner));
			shapes.triangle(x + (cosA * radius), y + (sinA * radius), x + (cosB * inner), y + (sinB * inner),
					x + (cosA * inner), y + (sinA * inner));
		}
	}

	private void drawIcons(final SpriteBatch batch) {
		batch.setColor(Color.WHITE);
		for (int i = 0; i < SKILL_SLOTS; i++) {
			final SkillSlot slot = this.skills[i];
			if (slot.icon == null) {
				continue;
			}
			// The mana tint is a blue wash over the art, the way the console's own buttons say it, and an unlearned
			// skill is greyed whatever else is true of it: it cannot be cast at all until the point is spent.
			batch.setColor(slot.unlearned ? UNLEARNED_TINT
					: slot.disabled ? DISABLED_TINT : slot.notEnoughMana ? NO_MANA_TINT : Color.WHITE);
			batch.draw(slot.icon, slot.x - this.skillRadius, slot.y - this.skillRadius, this.skillRadius * 2f,
					this.skillRadius * 2f);
		}
		final Texture attack = attackIcon();
		if (attack != null) {
			final float half = this.attackRadius;
			batch.setColor(Color.WHITE);
			batch.draw(attack, this.attackX - half, this.attackY - half, half * 2f, half * 2f);
		}
		for (int i = 0; i < this.itemButtonCount; i++) {
			final ItemButton button = this.items[i];
			if (button.icon == null) {
				continue;
			}
			batch.setColor(button.disabled ? DISABLED_TINT : Color.WHITE);
			batch.draw(button.icon, button.x - this.itemRadius, button.y - this.itemRadius, this.itemRadius * 2f,
					this.itemRadius * 2f);
		}
		for (int i = 0; i < this.extraButtonCount; i++) {
			final ExtraButton button = this.extras[i];
			if (button.icon == null) {
				continue;
			}
			batch.setColor(button.disabled ? DISABLED_TINT
					: button.notEnoughMana ? NO_MANA_TINT : Color.WHITE);
			batch.draw(button.icon, button.x - this.itemRadius, button.y - this.itemRadius, this.itemRadius * 2f,
					this.itemRadius * 2f);
		}
		batch.setColor(Color.WHITE);
	}

	private void drawCooldown(final ShapeRenderer shapes, final float x, final float y, final float radius,
			final float remaining, final float max) {
		final float fraction = HeroSkillSlots.cooldownFraction(remaining, max);
		if (fraction <= 0f) {
			return;
		}
		shapes.setColor(COOLDOWN_COLOR);
		final float degrees = 360f * fraction;
		// From twelve o'clock and back, the way the game's own cooldown swipe goes.
		shapes.arc(x, y, radius, 90f - degrees, degrees, WEDGE_SEGMENTS);
	}

	private void drawBadge(final ShapeRenderer shapes, final SkillSlot slot) {
		final float badgeX = slot.x + (BADGE_OFFSET * this.unit);
		final float badgeY = slot.y + (BADGE_OFFSET * this.unit);
		final float badgeRadius = BADGE_RADIUS * this.unit;
		shapes.setColor(BADGE_COLOR);
		shapes.circle(badgeX, badgeY, badgeRadius, 18);
		shapes.setColor(BADGE_PLUS_COLOR);
		final float arm = badgeRadius * 0.55f;
		final float thickness = Math.max(1f, badgeRadius * 0.22f);
		shapes.rect(badgeX - arm, badgeY - (thickness / 2f), arm * 2f, thickness);
		shapes.rect(badgeX - (thickness / 2f), badgeY - arm, thickness, arm * 2f);
	}

	/** The numbers the buttons carry: how many uses an item has left. Skill levels would say "1" on a fresh hero. */
	private void drawNumbers(final SpriteBatch batch) {
		final BitmapFont numbers = numbersFont();
		if (numbers == null) {
			return;
		}
		numbers.setColor(Color.WHITE);
		for (int i = 0; i < this.itemButtonCount; i++) {
			final ItemButton button = this.items[i];
			if (button.charges > 0) {
				drawCentered(batch, numbers, Integer.toString(button.charges), button.x,
						button.y - (this.itemRadius * 0.55f));
			}
		}
	}

	private void drawCentered(final SpriteBatch batch, final BitmapFont numbers, final String text, final float x,
			final float y) {
		this.textLayout.setText(numbers, text);
		numbers.draw(batch, this.textLayout, x - (this.textLayout.width / 2f), y);
	}

	/**
	 * A font at the size the tray needs now. The interface's fonts are sized for a 4:3 console in its own units, so
	 * the tray asks for its own, in world units, and keeps it until the size changes — a rotation or a new device.
	 */
	private BitmapFont numbersFont() {
		final int pixels = Math.max(8, Math.round(this.skillRadius * 0.62f));
		if ((this.font != null) && (this.fontPixelSize == pixels)) {
			return this.font;
		}
		this.fontPixelSize = pixels;
		final float minHeight = Math.max(1f, this.ui.uiViewport.getMinWorldHeight());
		// GameUI.generateFont takes the size in its own 0.8 by 0.6 space and converts it to pixels.
		this.font = SessionResources.track(this.ui.rootFrame.generateFont((pixels / minHeight) * 0.6f));
		return this.font;
	}

	/** What a skill's own check reported, kept as the states the tray draws. */
	private static final class ActivationState implements AbilityActivationReceiver {
		boolean canUse;
		boolean disabled;
		boolean notEnoughMana;
		boolean outOfStock;
		float cooldownRemaining;
		float cooldownMax;

		void reset() {
			this.canUse = false;
			this.disabled = false;
			this.notEnoughMana = false;
			this.outOfStock = false;
			this.cooldownRemaining = 0f;
			this.cooldownMax = 0f;
		}

		@Override
		public void useOk() {
			this.canUse = true;
		}

		@Override
		public void unknownReasonUseNotOk() {
		}

		@Override
		public void notAnActiveAbility() {
		}

		@Override
		public void missingRequirement(final War3ID type, final int level) {
		}

		@Override
		public void missingHeroLevelRequirement(final int level) {
		}

		@Override
		public void noHeroSkillPointsAvailable() {
		}

		@Override
		public void disabled() {
			this.disabled = true;
		}

		@Override
		public void techtreeMaximumReached() {
		}

		@Override
		public void techItemAlreadyInProgress() {
		}

		@Override
		public void cooldownNotYetReady(final float cooldownRemaining, final float cooldown) {
			this.cooldownRemaining = cooldownRemaining;
			this.cooldownMax = cooldown;
		}

		@Override
		public void noChargesRemaining() {
			this.outOfStock = true;
		}

		@Override
		public void activationCheckFailed(final String commandStringErrorKey) {
			this.notEnoughMana = CommandStringErrorKeys.NOT_ENOUGH_MANA.equals(commandStringErrorKey);
			this.outOfStock |= CommandStringErrorKeys.OUT_OF_STOCK.equals(commandStringErrorKey);
		}
	}
}
