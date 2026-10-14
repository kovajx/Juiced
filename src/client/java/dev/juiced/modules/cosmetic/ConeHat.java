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
import dev.juiced.render.Pose3;

public final class ConeHat extends CosmeticModule {
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.5, 1.0));
	private final SliderSetting opacity = add(SliderSetting.percent("opacity", "Deckkraft", 0.2, 1.0, 0.6));
	private final BoolSetting rim = add(new BoolSetting("rim", "Leuchtender Rand", true));

	public ConeHat() {
		super("cone_hat", "China-Hut", "Ein flacher, durchsichtiger China-Hut auf deinem Kopf, mit leuchtendem Rand, der sich langsam dreht. Er neigt sich mit deinem Kopf.");
	}

	@Override
	protected void draw(Geo geo) {
		float s = size.f();
		float radius = 0.6f * s;
		float peak = 0.26f * s;
		// Sits on the head and tilts with it. The slope just clears the corners of the outer skin layer;
		// a hat smaller than the head rests on top of it instead.
		float corner = HEAD_OUTER * 1.4142f;
		float lift = HEAD_TOP / 2 + HEAD_OUTER + 0.006f - peak * Math.max(0f, 1f - corner / radius);
		Pose3 hat = headPose().move(0, lift, 0);
		int segments = Theme.detail(32);
		float time = Clock.tf();
		float cx = geo.rx(hat.x);
		float cy = geo.ry(hat.y);
		float cz = geo.rz(hat.z);
		float tipX = cx + hat.ux * peak;
		float tipY = cy + hat.uy * peak;
		float tipZ = cz + hat.uz * peak;
		float alpha = opacity.f();
		int apex = Colors.argb(Math.min(1f, alpha + 0.2f), Colors.shade(color.rgb(time * 0.1f), 0.5f));
		float step = (float) (Math.PI * 2 / segments);
		float spin = time * 0.3f;
		// Rim points lie in the hat's own right/forward plane
		float c = (float) Math.cos(spin) * radius;
		float sn = (float) Math.sin(spin) * radius;
		float x0 = cx + hat.rx * c + hat.fx * sn;
		float y0 = cy + hat.ry * c + hat.fy * sn;
		float z0 = cz + hat.rz * c + hat.fz * sn;

		for (int i = 0; i < segments; i++) {
			float a1 = (i + 1) * step + spin;
			c = (float) Math.cos(a1) * radius;
			sn = (float) Math.sin(a1) * radius;
			float x1 = cx + hat.rx * c + hat.fx * sn;
			float y1 = cy + hat.ry * c + hat.fy * sn;
			float z1 = cz + hat.rz * c + hat.fz * sn;
			int edge = Colors.argb(alpha * 0.85f, color.rgb(i / (float) segments + time * 0.1f));
			geo.tri(tipX, tipY, tipZ, apex, x0, y0, z0, edge, x1, y1, z1, edge);
			x0 = x1;
			y0 = y1;
			z0 = z1;
		}

		if (rim.on()) {
			geo.glowRing(cx, cy, cz, hat.rx, hat.ry, hat.rz, hat.fx, hat.fy, hat.fz, radius, 0.025f * s, 0.08f * s, segments, spin, (float) (Math.PI * 2), t -> color.rgb(t + time * 0.1f), 0.95f);
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
