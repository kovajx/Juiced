package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class Aura extends CosmeticModule {
	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Orbit", "Ring", "Spirale", "Magiekreis", "Glühwürmchen", "Blütenwirbel", "Musiknoten", "Seifenblasen"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting count = add(new SliderSetting("count", "Anzahl", 2, 8, 1, 4, v -> String.valueOf(Math.round(v))));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Tempo", 0.2, 2.0, 1.0));

	public Aura() {
		super("aura", "Aura", "Leuchtende Kugeln, ein Energie-Ring, eine Spirale, ein Magiekreis am Boden, Glühwürmchen, ein Wirbel aus Blütenblättern, Musiknoten oder Seifenblasen um dich herum.");
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf() * speed.f();
		int n = count.i();

		switch (style.index()) {
			case 1 -> {
				float radius = 0.75f + 0.05f * (float) Math.sin(time * 3);
				geo.groundRing(px, py + 0.04, pz, radius, 0.05f, 0.2f, Theme.detail(48), time, (float) (Math.PI * 2), t -> color.rgb(t + time * 0.1f), 0.9f);
				geo.wall(px, py + 0.04, pz, radius, 0.9f, Theme.detail(32), time, t -> color.rgb(t + time * 0.1f), 0.28f, 0f);
			}
			case 3 -> magicCircle(geo, time);
			case 4 -> fireflies(geo, time, n);
			case 5 -> petals(geo, time, n);
			case 6 -> notes(geo, time, n);
			case 7 -> bubbles(geo, time, n);
			case 2 -> {
				int dots = Theme.detail(28);

				for (int i = 0; i < dots; i++) {
					float u = (i / (float) dots + time * 0.4f) % 1f;
					double angle = u * Math.PI * 4 + time;
					float radius = 0.6f - u * 0.15f;
					float alpha = (float) Math.sin(u * Math.PI);
					geo.glowDot(px + Math.cos(angle) * radius, py + u * height * 1.1, pz + Math.sin(angle) * radius, 0.06f, color.rgb(u), alpha);
				}
			}
			default -> {
				for (int k = 0; k < n; k++) {
					for (int j = 0; j < 5; j++) {
						double angle = time * 2 + k * Math.PI * 2 / n - j * 0.12;
						double y = py + height * 0.5 + 0.15 * Math.sin(time * 2 + k - j * 0.12);
						float alpha = j == 0 ? 1f : 0.45f * (1f - j / 5f);
						float radius = j == 0 ? 0.09f : 0.05f;
						geo.glowDot(px + Math.cos(angle) * 0.75, y, pz + Math.sin(angle) * 0.75, radius, color.rgb(k / (float) n), alpha);
					}
				}
			}
		}
	}

	/** A glowing circle of runes on the ground that slowly turns, with a six-pointed star inside. */
	private void magicCircle(Geo geo, float time) {
		double y = py + 0.03;
		float pulse = 0.75f + 0.25f * (float) Math.sin(time * 1.5f);
		int segments = Theme.detail(56);
		geo.groundRing(px, y, pz, 1.05f, 0.03f, 0.08f, segments, time * 0.2f, (float) (Math.PI * 2), t -> color.rgb(t + time * 0.05f), 0.85f * pulse);
		geo.groundRing(px, y, pz, 0.8f, 0.02f, 0.05f, segments, -time * 0.3f, (float) (Math.PI * 2), t -> color.rgb(t + 0.5f), 0.75f * pulse);
		geo.groundRing(px, y, pz, 0.22f, 0.02f, 0.05f, Theme.detail(24), 0f, (float) (Math.PI * 2), t -> color.rgb(t), 0.7f * pulse);
		int line = Colors.argb(0.75f * pulse, color.rgb(0.25f));
		double spin = time * 0.25;

		// Hexagram: two triangles
		for (int tri = 0; tri < 2; tri++) {
			for (int k = 0; k < 3; k++) {
				double a0 = spin + tri * Math.PI / 3 + k * Math.PI * 2 / 3;
				double a1 = a0 + Math.PI * 2 / 3;
				geo.line(px + Math.cos(a0) * 0.8, y, pz + Math.sin(a0) * 0.8, px + Math.cos(a1) * 0.8, y, pz + Math.sin(a1) * 0.8, 0.012f, line, line);
			}
		}

		// Rune marks between the two outer rings, turning the other way
		for (int k = 0; k < 18; k++) {
			double a = -time * 0.12 + k * Math.PI * 2 / 18;
			double r0 = 0.86;
			double r1 = k % 3 == 0 ? 0.99 : 0.94;
			float flicker = 0.5f + 0.5f * (float) Math.sin(time * 3 + k * 1.3f);
			int mark = Colors.argb((0.4f + 0.5f * flicker) * pulse, color.rgb(k / 18f));
			geo.line(px + Math.cos(a) * r0, y, pz + Math.sin(a) * r0, px + Math.cos(a) * r1, y, pz + Math.sin(a) * r1, 0.01f, mark, mark);
		}

		// A few motes rising from the circle
		for (int k = 0; k < 6; k++) {
			float rise = (time * 0.35f + k / 6f) % 1f;
			double a = k * 1.7 + time * 0.1;
			geo.glowDot(px + Math.cos(a) * 0.95, y + rise * 1.4, pz + Math.sin(a) * 0.95, 0.04f, color.rgb(k / 6f), (1f - rise) * 0.8f);
		}
	}

	/** Fireflies drifting slowly around you at different heights, blinking softly. */
	private void fireflies(Geo geo, float time, int n) {
		int count = n * 3;

		for (int k = 0; k < count; k++) {
			float seed = k * 1.618f;
			double a = time * (0.25 + (k % 3) * 0.08) * (k % 2 == 0 ? 1 : -1) + seed * 2.4;
			double radius = 0.7 + (seed % 1f) * 0.6 + 0.1 * Math.sin(time * 0.7 + seed);
			double y = py + 0.3 + ((seed * 0.37f) % 1f) * height + 0.15 * Math.sin(time * 0.9 + seed * 3);
			float blink = 0.25f + 0.75f * Math.max(0f, (float) Math.sin(time * 1.4f + seed * 5));
			geo.glowDot(px + Math.cos(a) * radius, y, pz + Math.sin(a) * radius, 0.07f, color.rgb(k / (float) count), blink);
		}
	}

	/** Petals spiralling up around you and fading at the top. */
	private void petals(Geo geo, float time, int n) {
		int count = n * 4;

		for (int k = 0; k < count; k++) {
			float u = (k / (float) count + time * 0.12f) % 1f;
			double a = u * Math.PI * 4 + time * 0.6 + k;
			double radius = 0.55 + 0.12 * Math.sin(time + k);
			double cx = px + Math.cos(a) * radius;
			double cy = py + 0.1 + u * height * 1.15;
			double cz = pz + Math.sin(a) * radius;
			float alpha = (float) Math.sin(u * Math.PI) * 0.95f;
			int rgb = Colors.mixRgb(color.rgb(u), 0xFFFFFF, 0.45f);
			// Petal lies along the direction it travels and tumbles around it
			float tx = (float) -Math.sin(a);
			float tz = (float) Math.cos(a);
			float flip = time * 3f + k;
			float bx = (float) (Math.cos(a) * Math.cos(flip));
			float by = (float) Math.sin(flip);
			float bz = (float) (Math.sin(a) * Math.cos(flip));
			float length = 0.06f;
			float width = 0.035f;
			int base = Colors.argb(alpha, Colors.shade(rgb, -0.1f));
			int tip = Colors.argb(alpha, rgb);
			geo.quadWorld(cx - tx * length, cy, cz - tz * length, base,
					cx + bx * width - tx * length * 0.2, cy + by * width, cz + bz * width - tz * length * 0.2, base,
					cx + tx * length, cy, cz + tz * length, tip,
					cx - bx * width - tx * length * 0.2, cy - by * width, cz - bz * width - tz * length * 0.2, base);
		}
	}

	/** Music notes rising around you and drifting away, like a calm song playing. */
	private void notes(Geo geo, float time, int n) {
		int count = n * 2;

		for (int k = 0; k < count; k++) {
			float seed = k * 2.399f;
			float life = (time * 0.22f + (seed % 1f)) % 1f;
			double a = seed * 3 + time * 0.15;
			double radius = 0.55 + 0.25 * Math.sin(seed);
			double x = px + Math.cos(a) * radius + Math.sin(time * 1.2 + seed) * 0.08;
			double z = pz + Math.sin(a) * radius;
			double y = py + height * 0.55 + life * 1.3;
			float alpha = (float) Math.sin(life * Math.PI);
			HeadWear.drawNote(geo, x, y, z, 0.055f, color.rgb(k / (float) count + time * 0.05f), alpha, k % 3 == 0);
		}
	}

	/** Soap bubbles shimmering in rainbow colors, floating up and popping. */
	private void bubbles(Geo geo, float time, int n) {
		int count = n * 3;

		for (int k = 0; k < count; k++) {
			float seed = k * 1.7f + 0.3f;
			float life = (time * 0.16f + (seed * 0.37f) % 1f) % 1f;
			double a = seed * 2.3 + Math.sin(time * 0.5 + seed) * 0.4;
			double radius = 0.4 + (seed * 0.53f % 1f) * 0.6;
			double x = px + Math.cos(a) * radius + Math.sin(time * 1.1 + seed) * 0.12;
			double z = pz + Math.sin(a) * radius + Math.cos(time * 0.9 + seed) * 0.12;
			double y = py + 0.2 + life * 2.4;
			float size = (0.05f + (seed * 0.29f % 1f) * 0.06f) * (0.8f + 0.2f * life);

			if (life > 0.92f) {
				// Pop: a quick ring that spreads out
				float pop = (life - 0.92f) / 0.08f;
				geo.billboardRing(x, y, z, size * (1f + pop * 1.5f), 0.004f, 0.01f, 16, t -> 0xFFFFFF, 0.6f * (1f - pop));
				continue;
			}

			float alpha = Math.min(1f, life * 6f);
			float shimmer = time * 0.3f + seed;
			geo.glowDot(x, y, z, size * 1.1f, Colors.mixRgb(color.rgb(), 0xFFFFFF, 0.6f), 0.12f * alpha);
			geo.billboardRing(x, y, z, size, 0.006f, 0.012f, Theme.detail(20), t -> Colors.mixRgb(Theme.rainbow(t + shimmer), 0xFFFFFF, 0.35f), 0.55f * alpha);
			geo.glowDot(x - size * 0.3, y + size * 0.35, z, size * 0.18f, 0xFFFFFF, 0.8f * alpha);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		int n = count.i();

		if (style.index() == 3) {
			Gui2D.ellipse(g, cx, feetY, 24, 5, 40, 1.2f, color.rgb(), 0.9f, time * 0.2f, false);
			Gui2D.ellipse(g, cx, feetY, 17, 3.5f, 30, 1f, color.rgb(0.5f), 0.7f, -time * 0.3f, false);
			return;
		}

		for (int k = 0; k < n; k++) {
			double a = time * 2 * speed.f() + k * Math.PI * 2 / n;
			float depth = (float) Math.sin(a);
			Gui2D.glowDot(g, cx + (float) Math.cos(a) * 20, feetY - 18 + depth * 5, 2.5f, color.rgb(k / (float) n), 0.5f + 0.5f * depth);
		}
	}
}
