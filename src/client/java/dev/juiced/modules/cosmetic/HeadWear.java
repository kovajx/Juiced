package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Pose3;
import dev.juiced.render.Shapes;

/** Things for your head: a flower crown, a top hat, a wizard hat, a floating crystal or a little rain cloud. */
public final class HeadWear extends CosmeticModule {
	private static final int[] FLOWERS = {0xF7A8C8, 0xFFFFFF, 0xC9B3F2, 0xFFD27A, 0xFFB4A2};

	public static final String[] KINDS = {"Blumenkranz", "Zylinder", "Zauberhut", "Kristall", "Regenwolke", "Strohhut", "Kopfhörer", "Mondsichel"};

	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, KINDS));
	private final BoolSetting ownColor = add(new BoolSetting("own_color", "Eigene Farbe statt Standard", false));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final ColorSetting accent = add(new ColorSetting("accent", "Zweitfarbe", Colors.GOLD, true));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.7, 1.4, 1.0));

	public HeadWear() {
		super("headwear", "Kopfschmuck", "Ein Blumenkranz, ein Zylinder, ein Zauberhut, ein Strohhut, Kopfhörer, ein schwebender Kristall, eine Mondsichel oder eine kleine Regenwolke über deinem Kopf.");
	}

	private int main(int standard) {
		return ownColor.on() ? color.rgb(Clock.tf() * 0.1f) : standard;
	}

	@Override
	protected void draw(Geo geo) {
		float s = size.f();
		float time = Clock.tf();

		switch (kind.index()) {
			case 1 -> topHat(geo, s);
			case 2 -> wizardHat(geo, s, time);
			case 3 -> crystal(geo, s, time);
			case 4 -> cloud(geo, s, time);
			case 5 -> strawHat(geo, s, time);
			case 6 -> headphones(geo, s, time);
			case 7 -> moon(geo, s, time);
			default -> flowerCrown(geo, s, time);
		}
	}

	private void flowerCrown(Geo geo, float s, float time) {
		Pose3 head = headPose();
		int flowers = 9;
		geo.beginModel();

		for (int k = 0; k < flowers; k++) {
			double a = k * Math.PI * 2 / flowers;
			float yaw = (float) Math.toDegrees(a);
			// Flowers sit around the top of the head, facing outwards, close to its sides at any size
			double r = (0.235 + 0.035 * s) * squareRadius(a);
			Pose3 flower = head.copy().move(Math.sin(a) * r, HEAD_TOP - 0.06, Math.cos(a) * r).yaw(yaw).pitch(-20);
			int petal = ownColor.on() ? Colors.mixRgb(color.rgb(k / (float) flowers), 0xFFFFFF, (k % 3) * 0.2f) : FLOWERS[k % FLOWERS.length];

			for (int p = 0; p < 5; p++) {
				double b = p * Math.PI * 2 / 5 + k;
				geo.box(flower.copy().move(Math.cos(b) * 0.032 * s, Math.sin(b) * 0.032 * s, 0.012 * s), 0.02f * s, 0.02f * s, 0.008f * s, petal, 1f);
			}

			geo.box(flower.copy().move(0, 0, 0.018 * s), 0.014f * s, 0.014f * s, 0.008f * s, 0xFFC94A, 1f);
			// Leaf between this flower and the next
			double leafAngle = a + Math.PI / flowers;
			double leafR = (0.23 + 0.035 * s) * squareRadius(leafAngle);
			Pose3 leaf = head.copy().move(Math.sin(leafAngle) * leafR, HEAD_TOP - 0.075, Math.cos(leafAngle) * leafR).yaw((float) Math.toDegrees(leafAngle)).roll(35);
			geo.box(leaf, 0.035f * s, 0.012f * s, 0.006f * s, 0x5FA85A, 1f);
		}

		geo.endModel();
	}

	/**
	 * Distance to a rounded square of size 1 in direction {@code a}, relative to a circle. Things placed
	 * around the head with it go around its corners instead of through them.
	 */
	private static double squareRadius(double a) {
		return 1 / Math.pow(Math.pow(Math.abs(Math.sin(a)), 8) + Math.pow(Math.abs(Math.cos(a)), 8), 1 / 8.0);
	}

	/**
	 * Height above the neck for the middle of a hat brim with this half thickness. The brim's top clears
	 * the hair on the skin's outer layer, so the hair never pokes through it, and its bottom stays on the
	 * head, so the hat doesn't float on skins without hair there.
	 */
	private static float brimHeight(float halfThickness) {
		return Math.max(HEAD_TOP + halfThickness, HAIR_TOP + 0.003f - halfThickness);
	}

	private void topHat(Geo geo, float s) {
		int body = main(0x24242C);
		int band = accent.rgb();
		Pose3 hat = headPose().move(0, brimHeight(0.012f * s), 0);
		geo.beginModel();
		geo.box(hat, 0.36f * s, 0.012f * s, 0.36f * s, body, 1f);
		geo.box(hat.copy().move(0, 0.03 * s, 0), 0.215f * s, 0.022f * s, 0.215f * s, band, 1f);
		geo.box(hat.copy().move(0, 0.17 * s, 0), 0.21f * s, 0.12f * s, 0.21f * s, body, 1f);
		geo.box(hat.copy().move(0, 0.292 * s, 0), 0.215f * s, 0.006f * s, 0.215f * s, Colors.shade(body, 0.12f), 1f);
		geo.endModel();
	}

	private void wizardHat(Geo geo, float s, float time) {
		int body = main(0x5B3FA8);
		int trim = accent.rgb();
		Pose3 hat = headPose().move(0, brimHeight(0.01f * s), 0);
		geo.beginModel();
		geo.box(hat, 0.4f * s, 0.01f * s, 0.4f * s, body, 1f);
		geo.box(hat.copy().move(0, 0.03 * s, 0), 0.25f * s, 0.022f * s, 0.25f * s, trim, 1f);
		// Cone from shrinking boxes; the tip flops over to the back
		Pose3 cone = hat.copy().move(0, 0.05 * s, 0);

		for (int i = 0; i < 8; i++) {
			float half = (0.24f - i * 0.028f) * s;
			geo.box(cone.copy().move(0, 0.035 * s, 0), half, 0.036f * s, half, Colors.shade(body, -0.04f * i), 1f);
			cone.move(0, 0.07 * s, 0);

			if (i >= 4) {
				cone.pitch(-16 - (float) Math.sin(time * 1.5f) * 3);
			}
		}

		geo.endModel();
		double sparkle = 0.5 + 0.5 * Math.sin(time * 3);
		geo.star(cone.x, cone.y, cone.z, 0.07f * s, time, trim, 0.6f + 0.4f * (float) sparkle);

		for (int k = 0; k < 3; k++) {
			double a = time * 0.8 + k * Math.PI * 2 / 3;
			Pose3 star = hat.copy().move(Math.cos(a) * 0.22 * s, 0.18 * s + k * 0.08 * s, Math.sin(a) * 0.22 * s);
			geo.star(star.x, star.y, star.z, 0.035f * s, time * 2 + k, trim, 0.5f + 0.5f * (float) Math.sin(time * 4 + k * 2));
		}
	}

	private void crystal(Geo geo, float s, float time) {
		int rgb = main(0x6CF26A);
		Pose3 above = aboveHead();
		double y = above.y + 0.28 + Math.sin(time * 1.8) * 0.05;
		geo.glowDot(above.x, y, above.z, 0.32f * s, rgb, 0.3f);
		Shapes.gem(geo, above.x, y, above.z, 0.085f * s, 0.16f * s, time * 1.2f, rgb, 0.85f);
	}

	private void cloud(Geo geo, float s, float time) {
		int rgb = main(0xEEF1F6);
		// Floats level over the head and follows it, so the rain keeps falling on your head
		Pose3 above = aboveHead();
		double baseY = above.y + 0.4;
		Pose3 cloud = Pose3.at(above.x, baseY + Math.sin(time * 1.2) * 0.03, above.z, headYaw);
		geo.beginModel();
		geo.box(cloud, 0.24f * s, 0.07f * s, 0.15f * s, rgb, 0.95f);
		geo.box(cloud.copy().move(-0.1 * s, 0.07 * s, 0), 0.1f * s, 0.07f * s, 0.11f * s, rgb, 0.95f);
		geo.box(cloud.copy().move(0.07 * s, 0.09 * s, 0.01 * s), 0.12f * s, 0.08f * s, 0.12f * s, rgb, 0.95f);
		geo.box(cloud.copy().move(0.2 * s, 0.02 * s, 0), 0.08f * s, 0.06f * s, 0.1f * s, Colors.shade(rgb, -0.06f), 0.95f);
		geo.box(cloud.copy().move(-0.22 * s, 0.0, 0.0), 0.07f * s, 0.05f * s, 0.09f * s, Colors.shade(rgb, -0.06f), 0.95f);
		geo.endModel();

		// Gentle rain that stops at your head
		int drop = Colors.argb(0.75f, accent.get() == Colors.GOLD ? 0x8FC8FF : accent.rgb());
		double headTop = above.y - 0.03;

		for (int k = 0; k < 9; k++) {
			float fall = (time * 1.4f + k * 0.37f) % 1f;
			double dx = ((k * 37) % 9 - 4) * 0.045 * s;
			double dz = ((k * 53) % 5 - 2) * 0.05 * s;
			double top = baseY - 0.06 * s;
			double yDrop = top - fall * (top - headTop);
			double x = above.x + cloud.rx * dx + cloud.fx * dz;
			double z = above.z + cloud.rz * dx + cloud.fz * dz;
			geo.line(x, yDrop, z, x, yDrop - 0.06, z, 0.006f, drop, Colors.fade(drop, 0.3f));
		}
	}

	/** A wide straw hat with a ribbon, like on a lazy summer afternoon. */
	private void strawHat(Geo geo, float s, float time) {
		int straw = main(0xE8C77A);
		int ribbon = accent.get() == Colors.GOLD ? 0xC8423A : accent.rgb();
		Pose3 head = headPose().move(0, brimHeight(0.008f * s), 0);
		// Brim: a flat round disc. Turned so the polygon lies flat
		int points = 16;
		float[] xs = new float[points];
		float[] ys = new float[points];

		for (int i = 0; i < points; i++) {
			double a = -i * Math.PI * 2 / points;
			xs[i] = (float) Math.cos(a) * 0.43f * s;
			ys[i] = (float) Math.sin(a) * 0.43f * s;
		}

		geo.prism(head.copy().pitch(-90), xs, ys, 0.008f * s, straw, 1f);
		geo.beginModel();
		geo.box(head.copy().move(0, 0.07 * s, 0), 0.2f * s, 0.07f * s, 0.2f * s, Colors.shade(straw, 0.05f), 1f);
		geo.box(head.copy().move(0, 0.025 * s, 0), 0.205f * s, 0.022f * s, 0.205f * s, ribbon, 1f);
		geo.box(head.copy().move(0, 0.145 * s, 0), 0.17f * s, 0.008f * s, 0.17f * s, Colors.shade(straw, 0.12f), 1f);
		// Ribbon ends fluttering at the back, just behind the head so they don't hang inside it
		float flutter = (float) Math.sin(time * 3f) * 8f;
		double back = -Math.max(0.215 * s, HEAD_OUTER + 0.008);
		geo.box(head.copy().move(0.05 * s, 0.0, back).roll(15 + flutter).move(0, -0.05 * s, 0), 0.018f * s, 0.05f * s, 0.004f * s, ribbon, 1f);
		geo.box(head.copy().move(-0.04 * s, 0.0, back).roll(-10 - flutter).move(0, -0.045 * s, 0), 0.018f * s, 0.045f * s, 0.004f * s, ribbon, 1f);
		geo.endModel();
	}

	/** Big comfy headphones; little music notes drift up from them now and then. */
	private void headphones(Geo geo, float s, float time) {
		int body = main(0x2A2A30);
		int glow = accent.rgb(time * 0.1f);
		Pose3 head = headPose().move(0, 0.24, 0);
		// Cups rest on the sides of the head at any size; the band clears the outer skin layer
		float cupX = 0.235f + 0.035f * s;
		float wide = Math.max(cupX, HEAD_OUTER + 0.02f * s);
		float tall = HEAD_OUTER - 0.005f + 0.02f * s;
		geo.beginModel();

		// Band over the top of the head, from ear to ear. It follows a rounded square, so it hugs
		// the blocky head instead of cutting through its top corners.
		int pieces = 16;
		float x0 = wide;
		float y0 = 0f;

		for (int i = 1; i <= pieces; i++) {
			double a = Math.PI * i / pieces;
			float c = (float) Math.cos(a);
			float sn = (float) Math.sin(a);
			float x1 = wide * Math.signum(c) * (float) Math.pow(Math.abs(c), 1 / 6.0);
			float y1 = tall * (float) Math.pow(sn, 1 / 6.0);
			float dx = x1 - x0;
			float dy = y1 - y0;
			float half = (float) Math.sqrt(dx * dx + dy * dy) / 2f + 0.004f;
			Pose3 band = head.copy().move((x0 + x1) / 2f, (y0 + y1) / 2f, -0.01 * s).roll((float) Math.toDegrees(Math.atan2(dx, dy)));
			boolean top = i == pieces / 2 || i == pieces / 2 + 1;
			geo.box(band, 0.016f * s, half, 0.03f * s, top ? Colors.shade(body, 0.15f) : body, 1f);
			x0 = x1;
			y0 = y1;
		}

		for (int side = -1; side <= 1; side += 2) {
			Pose3 cup = head.copy().move(side * cupX, -0.01 * s, -0.01 * s);
			geo.box(cup, 0.035f * s, 0.085f * s, 0.075f * s, body, 1f);
			geo.box(cup.copy().move(-side * 0.035 * s, 0, 0), 0.012f * s, 0.075f * s, 0.065f * s, 0x4A4A55, 1f);
			geo.box(cup.copy().move(side * 0.037 * s, 0, 0), 0.004f * s, 0.05f * s, 0.045f * s, glow, 1f);
		}

		geo.endModel();

		// Music notes floating up from the ear cups
		for (int k = 0; k < 3; k++) {
			float life = (time * 0.35f + k / 3f) % 1f;
			int side = k % 2 == 0 ? 1 : -1;
			Pose3 note = head.copy().move(side * (0.34 + life * 0.15) * s, (0.05 + life * 0.55) * s, Math.sin(time + k) * 0.05);
			float alpha = (float) Math.sin(life * Math.PI);
			drawNote(geo, note.x, note.y, note.z, 0.05f * s, glow, alpha, k == 1);
		}
	}

	/** A music note: round head, stem and a small flag (or two beamed notes). */
	public static void drawNote(Geo geo, double x, double y, double z, float size, int rgb, float alpha, boolean beamed) {
		if (alpha <= 0.02f) {
			return;
		}

		int c = Colors.argb(alpha, rgb);
		geo.glowDot(x, y, z, size * 0.9f, rgb, alpha);
		geo.line(x + size * 0.35, y, z, x + size * 0.35, y + size * 2.2, z, size * 0.12f, c, c);

		if (beamed) {
			geo.glowDot(x + size * 1.4, y + size * 0.3, z, size * 0.9f, rgb, alpha);
			geo.line(x + size * 1.75, y + size * 0.3, z, x + size * 1.75, y + size * 2.5, z, size * 0.12f, c, c);
			geo.line(x + size * 0.35, y + size * 2.2, z, x + size * 1.75, y + size * 2.5, z, size * 0.2f, c, c);
		} else {
			geo.line(x + size * 0.35, y + size * 2.2, z, x + size * 1.1, y + size * 1.6, z, size * 0.14f, c, c);
		}
	}

	/** A pale crescent moon floating above your head with a few tiny stars. */
	private void moon(Geo geo, float s, float time) {
		int rgb = main(0xFFF2B0);
		Pose3 above = aboveHead();
		double y = above.y + 0.32 + Math.sin(time * 1.3) * 0.04;
		geo.glowDot(above.x, y, above.z, 0.35f * s, rgb, 0.22f);
		float[] basis = geo.billboardBasis(above.x, y, above.z);
		float cx = geo.rx(above.x);
		float cy = geo.ry(y);
		float cz = geo.rz(above.z);
		float radius = 0.13f * s;
		float thickness = 0.075f * s;
		float tilt = (float) Math.toRadians(25 + Math.sin(time * 0.7) * 6);
		int steps = 16;
		int light = Colors.argb(0.95f, rgb);
		int edge = Colors.argb(0.95f, Colors.shade(rgb, -0.15f));
		float prevOX = 0;
		float prevOY = 0;
		float prevIX = 0;
		float prevIY = 0;

		for (int i = 0; i <= steps; i++) {
			// Crescent opening to the right: thick on the left, pointed at both ends
			float t = i / (float) steps;
			double a = Math.toRadians(50 + t * 260) + tilt;
			float w = thickness * (float) Math.sin(t * Math.PI);
			float ox = (float) Math.cos(a) * radius;
			float oy = (float) Math.sin(a) * radius;
			float ix = (float) Math.cos(a) * (radius - w);
			float iy = (float) Math.sin(a) * (radius - w);

			if (i > 0) {
				geo.quad(cx + basis[0] * prevOX + basis[3] * prevOY, cy + basis[1] * prevOX + basis[4] * prevOY, cz + basis[2] * prevOX + basis[5] * prevOY, edge,
						cx + basis[0] * ox + basis[3] * oy, cy + basis[1] * ox + basis[4] * oy, cz + basis[2] * ox + basis[5] * oy, edge,
						cx + basis[0] * ix + basis[3] * iy, cy + basis[1] * ix + basis[4] * iy, cz + basis[2] * ix + basis[5] * iy, light,
						cx + basis[0] * prevIX + basis[3] * prevIY, cy + basis[1] * prevIX + basis[4] * prevIY, cz + basis[2] * prevIX + basis[5] * prevIY, light);
			}

			prevOX = ox;
			prevOY = oy;
			prevIX = ix;
			prevIY = iy;
		}

		for (int k = 0; k < 3; k++) {
			double a = time * 0.4 + k * 2.1;
			float twinkle = 0.3f + 0.7f * Math.max(0f, (float) Math.sin(time * 2.5f + k * 1.9f));
			geo.star(above.x + Math.cos(a) * 0.3 * s, y + Math.sin(a * 1.3) * 0.08, above.z + Math.sin(a) * 0.3 * s, 0.035f * s, time + k, 0xFFFFFF, twinkle);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float top = feetY - 36;

		switch (kind.index()) {
			case 1 -> {
				int body = 0xFF000000 | main(0x24242C);
				Gui2D.fill(g, Math.round(cx - 9), Math.round(top - 1), Math.round(cx + 9), Math.round(top + 1), body);
				Gui2D.fill(g, Math.round(cx - 5), Math.round(top - 11), Math.round(cx + 5), Math.round(top - 1), body);
				Gui2D.fill(g, Math.round(cx - 5), Math.round(top - 3), Math.round(cx + 5), Math.round(top - 1), 0xFF000000 | accent.rgb());
			}
			case 2 -> {
				int body = 0xFF000000 | main(0x5B3FA8);
				Gui2D.fill(g, Math.round(cx - 10), Math.round(top - 1), Math.round(cx + 10), Math.round(top + 1), body);

				for (int i = 0; i < 7; i++) {
					Gui2D.fill(g, Math.round(cx - 6 + i * 0.9f + (i > 4 ? (i - 4) * 1.5f : 0)), Math.round(top - 3 - i * 2), Math.round(cx + 6 - i * 0.9f + (i > 4 ? (i - 4) * 1.5f : 0)), Math.round(top - 1 - i * 2), body);
				}
			}
			case 3 -> {
				float bob = (float) Math.sin(time * 1.8f) * 1.5f;
				Gui2D.glowDot(g, cx, top - 10 + bob, 3, main(0x6CF26A), 0.9f);
				Gui2D.fill(g, Math.round(cx - 1), Math.round(top - 15 + bob), Math.round(cx + 1), Math.round(top - 5 + bob), 0xFF000000 | main(0x6CF26A));
			}
			case 4 -> {
				Gui2D.rounded(g, Math.round(cx - 9), Math.round(top - 16), Math.round(cx + 9), Math.round(top - 10), 0xF0EEF1F6);
				Gui2D.rounded(g, Math.round(cx - 4), Math.round(top - 19), Math.round(cx + 5), Math.round(top - 14), 0xF0EEF1F6);

				for (int k = 0; k < 4; k++) {
					float fall = (time * 1.4f + k * 0.37f) % 1f;
					Gui2D.fill(g, Math.round(cx - 6 + k * 4), Math.round(top - 9 + fall * 8), Math.round(cx - 5 + k * 4), Math.round(top - 7 + fall * 8), 0xC08FC8FF);
				}
			}
			case 5 -> {
				Gui2D.fill(g, Math.round(cx - 13), Math.round(top - 1), Math.round(cx + 13), Math.round(top + 1), 0xFFE8C77A);
				Gui2D.fill(g, Math.round(cx - 6), Math.round(top - 6), Math.round(cx + 6), Math.round(top - 1), 0xFFF0D28C);
				Gui2D.fill(g, Math.round(cx - 6), Math.round(top - 3), Math.round(cx + 6), Math.round(top - 1), 0xFFC8423A);
			}
			case 6 -> {
				Gui2D.ellipse(g, cx, top + 2, 6, 7, 14, 1.2f, 0x2A2A30, 1f, (float) Math.PI, false);
				Gui2D.fill(g, Math.round(cx - 7), Math.round(top), Math.round(cx - 5), Math.round(top + 5), 0xFF2A2A30);
				Gui2D.fill(g, Math.round(cx + 5), Math.round(top), Math.round(cx + 7), Math.round(top + 5), 0xFF2A2A30);
				float life = (time * 0.35f) % 1f;
				Gui2D.text(g, "\u266A", cx + 8 + life * 4, top - life * 14, Colors.argb((float) Math.sin(life * Math.PI), accent.rgb()), false, 1f);
			}
			case 7 -> {
				float bob = (float) Math.sin(time * 1.3f) * 1.5f;
				Gui2D.glowDot(g, cx, top - 10 + bob, 4, 0xFFF2B0, 0.4f);
				Gui2D.text(g, "\u263D", cx - 4, top - 15 + bob, 0xFFFFF2B0, false, 1.4f);
			}
			default -> {
				for (int k = 0; k < 6; k++) {
					Gui2D.dot(g, cx - 7 + k * 2.8f, top - 1 - (k % 2), 2.4f, 0xFF000000 | FLOWERS[k % FLOWERS.length]);
				}
			}
		}
	}
}
