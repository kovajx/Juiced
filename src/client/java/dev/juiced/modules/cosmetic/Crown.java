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
import dev.juiced.render.Pose3;

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
		// Rests on the top of the head and tilts with it
		Pose3 crown = headPose().move(0, HEAD_TOP + 0.005, 0);
		int rgb = color.rgb();
		int side = Colors.argb(0.97f, Colors.shade(rgb, -0.1f));
		int dark = Colors.argb(0.97f, Colors.shade(rgb, -0.25f));
		int light = Colors.argb(0.97f, Colors.shade(rgb, 0.2f));
		int tip = Colors.argb(0.97f, Colors.shade(rgb, 0.45f));
		float cx = geo.rx(crown.x);
		float cy = geo.ry(crown.y);
		float cz = geo.rz(crown.z);
		// The band's height along the head's up axis
		float hx = crown.ux * band;
		float hy = crown.uy * band;
		float hz = crown.uz * band;
		float step = (float) (Math.PI * 2 / POINTS);
		float time = Clock.tf();
		float x0 = cx + crown.rx * radius;
		float y0 = cy + crown.ry * radius;
		float z0 = cz + crown.rz * radius;

		for (int i = 0; i < POINTS; i++) {
			float am = (i + 0.5f) * step;
			float a1 = (i + 1) * step;
			float cm = (float) Math.cos(am);
			float sm = (float) Math.sin(am);
			float c1 = (float) Math.cos(a1) * radius;
			float s1 = (float) Math.sin(a1) * radius;
			// Points on the band's bottom edge: middle of this spike and its far end
			float xm = cx + (crown.rx * cm + crown.fx * sm) * radius;
			float ym = cy + (crown.ry * cm + crown.fy * sm) * radius;
			float zm = cz + (crown.rz * cm + crown.fz * sm) * radius;
			float x1 = cx + crown.rx * c1 + crown.fx * s1;
			float y1 = cy + crown.ry * c1 + crown.fy * s1;
			float z1 = cz + crown.rz * c1 + crown.fz * s1;
			geo.quad(x0, y0, z0, side, xm, ym, zm, side, xm + hx, ym + hy, zm + hz, side, x0 + hx, y0 + hy, z0 + hz, side);
			geo.quad(xm, ym, zm, side, x1, y1, z1, side, x1 + hx, y1 + hy, z1 + hz, side, xm + hx, ym + hy, zm + hz, side);

			// Spike tips lean out a little
			float spike = (i % 2 == 0 ? 0.14f : 0.08f) * s;
			float tx = cx + (xm - cx) * 1.04f + crown.ux * (band + spike);
			float ty = cy + (ym - cy) * 1.04f + crown.uy * (band + spike);
			float tz = cz + (zm - cz) * 1.04f + crown.uz * (band + spike);
			geo.tri(x0 + hx, y0 + hy, z0 + hz, dark, x1 + hx, y1 + hy, z1 + hz, light, tx, ty, tz, tip);

			if (gems.on() && i % 2 == 0) {
				float twinkle = 0.6f + 0.4f * (float) Math.sin(time * 3 + i);
				double gr = radius * 1.05;
				geo.glowDot(crown.wx(cm * gr, band + spike, sm * gr), crown.wy(cm * gr, band + spike, sm * gr), crown.wz(cm * gr, band + spike, sm * gr),
						0.035f * s, gemColor.rgb(i / (float) POINTS), twinkle);
				double br = radius * 1.02;
				geo.glowDot(crown.wx(cm * br, band * 0.5, sm * br), crown.wy(cm * br, band * 0.5, sm * br), crown.wz(cm * br, band * 0.5, sm * br),
						0.025f * s, gemColor.rgb(), 0.9f);
			}

			x0 = x1;
			y0 = y1;
			z0 = z1;
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
