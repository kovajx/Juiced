package dev.juiced.modules.system;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sparks;

public final class PerformanceModule extends Module {
	public final ChoiceSetting quality = add(new ChoiceSetting("quality", "Qualität", 2, "Niedrig", "Mittel", "Hoch"));
	public final SliderSetting maxParticles = add(new SliderSetting("max_particles", "Max. eigene Partikel", 50, 600, 10, 250, v -> String.valueOf(Math.round(v))));
	public final SliderSetting range = add(new SliderSetting("range", "Effekt-Reichweite", 16, 96, 4, 48, v -> Math.round(v) + " Blöcke"));

	public PerformanceModule() {
		super("performance", "Leistung", Category.SYSTEM, true,
				"Regelt, wie aufwendig Effekte gezeichnet werden. Niedrig spart auf schwachen PCs am meisten.");
	}

	@Override
	public boolean alwaysOn() {
		return true;
	}

	/** It draws nothing itself; it only changes how much the other effects draw. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return "Wirkt auf alle Effekte";
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int bars = 3;
		int barW = 14;
		int total = bars * barW + (bars - 1) * 8;
		int startX = x + (w - total) / 2;

		for (int i = 0; i < bars; i++) {
			boolean lit = i <= quality.index();
			int barH = Math.round(h * (0.2f + 0.2f * i));
			int bx = startX + i * (barW + 8);
			int color = lit ? Colors.argb(0.95f, Theme.accent(i * 0.2f)) : 0x33FFFFFF;
			Gui2D.rounded(g, bx, y + h - 10 - barH, bx + barW, y + h - 10, color);
		}

		Gui2D.centered(g, Sparks.count() + " Partikel aktiv", x + w / 2f, y + 6, 0x99FFFFFF, false, 0.75f);
	}
}
