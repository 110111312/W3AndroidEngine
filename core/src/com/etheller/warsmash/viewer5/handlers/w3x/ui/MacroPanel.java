package com.etheller.warsmash.viewer5.handlers.w3x.ui;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.TimeUtils;
import com.etheller.warsmash.parsers.fdf.frames.AbstractRenderableFrame;
import com.etheller.warsmash.util.SessionResources;
import com.etheller.warsmash.util.War3ID;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.AbilityDataUI;
import com.etheller.warsmash.viewer5.handlers.w3x.rendersim.ability.IconUI;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroPlanExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroStatusLine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroTicket;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroTicketKind;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.Stuck;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.TicketState;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;

/**
 * The macro panel: two small round buttons above the skill arc, and the sheet each one opens.
 * <p>
 * The sheet is nearly the whole screen, because the point of it is to see the plan at once -- every icon at once and
 * almost every ticket at once, no paging, no scrolling -- and because the player opens it to do one small thing and
 * closes it again. The price is that it covers the world and the tray while it is open; the resource bar stays readable
 * at the top, since a plan is about gold.
 * <p>
 * The settled sketch is {@code docs/mockups/macro-panel.html}, adapted for touch rather than copied: the grid is icons
 * only (a name is what a tap tells you, in the line along the bottom), a ticket carries its number and its minus
 * <em>inside</em> its own icon, and a ticket's state is the colour of the frame around it -- gold for ready, dim gold
 * while it waits for gold, amber while its order is out, red when the game says it cannot be done.
 * <p>
 * The panel decides nothing. Everything it shows comes from {@link MacroPlanExecutor}, and every edit goes through its
 * own add/sooner/later/remove, which is the seam a future multiplayer command would call. The numbers are dp measured
 * from the screen's bottom-left corner, and every one of them goes through {@link #sx} or {@link #sy} -- the interface's
 * x 0 is not the screen's left edge, so a panel that drew in one space and hit-tested in another would answer taps a
 * hand's width away from where it painted. The grid's arithmetic lives in {@link MacroLayout}, which is tested.
 */
final class MacroPanel {
	/** Which sheet is open. Only one at a time: the two are two views of one numbering, not two places to be. */
	enum Sheet {
		NONE, BUILD, ARMY
	}

	// The two buttons, above the skill arc. The arc reaches 171dp above the corner at its highest point.
	private static final float BUTTON_RADIUS = 20f;
	private static final float BUTTON_INSET = 14f;
	private static final float ARMY_BOTTOM = 196f;
	private static final float BUILD_BOTTOM = 248f;
	// The sheet: the whole screen bar a margin, and the resource bar left visible along the top.
	private static final float INSET = 6f;
	private static final float TOP_RESERVE = 26f;
	private static final float HEADER = 26f;
	private static final float LABEL = 14f;
	private static final float SECTION_GAP = 6f;
	private static final float INFO = 16f;
	private static final float TAB_WIDTH = 66f;
	private static final float CHIP_HEIGHT = 18f;
	/** A ticket's minus is its own target in the top-right corner of the cell, and is hit a little generously. */
	private static final float MINUS = 19f;
	/** How often the grid is rebuilt from the engine: the icon states are the ability gate's, not a frame's. */
	private static final long REFRESH_MS = 400L;

	private static final Color PANEL_COLOR = new Color(0.075f, 0.058f, 0.030f, 0.96f);
	private static final Color HEADER_COLOR = new Color(0.125f, 0.100f, 0.058f, 0.98f);
	private static final Color CELL_COLOR = new Color(0.105f, 0.082f, 0.040f, 1f);
	private static final Color GOLD = new Color(0.784f, 0.647f, 0.322f, 1f);
	private static final Color GOLD_DIM = new Color(0.54f, 0.443f, 0.212f, 1f);
	private static final Color AMBER = new Color(0.66f, 0.46f, 0.16f, 1f);
	private static final Color TEXT = new Color(0.91f, 0.863f, 0.722f, 1f);
	private static final Color SUB = new Color(0.60f, 0.545f, 0.384f, 1f);
	private static final Color DANGER = new Color(0.69f, 0.337f, 0.247f, 1f);
	private static final Color CHIP = new Color(0.16f, 0.13f, 0.075f, 1f);
	private static final Color LOCKED_TINT = new Color(0.34f, 0.34f, 0.34f, 0.55f);
	private static final Color BADGE_PULSE = new Color(1f, 0.84f, 0.42f, 1f);
	private static final String LOG_TAG = "MACRO";

	private final MeleeUI ui;
	private final GlyphLayout layout = new GlyphLayout();
	private Sheet sheet = Sheet.NONE;
	private float scrollDp;
	private float unit;
	private float left;
	private float right;
	private float bottom;
	private MacroPlanExecutor lastExecutor;
	private long geometryLoggedMs;

	/** The cells as last built, so that a tap hits what the frame drew. Rebuilt on a beat, not on every frame. */
	private List<Cell> cells;
	private long rebuildAtMs;
	/** Where the two lists start this beat, for the labels that sit above them. */
	private float availableTopDp;
	private float orderTopDp;
	private float contentDp;
	/** The supply line's words, rebuilt with the grid rather than every frame: asking what it would build walks the base. */
	private String supplyText = "";
	private int ticketCells;
	/** What the last tap was about, in words, under the grid: the name a cell does not print. */
	private String info = "";

	private BitmapFont captionFont;
	private int captionPx;
	private BitmapFont titleFont;
	private int titlePx;
	private BitmapFont chipFont;
	private int chipPx;

	/** One icon in a sheet: where it is, what it is, and what a tap on it does. */
	private static final class Cell {
		float leftDp;
		float bottomDp;
		War3ID rawcode;
		int number = -1;
		int owned;
		boolean locked;
		boolean ready;
		boolean waiting;
		boolean pending;
		boolean stuck;
		/** The state in words, for the line along the bottom: the grid itself only has colour for height's sake. */
		String status = "";

		float rightDp() {
			return this.leftDp + MacroLayout.CELL;
		}

		float topDp() {
			return this.bottomDp + MacroLayout.CELL;
		}

		boolean inside(final float xDp, final float yDp) {
			return (xDp >= this.leftDp) && (xDp <= rightDp()) && (yDp >= this.bottomDp) && (yDp <= topDp());
		}

		boolean onMinus(final float xDp, final float yDp) {
			return ((xDp >= (rightDp() - MINUS)) && (yDp >= (topDp() - MINUS))) && inside(xDp, yDp);
		}
	}

	MacroPanel(final MeleeUI ui) {
		this.ui = ui;
	}

	// ------------------------------------------------------------------ geometry

	/** Where the interface is on this screen, in UI world units: the same corner the tray is laid out from. */
	private void layoutInterface() {
		final float density = (Gdx.graphics == null) ? 1f : Math.max(1f, Gdx.graphics.getDensity());
		MeleeUI.screenCoordsVector.set(0f, 0f);
		final float x0 = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector).x;
		MeleeUI.screenCoordsVector.set(100f, 0f);
		final float x100 = this.ui.uiViewport.unproject(MeleeUI.screenCoordsVector).x;
		this.unit = ((x100 - x0) / 100f) * density;
		this.left = this.ui.minimapPanel.heroInterfaceLeft();
		this.right = this.left + this.ui.uiViewport.getWorldWidth();
		this.bottom = this.ui.minimapPanel.heroInterfaceTop() - this.ui.uiViewport.getWorldHeight();
	}

	/** A dp length, in world units. */
	private float dp(final float value) {
		return value * this.unit;
	}

	/**
	 * A dp position on the screen, in world units. The interface's x 0 is not the screen's left edge -- the viewport is
	 * wider than the 4:3 interface and sticks out on both sides of it -- so drawing and hit-testing both go through this
	 * pair and neither can drift from the other.
	 */
	private float sx(final float xDp) {
		return this.left + (xDp * this.unit);
	}

	private float sy(final float yDp) {
		return this.bottom + (yDp * this.unit);
	}

	private float xDp(final float worldX) {
		return (worldX - this.left) / this.unit;
	}

	private float yDp(final float worldY) {
		return (worldY - this.bottom) / this.unit;
	}

	private float screenWidthDp() {
		return this.ui.uiViewport.getWorldWidth() / this.unit;
	}

	private float screenHeightDp() {
		return this.ui.uiViewport.getWorldHeight() / this.unit;
	}

	private float panelLeftDp() {
		return INSET;
	}

	private float panelRightDp() {
		return screenWidthDp() - INSET;
	}

	private float panelBottomDp() {
		return INSET;
	}

	private float panelTopDp() {
		return screenHeightDp() - TOP_RESERVE;
	}

	private float headerBottomDp() {
		return panelTopDp() - HEADER;
	}

	/** The band the grid is drawn in: under the header, above the line of words. */
	private float bodyTopDp() {
		return headerBottomDp() - 2f;
	}

	private float bodyBottomDp() {
		return panelBottomDp() + INFO;
	}

	private float bodyHeightDp() {
		return bodyTopDp() - bodyBottomDp();
	}

	private float gridLeftDp() {
		return panelLeftDp() + MacroLayout.MARGIN;
	}

	private int columns() {
		return MacroLayout.columns(panelRightDp() - panelLeftDp());
	}

	// The two round buttons, laid out from the screen's right edge, above the arc.
	private float buttonCentreX() {
		return screenWidthDp() - BUTTON_INSET - BUTTON_RADIUS;
	}

	private float buttonCentreY(final Sheet which) {
		return ((which == Sheet.BUILD) ? BUILD_BOTTOM : ARMY_BOTTOM) + BUTTON_RADIUS;
	}

	private boolean insideButton(final float xDp, final float yDp, final Sheet which) {
		final float dx = xDp - buttonCentreX();
		final float dy = yDp - buttonCentreY(which);
		return ((dx * dx) + (dy * dy)) <= (BUTTON_RADIUS * BUTTON_RADIUS);
	}

	private boolean insidePanel(final float xDp, final float yDp) {
		return (xDp >= panelLeftDp()) && (xDp <= panelRightDp()) && (yDp >= panelBottomDp())
				&& (yDp <= panelTopDp());
	}

	private float tabLeftDp(final Sheet which) {
		return panelLeftDp() + INSET + ((which == Sheet.BUILD) ? 0f : TAB_WIDTH);
	}

	private float tabBottomDp() {
		return panelTopDp() - HEADER + 4f;
	}

	private boolean insideTab(final float xDp, final float yDp, final Sheet which) {
		return (xDp >= tabLeftDp(which)) && (xDp <= (tabLeftDp(which) + TAB_WIDTH - 2f))
				&& (yDp >= tabBottomDp()) && (yDp <= (tabBottomDp() + CHIP_HEIGHT));
	}

	private float closeLeftDp() {
		return panelRightDp() - INSET - CHIP_HEIGHT;
	}

	private boolean insideClose(final float xDp, final float yDp) {
		return (xDp >= closeLeftDp()) && (xDp <= (closeLeftDp() + CHIP_HEIGHT)) && (yDp >= tabBottomDp())
				&& (yDp <= (tabBottomDp() + CHIP_HEIGHT));
	}

	private float pauseLeftDp() {
		return closeLeftDp() - 8f - 44f;
	}

	private boolean insidePause(final float xDp, final float yDp) {
		return (this.sheet == Sheet.BUILD) && (xDp >= pauseLeftDp()) && (xDp <= (pauseLeftDp() + 44f))
				&& (yDp >= tabBottomDp()) && (yDp <= (tabBottomDp() + CHIP_HEIGHT));
	}

	/** The pager, drawn only when the plan is too tall for the screen: up (false) and down (true). */
	private float arrowLeftDp(final boolean down) {
		return panelRightDp() - INSET - (down ? 20f : 42f);
	}

	private boolean insideArrow(final float xDp, final float yDp, final boolean down) {
		if (!overflows()) {
			return false;
		}
		return (xDp >= arrowLeftDp(down)) && (xDp <= (arrowLeftDp(down) + 18f))
				&& (yDp >= (panelBottomDp() + 1f)) && (yDp <= (panelBottomDp() + INFO - 1f));
	}

	private boolean overflows() {
		return this.contentDp > bodyHeightDp();
	}

	// ------------------------------------------------------------------ the cells

	/**
	 * The sheet's cells as they are drawn: the available grid, and then the order grid. The order grid is filtered, not
	 * renumbered -- a TRAIN ticket keeps the number it has in the shared plan, so the build sheet's 3 and the army
	 * sheet's 3 are the same 3.
	 */
	private List<Cell> cells(final MacroPlanExecutor executor) {
		if ((this.cells != null) && (TimeUtils.millis() < this.rebuildAtMs)) {
			return this.cells;
		}
		this.rebuildAtMs = TimeUtils.millis() + REFRESH_MS;
		final List<Cell> built = new ArrayList<>();
		this.availableTopDp = -1f;
		this.orderTopDp = -1f;
		this.contentDp = 0f;
		if ((executor == null) || (this.sheet == Sheet.NONE)) {
			this.ticketCells = 0;
			this.supplyText = "";
			this.cells = built;
			return built;
		}
		final boolean army = (this.sheet == Sheet.ARMY);
		final int columns = columns();
		float y = bodyTopDp() + this.scrollDp;
		final List<War3ID> available = executor.available(army);
		final List<TicketState> tickets = tickets(executor, army);
		this.availableTopDp = y;
		y -= LABEL;
		addAvailable(built, available, y, columns, executor);
		final int availableRows = ((available.size() + columns) - 1) / columns;
		y -= MacroLayout.rowsHeightDp(availableRows) + SECTION_GAP;
		this.orderTopDp = y;
		y -= LABEL;
		addTickets(built, tickets, y, columns);
		this.ticketCells = tickets.size();
		if (!army) {
			this.supplyText = supplyReport(executor);
		}
		final int ticketRows = ((tickets.size() + columns) - 1) / columns;
		this.contentDp = (2f * LABEL) + SECTION_GAP + MacroLayout.rowsHeightDp(availableRows)
				+ MacroLayout.rowsHeightDp(ticketRows);
		this.cells = built;
		return built;
	}

	/** The tickets of one sheet, in number order, with the ones the panel added since the last pass already in place. */
	private List<TicketState> tickets(final MacroPlanExecutor executor, final boolean army) {
		final List<TicketState> tickets = new ArrayList<>();
		for (final TicketState state : executor.getStates()) {
			if ((state.getTicket().getKind() == MacroTicketKind.TRAIN) == army) {
				tickets.add(state);
			}
		}
		for (final MacroTicket ticket : executor.getPlan().getTickets()) {
			if ((ticket.getKind() == MacroTicketKind.TRAIN) != army) {
				continue;
			}
			boolean known = false;
			for (final TicketState state : tickets) {
				if (state.getTicket() == ticket) {
					known = true;
					break;
				}
			}
			if (!known) {
				// Added since the last pass: it has no state yet, and a ticket with no state has nothing to wait for.
				tickets.add(TicketState.ready(executor.getPlan().indexOf(ticket), ticket));
			}
		}
		tickets.sort((first, second) -> Integer.compare(first.getNumber(), second.getNumber()));
		return tickets;
	}

	private void addAvailable(final List<Cell> into, final List<War3ID> rawcodes, final float firstRowTopDp,
			final int columns, final MacroPlanExecutor executor) {
		for (int i = 0; i < rawcodes.size(); i++) {
			final War3ID rawcode = rawcodes.get(i);
			final Cell cell = new Cell();
			cell.rawcode = rawcode;
			cell.owned = executor.ownedCount(rawcode);
			final Stuck locked = executor.lockOf(rawcode);
			cell.locked = locked != null;
			cell.status = (locked == null) ? "" : MacroStatusLine.of(locked, this::nameOf);
			place(cell, i, firstRowTopDp, columns);
			into.add(cell);
		}
	}

	private void addTickets(final List<Cell> into, final List<TicketState> tickets, final float firstRowTopDp,
			final int columns) {
		for (int i = 0; i < tickets.size(); i++) {
			final TicketState state = tickets.get(i);
			final Cell cell = new Cell();
			cell.rawcode = state.getTicket().getRawcode();
			cell.number = state.getNumber();
			cell.locked = state.isStuck();
			cell.stuck = state.isStuck();
			cell.pending = state.isPending();
			cell.waiting = !state.isStuck() && !state.isPending() && !state.isAffordable();
			cell.ready = state.isReady();
			cell.status = MacroStatusLine.of(state, this::nameOf);
			place(cell, i, firstRowTopDp, columns);
			into.add(cell);
		}
	}

	private void place(final Cell cell, final int index, final float firstRowTopDp, final int columns) {
		final int column = index % columns;
		final int row = index / columns;
		cell.leftDp = gridLeftDp() + (column * (MacroLayout.CELL + MacroLayout.GAP));
		cell.bottomDp = firstRowTopDp - MacroLayout.rowTopDp(row) - MacroLayout.CELL;
	}

	private boolean visible(final Cell cell) {
		return (cell.bottomDp >= bodyBottomDp()) && (cell.topDp() <= bodyTopDp());
	}

	// ------------------------------------------------------------------ drawing

	void render(final SpriteBatch batch, final ShapeRenderer shapes, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		logGeometry(executor);
		if (this.sheet == Sheet.NONE) {
			drawButtons(batch, shapes, executor);
			drawFoodBadge(batch, shapes);
			return;
		}
		final List<Cell> shown = cells(executor);
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Filled);
		drawPanel(shapes);
		drawFrames(shapes, shown);
		shapes.end();
		batch.begin();
		drawIcons(batch, shown);
		batch.end();
		shapes.begin(ShapeType.Filled);
		drawChips(shapes, shown);
		shapes.end();
		batch.begin();
		drawText(batch, shown, executor);
		Gdx.gl.glDisable(GL20.GL_BLEND);
		drawFoodBadge(batch, shapes);
	}

	private void drawButtons(final SpriteBatch batch, final ShapeRenderer shapes, final MacroPlanExecutor executor) {
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Filled);
		button(shapes, Sheet.BUILD);
		button(shapes, Sheet.ARMY);
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		batch.begin();
		final BitmapFont title = titleFont();
		final BitmapFont caption = captionFont();
		if ((title == null) || (caption == null)) {
			return;
		}
		buttonText(batch, title, caption, Sheet.BUILD, count(executor, false), "build");
		buttonText(batch, title, caption, Sheet.ARMY, count(executor, true), "army");
	}

	private void button(final ShapeRenderer shapes, final Sheet which) {
		final float cx = sx(buttonCentreX());
		final float cy = sy(buttonCentreY(which));
		final float radius = dp(BUTTON_RADIUS);
		shapes.setColor(GOLD);
		shapes.circle(cx, cy, radius, 28);
		shapes.setColor(PANEL_COLOR);
		shapes.circle(cx, cy, radius - dp(2f), 28);
	}

	private void buttonText(final SpriteBatch batch, final BitmapFont title, final BitmapFont caption,
			final Sheet which, final int count, final String label) {
		final float cx = sx(buttonCentreX());
		title.setColor(GOLD);
		centred(batch, title, (count < 0) ? "-" : Integer.toString(count), cx, sy(buttonCentreY(which) + 4f));
		caption.setColor(SUB);
		centred(batch, caption, label, cx, sy(((which == Sheet.BUILD) ? BUILD_BOTTOM : ARMY_BOTTOM) - 2f));
	}

	private void drawPanel(final ShapeRenderer shapes) {
		final float leftEdge = sx(panelLeftDp());
		final float rightEdge = sx(panelRightDp());
		final float bottomEdge = sy(panelBottomDp());
		final float top = sy(panelTopDp());
		shapes.setColor(GOLD);
		shapes.rect(leftEdge, bottomEdge, rightEdge - leftEdge, top - bottomEdge);
		shapes.setColor(PANEL_COLOR);
		shapes.rect(leftEdge + dp(2f), bottomEdge + dp(2f), (rightEdge - leftEdge) - dp(4f),
				(top - bottomEdge) - dp(4f));
		shapes.setColor(HEADER_COLOR);
		shapes.rect(leftEdge + dp(2f), sy(headerBottomDp()), (rightEdge - leftEdge) - dp(4f),
				sy(panelTopDp()) - sy(headerBottomDp()));
		// The two tabs, the active one filled: this is the only way to change sheets once open.
		tab(shapes, Sheet.BUILD);
		tab(shapes, Sheet.ARMY);
		if (this.sheet == Sheet.BUILD) {
			shapes.setColor(insidePaused() ? AMBER : CELL_COLOR);
			shapes.rect(sx(pauseLeftDp()), sy(tabBottomDp()), dp(44f), dp(CHIP_HEIGHT));
		}
		shapes.setColor(CELL_COLOR);
		shapes.rect(sx(closeLeftDp()), sy(tabBottomDp()), dp(CHIP_HEIGHT), dp(CHIP_HEIGHT));
		if (overflows()) {
			shapes.setColor(CHIP);
			shapes.rect(sx(arrowLeftDp(false)), sy(panelBottomDp() + 1f), dp(18f), dp(INFO - 2f));
			shapes.rect(sx(arrowLeftDp(true)), sy(panelBottomDp() + 1f), dp(18f), dp(INFO - 2f));
		}
	}

	private void tab(final ShapeRenderer shapes, final Sheet which) {
		if (this.sheet != which) {
			return;
		}
		shapes.setColor(GOLD_DIM);
		shapes.rect(sx(tabLeftDp(which)), sy(tabBottomDp()), dp(TAB_WIDTH - 2f), dp(CHIP_HEIGHT));
	}

	/** Every cell's frame: the state of a ticket is the colour around its icon. */
	private void drawFrames(final ShapeRenderer shapes, final List<Cell> shown) {
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			shapes.setColor(frameColor(cell));
			shapes.rect(sx(cell.leftDp), sy(cell.bottomDp), dp(MacroLayout.CELL), dp(MacroLayout.CELL));
		}
	}

	private Color frameColor(final Cell cell) {
		if (cell.number < 0) {
			return CELL_COLOR;
		}
		if (cell.stuck) {
			return DANGER;
		}
		if (cell.pending) {
			return AMBER;
		}
		return cell.ready ? GOLD : GOLD_DIM;
	}

	private void drawIcons(final SpriteBatch batch, final List<Cell> shown) {
		batch.setColor(Color.WHITE);
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			final Texture icon = iconOf(cell.rawcode, kindOf(cell));
			if (icon == null) {
				continue;
			}
			final float size = dp(MacroLayout.ICON);
			final float inset = dp((MacroLayout.CELL - MacroLayout.ICON) / 2f);
			batch.setColor(cell.locked ? LOCKED_TINT : Color.WHITE);
			batch.draw(icon, sx(cell.leftDp) + inset, sy(cell.bottomDp) + inset, size, size);
		}
		batch.setColor(Color.WHITE);
	}

	/** The number, the minus and the owned count, all inside the cell: what the sketch puts outside it costs a row. */
	private void drawChips(final ShapeRenderer shapes, final List<Cell> shown) {
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			if (cell.number >= 0) {
				shapes.setColor(GOLD);
				shapes.rect(sx(cell.leftDp), sy(cell.topDp() - MacroLayout.CHIP), dp(numberChipDp(cell)),
						dp(MacroLayout.CHIP));
				shapes.setColor(DANGER);
				shapes.rect(sx(cell.rightDp() - MacroLayout.CHIP), sy(cell.topDp() - MacroLayout.CHIP),
						dp(MacroLayout.CHIP), dp(MacroLayout.CHIP));
			}
			else if (cell.owned > 0) {
				shapes.setColor(CHIP);
				shapes.rect(sx(cell.rightDp() - 18f), sy(cell.bottomDp + 1f), dp(17f), dp(13f));
			}
		}
	}

	/** A two-digit number needs a wider chip than a one-digit one, or the digits squeeze into each other. */
	private static float numberChipDp(final Cell cell) {
		return (cell.number < 10) ? MacroLayout.CHIP : (MacroLayout.CHIP + 9f);
	}

	private void drawText(final SpriteBatch batch, final List<Cell> shown, final MacroPlanExecutor executor) {
		final BitmapFont title = titleFont();
		final BitmapFont caption = captionFont();
		final BitmapFont chip = chipFont();
		if ((title == null) || (caption == null) || (chip == null)) {
			return;
		}
		title.setColor(this.sheet == Sheet.BUILD ? CHIP : GOLD);
		centred(batch, title, "BUILD", sx(tabLeftDp(Sheet.BUILD) + ((TAB_WIDTH - 2f) / 2f)), sy(tabBottomDp() + 14f));
		title.setColor(this.sheet == Sheet.ARMY ? CHIP : GOLD);
		centred(batch, title, "ARMY", sx(tabLeftDp(Sheet.ARMY) + ((TAB_WIDTH - 2f) / 2f)), sy(tabBottomDp() + 14f));
		title.setColor(GOLD);
		centred(batch, title, "x", sx(closeLeftDp() + (CHIP_HEIGHT / 2f)), sy(tabBottomDp() + 14f));
		if (this.sheet == Sheet.BUILD) {
			caption.setColor(TEXT);
			draw(batch, caption, "food " + this.supplyText, sx(pauseLeftDp() - 150f), sy(tabBottomDp() + 13f),
					dp(146f));
			chip.setColor(insidePaused() ? CHIP : TEXT);
			centred(batch, chip, insidePaused() ? "paused" : "pause", sx(pauseLeftDp() + 22f),
					sy(tabBottomDp() + 13f));
		}
		// The two lists, and what each one is.
		label(batch, caption, "AVAILABLE - WHAT YOU HAVE", this.availableTopDp);
		label(batch, caption, "ORDER - WHAT YOU ARE ABOUT TO HAVE", this.orderTopDp);
		if (this.ticketCells == 0) {
			caption.setColor(SUB);
			draw(batch, caption, "empty - the plan does nothing", sx(gridLeftDp()), sy(this.orderTopDp - LABEL - 9f),
					dp(panelRightDp() - gridLeftDp() - MacroLayout.MARGIN));
		}
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			if (cell.number >= 0) {
				chip.setColor(CHIP);
				centred(batch, chip, Integer.toString(cell.number), sx(cell.leftDp + (numberChipDp(cell) / 2f)),
						sy(cell.topDp() - 3.5f));
				chip.setColor(Color.WHITE);
				centred(batch, chip, "-", sx(cell.rightDp() - (MacroLayout.CHIP / 2f)), sy(cell.topDp() - 3.5f));
			}
			else if (cell.owned > 0) {
				chip.setColor(SUB);
				centred(batch, chip, "x" + cell.owned, sx(cell.rightDp() - 9.5f), sy(cell.bottomDp + 10f));
			}
		}
		// One line along the bottom: what the plan is doing, and -- after a tap -- what was just done.
		caption.setColor(this.info.isEmpty() ? SUB : GOLD);
		draw(batch, caption, this.info.isEmpty() ? summary(executor) : this.info, sx(panelLeftDp() + 10f),
				sy(panelBottomDp() + INFO - 3f), dp(panelRightDp() - panelLeftDp() - (overflows() ? 56f : 20f)));
		if (overflows()) {
			chip.setColor(SUB);
			centred(batch, chip, "^", sx(arrowLeftDp(false) + 9f), sy(panelBottomDp() + INFO - 3f));
			centred(batch, chip, "v", sx(arrowLeftDp(true) + 9f), sy(panelBottomDp() + INFO - 3f));
		}
	}

	private void label(final SpriteBatch batch, final BitmapFont font, final String text, final float topDp) {
		if ((topDp <= bodyBottomDp()) || (topDp > bodyTopDp())) {
			return;
		}
		font.setColor(SUB);
		draw(batch, font, text, sx(gridLeftDp()), sy(topDp - 2f), dp(panelRightDp() - gridLeftDp() - 8f));
	}

	/** The line along the bottom while nothing was tapped: what the plan is, at a glance. */
	private String summary(final MacroPlanExecutor executor) {
		if (executor == null) {
			return "";
		}
		if (!executor.isSeeded()) {
			return "no base yet - the plan starts when you have one";
		}
		return this.ticketCells + " in the plan - tap an icon to add, a number to move it up, long press to move it down";
	}

	/** The supply line's words: the food it is buying headroom for, and what it would build. */
	private String supplyReport(final MacroPlanExecutor executor) {
		final CPlayer player = this.ui.war3MapViewer.simulation
				.getPlayer(this.ui.war3MapViewer.getLocalPlayerIndex());
		if (player == null) {
			return "";
		}
		final War3ID type = (executor == null) ? null : executor.getSupplyType();
		return player.getFoodUsed() + "/" + player.getFoodCapLimit() + " - "
				+ ((type == null) ? "no supply" : nameOf(type, MacroTicketKind.BUILD));
	}

	private boolean insidePaused() {
		return (this.lastExecutor != null) && this.lastExecutor.isSupplyPaused();
	}

	/**
	 * The dot on the resource bar while the supply line is paused: with the sheet closed that is the only signal that the
	 * base is not doing something the player asked it to, and the food number is where they would look.
	 */
	private void drawFoodBadge(final SpriteBatch batch, final ShapeRenderer shapes) {
		if (!insidePaused() || (this.ui.resourceBarSupplyText == null) || !this.ui.resourceBarSupplyText.isVisible()) {
			return;
		}
		final Rectangle bounds = ((AbstractRenderableFrame) this.ui.resourceBarSupplyText).getRenderBounds();
		if (bounds == null) {
			return;
		}
		final float pulse = 0.5f + (0.5f * (float) Math.abs(Math.sin(((TimeUtils.millis() % 1400L) / 1400.0) * Math.PI)));
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		shapes.begin(ShapeType.Filled);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.setColor(BADGE_PULSE.r, BADGE_PULSE.g, BADGE_PULSE.b, pulse);
		shapes.circle(bounds.x + bounds.width + dp(6f), bounds.y + (bounds.height * 0.55f), dp(3.5f), 12);
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
		batch.begin();
	}

	// ------------------------------------------------------------------ text helpers

	private void draw(final SpriteBatch batch, final BitmapFont font, final String text, final float x, final float y,
			final float maxWidth) {
		if (text.isEmpty()) {
			return;
		}
		this.layout.setText(font, text);
		if (this.layout.width > maxWidth) {
			int end = text.length();
			while ((end > 1) && (this.layout.width > maxWidth)) {
				end--;
				this.layout.setText(font, text.substring(0, end));
			}
		}
		font.draw(batch, this.layout, x, y);
	}

	private void centred(final SpriteBatch batch, final BitmapFont font, final String text, final float centreX,
			final float y) {
		this.layout.setText(font, text);
		font.draw(batch, this.layout, centreX - (this.layout.width / 2f), y);
	}

	private BitmapFont chipFont() {
		final int pixels = Math.max(7, Math.round(this.unit * 10f));
		if ((this.chipFont != null) && (this.chipPx == pixels)) {
			return this.chipFont;
		}
		this.chipPx = pixels;
		this.chipFont = SessionResources.track(this.ui.rootFrame.generateFont(fontSize(pixels)));
		return this.chipFont;
	}

	private BitmapFont captionFont() {
		final int pixels = Math.max(7, Math.round(this.unit * 9f));
		if ((this.captionFont != null) && (this.captionPx == pixels)) {
			return this.captionFont;
		}
		this.captionPx = pixels;
		this.captionFont = SessionResources.track(this.ui.rootFrame.generateFont(fontSize(pixels)));
		return this.captionFont;
	}

	private BitmapFont titleFont() {
		final int pixels = Math.max(8, Math.round(this.unit * 11f));
		if ((this.titleFont != null) && (this.titlePx == pixels)) {
			return this.titleFont;
		}
		this.titlePx = pixels;
		this.titleFont = SessionResources.track(this.ui.rootFrame.generateFont(fontSize(pixels)));
		return this.titleFont;
	}

	/** GameUI.generateFont takes its size in the interface's 0.8 by 0.6 space, not in pixels. */
	private float fontSize(final int pixels) {
		final float minHeight = Math.max(1f, this.ui.uiViewport.getMinWorldHeight());
		return (pixels / minHeight) * 0.6f;
	}

	// ------------------------------------------------------------------ input

	/**
	 * A tap: the two buttons while the panel is closed, and inside the sheet -- add, sooner, remove, switch sheet, close,
	 * page, pause. Every tap inside the open panel is eaten, even on empty panel, so a stray thumb cannot click the
	 * world through it.
	 */
	boolean tap(final float worldX, final float worldY, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		final float x = xDp(worldX);
		final float y = yDp(worldY);
		if (this.sheet == Sheet.NONE) {
			if (insideButton(x, y, Sheet.BUILD)) {
				open(Sheet.BUILD);
				return true;
			}
			if (insideButton(x, y, Sheet.ARMY)) {
				open(Sheet.ARMY);
				return true;
			}
			logNearMiss(x, y);
			return false;
		}
		if (!insidePanel(x, y)) {
			return false;
		}
		if (insideClose(x, y)) {
			this.sheet = Sheet.NONE;
			this.cells = null;
			this.info = "";
			return true;
		}
		if (insideTab(x, y, Sheet.BUILD)) {
			open(Sheet.BUILD);
			return true;
		}
		if (insideTab(x, y, Sheet.ARMY)) {
			open(Sheet.ARMY);
			return true;
		}
		if (insideArrow(x, y, false)) {
			page(-1);
			return true;
		}
		if (insideArrow(x, y, true)) {
			page(1);
			return true;
		}
		if (insidePause(x, y)) {
			if (executor != null) {
				executor.setSupplyPaused(!executor.isSupplyPaused());
				this.info = executor.isSupplyPaused() ? "supply paused - no farms or wells are ordered" : "supply resumed";
			}
			return true;
		}
		if ((y > bodyTopDp()) || (y < bodyBottomDp())) {
			return true;
		}
		for (final Cell cell : cells(executor)) {
			if (!visible(cell) || !cell.inside(x, y)) {
				continue;
			}
			if (cell.number < 0) {
				if (executor != null) {
					final MacroTicket ticket = executor.addTicket(cell.rawcode);
					this.info = nameOf(cell.rawcode) + " - added at " + executor.getPlan().indexOf(ticket)
							+ (cell.status.isEmpty() ? "" : ", " + cell.status);
					this.cells = null;
				}
				return true;
			}
			if (cell.onMinus(x, y)) {
				if (executor != null) {
					executor.removeAt(cell.number);
					this.info = nameOf(cell.rawcode) + " - removed";
					this.cells = null;
				}
				return true;
			}
			if (executor != null) {
				final boolean moved = executor.sooner(cell.number);
				this.info = nameOf(cell.rawcode) + (moved ? " - now " + (cell.number - 1) : " - already first")
						+ (cell.status.isEmpty() ? "" : ", " + cell.status);
				this.cells = null;
			}
			return true;
		}
		return true;
	}

	/** A long press on a ticket: it runs one number later. Anywhere else it means nothing, and is not eaten. */
	boolean longPress(final float worldX, final float worldY, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		if ((this.sheet == Sheet.NONE) || (executor == null)) {
			return false;
		}
		final float x = xDp(worldX);
		final float y = yDp(worldY);
		if (!insidePanel(x, y)) {
			return false;
		}
		for (final Cell cell : cells(executor)) {
			if (!visible(cell) || !cell.inside(x, y) || (cell.number < 0) || cell.onMinus(x, y)) {
				continue;
			}
			final boolean moved = executor.later(cell.number);
			this.info = nameOf(cell.rawcode) + (moved ? " - now " + (cell.number + 1) : " - already last")
					+ (cell.status.isEmpty() ? "" : ", " + cell.status);
			this.cells = null;
			return true;
		}
		return false;
	}

	/** Whether the point is on the panel at all: the two buttons, or the sheet while it is open. */
	boolean contains(final float worldX, final float worldY) {
		layoutInterface();
		final float x = xDp(worldX);
		final float y = yDp(worldY);
		if (this.sheet != Sheet.NONE) {
			return insidePanel(x, y);
		}
		return insideButton(x, y, Sheet.BUILD) || insideButton(x, y, Sheet.ARMY);
	}

	private void open(final Sheet opened) {
		if (this.sheet != opened) {
			this.sheet = opened;
			this.scrollDp = 0f;
			this.info = "";
		}
		this.cells = null;
	}

	/** One page down or up, only ever needed by a plan longer than the screen. */
	private void page(final int direction) {
		cells(this.lastExecutor);
		final float step = Math.max(MacroLayout.CELL, bodyHeightDp() - MacroLayout.CELL);
		this.scrollDp = MacroLayout.clampScroll(this.scrollDp + (direction * step), this.contentDp, bodyHeightDp());
		this.cells = null;
	}

	private int count(final MacroPlanExecutor executor, final boolean army) {
		if ((executor == null) || !executor.isSeeded()) {
			return -1;
		}
		int count = 0;
		for (final MacroTicket ticket : executor.getPlan().getTickets()) {
			if ((ticket.getKind() == MacroTicketKind.TRAIN) == army) {
				count++;
			}
		}
		return count;
	}

	// ------------------------------------------------------------------ the panel's own log

	/** Once a minute, the numbers every tap is answered with: the first thing to read when a button stops answering. */
	private void logGeometry(final MacroPlanExecutor executor) {
		if (TimeUtils.millis() < this.geometryLoggedMs) {
			return;
		}
		this.geometryLoggedMs = TimeUtils.millis() + 60000L;
		Gdx.app.log(LOG_TAG,
				"panel: unit=" + this.unit + " left=" + this.left + " bottom=" + this.bottom + " screen="
						+ screenWidthDp() + "x" + screenHeightDp() + " buttons=" + buttonCentreX() + ","
						+ buttonCentreY(Sheet.BUILD) + "|" + buttonCentreY(Sheet.ARMY) + " seeded="
						+ ((executor == null) ? "no executor" : Boolean.toString(executor.isSeeded())));
	}

	/** A tap that aimed at a button and missed it: logged, because it is invisible in a screenshot and rare in play. */
	private void logNearMiss(final float xDp, final float yDp) {
		final double toBuild = Math.hypot(xDp - buttonCentreX(), yDp - buttonCentreY(Sheet.BUILD));
		final double toArmy = Math.hypot(xDp - buttonCentreX(), yDp - buttonCentreY(Sheet.ARMY));
		if ((toBuild > 60f) && (toArmy > 60f)) {
			return;
		}
		Gdx.app.log(LOG_TAG, "panel: a tap at dp " + xDp + "," + yDp + " missed the buttons at " + buttonCentreX() + ","
				+ buttonCentreY(Sheet.BUILD) + "/" + buttonCentreY(Sheet.ARMY));
	}

	// ------------------------------------------------------------------ what the icons are

	private MacroTicketKind kindOf(final Cell cell) {
		if ((this.lastExecutor == null) || (cell.number < 0)) {
			return MacroTicketKind.BUILD;
		}
		final MacroTicket ticket = this.lastExecutor.getPlan().get(cell.number);
		return (ticket == null) ? MacroTicketKind.BUILD : ticket.getKind();
	}

	private IconUI iconUI(final War3ID rawcode, final MacroTicketKind kind) {
		final AbilityDataUI dataUI = this.ui.war3MapViewer.getAbilityDataUI();
		if (dataUI == null) {
			return null;
		}
		if ((kind == MacroTicketKind.RESEARCH) || isResearch(rawcode)) {
			// The build sheet's available grid carries researches as well as structures, and a research's button is an
			// upgrade button: it has no unit button at all.
			return dataUI.getUpgradeUI(rawcode, 0);
		}
		return dataUI.getUnitUI(rawcode);
	}

	private boolean isResearch(final War3ID rawcode) {
		return (this.ui.war3MapViewer.simulation.getUpgradeData() != null)
				&& (this.ui.war3MapViewer.simulation.getUpgradeData().getType(rawcode) != null);
	}

	private Texture iconOf(final War3ID rawcode, final MacroTicketKind kind) {
		final IconUI icon = iconUI(rawcode, kind);
		return (icon == null) ? null : icon.getIcon();
	}

	/** The name a cell shows when it is tapped: the icon's own tooltip, which is the game's word for the unit. */
	private String nameOf(final War3ID rawcode) {
		return nameOf(rawcode, MacroTicketKind.BUILD);
	}

	/**
	 * A command card tooltip is markup, not a name: "Create |cffffcc00Moon Well|r" is the button that builds one. The words
	 * after the markup, with the verb the card puts in front of them, are what a player calls the thing.
	 */
	static String nameIn(final String tooltip) {
		final String plain = tooltip.replaceAll("\\|c[0-9a-fA-F]{8}", "").replace("|r", "").trim();
		for (final String verb : new String[] { "Create ", "Train ", "Research ", "Build ", "Upgrade ", "Learn " }) {
			if (plain.regionMatches(true, 0, verb, 0, verb.length())) {
				final String rest = plain.substring(verb.length()).trim();
				// "Upgrade to Strength of the Wild" and "Research to ...": the verb leaves a preposition behind.
				return rest.regionMatches(true, 0, "to ", 0, 3) ? rest.substring(3).trim() : rest;
			}
		}
		return plain;
	}

	private String nameOf(final War3ID rawcode, final MacroTicketKind kind) {
		final IconUI icon = iconUI(rawcode, kind);
		if ((icon != null) && (icon.getToolTip() != null) && !icon.getToolTip().isEmpty()) {
			final String name = nameIn(icon.getToolTip());
			if (!name.isEmpty()) {
				return name;
			}
		}
		return rawcode.asStringValue();
	}
}
