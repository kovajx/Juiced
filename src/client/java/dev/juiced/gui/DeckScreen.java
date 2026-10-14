package dev.juiced.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleGroup;
import dev.juiced.core.ModuleManager;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * The "Deck" menu: modules are big cards in a row. The middle card is in focus,
 * its neighbours fan out to the sides. Click a card to switch it on or off, press the
 * gear to flip it over and edit its settings on the back. Typing anywhere searches.
 * While a cosmetic is in focus the camera switches to third person so you see it live.
 */
public class DeckScreen extends Screen implements CameraKeeper {
	private static final int DOCK_HEIGHT = 18;
	private static final int PANEL = 0xF2111117;
	private static final int PANEL_DARK = 0xFF0A0A0F;
	private static final int EDGE = 0xFF2A2A36;
	private static final int TEXT = 0xFFEDEDF2;
	private static final int DIM = 0xFF8A8A99;
	private static final int MUTED = 0xFF55556A;

	@FunctionalInterface
	private interface Click {
		void run(double mouseX, double mouseY);
	}

	private record HitBox(int x1, int y1, int x2, int y2, Click action) {
		boolean contains(double x, double y) {
			return x >= x1 && x < x2 && y >= y1 && y < y2;
		}
	}

	private final List<Module> visible = new ArrayList<>();
	private final List<HitBox> hits = new ArrayList<>();
	private final Map<Module, List<String>> wrapped = new HashMap<>();
	private final double openedAt = Clock.t();

	private Category category = Category.COMBAT;
	private String query = "";
	private int focus;
	private float scroll = -0.8f;
	private boolean flipped;
	private float flip;
	private int settingsScroll;
	private int settingsMaxScroll;

	private final SettingsList settingsList = new SettingsList(
			(x1, y1, x2, y2, action) -> hits.add(new HitBox(x1, y1, x2, y2, (mx, my) -> action.run())),
			color -> {
				// Keep the front view while picking, so the cosmetic changes color live
				keepCamera = true;
				this.minecraft.gui.setScreen(new ColorPickerScreen(this, color));
			});

	private CameraType previousCamera;
	private boolean cameraChanged;
	private boolean keepCamera;

	private int mouseX;
	private int mouseY;
	private int cardW;
	private int cardH;
	/** Top-left of the focused card on screen, used to turn card coordinates into screen coordinates. */
	private int originX;
	private int originY;

	public DeckScreen() {
		super(Component.literal("Juiced"));
		refresh();
	}

	@Override
	protected void init() {
		super.init();
		// Minecraft only sends typed characters to screens that ask for them
		this.minecraft.textInputManager().startTextInput(this);
	}

	// ---- data -----------------------------------------------------------------------------

	private void refresh() {
		visible.clear();

		if (query.isEmpty()) {
			visible.addAll(ModuleManager.in(category));
		} else {
			String q = query.toLowerCase(Locale.ROOT);

			for (Module module : ModuleManager.all()) {
				StringBuilder text = new StringBuilder(module.name).append(' ').append(module.description).append(' ').append(module.category.label);

				// Also find things by their settings and styles ("low fire", "koi", "laternen")
				for (Setting<?> setting : module.settings) {
					text.append(' ').append(setting.label);

					if (setting instanceof ChoiceSetting choice) {
						text.append(' ').append(String.join(" ", choice.options));
					}
				}

				if (module instanceof ModuleGroup group) {
					for (Module part : group.parts()) {
						text.append(' ').append(part.name).append(' ').append(part.description);
					}
				}

				String haystack = text.toString().toLowerCase(Locale.ROOT);

				if (haystack.contains(q)) {
					visible.add(module);
				}
			}
		}

		focus = Math.max(0, Math.min(focus, visible.size() - 1));
		flipped = false;
		settingsScroll = 0;
	}

	private void selectCategory(Category next) {
		if (next == category && query.isEmpty()) {
			return;
		}

		category = next;
		query = "";
		focus = 0;
		refresh();
		scroll = -0.8f;
		Fx.sound("ui.button.click", 0.25f, 1.2f);
	}

	private Module focused() {
		return visible.isEmpty() ? null : visible.get(focus);
	}

	private void moveFocus(int delta) {
		if (visible.isEmpty()) {
			return;
		}

		int next = Math.max(0, Math.min(visible.size() - 1, focus + delta));

		if (next != focus) {
			focus = next;
			flipped = false;
			settingsScroll = 0;
			Fx.sound("ui.button.click", 0.15f, 1.6f);
		}
	}

	private void toggle(Module module) {
		if (module.alwaysOn()) {
			flipped = true;
			return;
		}

		module.toggle();
		Fx.sound("ui.button.click", 0.3f, module.isEnabled() ? 1.4f : 0.8f);
	}

	private void updateCamera() {
		Module module = focused();
		boolean wantPreview = module != null && module.category == Category.COSMETICS && minecraft != null && minecraft.player != null;

		if (wantPreview && !cameraChanged) {
			previousCamera = minecraft.options.getCameraType();
			minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			cameraChanged = true;
		} else if (!wantPreview && cameraChanged) {
			restoreCamera();
		}
	}

	private void openWardrobe() {
		if (this.minecraft.player == null) {
			return;
		}

		Fx.sound("ui.button.click", 0.3f, 1.2f);
		// The wardrobe brings its own camera
		restoreCamera();
		this.minecraft.gui.setScreen(new WardrobeScreen(this));
	}

	private void restoreCamera() {
		if (cameraChanged && minecraft != null && previousCamera != null) {
			minecraft.options.setCameraType(previousCamera);
		}

		cameraChanged = false;
	}

	// ---- rendering ------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float open = openProgress();
		Gui2D.fill(graphics, 0, 0, this.width, this.height, Colors.argb(0.18f * open, 0x000000));
		graphics.fillGradient(0, this.height / 3, this.width, this.height, 0x00000000, Colors.argb(0.75f * open, 0x05050A));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Clock.frame();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();
		updateCamera();

		scroll = Clock.approach(scroll, focus, 14f);
		flip = Clock.approach(flip, flipped ? 1f : 0f, 13f);

		float open = Clock.easeOut(openProgress());
		graphics.pose().pushMatrix();
		graphics.pose().translate(0, (1f - open) * 16f);

		drawTopBar(graphics);
		drawCards(graphics);
		drawDock(graphics);
		drawHints(graphics);

		graphics.pose().popMatrix();

		if (!settingsList.isDragging()) {
			for (HitBox hit : hits) {
				if (hit.contains(mouseX, mouseY)) {
					graphics.requestCursor(CursorTypes.POINTING_HAND);
					break;
				}
			}
		}
	}

	private float openProgress() {
		return Colors.clamp01((float) ((Clock.t() - openedAt) / 0.22));
	}

	private void drawTopBar(GuiGraphicsExtractor g) {
		int accent = Theme.accent();
		Gui2D.text(g, "JUICED", 12, 10, Colors.argb(1f, accent), true, 1.4f);
		Gui2D.text(g, "PvP Visuals", 12 + Gui2D.width("JUICED", 1.4f) + 6, 13, DIM, false, 0.8f);

		int logoEnd = 12 + Gui2D.width("JUICED", 1.4f) + 6 + Gui2D.width("PvP Visuals", 0.8f);

		// Buttons on the right: HUD editor and wardrobe (short labels on narrow screens)
		boolean narrow = this.width < 470;
		String hudLabel = narrow ? "\u271B HUD" : "\u271B HUD bearbeiten";
		int bw = Gui2D.width(hudLabel, 0.85f) + 14;
		int bx2 = this.width - 10;
		int bx1 = bx2 - bw;
		String wardrobeLabel = "\u265B Garderobe";
		int ww = Gui2D.width(wardrobeLabel, 0.85f) + 14;
		int wx2 = bx1 - 6;
		int wx1 = wx2 - ww;

		// Search box in the space between, centered when there is room
		int gapStart = logoEnd + 8;
		int gapEnd = wx1 - 8;
		int boxW = Math.max(60, Math.min(180, gapEnd - gapStart));
		int x1 = Math.max(gapStart, Math.min((this.width - boxW) / 2, gapEnd - boxW));
		int y1 = 7;
		boolean active = !query.isEmpty();
		Gui2D.rounded(g, x1, y1, x1 + boxW, y1 + 16, active ? 0xE0181822 : 0x9010101A);
		Gui2D.outline(g, x1, y1, x1 + boxW, y1 + 16, active ? Colors.argb(0.8f, accent) : 0x40FFFFFF);
		Gui2D.text(g, "⌕", x1 + 6, y1 + 4, active ? Colors.argb(1f, accent) : DIM, false, 1f);

		if (active) {
			boolean caret = (Clock.t() * 2) % 2 < 1;
			Gui2D.text(g, query + (caret ? "_" : ""), x1 + 18, y1 + 4, TEXT, false, 1f);
		} else {
			Gui2D.text(g, boxW >= 120 ? "Tippen zum Suchen…" : "Suchen…", x1 + 18, y1 + 4, MUTED, false, 1f);
		}

		boolean hover = mouseInside(bx1, 7, bx2, 23);
		boolean hudTab = query.isEmpty() && category == Category.HUD;
		int bg = hover ? Colors.argb(1f, accent) : hudTab ? Colors.argb(0.35f, accent) : 0xE01A1A22;
		Gui2D.rounded(g, bx1, 7, bx2, 23, bg);
		Gui2D.outline(g, bx1, 7, bx2, 23, hover ? Colors.argb(1f, accent) : Colors.argb(0.5f, accent));
		Gui2D.centered(g, hudLabel, (bx1 + bx2) / 2f, 11, hover ? 0xFF0B0B10 : TEXT, false, 0.85f);
		hits.add(new HitBox(bx1, 7, bx2, 23, (mx, my) -> {
			Fx.sound("ui.button.click", 0.3f, 1.2f);
			this.minecraft.gui.setScreen(new HudEditorScreen(this));
		}));

		boolean wardrobeHover = mouseInside(wx1, 7, wx2, 23);
		boolean cosmeticTab = query.isEmpty() && category == Category.COSMETICS;
		Gui2D.rounded(g, wx1, 7, wx2, 23, wardrobeHover ? Colors.argb(1f, accent) : cosmeticTab ? Colors.argb(0.35f, accent) : 0xE01A1A22);
		Gui2D.outline(g, wx1, 7, wx2, 23, wardrobeHover ? Colors.argb(1f, accent) : Colors.argb(0.5f, accent));
		Gui2D.centered(g, wardrobeLabel, (wx1 + wx2) / 2f, 11, wardrobeHover ? 0xFF0B0B10 : TEXT, false, 0.85f);
		hits.add(new HitBox(wx1, 7, wx2, 23, (mx, my) -> openWardrobe()));

		String count = visible.isEmpty() ? "" : (focus + 1) + " / " + visible.size();
		int countX = wx1 - 8 - Gui2D.width(count, 0.8f);

		if (countX > x1 + boxW + 6) {
			Gui2D.text(g, count, countX, 13, DIM, false, 0.8f);
		}
	}

	private void drawCards(GuiGraphicsExtractor g) {
		int dockTop = this.height - DOCK_HEIGHT - 10;
		int top = 32;
		int bottom = dockTop - 22;
		int available = bottom - top;
		cardH = Math.max(110, Math.min(224, available));
		cardW = Math.round(cardH * 0.72f);
		float centerY = top + available / 2f;
		float centerX = this.width / 2f;
		float spacing = cardW * 0.88f;

		if (visible.isEmpty()) {
			Gui2D.centered(g, "Nichts gefunden für \"" + query + "\"", centerX, centerY - 4, DIM, true, 1f);
			return;
		}

		// Draw from the outside in, so the focused card ends up on top
		Integer[] order = new Integer[visible.size()];

		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}

		java.util.Arrays.sort(order, (a, b) -> Float.compare(Math.abs(b - scroll), Math.abs(a - scroll)));

		for (int index : order) {
			float d = index - scroll;
			float ad = Math.abs(d);

			if (ad > 3.2f) {
				continue;
			}

			float offset = ad < 1f ? ad * spacing : spacing + (ad - 1f) * spacing * 0.62f;
			float x = centerX + Math.signum(d) * offset;
			float scale = 1f - 0.16f * Math.min(ad, 2.5f);
			boolean isFocus = index == focus && ad < 0.5f;
			drawCard(g, visible.get(index), index, x, centerY, scale, ad, isFocus);
		}

		// Position dots under the deck
		int dots = visible.size();
		int dotsW = dots * 6;
		int dx = Math.round(centerX - dotsW / 2f);
		int dy = Math.round(centerY + cardH / 2f + 8);

		for (int i = 0; i < dots; i++) {
			boolean current = i == focus;
			int color = current ? Colors.argb(1f, Theme.accent()) : 0x50FFFFFF;
			Gui2D.fill(g, dx + i * 6, dy, dx + i * 6 + (current ? 4 : 2), dy + 2, color);
		}
	}

	private void drawCard(GuiGraphicsExtractor g, Module module, int index, float cx, float cy, float scale, float distance, boolean isFocus) {
		boolean settled = Math.abs(flip - (flipped ? 1f : 0f)) < 0.02f;
		float flipScale = isFocus ? Math.max(0.02f, Math.abs((float) Math.cos(Math.PI * flip))) : 1f;
		boolean showBack = isFocus && flip > 0.5f;
		boolean hover = isFocus && !flipped && mouseInside(cx - cardW / 2f, cy - cardH / 2f, cx + cardW / 2f, cy + cardH / 2f);
		float lift = hover ? -2f : 0f;

		g.pose().pushMatrix();
		g.pose().translate(cx, cy + lift);
		g.pose().scale(scale * flipScale, scale);
		g.pose().translate(-cardW / 2f, -cardH / 2f);

		if (isFocus) {
			originX = Math.round(cx - cardW / 2f);
			originY = Math.round(cy + lift - cardH / 2f + (1f - Clock.easeOut(openProgress())) * 16f);
		}

		boolean interactive = isFocus && settled;

		if (showBack) {
			drawBack(g, module, interactive);
		} else {
			drawFront(g, module, interactive);
		}

		if (distance > 0.05f) {
			Gui2D.rounded2(g, 0, 0, cardW, cardH, Colors.argb(Math.min(0.62f, distance * 0.34f), 0x06060A));
		}

		g.pose().popMatrix();

		if (!isFocus) {
			float half = cardW * scale / 2f;
			float halfH = cardH * scale / 2f;
			hits.add(new HitBox(Math.round(cx - half), Math.round(cy - halfH), Math.round(cx + half), Math.round(cy + halfH), (mx, my) -> moveFocus(index - focus)));
		}
	}

	private void drawFront(GuiGraphicsExtractor g, Module module, boolean interactive) {
		int accent = Theme.accent();
		boolean on = module.isEnabled();
		int w = cardW;
		int h = cardH;

		if (on) {
			Gui2D.glow(g, 0, 0, w, h, accent, 0.9f, 4);
		}

		Gui2D.rounded2(g, 0, 0, w, h, PANEL);
		Gui2D.outline(g, 0, 0, w, h, on ? Colors.argb(0.9f, accent) : EDGE);

		// Preview window
		int previewH = Math.round(h * 0.46f);
		Gui2D.rounded(g, 6, 6, w - 6, previewH, PANEL_DARK);
		g.fillGradient(7, previewH / 2, w - 7, previewH, 0x00000000, Colors.argb(on ? 0.16f : 0.06f, accent));
		module.drawPreview(g, 6, 6, w - 12, previewH - 6, Clock.tf());
		Gui2D.text(g, module.category.icon + " " + module.category.label.toUpperCase(Locale.ROOT), 11, 10, Colors.argb(0.75f, 0x8A8A99), false, 0.6f);

		// Title and description
		Gui2D.text(g, module.name, 9, previewH + 7, TEXT, true, 1.25f);
		List<String> lines = wrapped.computeIfAbsent(module, m -> Gui2D.wrap(m.description, w - 18, 0.75f));
		int maxLines = Math.max(1, (h - previewH - 50) / 8);

		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			Gui2D.text(g, lines.get(i), 9, previewH + 23 + i * 8, DIM, false, 0.75f);
		}

		// Bottom bar: on/off pill and settings gear
		int barY = h - 24;

		if (module.alwaysOn()) {
			Gui2D.rounded(g, 8, barY, 60, barY + 16, 0xFF1E1E28);
			Gui2D.centered(g, "SYSTEM", 34, barY + 5, DIM, false, 0.75f);
		} else {
			int pill = on ? Colors.argb(1f, accent) : 0xFF22222C;
			Gui2D.rounded(g, 8, barY, 60, barY + 16, pill);
			Gui2D.centered(g, on ? "● AN" : "○ AUS", 34, barY + 4, on ? 0xFF0B0B10 : DIM, false, 0.9f);
		}

		boolean hasSettings = !module.settings.isEmpty();

		if (hasSettings) {
			boolean gearHover = interactive && mouseInsideCard(w - 30, barY, w - 8, barY + 16);
			Gui2D.rounded(g, w - 30, barY, w - 8, barY + 16, gearHover ? 0xFF2C2C3A : 0xFF1E1E28);
			Gui2D.centered(g, "⚙", w - 19, barY + 4, gearHover ? Colors.argb(1f, accent) : TEXT, false, 1f);
		}

		if (interactive) {
			cardHit(0, 0, w, h, (mx, my) -> toggle(module));

			if (hasSettings) {
				cardHit(w - 30, barY, w - 8, barY + 16, (mx, my) -> {
					flipped = true;
					settingsScroll = 0;
					Fx.sound("ui.button.click", 0.25f, 1.1f);
				});
			}
		}
	}

	private void drawBack(GuiGraphicsExtractor g, Module module, boolean interactive) {
		int accent = Theme.accent();
		int w = cardW;
		int h = cardH;
		Gui2D.glow(g, 0, 0, w, h, accent, 0.5f, 3);
		Gui2D.rounded2(g, 0, 0, w, h, PANEL);
		Gui2D.outline(g, 0, 0, w, h, Colors.argb(0.9f, accent));

		// Header
		boolean backHover = interactive && mouseInsideCard(4, 4, 22, 22);
		Gui2D.rounded(g, 4, 4, 22, 22, backHover ? 0xFF2C2C3A : 0xFF1E1E28);
		Gui2D.centered(g, "‹", 13, 8, backHover ? Colors.argb(1f, accent) : TEXT, false, 1.2f);
		Gui2D.text(g, module.name, 27, 6, TEXT, true, 1f);
		Gui2D.text(g, "Einstellungen", 27, 16, DIM, false, 0.65f);
		Gui2D.fill(g, 6, 26, w - 6, 27, Colors.argb(0.6f, accent));

		if (interactive) {
			cardHit(4, 4, 22, 22, (mx, my) -> {
				flipped = false;
				Fx.sound("ui.button.click", 0.2f, 0.9f);
			});
		}

		int top = 31;
		int bottom = h - 6;
		settingsList.originX = originX;
		settingsList.originY = originY;
		settingsList.mouseX = mouseX;
		settingsList.mouseY = mouseY;
		int contentHeight = settingsList.draw(g, module.settings, 9, w - 18, top, bottom, settingsScroll, interactive);
		int y = top - settingsScroll + contentHeight;

		// Reset button at the end of the list
		int resetH = 18;

		if (y >= top - 1 && y + resetH <= bottom + 1) {
			boolean hover = interactive && mouseInsideCard(8, y + 2, w - 8, y + 16);
			Gui2D.rounded(g, 8, y + 2, w - 8, y + 16, hover ? 0xFF2C2C3A : 0xFF1A1A22);
			Gui2D.centered(g, "Standard wiederherstellen", w / 2f, y + 6, hover ? TEXT : DIM, false, 0.7f);

			if (interactive) {
				cardHit(8, y + 2, w - 8, y + 16, (mx, my) -> {
					module.settings.forEach(Setting::reset);
					Fx.sound("ui.button.click", 0.25f, 0.7f);
				});
			}
		}

		contentHeight += resetH;
		settingsMaxScroll = Math.max(0, contentHeight - (bottom - top));
		settingsScroll = Math.max(0, Math.min(settingsScroll, settingsMaxScroll));

		if (settingsMaxScroll > 0) {
			int trackH = bottom - top;
			int thumbH = Math.max(12, trackH * trackH / (trackH + settingsMaxScroll));
			int thumbY = top + Math.round((trackH - thumbH) * (settingsScroll / (float) settingsMaxScroll));
			Gui2D.fill(g, w - 3, top, w - 2, bottom, 0x20FFFFFF);
			Gui2D.fill(g, w - 3, thumbY, w - 2, thumbY + thumbH, Colors.argb(0.8f, accent));
		}
	}

	private void drawDock(GuiGraphicsExtractor g) {
		int accent = Theme.accent();
		Category[] categories = Category.values();
		int gap = 4;
		int total = -gap;

		for (Category c : categories) {
			total += Gui2D.width(c.icon + " " + c.label, 0.85f) + 16 + gap;
		}

		int x = (this.width - total) / 2;
		int y = this.height - DOCK_HEIGHT - 10;
		Gui2D.rounded2(g, x - 5, y - 4, x + total + 5, y + DOCK_HEIGHT + 4, 0xC00C0C12);

		for (Category c : categories) {
			String label = c.icon + " " + c.label;
			int w = Gui2D.width(label, 0.85f) + 16;
			boolean selected = query.isEmpty() && c == category;
			boolean hover = mouseInside(x, y, x + w, y + DOCK_HEIGHT);
			int bg = selected ? Colors.argb(1f, accent) : hover ? 0x22FFFFFF : 0x00000000;
			Gui2D.rounded(g, x, y, x + w, y + DOCK_HEIGHT, bg);
			Gui2D.centered(g, label, x + w / 2f, y + 6, selected ? 0xFF0B0B10 : hover ? TEXT : DIM, false, 0.85f);
			hits.add(new HitBox(x, y, x + w, y + DOCK_HEIGHT, (mx, my) -> selectCategory(c)));
			x += w + gap;
		}
	}

	private void drawHints(GuiGraphicsExtractor g) {
		String hint;

		if (flipped) {
			hint = "Scrollen = mehr Einstellungen  ·  ESC = zurück";
		} else if (query.isEmpty() && category == Category.HUD) {
			hint = "\u271B HUD bearbeiten (oben rechts) = Elemente frei verschieben  \u00B7  Klick = an/aus  \u00B7  \u2699 = Einstellungen";
		} else if (focused() != null && focused().category == Category.COSMETICS) {
			hint = "Live-Vorschau an dir  ·  ← → blättern  ·  Klick = an/aus";
		} else {
			hint = "← → blättern  ·  Klick = an/aus  ·  ⚙ = Einstellungen  ·  Tippen = Suchen";
		}

		Gui2D.centered(g, hint, this.width / 2f, this.height - DOCK_HEIGHT - 26, MUTED, false, 0.7f);
	}

	// ---- hit testing helpers ----------------------------------------------------------------

	/** Registers a clickable area given in focused-card coordinates. */
	private void cardHit(int x1, int y1, int x2, int y2, Click action) {
		hits.add(new HitBox(originX + x1, originY + y1, originX + x2, originY + y2, action));
	}

	private boolean mouseInsideCard(int x1, int y1, int x2, int y2) {
		return mouseInside(originX + x1, originY + y1, originX + x2, originY + y2);
	}

	private boolean mouseInside(float x1, float y1, float x2, float y2) {
		return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
	}

	// ---- input ----------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();

		for (int i = hits.size() - 1; i >= 0; i--) {
			HitBox hit = hits.get(i);

			if (hit.contains(x, y)) {
				hit.action().run(x, y);
				return true;
			}
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (settingsList.isDragging()) {
			settingsList.drag(event.x());
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (settingsList.isDragging()) {
			settingsList.release();
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (flipped && mouseInside(originX, originY, originX + cardW, originY + cardH)) {
			settingsScroll = Math.max(0, Math.min(settingsMaxScroll, settingsScroll - (int) Math.round(scrollY * 14)));
			return true;
		}

		if (scrollY != 0) {
			moveFocus(scrollY > 0 ? -1 : 1);
			return true;
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (JuicedClient.MENU_KEY != null && JuicedClient.MENU_KEY.matches(event) && query.isEmpty()) {
			this.onClose();
			return true;
		}

		if (event.isEscape()) {
			if (flipped) {
				flipped = false;
				return true;
			}

			if (!query.isEmpty()) {
				query = "";
				refresh();
				return true;
			}

			return super.keyPressed(event);
		}

		if (event.key() == InputConstants.KEY_BACKSPACE && !query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			refresh();
			return true;
		}

		if (event.isLeft()) {
			moveFocus(-1);
			return true;
		}

		if (event.isRight()) {
			moveFocus(1);
			return true;
		}

		if (event.isSelection() && focused() != null && !flipped) {
			toggle(focused());
			return true;
		}

		if (event.isUp() || event.isDown()) {
			if (flipped) {
				int step = event.isUp() ? -14 : 14;
				settingsScroll = Math.max(0, Math.min(settingsMaxScroll, settingsScroll + step));
			} else if (query.isEmpty()) {
				Category[] all = Category.values();
				int next = Math.floorMod(category.ordinal() + (event.isUp() ? -1 : 1), all.length);
				selectCategory(all[next]);
			}

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String typed = event.codepointAsString();

		if (flipped || typed.isEmpty() || query.length() >= 24) {
			return false;
		}

		char c = typed.charAt(0);

		if (Character.isLetterOrDigit(c) || (c == ' ' && !query.isEmpty()) || c == '-') {
			query += typed;
			focus = 0;
			refresh();
			scroll = 0;
			return true;
		}

		return false;
	}

	@Override
	public void removed() {
		super.removed();
		this.minecraft.textInputManager().stopTextInput(this);

		if (keepCamera) {
			keepCamera = false;
		} else {
			restoreCamera();
		}

		Config.save();
	}

	@Override
	public void releaseCamera() {
		restoreCamera();
	}
}
