package dev.juiced.gui;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Config;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleManager;
import dev.juiced.core.Theme;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * Drag HUD elements anywhere. Snaps to the screen edges, the middle and other elements
 * (hold Shift to move freely). Scroll over an element to resize it, right-click to reset it.
 */
public class HudEditorScreen extends Screen {
	private static final int GRID = 10;
	private static final int TEXT = 0xFFEDEDF2;
	private static final int DIM = 0xFF8A8A99;

	private record Box(HudModule module, int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private record Button(String label, int x1, int y1, int x2, int y2, Runnable action) {
		boolean contains(double px, double py) {
			return px >= x1 && px < x2 && py >= y1 && py < y2;
		}
	}

	private final Screen parent;
	/** Shown and editable even while switched off, because the editor was opened for it. */
	private final @Nullable HudModule preselect;
	private final List<Box> boxes = new ArrayList<>();
	private final List<Button> buttons = new ArrayList<>();
	private final double openedAt = Clock.t();

	private HudModule selected;
	private HudModule dragging;
	private float grabX;
	private float grabY;
	private Integer guideX;
	private Integer guideY;
	private boolean grid;
	private boolean panelOpen = true;
	private int mouseX;
	private int mouseY;

	/** Opens the editor with {@code preselect} selected, shown even while it is switched off (from the Weltprobe menu). */
	public HudEditorScreen(Screen parent, @Nullable HudModule preselect) {
		super(Component.literal("HUD bearbeiten"));
		this.parent = parent;
		this.preselect = preselect;
		this.selected = preselect;
	}

	public HudEditorScreen(Screen parent) {
		this(parent, null);
	}

	private static List<HudModule> hudModules() {
		List<HudModule> result = new ArrayList<>();

		for (Module module : ModuleManager.all()) {
			if (module instanceof HudModule hud) {
				result.add(hud);
			}
		}

		return result;
	}

	// ---- rendering ------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		Gui2D.fill(g, 0, 0, this.width, this.height, 0x40000000);

		if (grid) {
			for (int x = 0; x < this.width; x += GRID) {
				Gui2D.fill(g, x, 0, x + 1, this.height, x % (GRID * 5) == 0 ? 0x22FFFFFF : 0x0FFFFFFF);
			}

			for (int y = 0; y < this.height; y += GRID) {
				Gui2D.fill(g, 0, y, this.width, y + 1, y % (GRID * 5) == 0 ? 0x22FFFFFF : 0x0FFFFFFF);
			}
		}

		// Middle lines, always faintly visible
		Gui2D.fill(g, this.width / 2, 0, this.width / 2 + 1, this.height, 0x18FFFFFF);
		Gui2D.fill(g, 0, this.height / 2, this.width, this.height / 2 + 1, 0x18FFFFFF);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);
		Clock.frame();
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		boxes.clear();
		buttons.clear();

		Hud hud = new Hud(this.minecraft, this.width, this.height, partialTick);
		int accent = Theme.accent();
		Box hovered = null;

		for (HudModule module : hudModules()) {
			if ((!module.isEnabled() && module != preselect) || !module.renderForEditor(g, hud)) {
				continue;
			}

			Box box = new Box(module, module.screenX(this.width), module.screenY(this.height), module.scaledWidth(), module.scaledHeight());
			boxes.add(box);

			if (dragging == null && box.contains(mouseX, mouseY)) {
				hovered = box;
			}
		}

		for (Box box : boxes) {
			boolean isSelected = box.module == selected || box.module == dragging;
			boolean isHovered = hovered == box;
			int color = isSelected ? Colors.argb(1f, accent) : isHovered ? Colors.argb(0.75f, accent) : 0x40FFFFFF;

			if (isSelected) {
				Gui2D.glow(g, box.x - 1, box.y - 1, box.x + box.w + 1, box.y + box.h + 1, accent, 0.8f, 3);
			}

			Gui2D.outline(g, box.x - 1, box.y - 1, box.x + box.w + 1, box.y + box.h + 1, color);

			// The preselected element can be shown while switched off: keep that visible on it
			boolean off = !box.module.isSwitchedOn();

			if (isSelected || isHovered || off) {
				String label = box.module.name + "  " + Math.round(box.module.scale * 100) + "%" + (off ? "  · aus" : "");
				int lw = Gui2D.width(label, 0.75f) + 8;
				int ly = box.y - 12 >= 0 ? box.y - 12 : box.y + box.h + 2;
				Gui2D.rounded(g, box.x - 1, ly, box.x - 1 + lw, ly + 10, Colors.argb(0.92f, isSelected ? accent : 0x1A1A24));
				Gui2D.text(g, label, box.x + 3, ly + 2, isSelected ? 0xFF0B0B10 : TEXT, false, 0.75f);
			}
		}

		if (dragging != null) {
			if (guideX != null) {
				Gui2D.fill(g, guideX, 0, guideX + 1, this.height, Colors.argb(0.85f, accent));
			}

			if (guideY != null) {
				Gui2D.fill(g, 0, guideY, this.width, guideY + 1, Colors.argb(0.85f, accent));
			}
		}

		if (panelOpen) {
			drawPanel(g);
		}

		drawToolbar(g);

		if (boxes.isEmpty()) {
			Gui2D.centered(g, "Keine HUD-Elemente an. Links in der Liste welche einschalten.", this.width / 2f, this.height / 2f - 20, DIM, true, 1f);
		}

		boolean pointer = dragging != null || hovered != null;

		for (Button button : buttons) {
			if (button.contains(mouseX, mouseY)) {
				pointer = true;
				break;
			}
		}

		if (pointer) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private void drawPanel(GuiGraphicsExtractor g) {
		List<HudModule> modules = hudModules();
		int rowH = 13;
		int w = 120;
		int h = 18 + modules.size() * rowH + 4;
		int x = 6;
		int y = Math.max(28, (this.height - h) / 2);
		int accent = Theme.accent();
		Gui2D.rounded2(g, x, y, x + w, y + h, 0xE60C0C12);
		Gui2D.outline(g, x, y, x + w, y + h, 0xFF2A2A36);
		Gui2D.text(g, "Elemente", x + 7, y + 5, TEXT, true, 0.85f);
		addButton("✕", x + w - 14, y + 3, x + w - 3, y + 14, () -> panelOpen = false);
		Gui2D.text(g, "✕", x + w - 11, y + 5, DIM, false, 0.8f);

		for (int i = 0; i < modules.size(); i++) {
			HudModule module = modules.get(i);
			int ry = y + 18 + i * rowH;
			boolean on = module.isSwitchedOn();
			boolean hover = mouseX >= x + 3 && mouseX < x + w - 3 && mouseY >= ry && mouseY < ry + rowH;

			if (hover) {
				Gui2D.rounded(g, x + 3, ry, x + w - 3, ry + rowH, 0x18FFFFFF);
			}

			Gui2D.rounded(g, x + 7, ry + 3, x + 14, ry + 10, on ? Colors.argb(1f, accent) : 0xFF33333F);
			Gui2D.text(g, module.name, x + 18, ry + 3, on ? TEXT : DIM, false, 0.75f);
			addButton(module.name, x + 3, ry, x + w - 3, ry + rowH, () -> {
				module.toggle();
				Fx.sound("ui.button.click", 0.25f, module.isSwitchedOn() ? 1.4f : 0.8f);
			});
		}
	}

	private void drawToolbar(GuiGraphicsExtractor g) {
		String[] labels = {panelOpen ? "Liste ausblenden" : "Liste zeigen", grid ? "Raster: An" : "Raster: Aus", "Alles zurücksetzen", "Fertig"};
		Runnable[] actions = {
				() -> panelOpen = !panelOpen,
				() -> grid = !grid,
				this::resetAll,
				this::onClose
		};
		int gap = 4;
		int total = -gap;

		for (String label : labels) {
			total += Gui2D.width(label, 0.85f) + 14 + gap;
		}

		int x = (this.width - total) / 2;
		int y = this.height - 22;
		int accent = Theme.accent();

		for (int i = 0; i < labels.length; i++) {
			int w = Gui2D.width(labels[i], 0.85f) + 14;
			boolean primary = i == labels.length - 1;
			boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 15;
			int bg = primary ? Colors.argb(hover ? 1f : 0.9f, accent) : hover ? 0xF02C2C3A : 0xE01A1A22;
			Gui2D.rounded(g, x, y, x + w, y + 15, bg);
			Gui2D.centered(g, labels[i], x + w / 2f, y + 4, primary ? 0xFF0B0B10 : TEXT, false, 0.85f);
			addButton(labels[i], x, y, x + w, y + 15, actions[i]);
			x += w + gap;
		}

		float fade = Colors.clamp01((float) (6.0 - (Clock.t() - openedAt)) / 1.5f);
		String hint = "Ziehen = verschieben  ·  Mausrad = Größe  ·  Rechtsklick = zurücksetzen  ·  Shift = ohne Einrasten  ·  Pfeiltasten = feinjustieren";
		Gui2D.centered(g, hint, this.width / 2f, y - 11, Colors.argb(0.6f + 0.4f * fade, 0x9A9AAA), true, 0.7f);
	}

	private void addButton(String label, int x1, int y1, int x2, int y2, Runnable action) {
		buttons.add(new Button(label, x1, y1, x2, y2, action));
	}

	private void resetAll() {
		for (HudModule module : hudModules()) {
			module.resetPosition();
		}

		Fx.sound("ui.button.click", 0.3f, 0.7f);
	}

	// ---- dragging -------------------------------------------------------------------------

	private void dragTo(double mouseX, double mouseY) {
		HudModule module = dragging;
		int w = module.scaledWidth();
		int h = module.scaledHeight();
		float x = (float) mouseX - grabX;
		float y = (float) mouseY - grabY;
		guideX = null;
		guideY = null;

		if (!this.minecraft.hasShiftDown()) {
			List<int[]> others = new ArrayList<>();

			for (Box box : boxes) {
				if (box.module != dragging) {
					others.add(new int[] {box.x, box.y, box.w, box.h});
				}
			}

			float[] snappedX = snap(x, w, true, others);
			float[] snappedY = snap(y, h, false, others);
			x = snappedX[0];
			y = snappedY[0];

			if (snappedX[1] >= 0) {
				guideX = Math.round(snappedX[1]);
			}

			if (snappedY[1] >= 0) {
				guideY = Math.round(snappedY[1]);
			}
		}

		x = HudSnap.clamp(x, w, this.width);
		y = HudSnap.clamp(y, h, this.height);
		module.moveTo(x, y, this.width, this.height);
	}

	/**
	 * Snaps one axis with {@link HudSnap#snapAxis}; with the grid on, a position that snaps to no line rounds to
	 * the grid instead. Returns {new position, guide line or -1}. Positions stay fractional so the element keeps
	 * the exact spot it was grabbed at.
	 */
	private float[] snap(float position, int size, boolean horizontal, List<int[]> others) {
		float[] snapped = HudSnap.snapAxis(position, size, horizontal ? this.width : this.height, others, horizontal);

		if (snapped[1] < 0 && grid) {
			snapped[0] = Math.round(position / GRID) * GRID;
		}

		return snapped;
	}

	// ---- input ----------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double x = event.x();
		double y = event.y();

		for (int i = buttons.size() - 1; i >= 0; i--) {
			Button button = buttons.get(i);

			if (button.contains(x, y)) {
				button.action().run();
				return true;
			}
		}

		Box hit = null;

		for (int i = boxes.size() - 1; i >= 0; i--) {
			if (boxes.get(i).contains(x, y)) {
				hit = boxes.get(i);
				break;
			}
		}

		if (hit == null) {
			selected = null;
			return super.mouseClicked(event, doubleClick);
		}

		selected = hit.module;

		if (event.button() == 1) {
			hit.module.resetPosition();
			Fx.sound("ui.button.click", 0.25f, 0.8f);
			return true;
		}

		dragging = hit.module;
		grabX = (float) x - hit.x;
		grabY = (float) y - hit.y;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging != null) {
			dragTo(event.x(), event.y());
			return true;
		}

		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guideX = null;
			guideY = null;
			return true;
		}

		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (int i = boxes.size() - 1; i >= 0; i--) {
			Box box = boxes.get(i);

			if (box.contains(x, y)) {
				HudModule module = box.module;
				// Keep the element's center in place while resizing
				float centerX = box.x + box.w / 2f;
				float centerY = box.y + box.h / 2f;
				module.setScale(module.scale + (float) Math.signum(scrollY) * 0.05f);
				module.moveTo(centerX - module.scaledWidth() / 2f, centerY - module.scaledHeight() / 2f, this.width, this.height);
				selected = module;
				return true;
			}
		}

		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (selected != null) {
			int step = this.minecraft.hasShiftDown() ? 10 : 1;
			int dx = event.isLeft() ? -step : event.isRight() ? step : 0;
			int dy = event.isUp() ? -step : event.isDown() ? step : 0;

			if (dx != 0 || dy != 0) {
				selected.moveTo(selected.screenX(this.width) + dx, selected.screenY(this.height) + dy, this.width, this.height);
				return true;
			}
		}

		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Config.save();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		super.removed();
		Config.save();
	}
}
