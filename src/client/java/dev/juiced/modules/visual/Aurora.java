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
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sky;

/**
 * Northern lights: a few glowing curtains far away in the night sky. They slowly ripple and sway,
 * and bright rays run along them. They move with you like the sky does, so you never reach them.
 */
public final class Aurora extends Module {
	private static final int CURTAINS = 3;
	private static final double DISTANCE = 88;

	/** Bottom, middle and top color of each palette. */
	private static final int[][] PALETTES = {
			{0x3DFFA0, 0x57E8B5, 0xB070FF},
			{0x40F0FF, 0x3D9BFF, 0x7A5CFF},
			{0xFF7AC8, 0xD07AFF, 0x7A6CFF}
	};

	/** Heights across a curtain (0 = bottom edge, 1 = top), its brightness there, and which palette color it has. */
	private static final float[] HEIGHTS = {0f, 0.12f, 0.45f, 1f};
	private static final float[] STRENGTH = {0f, 1f, 0.55f, 0f};
	private static final int[] TINT = {0, 0, 1, 2};

	private final ChoiceSetting colors = add(new ChoiceSetting("colors", "Farben", 0, "Grün & Violett", "Türkis & Blau", "Rosa & Lila"));
	private final SliderSetting brightness = add(SliderSetting.percent("brightness", "Helligkeit", 0.3, 1.5, 1.0));
	private final BoolSetting always = add(new BoolSetting("always", "Auch tagsüber", false));

	private float visible;
	/** When the menu last showed this; it fades in quicker there, so zapping past it isn't an empty sky. */
	private double probeSeen = -1;

	public Aurora() {
		super("aurora", "Polarlicht", Category.VISUALS, true,
				"Nachts ziehen leuchtende Vorhänge aus Grün und Violett über den Himmel, wiegen sich langsam und schimmern.");
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

		// Fades in and out over a few seconds instead of popping
		visible = Clock.approach(visible, target, Clock.t() - probeSeen < 0.3 ? 2.5f : 0.5f);

		if (visible < 0.01f) {
			return;
		}

		double cx = geo.x(player);
		double cy = geo.y(player) + 1.6;
		double cz = geo.z(player);
		float time = Clock.tf();
		int[] palette = PALETTES[colors.index()];
		int segments = Theme.detail(48);
		float alpha = 0.42f * visible * brightness.f();

		for (int c = 0; c < CURTAINS; c++) {
			curtain(geo, cx, cy, cz, c, segments, time, palette, alpha * (c == 1 ? 1f : 0.75f));
		}
	}

	/** One curtain: a wavy band that hangs down from the sky, bright at the bottom and fading to violet at the top. */
	private void curtain(Geo geo, double cx, double cy, double cz, int c, int segments, float time, int[] palette, float alpha) {
		// Curtains spread over the northern half of the sky (north is -Z)
		double center = -Math.PI / 2 + (c - 1) * 0.75 + Math.sin(time * 0.013 + c * 2.1) * 0.15;
		double arc = 1.3 + c * 0.2;
		double base = cy + 26 + c * 7;
		float height = 26f + c * 6f;
		double prevX = 0;
		double prevZ = 0;
		double prevBottom = 0;
		float prevIntensity = 0f;

		for (int i = 0; i <= segments; i++) {
			float u = i / (float) segments;
			double angle = center + (u - 0.5) * arc;
			double radius = DISTANCE
					+ 14 * Math.sin(u * 5.0 + time * 0.12 + c * 1.7)
					+ 6 * Math.sin(u * 13.0 - time * 0.21 + c);
			double x = cx + Math.cos(angle) * radius;
			double z = cz + Math.sin(angle) * radius;
			double bottom = base + 4 * Math.sin(u * 3.0 + time * 0.09 + c);
			// Bright rays drift along the curtain; the ends fade out
			float rays = 0.55f + 0.45f * (float) (Math.sin(u * 41.0 + time * 0.9 + c * 3) * Math.sin(u * 17.0 - time * 0.55));
			float intensity = (float) Math.sin(u * Math.PI) * (0.6f + 0.4f * rays);

			if (i > 0) {
				strip(geo, prevX, prevBottom, prevZ, prevIntensity, x, bottom, z, intensity, height, palette, alpha);
			}

			prevX = x;
			prevZ = z;
			prevBottom = bottom;
			prevIntensity = intensity;
		}
	}

	/** One vertical slice of a curtain, from transparent at the bottom edge up to transparent at the top. */
	private static void strip(Geo geo, double x0, double y0, double z0, float i0, double x1, double y1, double z1, float i1, float height, int[] palette, float alpha) {
		for (int k = 0; k < 3; k++) {
			double ya0 = y0 + HEIGHTS[k] * height;
			double yb0 = y0 + HEIGHTS[k + 1] * height;
			double ya1 = y1 + HEIGHTS[k] * height;
			double yb1 = y1 + HEIGHTS[k + 1] * height;
			int tintA = palette[TINT[k]];
			int tintB = palette[TINT[k + 1]];
			int a0 = Colors.argb(alpha * i0 * STRENGTH[k], tintA);
			int b0 = Colors.argb(alpha * i0 * STRENGTH[k + 1], tintB);
			int a1 = Colors.argb(alpha * i1 * STRENGTH[k], tintA);
			int b1 = Colors.argb(alpha * i1 * STRENGTH[k + 1], tintB);
			geo.quadWorld(x0, ya0, z0, a0, x1, ya1, z1, a1, x1, yb1, z1, b1, x0, yb0, z0, b0);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		Gui2D.fill(g, x + 6, y + 2, x + w - 6, y + h - 2, 0xFF0B1022);
		int[] palette = PALETTES[colors.index()];
		int columns = (w - 16) / 2;

		for (int i = 0; i < columns; i++) {
			float u = i / (float) columns;
			float wave = (float) Math.sin(u * 6 + time * 0.8f) * 5f + (float) Math.sin(u * 13 - time * 0.5f) * 2f;
			float rays = 0.55f + 0.45f * (float) (Math.sin(u * 30 + time * 1.5f) * Math.sin(u * 11 - time));
			float intensity = (float) Math.sin(u * Math.PI) * rays;
			int bx = x + 8 + i * 2;
			int bottom = Math.round(y + h * 0.62f + wave);
			int mid = bottom - 10;
			int top = bottom - 26;
			Gui2D.fill(g, bx, mid, bx + 2, bottom, Colors.argb(0.75f * intensity, palette[0]));
			Gui2D.fill(g, bx, top, bx + 2, mid, Colors.argb(0.4f * intensity, palette[2]));
		}

		for (int i = 0; i < 12; i++) {
			float sx = x + 10 + (i * 37 % (w - 20));
			float sy = y + 6 + (i * 13 % 14);
			Gui2D.dot(g, sx, sy, 1f, Colors.argb(0.4f + 0.3f * (float) Math.sin(time * 2 + i), 0xFFFFFF));
		}

		Gui2D.fill(g, x + 6, y + h - 10, x + w - 6, y + h - 2, 0xFF07090F);
	}
}
