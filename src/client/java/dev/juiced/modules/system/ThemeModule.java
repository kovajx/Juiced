package dev.juiced.modules.system;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

public final class ThemeModule extends Module {
	public final ColorSetting accent = add(new ColorSetting("accent", "Hauptfarbe", 0, false));
	public final ColorSetting secondary = add(new ColorSetting("secondary", "Zweitfarbe", 3, false));
	public final BoolSetting gradient = add(new BoolSetting("gradient", "Farbverlauf", true));
	public final SliderSetting rainbowSpeed = SliderSetting.percent("rainbow_speed", "Regenbogen-Tempo", 0.1, 2.0, 0.5);
	public final SliderSetting hudBackground = SliderSetting.percent("hud_background", "HUD-Hintergrund", 0.0, 1.0, 0.55);
	public final BoolSetting hudAccent = new BoolSetting("hud_accent", "HUD-Farbstreifen", true);
	/** Which Shift menu opens: the Weltprobe (index 0) or the older card deck. */
	public final ChoiceSetting menuStyle = new ChoiceSetting("menu_style", "Menü", 0, "Weltprobe", "Deck");
	/** How often the Weltprobe menu has been opened; its little help bubble shows the first few times. Hidden. */
	public final SliderSetting menuOpens = new SliderSetting("menu_opens", "Menü geöffnet", 0, 99, 1, 0, v -> Integer.toString((int) Math.round(v)));

	public ThemeModule() {
		super("theme", "Theme", Category.SYSTEM, true,
				"Farben für alle Effekte und das Menü. Module mit Farbe \"Theme\" folgen diesen Farben.");
		add(rainbowSpeed);
		add(hudBackground);
		add(hudAccent);
		add(menuStyle);
		add(menuOpens).showIf(() -> false);
	}

	@Override
	public boolean alwaysOn() {
		return true;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int bars = 14;
		int barW = Math.max(2, (w - 24) / bars);

		for (int i = 0; i < bars; i++) {
			float t = i / (float) (bars - 1) + time * 0.15f;
			float wave = 0.5f + 0.5f * (float) Math.sin(time * 2.5f + i * 0.5f);
			int barH = Math.round(h * (0.25f + 0.45f * wave));
			int bx = x + 12 + i * barW;
			Gui2D.fill(g, bx, y + h - 8 - barH, bx + barW - 1, y + h - 8, Colors.argb(0.9f, Theme.accent(t)));
		}
	}
}
