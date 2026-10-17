package dev.juiced.modules.visual;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sky;

/**
 * A few real constellations in the night sky. Their stars twinkle, and one after another
 * a constellation is slowly traced with fine lines, stays for a while and fades again.
 */
public final class Constellations extends Module {
	private static final double DISTANCE = 90;
	private static final float CYCLE = 26f;

	/**
	 * Star positions (x, y pairs, roughly -1..1) and the lines between them (pairs of star indices),
	 * plus where the constellation sits: compass direction and height in degrees (0 = north, 90 = east).
	 */
	private record Shape(float[] stars, int[] lines, float azimuth, float elevation, float size) {
	}

	private static final Shape[] SHAPES = {
			// Großer Wagen (Big Dipper)
			new Shape(new float[] {-0.6f, 0.15f, -0.55f, -0.2f, -0.15f, -0.3f, -0.1f, 0f, 0.2f, 0.1f, 0.45f, 0.18f, 0.75f, 0.05f},
					new int[] {0, 1, 1, 2, 2, 3, 3, 0, 3, 4, 4, 5, 5, 6}, 10f, 38f, 14f),
			// Kassiopeia (Cassiopeia)
			new Shape(new float[] {-0.6f, 0.15f, -0.3f, -0.15f, 0f, 0.1f, 0.3f, -0.2f, 0.6f, 0.2f},
					new int[] {0, 1, 1, 2, 2, 3, 3, 4}, 55f, 52f, 9f),
			// Orion
			new Shape(new float[] {-0.4f, 0.6f, 0.35f, 0.55f, -0.12f, 0f, 0f, 0.03f, 0.12f, 0.06f, -0.35f, -0.6f, 0.4f, -0.55f},
					new int[] {0, 1, 0, 2, 1, 4, 2, 3, 3, 4, 2, 5, 4, 6}, 175f, 32f, 13f),
			// Schwan (Cygnus)
			new Shape(new float[] {0f, 0.7f, 0f, 0.2f, 0f, -0.6f, -0.5f, 0.3f, 0.5f, 0.1f},
					new int[] {0, 1, 1, 2, 3, 1, 1, 4}, 265f, 56f, 13f)
	};

	// Reused every frame for the star positions of one constellation
	private static final double[] STAR_X = new double[8];
	private static final double[] STAR_Y = new double[8];
	private static final double[] STAR_Z = new double[8];

	private final BoolSetting lines = add(new BoolSetting("lines", "Linien nachzeichnen", true));
	private final SliderSetting brightness = add(SliderSetting.percent("brightness", "Helligkeit", 0.3, 1.5, 1.0));
	private final BoolSetting always = add(new BoolSetting("always", "Auch tagsüber", false));

	private float visible;
	/** When the menu last showed this; it fades in quicker there, so zapping past it isn't an empty sky. */
	private double probeSeen = -1;

	public Constellations() {
		super("constellations", "Sternbilder", Category.VISUALS, true,
				"Nachts funkeln Großer Wagen, Kassiopeia, Orion und Schwan am Himmel. Nacheinander werden sie mit feinen Linien nachgezeichnet.");
	}

	@Override
	protected void onDisable() {
		visible = 0f;
	}

	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		probeSeen = Clock.t();
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		String sky = SkyCheck.reason(minecraft);

		if (sky != null || always.on() || minecraft.player == null || minecraft.level == null) {
			return sky;
		}

		return Sky.night(minecraft.level, minecraft.player.blockPosition()) < 0.3f ? "Nur nachts" : null;
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return;
		}

		float target = 0f;

		if (Sky.overworld(level) && !level.isRaining()) {
			target = always.on() ? 1f : Sky.night(level, player.blockPosition());
		}

		visible = Clock.approach(visible, target, Clock.t() - probeSeen < 0.3 ? 2.5f : 0.5f);

		if (visible < 0.01f) {
			return;
		}

		double cx = geo.x(player);
		double cy = geo.y(player) + 1.6;
		double cz = geo.z(player);
		float time = Clock.tf();
		float alpha = visible * brightness.f();
		// One constellation after another gets its lines
		int traced = (int) (time / CYCLE) % SHAPES.length;
		float progress = (time % CYCLE) / CYCLE;

		for (int s = 0; s < SHAPES.length; s++) {
			drawShape(geo, SHAPES[s], s, cx, cy, cz, time, alpha, lines.on() && s == traced ? progress : -1f);
		}
	}

	/** {@code progress} runs 0..1 through the tracing cycle of this constellation, or is negative when it isn't traced. */
	private static void drawShape(Geo geo, Shape shape, int index, double cx, double cy, double cz, float time, float alpha, float progress) {
		double azimuth = Math.toRadians(shape.azimuth);
		double elevation = Math.toRadians(shape.elevation);
		// Direction to the constellation (north = -Z, east = +X), and right/up vectors across the sky
		double fx = Math.sin(azimuth) * Math.cos(elevation);
		double fy = Math.sin(elevation);
		double fz = -Math.cos(azimuth) * Math.cos(elevation);
		double rx = Math.cos(azimuth);
		double rz = Math.sin(azimuth);
		// up = right x forward
		double ux = -Math.sin(azimuth) * fy;
		double uy = Math.cos(elevation);
		double uz = Math.cos(azimuth) * fy;
		double scale = DISTANCE * Math.tan(Math.toRadians(shape.size));
		int count = shape.stars.length / 2;
		double[] sx = STAR_X;
		double[] sy = STAR_Y;
		double[] sz = STAR_Z;

		for (int i = 0; i < count; i++) {
			double a = shape.stars[i * 2] * scale;
			double b = shape.stars[i * 2 + 1] * scale;
			sx[i] = cx + fx * DISTANCE + rx * a + ux * b;
			sy[i] = cy + fy * DISTANCE + uy * b;
			sz[i] = cz + fz * DISTANCE + rz * a + uz * b;
		}

		float glow = progress < 0f ? 0f : (float) Math.sin(Math.min(1f, progress / 0.85f) * Math.PI);

		if (progress >= 0f) {
			// Draw the lines one after another during the first quarter, fade all of them at the end
			int lineCount = shape.lines.length / 2;
			float drawn = Math.min(1f, progress / 0.25f) * lineCount;
			float fade = progress > 0.75f ? Math.max(0f, (0.9f - progress) / 0.15f) : 1f;
			int color = Colors.argb(0.5f * alpha * fade, 0xBFD8FF);

			for (int l = 0; l < lineCount && l < drawn; l++) {
				int a = shape.lines[l * 2];
				int b = shape.lines[l * 2 + 1];
				float part = Math.min(1f, drawn - l);
				double ex = sx[a] + (sx[b] - sx[a]) * part;
				double ey = sy[a] + (sy[b] - sy[a]) * part;
				double ez = sz[a] + (sz[b] - sz[a]) * part;
				geo.line(sx[a], sy[a], sz[a], ex, ey, ez, 0.07f, color, color);
			}
		}

		for (int i = 0; i < count; i++) {
			float twinkle = 0.7f + 0.3f * (float) Math.sin(time * (1.3f + i * 0.21f) + index * 2.7f + i * 1.9f);
			float shine = Math.min(1f, alpha * twinkle * (0.75f + 0.25f * glow));
			geo.star(sx[i], sy[i], sz[i], 0.55f + 0.2f * glow, time * 0.05f + i, 0xE8F0FF, shine);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Gui2D.fill(g, x + 6, y + 2, x + w - 6, y + h - 2, 0xFF0A0E1E);
		Shape shape = SHAPES[0];
		int count = shape.stars.length / 2;
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		float scale = Math.min(w, h) * 0.55f;
		float progress = (time % 8f) / 8f;
		float drawn = Math.min(1f, progress / 0.4f) * (shape.lines.length / 2f);
		float fade = progress > 0.8f ? (1f - progress) / 0.2f : 1f;

		for (int l = 0; l < shape.lines.length / 2 && lines.on(); l++) {
			if (l >= drawn) {
				break;
			}

			float ax = cx + shape.stars[shape.lines[l * 2] * 2] * scale;
			float ay = cy - shape.stars[shape.lines[l * 2] * 2 + 1] * scale;
			float bx = cx + shape.stars[shape.lines[l * 2 + 1] * 2] * scale;
			float by = cy - shape.stars[shape.lines[l * 2 + 1] * 2 + 1] * scale;
			float part = Math.min(1f, drawn - l);
			int steps = 12;

			for (int k = 0; k <= steps * part; k++) {
				float t = k / (float) steps;
				Gui2D.dot(g, ax + (bx - ax) * t, ay + (by - ay) * t, 0.8f, Colors.argb(0.45f * fade, 0xBFD8FF));
			}
		}

		for (int i = 0; i < count; i++) {
			float twinkle = 0.6f + 0.4f * (float) Math.sin(time * 2f + i * 1.7f);
			Gui2D.glowDot(g, cx + shape.stars[i * 2] * scale, cy - shape.stars[i * 2 + 1] * scale, 2.2f, 0xE8F0FF, twinkle);
		}
	}
}
