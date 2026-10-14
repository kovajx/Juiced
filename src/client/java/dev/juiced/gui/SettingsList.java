package dev.juiced.gui;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ButtonSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.Setting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

/**
 * Draws a list of settings (toggles, sliders, choices, colors, buttons) and makes them clickable.
 * Used on the back of the menu cards and in the wardrobe.
 *
 * <p>Drawing happens in the caller's local coordinates; {@link #originX}/{@link #originY} say where
 * local (0, 0) is on screen, so click areas end up in the right place.
 */
final class SettingsList {
	static final int TEXT = 0xFFEDEDF2;
	static final int DIM = 0xFF8A8A99;
	static final int TRACK = 0xFF33333F;
	static final int DOT = 9;
	static final int DOT_GAP = 3;

	@FunctionalInterface
	interface Sink {
		/** Registers a click area in screen coordinates. */
		void add(int x1, int y1, int x2, int y2, Runnable action);
	}

	private final Sink sink;
	private final Consumer<ColorSetting> openPicker;
	int originX;
	int originY;
	int mouseX;
	int mouseY;

	private SliderSetting dragging;
	private int dragX1;
	private int dragX2;

	SettingsList(Sink sink, Consumer<ColorSetting> openPicker) {
		this.sink = sink;
		this.openPicker = openPicker;
	}

	boolean isDragging() {
		return dragging != null;
	}

	void drag(double screenX) {
		if (dragging != null && dragX2 > dragX1) {
			dragging.setFraction((screenX - dragX1) / (double) (dragX2 - dragX1));
		}
	}

	void release() {
		dragging = null;
	}

	/**
	 * Draws the settings between {@code top} and {@code bottom}, scrolled by {@code scroll} pixels.
	 * Returns the full content height.
	 */
	int draw(GuiGraphicsExtractor g, List<Setting<?>> settings, int left, int width, int top, int bottom, int scroll, boolean interactive) {
		int y = top - scroll;
		int height = 0;

		for (Setting<?> setting : settings) {
			if (!setting.visible()) {
				continue;
			}

			int rowH = rowHeight(setting, width);

			if (y >= top - 1 && y + rowH <= bottom + 1) {
				drawSetting(g, setting, left, left + width, y, interactive);
			}

			y += rowH;
			height += rowH;
		}

		return height;
	}

	static int rowHeight(Setting<?> setting, int width) {
		if (setting instanceof BoolSetting) {
			return 18;
		}

		if (setting instanceof ButtonSetting) {
			return 20;
		}

		if (setting instanceof ColorSetting color) {
			int perRow = Math.max(1, (width + DOT_GAP) / (DOT + DOT_GAP));
			int rows = (colorOptions(color).length + 1 + perRow - 1) / perRow;
			return 13 + rows * (DOT + DOT_GAP) + 3;
		}

		return 27;
	}

	static int[] colorOptions(ColorSetting setting) {
		int palette = Colors.PALETTE.length;
		int[] options = new int[palette + (setting.allowTheme ? 2 : 1)];
		int i = 0;

		if (setting.allowTheme) {
			options[i++] = ColorSetting.THEME;
		}

		options[i++] = ColorSetting.RAINBOW;

		for (int p = 0; p < palette; p++) {
			options[i++] = p;
		}

		return options;
	}

	private boolean hover(int x1, int y1, int x2, int y2) {
		return mouseX >= originX + x1 && mouseX < originX + x2 && mouseY >= originY + y1 && mouseY < originY + y2;
	}

	private void hit(int x1, int y1, int x2, int y2, Runnable action) {
		sink.add(originX + x1, originY + y1, originX + x2, originY + y2, action);
	}

	private void drawSetting(GuiGraphicsExtractor g, Setting<?> setting, int left, int right, int y, boolean interactive) {
		int accent = Theme.accent();

		if (setting instanceof BoolSetting bool) {
			Gui2D.text(g, setting.label, left, y + 5, TEXT, false, 0.8f);
			int sw = 20;
			int sx = right - sw;
			int sy = y + 3;
			Gui2D.rounded(g, sx, sy, sx + sw, sy + 11, bool.on() ? Colors.argb(1f, accent) : TRACK);
			int knob = bool.on() ? sx + sw - 9 : sx + 2;
			Gui2D.rounded(g, knob, sy + 2, knob + 7, sy + 9, 0xFFFFFFFF);

			if (interactive) {
				hit(left - 3, y, right + 3, y + 17, () -> {
					bool.toggle();
					Fx.sound("ui.button.click", 0.25f, bool.on() ? 1.4f : 0.9f);
				});
			}
		} else if (setting instanceof SliderSetting slider) {
			String value = slider.display();
			Gui2D.text(g, setting.label, left, y + 3, TEXT, false, 0.8f);
			Gui2D.text(g, value, right - Gui2D.width(value, 0.8f), y + 3, DIM, false, 0.8f);
			int ty = y + 17;
			int fillX = left + Math.round((float) slider.fraction() * (right - left));
			Gui2D.fill(g, left, ty, right, ty + 2, TRACK);
			Gui2D.fill(g, left, ty, fillX, ty + 2, Colors.argb(1f, accent));
			int knob = dragging == slider ? 4 : 3;
			Gui2D.rounded(g, fillX - knob, ty - 3, fillX + knob, ty + 5, 0xFFFFFFFF);

			if (interactive) {
				int x1 = originX + left;
				int x2 = originX + right;
				hit(left - 4, ty - 6, right + 4, ty + 8, () -> {
					dragging = slider;
					dragX1 = x1;
					dragX2 = x2;
					drag(mouseX);
				});
			}
		} else if (setting instanceof ChoiceSetting choice) {
			Gui2D.text(g, setting.label, left, y + 3, TEXT, false, 0.8f);
			int boxY = y + 13;
			Gui2D.rounded(g, left, boxY, right, boxY + 12, 0xFF1A1A22);
			Gui2D.centered(g, choice.name(), (left + right) / 2f, boxY + 3, TEXT, false, 0.8f);
			int mid = (left + right) / 2;
			boolean hoverLeft = interactive && hover(left, boxY, mid, boxY + 12);
			boolean hoverRight = interactive && hover(mid, boxY, right, boxY + 12);
			Gui2D.text(g, "‹", left + 4, boxY + 2, hoverLeft ? Colors.argb(1f, accent) : DIM, false, 1f);
			Gui2D.text(g, "›", right - 8, boxY + 2, hoverRight ? Colors.argb(1f, accent) : DIM, false, 1f);

			if (interactive) {
				hit(left, boxY, mid, boxY + 12, () -> {
					choice.previous();
					Fx.sound("ui.button.click", 0.2f, 1.1f);
				});
				hit(mid, boxY, right, boxY + 12, () -> {
					choice.next();
					Fx.sound("ui.button.click", 0.2f, 1.2f);
				});
			}
		} else if (setting instanceof ColorSetting color) {
			Gui2D.text(g, setting.label, left, y + 3, TEXT, false, 0.8f);
			int[] options = colorOptions(color);
			int perRow = Math.max(1, (right - left + DOT_GAP) / (DOT + DOT_GAP));

			for (int i = 0; i <= options.length; i++) {
				int dx = left + (i % perRow) * (DOT + DOT_GAP);
				int dy = y + 13 + (i / perRow) * (DOT + DOT_GAP);

				if (i == options.length) {
					// Last swatch: free color from the color picker
					boolean custom = color.isCustom();

					if (custom) {
						Gui2D.fill(g, dx, dy, dx + DOT, dy + DOT, Colors.argb(1f, color.rgb()));
						Gui2D.outline(g, dx - 2, dy - 2, dx + DOT + 2, dy + DOT + 2, 0xFFFFFFFF);
					} else {
						Gui2D.outline(g, dx, dy, dx + DOT, dy + DOT, 0x90FFFFFF);
						Gui2D.centered(g, "+", dx + DOT / 2f + 0.5f, dy + 0.5f, TEXT, false, 0.85f);
					}

					if (interactive && openPicker != null) {
						hit(dx - 1, dy - 1, dx + DOT + 1, dy + DOT + 1, () -> {
							Fx.sound("ui.button.click", 0.2f, 1.3f);
							openPicker.accept(color);
						});
					}

					break;
				}

				int option = options[i];
				drawSwatch(g, dx, dy, option);

				if (color.get() == option) {
					Gui2D.outline(g, dx - 2, dy - 2, dx + DOT + 2, dy + DOT + 2, 0xFFFFFFFF);
				}

				if (interactive) {
					hit(dx - 1, dy - 1, dx + DOT + 1, dy + DOT + 1, () -> {
						color.set(option);
						Fx.sound("ui.button.click", 0.2f, 1.3f);
					});
				}
			}
		} else if (setting instanceof ButtonSetting button) {
			String label = button.button();
			int bw = Gui2D.width(label, 0.75f) + 10;
			Gui2D.text(g, trim(button.text(), right - left - bw - 6), left, y + 6, TEXT, false, 0.75f);
			boolean hovered = interactive && hover(right - bw, y + 3, right, y + 16);
			Gui2D.rounded(g, right - bw, y + 3, right, y + 16, hovered ? Colors.argb(1f, accent) : 0xFF22222C);
			Gui2D.centered(g, label, right - bw / 2f, y + 6, hovered ? 0xFF0B0B10 : TEXT, false, 0.75f);

			if (interactive) {
				hit(right - bw, y + 3, right, y + 16, () -> {
					button.press();
					Fx.sound("ui.button.click", 0.25f, 1.2f);
				});
			}
		}
	}

	static void drawSwatch(GuiGraphicsExtractor g, int x, int y, int option) {
		if (option == ColorSetting.RAINBOW) {
			int half = DOT / 2;
			float t = Clock.tf() * 0.3f;
			Gui2D.fill(g, x, y, x + half, y + half, Colors.argb(1f, Colors.hsv(t, 0.7f, 1f)));
			Gui2D.fill(g, x + half, y, x + DOT, y + half, Colors.argb(1f, Colors.hsv(t + 0.25f, 0.7f, 1f)));
			Gui2D.fill(g, x, y + half, x + half, y + DOT, Colors.argb(1f, Colors.hsv(t + 0.75f, 0.7f, 1f)));
			Gui2D.fill(g, x + half, y + half, x + DOT, y + DOT, Colors.argb(1f, Colors.hsv(t + 0.5f, 0.7f, 1f)));
		} else if (option == ColorSetting.THEME) {
			Gui2D.fill(g, x, y, x + DOT, y + DOT, Colors.argb(1f, Theme.accent()));
			Gui2D.centered(g, "T", x + DOT / 2f + 0.5f, y + 1.5f, 0xFF0B0B10, false, 0.7f);
		} else if (ColorSetting.isCustom(option)) {
			Gui2D.fill(g, x, y, x + DOT, y + DOT, Colors.argb(1f, option & 0xFFFFFF));
		} else {
			Gui2D.fill(g, x, y, x + DOT, y + DOT, Colors.argb(1f, Colors.PALETTE[option]));
		}
	}

	static String trim(String text, int maxWidth) {
		if (Gui2D.width(text, 0.75f) <= maxWidth) {
			return text;
		}

		String result = text;

		while (!result.isEmpty() && Gui2D.width(result + "…", 0.75f) > maxWidth) {
			result = result.substring(0, result.length() - 1);
		}

		return result + "…";
	}
}
