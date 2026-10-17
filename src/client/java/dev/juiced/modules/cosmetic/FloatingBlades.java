package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

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

/** Small swords that float around you, as a fan on your back, an orbit or a crown. They flare out when you land a hit. */
public final class FloatingBlades extends CosmeticModule {
	private static final int HANDLE = 0x4A2E1C;
	private static final int[] BLADES = {0xD8DCE6, 0xF5C542, 0x5FE3E0, 0x4A4248};

	private final SliderSetting count = add(new SliderSetting("count", "Anzahl", 2, 6, 1, 3, v -> String.valueOf(Math.round(v))));
	private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Anordnung", 0, "Fächer", "Kreis", "Krone", "Flügel"));
	private final ChoiceSetting weapon = add(new ChoiceSetting("weapon", "Waffe", 0, "Schwert", "Axt", "Dreizack"));
	private final ChoiceSetting material = add(new ChoiceSetting("material", "Klinge", 2, "Eisen", "Gold", "Diamant", "Netherit", "Akzentfarbe"));
	private final ColorSetting color = add(new ColorSetting("color", "Akzent & Leuchten"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.5, 1.0));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Tempo", 0.0, 2.0, 1.0));
	private final BoolSetting glow = add(new BoolSetting("glow", "Leuchten", true));

	private float flare;
	private float spin;

	public FloatingBlades() {
		super("blades", "Schwebende Klingen", "Kleine Schwerter, Äxte oder Dreizacke schweben um dich herum: als Fächer, Flügel, im Kreis oder als Krone. Bei einem Treffer fächern sie auf.");
	}

	@Override
	public void onHit(LivingEntity target, boolean crit) {
		flare = crit ? 1f : Math.max(flare, 0.7f);
	}

	@Override
	protected void onDisable() {
		flare = 0f;
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf();
		float dt = Clock.dt();
		flare = Math.max(0f, flare - dt * 1.4f);
		// Smooth in and out
		float burst = flare * flare * (3f - 2f * flare);
		spin += dt * speed.f() * (0.9f + burst * 5f);
		int n = Math.max(1, count.i());
		float s = size.f();
		// The crown floats over the head and follows it
		Pose3 above = layout.index() == 2 ? aboveHead() : null;

		for (int i = 0; i < n; i++) {
			float k = n == 1 ? 0.5f : i / (float) (n - 1);
			float bob = (float) Math.sin(time * 1.8f * Math.max(0.3f, speed.f()) + i * 1.3f) * 0.03f;
			Pose3 pose;
			float scale = s;

			switch (layout.index()) {
				case 1 -> {
					// Orbit around the body, tip first
					float angle = (float) Math.toDegrees(spin * 1.6f) + i * 360f / n;
					double radius = 0.8 + burst * 0.45;
					pose = Pose3.at(px, py + height * 0.55 + bob, pz, angle).move(0, 0, radius).roll(90).pitch(10);
				}
				case 2 -> {
					// Crown of small blades above the head, leaning outwards
					float angle = (float) Math.toDegrees(spin * 0.6f) + i * 360f / n;
					scale = s * 0.6f;
					double radius = 0.3 + burst * 0.3;
					pose = Pose3.at(above.x, above.y + 0.1 + bob, above.z, angle).move(0, 0, radius).pitch(18 + burst * 30).move(0, 0.12 * scale, 0);
				}
				case 3 -> {
					// Two wings of blades, half on each side, sweeping out and up
					int perSide = (n + 1) / 2;
					int side = i < perSide ? -1 : 1;
					int index = i < perSide ? i : i - perSide;
					int count = i < perSide ? perSide : n - perSide;
					float t = count == 1 ? 0.5f : index / (float) (count - 1);
					float flap = (float) Math.sin(time * 1.6f) * 5f + burst * 15f;
					Pose3 center = bodyPose().move(side * 0.12, eyeHeight - 0.2 + bob, crouching ? -0.5 : -0.36);
					pose = center.roll(side * (35 + t * 55 + flap)).pitch(-14).move(0, (0.14 + t * 0.06 + burst * 0.12) * s, 0);
				}
				default -> {
					// Fan on the back, tips pointing outwards
					float spread = n == 1 ? 0f : 70f + n * 12f + burst * 40f;
					float sway = (float) Math.sin(time * 1.6f + i) * 4f;
					Pose3 center = bodyPose().move(0, eyeHeight - 0.15 + bob, crouching ? -0.55 : -0.42);
					pose = center.roll(-spread / 2f + spread * k + sway).pitch(-12).move(0, (0.16 + burst * 0.18) * s, 0);
				}
			}

			sword(geo, pose, scale, i, n, time);
		}
	}

	/** One sword; the pose sits at the grip, its up axis points to the tip and its right axis across the blade. */
	private void sword(Geo geo, Pose3 pose, float s, int index, int n, float time) {
		int accent = color.rgb(index / (float) n + time * 0.05f);
		int blade = material.index() < BLADES.length ? BLADES[material.index()] : Colors.mixRgb(accent, 0xFFFFFF, 0.35f);

		if (glow.on()) {
			float alpha = 0.16f + flare * 0.3f;
			int c = Colors.argb(alpha, accent);
			int end = Colors.argb(0f, accent);
			geo.line(pose.wx(0, 0.02 * s, 0), pose.wy(0, 0.02 * s, 0), pose.wz(0, 0.02 * s, 0),
					pose.wx(0, 0.46 * s, 0), pose.wy(0, 0.46 * s, 0), pose.wz(0, 0.46 * s, 0), 0.07f * s, c, end);

			if (flare > 0.05f) {
				geo.glowDot(pose.wx(0, 0.43 * s, 0), pose.wy(0, 0.43 * s, 0), pose.wz(0, 0.43 * s, 0), 0.1f * s, accent, flare);
			}
		}

		switch (weapon.index()) {
			case 1 -> axe(geo, pose, s, blade, accent);
			case 2 -> trident(geo, pose, s, blade, accent);
			default -> {
				geo.beginModel();
				geo.box(pose.copy().move(0, -0.11 * s, 0), 0.022f * s, 0.022f * s, 0.022f * s, accent, 1f);
				geo.box(pose.copy().move(0, -0.045 * s, 0), 0.013f * s, 0.045f * s, 0.013f * s, HANDLE, 1f);
				geo.box(pose.copy().move(0, 0.01 * s, 0), 0.068f * s, 0.013f * s, 0.02f * s, accent, 1f);
				geo.box(pose.copy().move(0, 0.195 * s, 0), 0.03f * s, 0.172f * s, 0.007f * s, blade, 1f);
				geo.endModel();
				geo.prism(pose.copy().move(0, 0.367 * s, 0), new float[] {-0.03f * s, 0.03f * s, 0f}, new float[] {0f, 0f, 0.075f * s}, 0.007f * s, blade, 1f);
			}
		}
	}

	/** Long handle with a curved blade on one side near the top. */
	private static void axe(Geo geo, Pose3 pose, float s, int blade, int accent) {
		geo.beginModel();
		geo.box(pose.copy().move(0, 0.1 * s, 0), 0.014f * s, 0.24f * s, 0.014f * s, HANDLE, 1f);
		geo.box(pose.copy().move(0, -0.15 * s, 0), 0.02f * s, 0.018f * s, 0.02f * s, accent, 1f);
		geo.box(pose.copy().move(0, 0.29 * s, 0), 0.03f * s, 0.035f * s, 0.022f * s, accent, 1f);
		geo.endModel();
		// Blade: wide at the edge, narrow where it meets the handle
		geo.prism(pose.copy().move(0.03 * s, 0.29 * s, 0), new float[] {0f, 0.1f * s, 0.12f * s, 0.1f * s, 0f},
				new float[] {-0.03f * s, -0.08f * s, 0f, 0.08f * s, 0.03f * s}, 0.008f * s, blade, 1f);
	}

	/** A shaft with a crossbar and three prongs. */
	private static void trident(Geo geo, Pose3 pose, float s, int blade, int accent) {
		geo.beginModel();
		geo.box(pose.copy().move(0, 0.06 * s, 0), 0.011f * s, 0.25f * s, 0.011f * s, accent, 1f);
		geo.box(pose.copy().move(0, 0.31 * s, 0), 0.075f * s, 0.012f * s, 0.014f * s, blade, 1f);

		for (int k = -1; k <= 1; k++) {
			float length = k == 0 ? 0.11f : 0.08f;
			geo.box(pose.copy().move(k * 0.065 * s, (0.32 + length / 2) * s, 0), 0.01f * s, length / 2 * s, 0.01f * s, blade, 1f);
		}

		geo.endModel();

		for (int k = -1; k <= 1; k++) {
			float length = k == 0 ? 0.11f : 0.08f;
			geo.prism(pose.copy().move(k * 0.065 * s, (0.32 + length) * s, 0), new float[] {-0.016f * s, 0.016f * s, 0f}, new float[] {0f, 0f, 0.045f * s}, 0.008f * s, blade, 1f);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		float cy = feetY - 26;
		int n = Math.max(1, count.i());
		float pulse = Previews.cycle(time, 2.5f) < 0.15f ? 1f : 0f;

		for (int i = 0; i < n; i++) {
			float k = n == 1 ? 0.5f : i / (float) (n - 1);
			double angle = Math.toRadians(-90 - 55 + 110 * k);
			float reach = 9 + pulse * 3;
			float bx = cx + (float) Math.cos(angle) * 4;
			float by = cy + (float) Math.sin(angle) * 4;
			int accent = color.rgb(i / (float) n);
			int blade = material.index() < BLADES.length ? BLADES[material.index()] : accent;

			for (int step = 0; step < 6; step++) {
				float t = step / 5f;
				Gui2D.dot(g, bx + (float) Math.cos(angle) * reach * t, by + (float) Math.sin(angle) * reach * t, step == 0 ? 2f : 1.4f,
						Colors.argb(1f, step == 0 ? accent : blade));
			}
		}
	}
}
