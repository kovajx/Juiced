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

public final class Wings extends CosmeticModule {
	// Feather directions (degrees from horizontal, in the wing plane) and lengths
	private static final float[] FEATHER_ANGLES = {80, 58, 36, 14, -8, -30, -50};
	private static final float[] FEATHER_LENGTHS = {0.5f, 0.78f, 0.95f, 0.92f, 0.8f, 0.65f, 0.5f};
	private static final float[] FINGER_ANGLES = {75, 40, 5, -30};
	private static final float[] FINGER_LENGTHS = {1.0f, 0.95f, 0.85f, 0.7f};

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Engel", "Drache", "Energie", "Schmetterling", "Papier"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Flügelschlag", 0.2, 2.0, 1.0));

	// Wing frame for the side being drawn (camera-relative)
	private float rootX;
	private float rootY;
	private float rootZ;
	private float ux;
	private float uz;

	public Wings() {
		super("wings", "Flügel", "Engels-, Drachen-, Energie- oder Schmetterlingsflügel auf deinem Rücken, die langsam schlagen.");
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf();
		boolean airborne = !player.onGround();
		float flapSpeed = speed.f() * (airborne ? 5.2f : 3.0f);
		float flap = (float) Math.sin(time * flapSpeed);
		float openDegrees = airborne ? 18 + 30 * flap : 30 + 16 * flap;
		float[] fwd = forward(bodyYaw);
		float[] right = right(bodyYaw);
		float backX = -fwd[0];
		float backZ = -fwd[1];
		float s = size.f();
		float ox = geo.rx(px + backX * 0.17);
		float oy = geo.ry(py + height * 0.74);
		float oz = geo.rz(pz + backZ * 0.17);
		double open = Math.toRadians(openDegrees);

		for (int side = -1; side <= 1; side += 2) {
			ux = (float) (right[0] * side * Math.cos(open) + backX * Math.sin(open));
			uz = (float) (right[1] * side * Math.cos(open) + backZ * Math.sin(open));
			rootX = ox + right[0] * side * 0.07f;
			rootY = oy;
			rootZ = oz + right[1] * side * 0.07f;

			switch (style.index()) {
				case 1 -> dragon(geo, s);
				case 2 -> energy(geo, s, time);
				case 3 -> butterfly(geo, s, time);
				case 4 -> paper(geo, s);
				default -> angel(geo, s);
			}
		}
	}

	/** Point in the wing plane: {@code along} the wing direction at {@code angle} degrees, offset {@code side} across it. */
	private float[] point(float angleDegrees, float along, float across) {
		double phi = Math.toRadians(angleDegrees);
		float dirX = (float) (ux * Math.cos(phi));
		float dirY = (float) Math.sin(phi);
		float dirZ = (float) (uz * Math.cos(phi));
		float perpX = (float) (-ux * Math.sin(phi));
		float perpY = (float) Math.cos(phi);
		float perpZ = (float) (-uz * Math.sin(phi));
		return new float[] {
				rootX + dirX * along + perpX * across,
				rootY + dirY * along + perpY * across,
				rootZ + dirZ * along + perpZ * across
		};
	}

	private void angel(Geo geo, float s) {
		int rgb = color.rgb();
		int base = Colors.argb(0.95f, Colors.shade(rgb, 0.75f));
		int tipColor = Colors.argb(0.85f, rgb);
		int end = Colors.argb(0.25f, rgb);

		for (int layer = 0; layer < 2; layer++) {
			float lengthScale = layer == 0 ? 1f : 0.52f;
			float widthScale = layer == 0 ? 1f : 1.3f;
			int layerBase = layer == 0 ? base : Colors.argb(0.97f, Colors.shade(rgb, 0.9f));
			int layerTip = layer == 0 ? tipColor : Colors.argb(0.9f, Colors.shade(rgb, 0.45f));

			for (int k = 0; k < FEATHER_ANGLES.length; k++) {
				float angle = FEATHER_ANGLES[k];
				float length = FEATHER_LENGTHS[k] * s * lengthScale;
				float[] a = point(angle, 0.04f, -0.02f * widthScale);
				float[] b = point(angle, 0.04f, 0.02f * widthScale);
				float[] c = point(angle, length, 0.065f * s * widthScale);
				float[] d = point(angle, length, -0.05f * s * widthScale);
				float[] e = point(angle, length + 0.1f * s * lengthScale, 0.005f);
				geo.quad(a[0], a[1], a[2], layerBase, b[0], b[1], b[2], layerBase, c[0], c[1], c[2], layerTip, d[0], d[1], d[2], layerTip);
				geo.tri(d[0], d[1], d[2], layerTip, c[0], c[1], c[2], layerTip, e[0], e[1], e[2], layer == 0 ? end : layerTip);
			}
		}
	}

	private void dragon(Geo geo, float s) {
		int rgb = color.rgb();
		int membrane = Colors.argb(0.55f, Colors.shade(rgb, -0.35f));
		int membraneEdge = Colors.argb(0.35f, rgb);
		int bone = Colors.argb(0.95f, Colors.shade(rgb, 0.3f));
		float[][] tips = new float[FINGER_ANGLES.length][];

		for (int k = 0; k < FINGER_ANGLES.length; k++) {
			tips[k] = point(FINGER_ANGLES[k], FINGER_LENGTHS[k] * s, 0f);
		}

		for (int k = 0; k < tips.length - 1; k++) {
			float[] a = tips[k];
			float[] b = tips[k + 1];
			// Scalloped edge: the middle of each panel is pulled towards the root
			float mx = rootX + ((a[0] + b[0]) / 2f - rootX) * 0.72f;
			float my = rootY + ((a[1] + b[1]) / 2f - rootY) * 0.72f;
			float mz = rootZ + ((a[2] + b[2]) / 2f - rootZ) * 0.72f;
			geo.tri(rootX, rootY, rootZ, membrane, a[0], a[1], a[2], membraneEdge, mx, my, mz, membraneEdge);
			geo.tri(rootX, rootY, rootZ, membrane, mx, my, mz, membraneEdge, b[0], b[1], b[2], membraneEdge);
		}

		for (float[] tip : tips) {
			boneLine(geo, tip, 0.022f * s, bone);
		}
	}

	private void energy(Geo geo, float s, float time) {
		float pulse = 0.7f + 0.3f * (float) Math.sin(time * 4);
		float[] previous = null;

		for (int k = 0; k < FEATHER_ANGLES.length; k++) {
			float[] tip = point(FEATHER_ANGLES[k], FEATHER_LENGTHS[k] * s, 0f);
			int rgb = color.rgb(k / (float) FEATHER_ANGLES.length + time * 0.1f);
			boneLine(geo, tip, 0.012f * s, Colors.argb(0.9f * pulse, Colors.shade(rgb, 0.4f)));
			boneLine(geo, tip, 0.05f * s, Colors.argb(0.18f * pulse, rgb));

			if (previous != null) {
				lineBetween(geo, previous, tip, 0.01f * s, Colors.argb(0.6f * pulse, rgb));
			}

			previous = tip;
		}
	}

	/** Big soft upper wings and smaller lower wings, see-through, with a light rim and fine veins. */
	private void butterfly(Geo geo, float s, float time) {
		int rgb = color.rgb(time * 0.05f);
		int inner = Colors.argb(0.78f, Colors.shade(rgb, -0.3f));
		int outer = Colors.argb(0.7f, Colors.mixRgb(rgb, 0xFFFFFF, 0.4f));
		int rim = Colors.argb(0.8f, Colors.mixRgb(rgb, 0xFFFFFF, 0.75f));
		int vein = Colors.argb(0.35f, Colors.shade(rgb, -0.6f));
		fan(geo, 88, 6, 0.74f * s, 0.3f, inner, outer, rim, vein, 10, s);
		fan(geo, -4, -74, 0.5f * s, 0.35f, inner, outer, rim, vein, 8, s);
	}

	/** Folded paper wings: a few flat facets in light and shadow, with sharp creases. */
	private void paper(Geo geo, float s) {
		int paper = Colors.mixRgb(color.rgb(), 0xF6F3EC, 0.82f);
		int light = Colors.argb(0.97f, paper);
		int shadow = Colors.argb(0.97f, Colors.shade(paper, -0.16f));
		int crease = Colors.argb(0.55f, Colors.shade(paper, -0.4f));
		float[][] tips = {
				point(84, 0.62f * s, 0f), point(60, 0.92f * s, 0f), point(34, 0.84f * s, 0f), point(10, 0.7f * s, 0f), point(-16, 0.5f * s, 0f), point(-40, 0.32f * s, 0f)
		};

		for (int k = 0; k < tips.length - 1; k++) {
			float[] a = tips[k];
			float[] b = tips[k + 1];
			geo.tri(rootX, rootY, rootZ, k % 2 == 0 ? light : shadow, a[0], a[1], a[2], k % 2 == 0 ? light : shadow, b[0], b[1], b[2], k % 2 == 0 ? light : shadow);
			lineBetween(geo, new float[] {rootX, rootY, rootZ}, b, 0.005f * s, crease);
		}
	}

	private void fan(Geo geo, float fromDegrees, float toDegrees, float radius, float roundness, int inner, int outer, int rim, int vein, int steps, float s) {
		float[] previous = null;

		for (int i = 0; i <= steps; i++) {
			float t = i / (float) steps;
			float angle = fromDegrees + (toDegrees - fromDegrees) * t;
			float r = radius * (roundness + (1f - roundness) * (float) Math.sin(t * Math.PI));
			float[] p = point(angle, r, 0f);

			if (previous != null) {
				geo.tri(rootX, rootY, rootZ, inner, previous[0], previous[1], previous[2], outer, p[0], p[1], p[2], outer);
				lineBetween(geo, previous, p, 0.008f * s, rim);
			}

			if (i % 3 == 1) {
				lineBetween(geo, new float[] {rootX, rootY, rootZ}, point(angle, r * 0.85f, 0f), 0.004f * s, vein);
			}

			previous = p;
		}
	}

	private void boneLine(Geo geo, float[] tip, float halfWidth, int color) {
		lineBetween(geo, new float[] {rootX, rootY, rootZ}, tip, halfWidth, color);
	}

	private static void lineBetween(Geo geo, float[] a, float[] b, float halfWidth, int color) {
		// Camera-facing strip between two camera-relative points
		float tx = b[0] - a[0];
		float ty = b[1] - a[1];
		float tz = b[2] - a[2];
		float mx = (a[0] + b[0]) / 2f;
		float my = (a[1] + b[1]) / 2f;
		float mz = (a[2] + b[2]) / 2f;
		float cx = -(ty * mz - tz * my);
		float cy = -(tz * mx - tx * mz);
		float cz = -(tx * my - ty * mx);
		float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);

		if (len < 1.0e-6f) {
			return;
		}

		cx = cx / len * halfWidth;
		cy = cy / len * halfWidth;
		cz = cz / len * halfWidth;
		geo.quad(a[0] - cx, a[1] - cy, a[2] - cz, color, a[0] + cx, a[1] + cy, a[2] + cz, color,
				b[0] + cx, b[1] + cy, b[2] + cz, Colors.fade(color, 0.6f), b[0] - cx, b[1] - cy, b[2] - cz, Colors.fade(color, 0.6f));
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float sy = feetY - 26;
		float flap = (float) Math.sin(time * 3 * speed.f());
		int rgb = color.rgb();

		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < FEATHER_ANGLES.length; k++) {
				double phi = Math.toRadians(FEATHER_ANGLES[k] + flap * 12);
				float length = FEATHER_LENGTHS[k] * 26;

				for (int step = 2; step <= 8; step++) {
					float f = step / 8f;
					float dx = (float) Math.cos(phi) * length * f * side;
					float dy = (float) -Math.sin(phi) * length * f;
					Gui2D.dot(g, cx + side * 5 + dx, sy + dy, 2, Colors.argb(0.35f + 0.6f * f, Colors.mixRgb(0xFFFFFF, rgb, f)));
				}
			}
		}
	}
}
