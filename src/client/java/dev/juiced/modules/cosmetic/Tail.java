package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Pose3;

/** A fox, cat, dragon or devil tail that swings behind you with simple physics. */
public final class Tail extends CosmeticModule {
	private static final int SEGMENTS = 10;

	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, "Fuchs", "Katze", "Drache", "Teufel", "Kitsune", "Sternenschweif"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", Colors.ORANGE, true));
	private final ColorSetting tip = add(new ColorSetting("tip", "Spitze", Colors.WHITE, true));
	private final SliderSetting length = add(SliderSetting.percent("length", "Länge", 0.6, 1.6, 1.0));
	private final SliderSetting wag = add(SliderSetting.percent("wag", "Wedeln", 0.0, 2.0, 1.0));

	private final double[] x = new double[SEGMENTS + 1];
	private final double[] y = new double[SEGMENTS + 1];
	private final double[] z = new double[SEGMENTS + 1];
	private boolean placed;

	private final float[] px = new float[SEGMENTS + 1];
	private final float[] py = new float[SEGMENTS + 1];
	private final float[] pz = new float[SEGMENTS + 1];
	private final float[] hw = new float[SEGMENTS + 1];
	private final int[] colors = new int[SEGMENTS + 1];
	private final float[] savedX = new float[SEGMENTS + 1];
	private final float[] savedY = new float[SEGMENTS + 1];
	private final float[] savedZ = new float[SEGMENTS + 1];

	public Tail() {
		super("tail", "Schweif", "Ein Fuchs-, Katzen-, Drachen- oder Teufelsschweif, drei Kitsune-Schweife oder ein leuchtender Sternenschweif, der hinter dir mitschwingt.");
	}

	@Override
	protected void onDisable() {
		placed = false;
	}

	/** Resting direction of segment {@code t} (0 = root, 1 = tip) in body space: {right, up, forward}. */
	private float[] rest(float t) {
		return switch (kind.index()) {
			case 1 -> new float[] {0, -0.25f + 1.3f * t, -0.6f};
			case 2 -> new float[] {0, -0.55f + 0.25f * t, -1f};
			case 3 -> new float[] {0, -0.35f + 0.9f * t, -1f};
			case 5 -> new float[] {0, -0.45f + 1.15f * t, -1f};
			default -> new float[] {0, -0.6f + 0.65f * t, -1f};
		};
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf();
		float dt = Clock.dt();
		Pose3 body = bodyPose();
		Pose3 root = body.copy().move(0, crouching ? 0.55 : 0.72, crouching ? -0.3 : -0.16);
		float total = (kind.index() == 1 ? 0.8f : 0.7f) * length.f();
		float seg = total / SEGMENTS;

		if (!placed || Math.abs(root.x - x[0]) + Math.abs(root.z - z[0]) > 6) {
			for (int i = 0; i <= SEGMENTS; i++) {
				x[i] = root.x;
				y[i] = root.y - i * seg;
				z[i] = root.z;
			}

			placed = true;
		}

		x[0] = root.x;
		y[0] = root.y;
		z[0] = root.z;
		float stiffness = 1f - (float) Math.exp(-16f * dt);

		for (int i = 1; i <= SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			float[] dir = rest(t);
			float sway = (float) Math.sin(time * 3.5f * Math.max(0.2f, wag.f()) - i * 0.45f) * 0.55f * t * wag.f();
			double dx = body.rx * (dir[0] + sway) + body.ux * dir[1] + body.fx * dir[2];
			double dy = body.ry * (dir[0] + sway) + body.uy * dir[1] + body.fy * dir[2];
			double dz = body.rz * (dir[0] + sway) + body.uz * dir[1] + body.fz * dir[2];
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			double tx = x[i - 1] + dx / len * seg;
			double ty = y[i - 1] + dy / len * seg;
			double tz = z[i - 1] + dz / len * seg;
			x[i] += (tx - x[i]) * stiffness;
			y[i] += (ty - y[i]) * stiffness;
			z[i] += (tz - z[i]) * stiffness;

			// Keep each segment the same length
			double ex = x[i] - x[i - 1];
			double ey = y[i] - y[i - 1];
			double ez = z[i] - z[i - 1];
			double d = Math.sqrt(ex * ex + ey * ey + ez * ez);

			if (d > 1.0e-6) {
				x[i] = x[i - 1] + ex / d * seg;
				y[i] = y[i - 1] + ey / d * seg;
				z[i] = z[i - 1] + ez / d * seg;
			}
		}

		for (int i = 0; i <= SEGMENTS; i++) {
			px[i] = geo.rx(x[i]);
			py[i] = geo.ry(y[i]);
			pz[i] = geo.rz(z[i]);
		}

		int main = color.rgb();
		int end = tip.rgb();

		switch (kind.index()) {
			case 1 -> ribbon(geo, main, end, 0.028f, 0.03f, 0.8f, false);
			case 2 -> {
				ribbon(geo, main, Colors.shade(main, -0.35f), 0.07f, 0.012f, 1f, true);
				spikes(geo, Colors.shade(main, -0.3f));
			}
			case 3 -> {
				ribbon(geo, main, main, 0.018f, 0.018f, 1f, true);
				spade(geo, main);
			}
			case 4 -> kitsune(geo, main, end);
			case 5 -> starTail(geo, time);
			default -> {
				// Fluffy: wide soft outside, then a fuller core
				fox(geo, main, end, 2.2f, 0.25f);
				fox(geo, main, end, 1f, 1f);
			}
		}
	}

	/** Three fox tails fanned out behind you. The side ones are copies turned around the root. */
	private void kitsune(Geo geo, int main, int end) {
		System.arraycopy(px, 0, savedX, 0, SEGMENTS + 1);
		System.arraycopy(py, 0, savedY, 0, SEGMENTS + 1);
		System.arraycopy(pz, 0, savedZ, 0, SEGMENTS + 1);

		for (int copy = -1; copy <= 1; copy++) {
			double angle = Math.toRadians(copy * 38);
			float cos = (float) Math.cos(angle);
			float sin = (float) Math.sin(angle);

			for (int i = 0; i <= SEGMENTS; i++) {
				float dx = savedX[i] - savedX[0];
				float dz = savedZ[i] - savedZ[0];
				// Side tails sit a little lower
				float t = i / (float) SEGMENTS;
				px[i] = savedX[0] + dx * cos - dz * sin;
				pz[i] = savedZ[0] + dx * sin + dz * cos;
				py[i] = savedY[i] - (copy == 0 ? 0f : 0.05f * t);
			}

			fox(geo, main, end, 1.6f, 0.2f);
			fox(geo, main, end, 0.75f, 1f);
		}
	}

	/** A glowing ribbon with twinkling stars and sparkles drifting off its tip. */
	private void starTail(Geo geo, float time) {
		for (int i = 0; i <= SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			hw[i] = 0.09f * (1f - t * 0.75f);
			colors[i] = Colors.argb(0.2f * (1f - t * 0.5f), color.rgb(t + time * 0.1f));
		}

		geo.ribbon(px, py, pz, hw, colors, SEGMENTS + 1);

		for (int i = 0; i <= SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			hw[i] = 0.022f * (1f - t * 0.7f);
			colors[i] = Colors.argb(0.95f, Colors.mixRgb(color.rgb(t + time * 0.1f), 0xFFFFFF, 0.45f));
		}

		geo.ribbon(px, py, pz, hw, colors, SEGMENTS + 1);

		for (int i = 2; i <= SEGMENTS; i += 3) {
			float twinkle = 0.4f + 0.6f * Math.max(0f, (float) Math.sin(time * 3f + i));
			geo.star(x[i], y[i], z[i], 0.06f, time + i, tip.rgb(), twinkle);
		}

		for (int k = 0; k < 5; k++) {
			float age = (time * 0.8f + k * 0.2f) % 1f;
			geo.glowDot(x[SEGMENTS], y[SEGMENTS] - age * 0.25 + Math.sin(k * 2.3) * 0.05, z[SEGMENTS] + Math.cos(k * 1.7) * 0.05,
					0.025f, color.rgb(k / 5f), (1f - age) * 0.8f);
		}
	}

	private void ribbon(Geo geo, int from, int to, float rootWidth, float tipWidth, float alpha, boolean taper) {
		for (int i = 0; i <= SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			hw[i] = rootWidth + (tipWidth - rootWidth) * (taper ? t : t * t);
			colors[i] = Colors.argb(alpha, Colors.mixRgb(from, to, t < 0.75f ? 0f : (t - 0.75f) / 0.25f));
		}

		geo.ribbon(px, py, pz, hw, colors, SEGMENTS + 1);
	}

	private void fox(Geo geo, int main, int end, float widthScale, float alpha) {
		for (int i = 0; i <= SEGMENTS; i++) {
			float t = i / (float) SEGMENTS;
			// Thin at the root, bushy in the middle, round at the tip
			float bush = (float) Math.sin(Math.min(1f, t * 1.15f) * Math.PI) * 0.75f + 0.25f;
			hw[i] = 0.085f * bush * widthScale * (t > 0.92f ? (1f - t) / 0.08f * 0.6f + 0.4f : 1f);
			int rgb = t < 0.72f ? main : Colors.mixRgb(main, end, Math.min(1f, (t - 0.72f) / 0.12f));
			colors[i] = Colors.argb(alpha, rgb);
		}

		geo.ribbon(px, py, pz, hw, colors, SEGMENTS + 1);
	}

	private void spikes(Geo geo, int rgb) {
		int c = Colors.argb(1f, rgb);

		for (int i = 1; i < SEGMENTS - 1; i += 2) {
			float size = 0.06f * (1f - i / (float) SEGMENTS) + 0.015f;
			geo.triWorld(x[i], y[i], z[i], c, x[i + 1], y[i + 1], z[i + 1], c,
					(x[i] + x[i + 1]) / 2, (y[i] + y[i + 1]) / 2 + size, (z[i] + z[i + 1]) / 2, c);
		}
	}

	private void spade(Geo geo, int rgb) {
		double dx = x[SEGMENTS] - x[SEGMENTS - 1];
		double dy = y[SEGMENTS] - y[SEGMENTS - 1];
		double dz = z[SEGMENTS] - z[SEGMENTS - 1];
		double len = Math.max(1.0e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
		dx /= len;
		dy /= len;
		dz /= len;
		// Side direction: perpendicular to the tail and to the line of sight, so the spade faces the camera
		double vx = px[SEGMENTS];
		double vy = py[SEGMENTS];
		double vz = pz[SEGMENTS];
		double sx = dy * vz - dz * vy;
		double sy = dz * vx - dx * vz;
		double sz = dx * vy - dy * vx;
		double sl = Math.max(1.0e-6, Math.sqrt(sx * sx + sy * sy + sz * sz));
		sx = sx / sl * 0.06;
		sy = sy / sl * 0.06;
		sz = sz / sl * 0.06;
		int c = Colors.argb(1f, rgb);
		double bx = x[SEGMENTS];
		double by = y[SEGMENTS];
		double bz = z[SEGMENTS];
		double tipX = bx + dx * 0.12;
		double tipY = by + dy * 0.12;
		double tipZ = bz + dz * 0.12;
		geo.quadWorld(bx, by, bz, c, bx + dx * 0.04 + sx, by + dy * 0.04 + sy, bz + dz * 0.04 + sz, c,
				tipX, tipY, tipZ, c, bx + dx * 0.04 - sx, by + dy * 0.04 - sy, bz + dz * 0.04 - sz, c);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px0, int py0, int w, int h, float time) {
		float feetY = Previews.stage(g, px0, py0, w, h, px0 + w * 0.42f, 0);
		float rootX = px0 + w * 0.42f + 4;
		float rootY = feetY - 14;
		int main = color.rgb();
		int end = tip.rgb();

		for (int i = 0; i < 14; i++) {
			float t = i / 14f;
			float sway = (float) Math.sin(time * 3.5f - i * 0.45f) * 4 * t;
			float tx = rootX + t * 22;
			float ty = rootY + 6 * t - (kind.index() == 1 ? t * t * 18 : t * t * 8) + sway;
			float size = kind.index() == 0 ? 2 + (float) Math.sin(t * Math.PI) * 3 : 1.5f;
			int rgb = kind.index() == 0 && t > 0.72f ? end : main;
			Gui2D.dot(g, tx, ty, size, Colors.argb(1f, rgb));
		}
	}
}
