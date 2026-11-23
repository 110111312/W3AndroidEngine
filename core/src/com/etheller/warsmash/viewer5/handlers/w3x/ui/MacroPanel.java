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
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.players.CPlayer;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroPlanExecutor;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroStatusLine;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroTicket;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.MacroTicketKind;
import com.etheller.warsmash.viewer5.handlers.w3x.simulation.macro.TicketState;

/**
 * The macro panel: two buttons above the skill arc, and the sheet each one opens.
 * <p>
 * The settled design is {@code docs/mockups/macro-panel.html}, and the shape of it is the plan itself -- available above
 * (what you have, xN), your numbered order below (what you are about to have), one sheet per kind of ticket, one
 * numbering shared by both. The panel only decides what a tap means: adding, moving one number sooner or later, or
 * removing. Everything it shows comes from {@link MacroPlanExecutor}: this class never decides what runs and never
 * issues an order.
 * <p>
 * The numbers are dp against the interface's bottom-right corner, like {@link HeroTray}'s, so a screen of another size
 * scales the whole panel instead of clipping it. The arithmetic of the grid lives in {@link MacroLayout}, which is
 * tested; this class draws with the same numbers it hit-tests with, from one list of cells, so a tap can never land on
 * something the frame did not draw.
 */
final class MacroPanel {
	/** Which sheet is open. Only one at a time: the two are two views of one numbering, not two places to be. */
	enum Sheet {
		NONE, BUILD, ARMY
	}

	// The two buttons, above the skill arc and stacked. The arc reaches 171dp above the corner at its highest point.
	private static final float BUTTON = 52f;
	private static final float BUTTON_INSET = 14f;
	private static final float ARMY_BOTTOM = 178f;
	private static final float BUILD_BOTTOM = 236f;
	// The sheet: the right-middle band, from under the resource bar to the top of the tray.
	private static final float SHEET_INSET = 8f;
	private static final float SHEET_BOTTOM = 176f;
	private static final float SHEET_TOP_INSET = 36f;
	private static final float SHEET_WIDTH = 360f;
	private static final float HEADER = 26f;
	private static final float SUPPLY = 26f;
	private static final float SECTION = 14f;
	private static final float ARROW = 22f;
	/** How often the grid is rebuilt from the engine: the icon states are the ability gate's, not a frame's. */
	private static final long REFRESH_MS = 400L;

	private static final Color PANEL_COLOR = new Color(0.09f, 0.07f, 0.035f, 0.93f);
	private static final Color HEADER_COLOR = new Color(0.13f, 0.10f, 0.06f, 0.97f);
	private static final Color SUPPLY_COLOR = new Color(0.14f, 0.19f, 0.10f, 0.95f);
	private static final Color GOLD = new Color(0.784f, 0.647f, 0.322f, 1f);
	private static final Color GOLD_DIM = new Color(0.54f, 0.443f, 0.212f, 1f);
	private static final Color TEXT = new Color(0.91f, 0.863f, 0.722f, 1f);
	private static final Color SUB = new Color(0.60f, 0.545f, 0.384f, 1f);
	private static final Color DANGER = new Color(0.69f, 0.337f, 0.247f, 1f);
	private static final Color CHIP = new Color(0.17f, 0.14f, 0.08f, 1f);
	private static final Color LOCKED_TINT = new Color(0.42f, 0.42f, 0.42f, 0.55f);
	private static final Color BADGE_PULSE = new Color(1f, 0.84f, 0.42f, 1f);

	private final MeleeUI ui;
	private final GlyphLayout layout = new GlyphLayout();
	private Sheet sheet = Sheet.NONE;
	private float scrollDp;
	private float unit;
	private float right;
	private float bottom;
	private MacroPlanExecutor lastExecutor;

	/** The cells as last built, so that a tap hits what the frame drew. Rebuilt on a beat, not on every frame. */
	private List<Cell> cells;
	private long rebuildAtMs;
	/** Where the two lists start this beat, for the labels that sit above them. */
	private float availableTop;
	private float orderTop;
	private float contentDp;
	/** The supply line's words, rebuilt with the grid rather than every frame: asking what it would build walks the base. */
	private String supplyText = "";
	private int ticketCells;

	private BitmapFont captionFont;
	private int captionPx;
	private BitmapFont titleFont;
	private int titlePx;

	/** One icon in a sheet: where it is, what it is, and what a tap on it does. */
	private static final class Cell {
		float left;
		float top;
		float width;
		float height;
		War3ID rawcode;
		String caption;
		String status = "";
		int owned;
		/** The ticket's number in the shared plan, or -1 for an icon of the available grid. */
		int number = -1;
		boolean locked;

		boolean inside(final float x, final float y) {
			return (x >= this.left) && (x <= (this.left + this.width)) && (y <= this.top)
					&& (y >= (this.top - this.height));
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
		this.right = this.ui.minimapPanel.heroInterfaceLeft() + this.ui.uiViewport.getWorldWidth();
		this.bottom = this.ui.minimapPanel.heroInterfaceTop() - this.ui.uiViewport.getWorldHeight();
	}

	private float dp(final float value) {
		return value * this.unit;
	}

	private float interfaceHeightDp() {
		return this.ui.uiViewport.getMinWorldHeight() / this.unit;
	}

	private float sheetWidthDp() {
		// Never across the hero: hero mode locks the camera on her, so she stands at the screen's centre and the sheet has
		// to start to the right of it. The sketch's 360dp only fits on screens wider than the 4:3 interface.
		final float centreDp = (this.ui.uiViewport.getWorldWidth() / 2f) / this.unit;
		final float maxDp = ((this.right / this.unit) - SHEET_INSET - centreDp) - 12f;
		return Math.max(160f, Math.min(SHEET_WIDTH, maxDp));
	}

	private float sheetLeft() {
		return this.right - dp(SHEET_INSET) - dp(sheetWidthDp());
	}

	private float sheetRight() {
		return this.right - dp(SHEET_INSET);
	}

	private float sheetBottom() {
		return this.bottom + dp(SHEET_BOTTOM);
	}

	private float sheetTop() {
		return this.bottom + dp(interfaceHeightDp() - SHEET_TOP_INSET);
	}

	private float bodyTop() {
		return sheetTop() - dp(HEADER);
	}

	private float bodyHeight() {
		return bodyTop() - sheetBottom();
	}

	private float buttonLeft() {
		return this.right - dp(BUTTON_INSET + BUTTON);
	}

	private float buttonTop(final Sheet which) {
		final float fromBottom = (which == Sheet.BUILD) ? BUILD_BOTTOM : ARMY_BOTTOM;
		return this.bottom + dp(fromBottom + BUTTON);
	}

	private float headerTop() {
		return sheetTop() - dp(2f);
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
		this.availableTop = -1f;
		this.orderTop = -1f;
		this.contentDp = 0f;
		if ((executor == null) || (this.sheet == Sheet.NONE)) {
			this.ticketCells = 0;
			this.supplyText = "";
			this.cells = built;
			return built;
		}
		final boolean army = (this.sheet == Sheet.ARMY);
		final float left = sheetLeft();
		final int columns = MacroLayout.columns(sheetWidthDp());
		float y = bodyTop() + dp(this.scrollDp);
		if (!army) {
			y -= dp(SUPPLY);
		}
		// AVAILABLE -- WHAT YOU HAVE
		y -= dp(SECTION);
		this.availableTop = y + dp(SECTION - 3f);
		final List<War3ID> available = executor.available(army);
		y = addAvailable(built, available, y, left, columns, executor);
		// ORDER -- WHAT YOU ARE ABOUT TO HAVE
		y -= dp(SECTION);
		this.orderTop = y + dp(SECTION - 3f);
		final List<TicketState> tickets = tickets(executor, army);
		addTickets(built, tickets, y, left, columns);
		this.ticketCells = tickets.size();
		if (!army) {
			this.supplyText = supplyReport(executor);
		}
		final int availableRows = (available.size() + columns - 1) / columns;
		final int ticketRows = (tickets.size() + columns - 1) / columns;
		this.contentDp = (army ? 0f : SUPPLY) + (2f * SECTION) + MacroLayout.rowsHeightDp(availableRows)
				+ MacroLayout.rowsHeightDp(ticketRows) + (3f * MacroLayout.GAP);
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

	private float addAvailable(final List<Cell> into, final List<War3ID> rawcodes, final float startY, final float left,
			final int columns, final MacroPlanExecutor executor) {
		for (int i = 0; i < rawcodes.size(); i++) {
			final War3ID rawcode = rawcodes.get(i);
			final Cell cell = new Cell();
			cell.rawcode = rawcode;
			cell.owned = executor.ownedCount(rawcode);
			cell.locked = executor.lockOf(rawcode) != null;
			cell.caption = nameOf(rawcode, MacroTicketKind.BUILD);
			place(cell, i, startY, left, columns);
			into.add(cell);
		}
		final int rows = (rawcodes.size() + columns - 1) / columns;
		return startY - dp(MacroLayout.rowsHeightDp(rows)) - (rows > 0 ? dp(MacroLayout.GAP) : 0f);
	}

	private void addTickets(final List<Cell> into, final List<TicketState> tickets, final float startY,
			final float left, final int columns) {
		for (int i = 0; i < tickets.size(); i++) {
			final TicketState state = tickets.get(i);
			final Cell cell = new Cell();
			cell.rawcode = state.getTicket().getRawcode();
			cell.number = state.getNumber();
			cell.locked = state.isStuck();
			cell.caption = nameOf(cell.rawcode, state.getTicket().getKind());
			cell.status = MacroStatusLine.of(state, this::nameOf);
			place(cell, i, startY, left, columns);
			into.add(cell);
		}
	}

	private void place(final Cell cell, final int index, final float startY, final float left, final int columns) {
		final int column = index % columns;
		final int row = index / columns;
		cell.left = left + dp(MacroLayout.MARGIN + (column * (MacroLayout.CELL + MacroLayout.GAP)));
		cell.top = startY - dp(MacroLayout.rowTopDp(row));
		cell.width = dp(MacroLayout.CELL);
		cell.height = dp(MacroLayout.STRIP);
	}

	// ------------------------------------------------------------------ drawing

	void render(final SpriteBatch batch, final ShapeRenderer shapes, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		if (this.sheet == Sheet.NONE) {
			drawButtons(batch, shapes, executor);
			drawFoodBadge(batch, shapes);
			return;
		}
		final List<Cell> shown = cells(executor);
		batch.end();
		shapes.setProjectionMatrix(batch.getProjectionMatrix());
		drawSheetBackground(shapes);
		batch.begin();
		drawIcons(batch, shown);
		batch.end();
		drawChips(shapes, shown);
		batch.begin();
		drawText(batch, shown, executor);
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
		final int buildCount = count(executor, false);
		buttonText(batch, title, caption, Sheet.BUILD, buildCount, "build");
		buttonText(batch, title, caption, Sheet.ARMY, count(executor, true), "army");
	}

	private void button(final ShapeRenderer shapes, final Sheet which) {
		final float left = buttonLeft();
		final float top = buttonTop(which);
		final float size = dp(BUTTON);
		shapes.setColor(GOLD);
		shapes.rect(left, top - size, size, size);
		final float border = dp(2f);
		shapes.setColor(PANEL_COLOR);
		shapes.rect(left + border, (top - size) + border, size - (2 * border), size - (2 * border));
	}

	private void buttonText(final SpriteBatch batch, final BitmapFont title, final BitmapFont caption,
			final Sheet which, final int count, final String label) {
		final float left = buttonLeft();
		final float top = buttonTop(which);
		final float size = dp(BUTTON);
		title.setColor(GOLD);
		centred(batch, title, Integer.toString(count), left + (size / 2f), top - dp(12f));
		caption.setColor(SUB);
		centred(batch, caption, label, left + (size / 2f), top - dp(38f));
	}

	private void drawSheetBackground(final ShapeRenderer shapes) {
		final float left = sheetLeft();
		final float rightEdge = sheetRight();
		final float bottomEdge = sheetBottom();
		final float top = sheetTop();
		final float border = dp(2f);
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Filled);
		shapes.setColor(GOLD);
		shapes.rect(left, bottomEdge, rightEdge - left, top - bottomEdge);
		shapes.setColor(PANEL_COLOR);
		shapes.rect(left + border, bottomEdge + border, (rightEdge - left) - (2 * border),
				(top - bottomEdge) - (2 * border));
		shapes.setColor(HEADER_COLOR);
		shapes.rect(left + border, bodyTop(), (rightEdge - left) - (2 * border), headerTop() - bodyTop() - border);
		if (this.sheet == Sheet.BUILD) {
			shapes.setColor(SUPPLY_COLOR);
			shapes.rect(left + dp(6f), bodyTop() - dp(SUPPLY - 3f), (rightEdge - left) - dp(12f), dp(SUPPLY - 7f));
		}
		shapes.setColor(GOLD_DIM);
		shapes.rect(arrowLeft(false), headerTop() - dp(ARROW) - dp(2f), dp(ARROW), dp(ARROW));
		shapes.rect(arrowLeft(true), headerTop() - dp(ARROW) - dp(2f), dp(ARROW), dp(ARROW));
		if (this.sheet == Sheet.BUILD) {
			shapes.rect(pauseLeft(), bodyTop() - dp(SUPPLY - 5f), dp(40f), dp(SUPPLY - 11f));
		}
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
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
			final float left = cell.left + dp((MacroLayout.CELL - MacroLayout.ICON) / 2f);
			batch.setColor(cell.locked ? LOCKED_TINT : Color.WHITE);
			batch.draw(icon, left, cell.top - dp(2f) - size, size, size);
		}
		batch.setColor(Color.WHITE);
	}

	private void drawChips(final ShapeRenderer shapes, final List<Cell> shown) {
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shapes.begin(ShapeType.Filled);
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			final float left = cell.left + dp((MacroLayout.CELL - MacroLayout.ICON) / 2f);
			final float top = cell.top - dp(2f);
			// The number of a ticket, top-left, and the minus, top-right: the sheet's anatomy, and both are taps.
			if (cell.number >= 0) {
				shapes.setColor(GOLD);
				shapes.rect(left - dp(5f), top - dp(7f), dp(16f), dp(16f));
				shapes.setColor(DANGER);
				shapes.rect((left + dp(MacroLayout.ICON)) - dp(11f), top - dp(7f), dp(16f), dp(16f));
			}
			else if (cell.owned > 0) {
				shapes.setColor(CHIP);
				shapes.rect((left + dp(MacroLayout.ICON)) - dp(18f), top - dp(7f), dp(23f), dp(15f));
			}
		}
		shapes.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
	}

	private void drawText(final SpriteBatch batch, final List<Cell> shown, final MacroPlanExecutor executor) {
		final BitmapFont title = titleFont();
		final BitmapFont caption = captionFont();
		if ((title == null) || (caption == null)) {
			return;
		}
		final float left = sheetLeft();
		final float rightEdge = sheetRight();
		title.setColor(GOLD);
		draw(batch, title, (this.sheet == Sheet.BUILD) ? "BUILD PLAN" : "ARMY PLAN", left + dp(10f),
				headerTop() - dp(6f), rightEdge - left - dp(100f));
		title.setColor(SUB);
		centred(batch, title, "^", arrowLeft(false) + (dp(ARROW) / 2f), headerTop() - dp(4f));
		centred(batch, title, "v", arrowLeft(true) + (dp(ARROW) / 2f), headerTop() - dp(4f));
		centred(batch, title, "x", rightEdge - dp(14f), headerTop() - dp(4f));
		if (this.sheet == Sheet.BUILD) {
			caption.setColor(TEXT);
			draw(batch, caption, this.supplyText, left + dp(12f), bodyTop() - dp(9f), (rightEdge - left) - dp(80f));
			caption.setColor(GOLD);
			centred(batch, caption, supplyPaused(executor) ? "paused" : "pause", pauseLeft() + dp(20f),
					bodyTop() - dp(9f));
		}
		section(batch, caption, "AVAILABLE - WHAT YOU HAVE", left, this.availableTop);
		section(batch, caption, "ORDER - WHAT YOU ARE ABOUT TO HAVE", left, this.orderTop);
		if (this.ticketCells == 0) {
			caption.setColor(SUB);
			draw(batch, caption, "empty - the plan does nothing", left + dp(MacroLayout.MARGIN),
					this.orderTop - dp(4f), dp(sheetWidthDp() - (2f * MacroLayout.MARGIN)));
		}
		for (final Cell cell : shown) {
			if (!visible(cell)) {
				continue;
			}
			caption.setColor(TEXT);
			draw(batch, caption, cell.caption, cell.left, cell.top - dp(MacroLayout.ICON + 8f), dp(MacroLayout.CELL));
			if (cell.number >= 0) {
				title.setColor(CHIP);
				centred(batch, title, Integer.toString(cell.number), cell.left + dp(3f), cell.top + dp(1f));
				caption.setColor(Color.WHITE);
				centred(batch, caption, "-", cell.left + dp(MacroLayout.ICON + 6f), cell.top + dp(1f));
			}
			else if (cell.owned > 0) {
				caption.setColor(SUB);
				centred(batch, caption, "x" + cell.owned, cell.left + dp(MacroLayout.ICON - 6f), cell.top + dp(1f));
			}
			if (!cell.status.isEmpty()) {
				caption.setColor(cell.locked ? SUB : GOLD);
				draw(batch, caption, cell.status, cell.left, cell.top - dp(MacroLayout.ICON + 20f),
						dp(MacroLayout.CELL));
			}
		}
	}

	/** The supply line's one line: the food it is buying headroom for, and what it would build. */
	private String supplyReport(final MacroPlanExecutor executor) {
		final CPlayer player = this.ui.war3MapViewer.simulation
				.getPlayer(this.ui.war3MapViewer.getLocalPlayerIndex());
		if (player == null) {
			return "";
		}
		final War3ID type = (executor == null) ? null : executor.getSupplyType();
		return "Food " + player.getFoodUsed() + "/" + player.getFoodCapLimit() + " - "
				+ ((type == null) ? "no supply" : nameOf(type, MacroTicketKind.BUILD));
	}

	private boolean supplyPaused(final MacroPlanExecutor executor) {
		return (executor != null) && executor.isSupplyPaused();
	}

	/**
	 * The dot on the resource bar while the supply line is paused: with the sheet closed that is the only signal that the
	 * base is not doing something the player asked it to, and the food number is where they would look.
	 */
	private void drawFoodBadge(final SpriteBatch batch, final ShapeRenderer shapes) {
		if (!supplyPaused(this.lastExecutor) || (this.ui.resourceBarSupplyText == null)
				|| !this.ui.resourceBarSupplyText.isVisible()) {
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

	private void section(final SpriteBatch batch, final BitmapFont font, final String text, final float left,
			final float top) {
		if ((top <= 0f) || (top < sheetBottom()) || (top > bodyTop())) {
			return;
		}
		font.setColor(SUB);
		draw(batch, font, text, left + dp(10f), top - dp(2f), dp(sheetWidthDp() - 20f));
	}

	/** Draws text, cut to the width it is given: a caption is a name, not a paragraph. */
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
	 * A tap: the two buttons while the panel is closed, and inside the sheet -- add, sooner, remove, close, page, pause.
	 * Every tap inside the open sheet is eaten, even on empty panel, so a stray thumb cannot click the world through it.
	 */
	boolean tap(final float x, final float y, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		if (this.sheet == Sheet.NONE) {
			if (insideButton(x, y, Sheet.BUILD)) {
				open(Sheet.BUILD);
				return true;
			}
			if (insideButton(x, y, Sheet.ARMY)) {
				open(Sheet.ARMY);
				return true;
			}
			return false;
		}
		if (!insideSheet(x, y)) {
			return false;
		}
		if (insideClose(x, y)) {
			this.sheet = Sheet.NONE;
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
		if ((this.sheet == Sheet.BUILD) && insidePause(x, y)) {
			if (executor != null) {
				executor.setSupplyPaused(!executor.isSupplyPaused());
			}
			return true;
		}
		for (final Cell cell : cells(executor)) {
			if (!cell.inside(x, y) || !visible(cell)) {
				continue;
			}
			if (cell.number < 0) {
				if (executor != null) {
					executor.addTicket(cell.rawcode);
					this.cells = null;
				}
				return true;
			}
			if (insideMinus(cell, x, y)) {
				if (executor != null) {
					executor.removeAt(cell.number);
					this.cells = null;
				}
			}
			else if (executor != null) {
				executor.sooner(cell.number);
				this.cells = null;
			}
			return true;
		}
		return true;
	}

	/** A long press on a ticket: it runs one number later. Anywhere else it means nothing, and is not eaten. */
	boolean longPress(final float x, final float y, final MacroPlanExecutor executor) {
		layoutInterface();
		this.lastExecutor = executor;
		if ((this.sheet == Sheet.NONE) || !insideSheet(x, y) || (executor == null)) {
			return false;
		}
		for (final Cell cell : cells(executor)) {
			if (!cell.inside(x, y) || !visible(cell) || (cell.number < 0) || insideMinus(cell, x, y)) {
				continue;
			}
			executor.later(cell.number);
			this.cells = null;
			return true;
		}
		return false;
	}

	/** Whether the point is on the panel at all: the two buttons, or the sheet while it is open. */
	boolean contains(final float x, final float y) {
		layoutInterface();
		if (this.sheet != Sheet.NONE) {
			return insideSheet(x, y);
		}
		return insideButton(x, y, Sheet.BUILD) || insideButton(x, y, Sheet.ARMY);
	}

	private boolean insideButton(final float x, final float y, final Sheet which) {
		return (x >= buttonLeft()) && (x <= (buttonLeft() + dp(BUTTON))) && (y <= buttonTop(which))
				&& (y >= (buttonTop(which) - dp(BUTTON)));
	}

	private boolean insideSheet(final float x, final float y) {
		return (x >= sheetLeft()) && (x <= sheetRight()) && (y >= sheetBottom()) && (y <= sheetTop());
	}

	private boolean insideClose(final float x, final float y) {
		return (x >= (sheetRight() - dp(26f))) && (y >= (headerTop() - dp(HEADER)));
	}

	/** The two pager buttons of the header: up (false) and down (true). */
	private float arrowLeft(final boolean down) {
		return sheetRight() - dp(26f + ARROW + 2f + (down ? (ARROW + 2f) : 0f));
	}

	private boolean insideArrow(final float x, final float y, final boolean down) {
		return (x >= arrowLeft(down)) && (x <= (arrowLeft(down) + dp(ARROW))) && (y <= (headerTop() - dp(2f)))
				&& (y >= ((headerTop() - dp(ARROW)) - dp(2f)));
	}

	private float pauseLeft() {
		return sheetRight() - dp(52f);
	}

	private boolean insidePause(final float x, final float y) {
		return (x >= pauseLeft()) && (x <= (pauseLeft() + dp(40f))) && (y <= bodyTop())
				&& (y >= (bodyTop() - dp(SUPPLY)));
	}

	/** The minus is its own target, top-right of the icon: tapping the icon reorders, tapping the minus deletes. */
	private boolean insideMinus(final Cell cell, final float x, final float y) {
		final float left = cell.left + dp((MacroLayout.CELL - MacroLayout.ICON) / 2f) + dp(MacroLayout.ICON) - dp(11f);
		final float top = cell.top - dp(2f) - dp(7f);
		return (x >= (left - dp(4f))) && (x <= (left + dp(20f))) && (y <= (top + dp(4f))) && (y >= (top - dp(20f)));
	}

	private void open(final Sheet opened) {
		this.sheet = opened;
		this.scrollDp = 0f;
		this.cells = null;
	}

	/** One page down or up: the body less one row, so a row of context stays on screen. */
	private void page(final int direction) {
		cells(this.lastExecutor);
		final float step = Math.max(MacroLayout.STRIP, (bodyHeight() / this.unit) - MacroLayout.STRIP);
		this.scrollDp = MacroLayout.clampScroll(this.scrollDp + (direction * step), this.contentDp,
				bodyHeight() / this.unit);
		this.cells = null;
	}

	private boolean visible(final Cell cell) {
		return ((cell.top - cell.height) >= sheetBottom()) && (cell.top <= bodyTop());
	}

	private int count(final MacroPlanExecutor executor, final boolean army) {
		if (executor == null) {
			return 0;
		}
		int count = 0;
		for (final MacroTicket ticket : executor.getPlan().getTickets()) {
			if ((ticket.getKind() == MacroTicketKind.TRAIN) == army) {
				count++;
			}
		}
		return count;
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
		if (kind == MacroTicketKind.RESEARCH) {
			return dataUI.getUpgradeUI(rawcode, 0);
		}
		return dataUI.getUnitUI(rawcode);
	}

	private Texture iconOf(final War3ID rawcode, final MacroTicketKind kind) {
		final IconUI icon = iconUI(rawcode, kind);
		return (icon == null) ? null : icon.getIcon();
	}

	/** The name a cell shows: the icon's own tooltip, which is the game's word for the unit, or the rawcode. */
	private String nameOf(final War3ID rawcode) {
		for (final MacroTicketKind kind : new MacroTicketKind[] { MacroTicketKind.BUILD,
				MacroTicketKind.RESEARCH }) {
			final IconUI icon = iconUI(rawcode, kind);
			if ((icon != null) && (icon.getToolTip() != null) && !icon.getToolTip().isEmpty()) {
				return icon.getToolTip();
			}
		}
		return rawcode.asStringValue();
	}

	private String nameOf(final War3ID rawcode, final MacroTicketKind kind) {
		final IconUI icon = iconUI(rawcode, kind);
		if ((icon != null) && (icon.getToolTip() != null) && !icon.getToolTip().isEmpty()) {
			return icon.getToolTip();
		}
		return nameOf(rawcode);
	}
}
