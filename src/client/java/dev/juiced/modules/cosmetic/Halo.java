package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class Halo extends CosmeticModule {
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final SliderSetting glow = add(SliderSetting.percent("glow", "Leuchten", 0.0, 1.5, 0.8));
	private final BoolSetting bob = add(new BoolSetting("bob", "Schweben", true));
	private final BoolSetting sparkles = add(new BoolSetting("sparkles", "Funkeln", true));

	public Halo() {
		super("halo", "Halo", "Ein leuchtender Ring über deinem Kopf, der sanft schwebt und funkelt.");
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf();
		float s = size.f();
		double y = py + height + 0.22 + (bob.on() ? 0.035 * Math.sin(time * 2.2) : 0);
		float radius = 0.28f * s;
		geo.groundRing(px, y, pz, radius, 0.04f * s, 0.09f * glow.f() * s, Theme.detail(48), time * 0.6f, (float) (Math.PI * 2), t -> color.rgb(t + time * 0.1f), 0.95f);

		if (sparkles.on()) {
			for (int k = 0; k < 3; k++) {
				double a = time * 1.2 + k * Math.PI * 2 / 3;
				float twinkle = 0.4f + 0.6f * Math.max(0f, (float) Math.sin(time * 4 + k * 2));
				geo.star(px + Math.cos(a) * radius, y, pz + Math.sin(a) * radius, 0.06f * s, time * 2 + k, color.rgb(k / 3f), twinkle);
			}
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float hy = feetY - 42 + (float) Math.sin(time * 2.2f) * 1.5f;
		Gui2D.ellipse(g, cx, hy, 11, 3, 28, 2, color.rgb(time * 0.1f), 0.95f, time * 0.6f, true);
		Gui2D.ellipse(g, cx, hy, 13, 4, 28, 1, color.rgb(time * 0.1f), 0.25f, time * 0.6f, false);
	}
}
