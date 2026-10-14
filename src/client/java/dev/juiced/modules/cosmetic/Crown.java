package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class Crown extends CosmeticModule {
	private static final int POINTS = 10;

	private final ColorSetting color = add(new ColorSetting("color", "Farbe", Colors.GOLD, true));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.5, 1.0));
	private final BoolSetting gems = add(new BoolSetting("gems", "Edelsteine", true));
	private final ColorSetting gemColor = add(new ColorSetting("gem_color", "Steinfarbe", 4, true));

	public Crown() {
		super("crown", "Krone", "Eine goldene Krone mit funkelnden Edelsteinen, die sich mit deinem Kopf dreht.");
	}

	@Override
	protected void draw(Geo geo) {
		float s = size.f();
		float radius = 0.27f * s;
		float band = 0.07f * s;
		double y0 = py + height - 0.05;
		float start = (float) Math.toRadians(headYaw);
		int rgb = color.rgb();
		int dark = Colors.argb(0.97f, Colors.shade(rgb, -0.25f));
		int light = Colors.argb(0.97f, Colors.shade(rgb, 0.2f));
		int tip = Colors.argb(0.97f, Colors.shade(rgb, 0.45f));

		geo.wall(px, y0, pz, radius, band, POINTS * 2, start, t -> Colors.shade(rgb, -0.1f), 0.97f, 0.97f);

		float step = (float) (Math.PI * 2 / POINTS);
		float cx = geo.rx(px);
		float cz = geo.rz(pz);
		float top = geo.ry(y0 + band);
		float time = Clock.tf();

		for (int i = 0; i < POINTS; i++) {
			float a0 = start + i * step;
			float a1 = a0 + step;
			float am = (a0 + a1) / 2f;
			float spike = (i % 2 == 0 ? 0.14f : 0.08f) * s;
			float x0 = cx + (float) Math.cos(a0) * radius;
			float z0 = cz + (float) Math.sin(a0) * radius;
			float x1 = cx + (float) Math.cos(a1) * radius;
			float z1 = cz + (float) Math.sin(a1) * radius;
			float xm = cx + (float) Math.cos(am) * radius * 1.04f;
			float zm = cz + (float) Math.sin(am) * radius * 1.04f;
			geo.tri(x0, top, z0, dark, x1, top, z1, light, xm, top + spike, zm, tip);

			if (gems.on() && i % 2 == 0) {
				double gx = px + Math.cos(am) * radius * 1.05;
				double gz = pz + Math.sin(am) * radius * 1.05;
				float twinkle = 0.6f + 0.4f * (float) Math.sin(time * 3 + i);
				geo.glowDot(gx, y0 + band + spike, gz, 0.035f * s, gemColor.rgb(i / (float) POINTS), twinkle);
				geo.glowDot(px + Math.cos(am) * radius * 1.02, y0 + band * 0.5, pz + Math.sin(am) * radius * 1.02, 0.025f * s, gemColor.rgb(), 0.9f);
			}
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float base = feetY - 36;
		int rgb = color.rgb();
		int c = Colors.argb(1f, rgb);
		Gui2D.fill(g, Math.round(cx - 6), Math.round(base - 2), Math.round(cx + 6), Math.round(base + 1), c);

		for (int i = 0; i < 5; i++) {
			float sx = cx - 6 + i * 3;
			int spikeH = i % 2 == 0 ? 6 : 3;
			Gui2D.fill(g, Math.round(sx), Math.round(base - 2 - spikeH), Math.round(sx) + 1, Math.round(base - 2), c);

			if (i % 2 == 0 && gems.on()) {
				Gui2D.glowDot(g, sx + 0.5f, base - 3 - spikeH, 1.5f, gemColor.rgb(), 0.7f + 0.3f * (float) Math.sin(time * 3 + i));
			}
		}
	}
}
