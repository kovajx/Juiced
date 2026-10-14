package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class ConeHat extends CosmeticModule {
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.5, 1.0));
	private final SliderSetting opacity = add(SliderSetting.percent("opacity", "Deckkraft", 0.2, 1.0, 0.6));
	private final BoolSetting rim = add(new BoolSetting("rim", "Leuchtender Rand", true));

	public ConeHat() {
		super("cone_hat", "China-Hut", "Ein flacher, durchsichtiger China-Hut über deinem Kopf, mit leuchtendem Rand, der sich langsam dreht.");
	}

	@Override
	protected void draw(Geo geo) {
		float s = size.f();
		float radius = 0.6f * s;
		float peak = 0.26f * s;
		double y0 = py + height - 0.04;
		int segments = Theme.detail(32);
		float time = Clock.tf();
		float cx = geo.rx(px);
		float cy = geo.ry(y0);
		float cz = geo.rz(pz);
		float alpha = opacity.f();
		int apex = Colors.argb(Math.min(1f, alpha + 0.2f), Colors.shade(color.rgb(time * 0.1f), 0.5f));
		float step = (float) (Math.PI * 2 / segments);

		for (int i = 0; i < segments; i++) {
			float a0 = i * step + time * 0.3f;
			float a1 = a0 + step;
			int edge = Colors.argb(alpha * 0.85f, color.rgb(i / (float) segments + time * 0.1f));
			geo.tri(cx, cy + peak, cz, apex,
					cx + (float) Math.cos(a0) * radius, cy, cz + (float) Math.sin(a0) * radius, edge,
					cx + (float) Math.cos(a1) * radius, cy, cz + (float) Math.sin(a1) * radius, edge);
		}

		if (rim.on()) {
			geo.groundRing(px, y0, pz, radius, 0.025f * s, 0.08f * s, segments, time * 0.3f, (float) (Math.PI * 2), t -> color.rgb(t + time * 0.1f), 0.95f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float base = feetY - 35;
		int rgb = color.rgb(time * 0.1f);

		for (int row = 0; row < 8; row++) {
			float f = row / 7f;
			float half = 3 + f * 13;
			Gui2D.fill(g, Math.round(cx - half), Math.round(base - 8 + row), Math.round(cx + half), Math.round(base - 7 + row), Colors.argb(0.35f + 0.5f * f, rgb));
		}

		Gui2D.ellipse(g, cx, base, 16, 2, 24, 1.5f, rgb, 0.9f, time, false);
	}
}
