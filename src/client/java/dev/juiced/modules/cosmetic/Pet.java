package dev.juiced.modules.cosmetic;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Koi;
import dev.juiced.render.Pose3;
import dev.juiced.render.Shapes;

/** A little companion that floats next to you, follows you around and looks at your opponent. */
public final class Pet extends CosmeticModule {
	private final ChoiceSetting kind = add(new ChoiceSetting("kind", "Art", 0, "Geist", "Mini-Drache", "Orb", "Slime", "Fee", "Planet", "Kristall", "Koi-Blase", "Qualle", "Himmelswal", "Papierkranich", "Axolotl"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final ChoiceSetting side = add(new ChoiceSetting("side", "Seite", 1, "Links", "Rechts"));
	private final BoolSetting firstPerson = add(new BoolSetting("first_person", "In Ego-Sicht zeigen", true));

	private static final int TRAIL = 14;
	private final double[] trailX = new double[TRAIL];
	private final double[] trailY = new double[TRAIL];
	private final double[] trailZ = new double[TRAIL];
	private int trailHead;
	private double lastTrail;
	private final int[] koiColors = new int[Koi.SEGMENTS + 1];
	private int koiLook = -1;

	private double x;
	private double y;
	private double z;
	private float yaw;
	private boolean placed;

	public Pet() {
		super("pet", "Begleiter", "Ein kleiner Begleiter schwebt neben dir: Geist, Mini-Drache, Orb, Slime, Fee, Planet, Kristall, ein Koi in einer Wasserblase, eine Qualle, ein Himmelswal, ein Papierkranich oder ein Axolotl. Er folgt dir und schaut deinen Gegner an.");
	}

	@Override
	protected boolean showInFirstPerson() {
		return firstPerson.on();
	}

	@Override
	protected void onDisable() {
		placed = false;
	}

	@Override
	protected void draw(Geo geo) {
		float time = Clock.tf();
		float s = size.f();
		float sideSign = side.index() == 1 ? 1f : -1f;
		Pose3 anchor = bodyPose().move(0.75 * sideSign, height + 0.1, -0.35);
		double bob = Math.sin(time * 2.2) * 0.08;
		double tx = anchor.x;
		double ty = anchor.y + bob;
		double tz = anchor.z;

		if (!placed || Math.abs(tx - x) + Math.abs(ty - y) + Math.abs(tz - z) > 12) {
			x = tx;
			y = ty;
			z = tz;
			yaw = bodyYaw;
			placed = true;
		}

		float follow = 1f - (float) Math.exp(-4.5f * Clock.dt());
		x += (tx - x) * follow;
		y += (ty - y) * follow;
		z += (tz - z) * follow;

		// Look at the opponent if there is one, otherwise the same way as you
		float targetYaw = bodyYaw;
		LivingEntity target = CombatEvents.target();

		if (target != null) {
			targetYaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - x), target.getZ() - z));
		}

		float delta = ((targetYaw - yaw) % 360f + 540f) % 360f - 180f;
		yaw += delta * (1f - (float) Math.exp(-6f * Clock.dt()));

		Pose3 pose = Pose3.at(x, y, z, yaw);

		// Remember where it was, for the fairy's sparkle trail
		if (Clock.t() - lastTrail > 0.05) {
			trailX[trailHead] = x;
			trailY[trailHead] = y;
			trailZ[trailHead] = z;
			trailHead = (trailHead + 1) % TRAIL;
			lastTrail = Clock.t();
		}
		int rgb = color.rgb(time * 0.1f);

		switch (kind.index()) {
			case 1 -> dragon(geo, pose, s, rgb, time);
			case 2 -> orb(geo, pose, s, rgb, time);
			case 3 -> slime(geo, pose, s, rgb, time);
			case 4 -> fairy(geo, pose, s, rgb, time);
			case 5 -> planet(geo, pose, s, rgb, time);
			case 6 -> crystal(geo, pose, s, rgb, time);
			case 7 -> koiBubble(geo, pose, s, rgb, time);
			case 8 -> jellyfish(geo, pose, s, rgb, time);
			case 9 -> whale(geo, s, rgb, time);
			case 10 -> crane(geo, pose, s, rgb, time);
			case 11 -> axolotl(geo, pose, s, rgb, time);
			default -> ghost(geo, pose, s, rgb, time);
		}
	}

	private static void eyes(Geo geo, Pose3 head, float s, float forward, float spacing, float up) {
		geo.box(head.copy().move(-spacing * s, up * s, forward * s), 0.022f * s, 0.03f * s, 0.006f * s, 0x101014, 1f);
		geo.box(head.copy().move(spacing * s, up * s, forward * s), 0.022f * s, 0.03f * s, 0.006f * s, 0x101014, 1f);
	}

	private static void ghost(Geo geo, Pose3 pose, float s, int rgb, float time) {
		int body = Colors.mixRgb(0xFFFFFF, rgb, 0.25f);
		geo.glowDot(pose.x, pose.y, pose.z, 0.32f * s, rgb, 0.22f);
		geo.beginModel();
		geo.box(pose, 0.11f * s, 0.12f * s, 0.1f * s, body, 0.88f);
		geo.box(pose.copy().move(0, 0.13 * s, 0), 0.085f * s, 0.02f * s, 0.08f * s, body, 0.88f);

		// Wavy skirt
		for (int i = 0; i < 3; i++) {
			float wobble = (float) Math.sin(time * 6 + i * 2.1f) * 0.02f * s;
			geo.box(pose.copy().move((i - 1) * 0.07 * s, -0.15 * s + wobble, 0), 0.033f * s, 0.04f * s, 0.09f * s, body, 0.8f);
		}

		eyes(geo, pose, s, 0.102f, 0.045f, 0.03f);
		geo.endModel();
	}

	private static void dragon(Geo geo, Pose3 pose, float s, int rgb, float time) {
		int belly = Colors.shade(rgb, 0.35f);
		int dark = Colors.shade(rgb, -0.3f);
		float flap = (float) Math.sin(time * 7f) * 35f;
		geo.beginModel();
		geo.box(pose, 0.07f * s, 0.06f * s, 0.11f * s, rgb, 1f);
		geo.box(pose.copy().move(0, -0.035 * s, 0.01 * s), 0.055f * s, 0.03f * s, 0.09f * s, belly, 1f);
		Pose3 head = pose.copy().move(0, 0.08 * s, 0.12 * s);
		geo.box(head, 0.06f * s, 0.055f * s, 0.06f * s, rgb, 1f);
		geo.box(head.copy().move(0, -0.015 * s, 0.075 * s), 0.035f * s, 0.025f * s, 0.035f * s, belly, 1f);
		geo.box(head.copy().move(-0.035 * s, 0.07 * s, -0.02 * s).pitch(-25), 0.012f * s, 0.035f * s, 0.012f * s, dark, 1f);
		geo.box(head.copy().move(0.035 * s, 0.07 * s, -0.02 * s).pitch(-25), 0.012f * s, 0.035f * s, 0.012f * s, dark, 1f);
		eyes(geo, head, s, 0.062f, 0.03f, 0.015f);

		for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
			Pose3 wing = pose.copy().move(sideSign * 0.07 * s, 0.05 * s, 0).roll(sideSign * (20 + flap));
			geo.box(wing.copy().move(sideSign * 0.09 * s, 0, 0), 0.09f * s, 0.008f * s, 0.07f * s, dark, 1f);
		}

		Pose3 tail = pose.copy().move(0, -0.01 * s, -0.11 * s);

		for (int i = 0; i < 3; i++) {
			float sway = (float) Math.sin(time * 3 + i) * 15f;
			tail.yaw(sway).pitch(12).move(0, 0, -0.045 * s);
			float half = (0.035f - i * 0.009f) * s;
			geo.box(tail, half, half, 0.04f * s, i == 2 ? dark : rgb, 1f);
			tail.move(0, 0, -0.04 * s);
		}

		geo.endModel();
	}

	private static void orb(Geo geo, Pose3 pose, float s, int rgb, float time) {
		geo.glowDot(pose.x, pose.y, pose.z, 0.22f * s, rgb, 0.9f);
		geo.glowDot(pose.x, pose.y, pose.z, 0.08f * s, 0xFFFFFF, 1f);
		float px = geo.rx(pose.x);
		float py = geo.ry(pose.y);
		float pz = geo.rz(pose.z);
		int segments = Theme.detail(32);

		for (int ring = 0; ring < 2; ring++) {
			double a = time * (ring == 0 ? 1.3 : -0.9) + ring * 1.2;
			float tilt = ring == 0 ? 0.5f : -0.7f;
			float ux = (float) Math.cos(a);
			float uz = (float) Math.sin(a);
			float vx = -uz * (float) Math.cos(tilt);
			float vy = (float) Math.sin(tilt);
			float vz = ux * (float) Math.cos(tilt);
			geo.glowRing(px, py, pz, ux, 0, uz, vx, vy, vz, 0.2f * s, 0.012f * s, 0.03f * s, segments, 0f, (float) (Math.PI * 2), t -> rgb, 0.85f);
			double moon = time * 2.5 + ring * Math.PI;
			geo.glowDot(pose.x + (ux * Math.cos(moon) + vx * Math.sin(moon)) * 0.2 * s,
					pose.y + vy * Math.sin(moon) * 0.2 * s,
					pose.z + (uz * Math.cos(moon) + vz * Math.sin(moon)) * 0.2 * s, 0.035f * s, 0xFFFFFF, 1f);
		}
	}

	/** A tiny glowing fairy with four fluttering wings and a trail of sparkles. */
	private void fairy(Geo geo, Pose3 pose, float s, int rgb, float time) {
		for (int k = 1; k < TRAIL; k++) {
			int index = (trailHead - 1 - k + TRAIL * 2) % TRAIL;
			float fade = 1f - k / (float) TRAIL;
			geo.glowDot(trailX[index], trailY[index] - k * 0.01, trailZ[index], 0.035f * s * fade, rgb, 0.5f * fade);
		}

		geo.glowDot(pose.x, pose.y, pose.z, 0.3f * s, rgb, 0.3f);
		geo.glowDot(pose.x, pose.y, pose.z, 0.06f * s, 0xFFFFFF, 1f);
		double flap = Math.toRadians(25 + Math.sin(time * 22f) * 40);
		float c = (float) Math.cos(flap);
		float sn = (float) Math.sin(flap);
		int wing = Colors.argb(0.45f, Colors.mixRgb(rgb, 0xFFFFFF, 0.6f));
		int edge = Colors.argb(0.15f, rgb);

		for (int side = -1; side <= 1; side += 2) {
			// Upper and lower wing, both behind the body
			geo.triWorld(pose.wx(0, 0.01 * s, -0.02 * s), pose.wy(0, 0.01 * s, -0.02 * s), pose.wz(0, 0.01 * s, -0.02 * s), wing,
					pose.wx(side * c * 0.16 * s, sn * 0.16 * s + 0.08 * s, -0.08 * s), pose.wy(side * c * 0.16 * s, sn * 0.16 * s + 0.08 * s, -0.08 * s), pose.wz(side * c * 0.16 * s, sn * 0.16 * s + 0.08 * s, -0.08 * s), edge,
					pose.wx(side * c * 0.06 * s, sn * 0.06 * s + 0.12 * s, -0.05 * s), pose.wy(side * c * 0.06 * s, sn * 0.06 * s + 0.12 * s, -0.05 * s), pose.wz(side * c * 0.06 * s, sn * 0.06 * s + 0.12 * s, -0.05 * s), edge);
			geo.triWorld(pose.wx(0, -0.01 * s, -0.02 * s), pose.wy(0, -0.01 * s, -0.02 * s), pose.wz(0, -0.01 * s, -0.02 * s), wing,
					pose.wx(side * c * 0.11 * s, sn * 0.11 * s - 0.07 * s, -0.06 * s), pose.wy(side * c * 0.11 * s, sn * 0.11 * s - 0.07 * s, -0.06 * s), pose.wz(side * c * 0.11 * s, sn * 0.11 * s - 0.07 * s, -0.06 * s), edge,
					pose.wx(side * c * 0.03 * s, sn * 0.03 * s - 0.08 * s, -0.04 * s), pose.wy(side * c * 0.03 * s, sn * 0.03 * s - 0.08 * s, -0.04 * s), pose.wz(side * c * 0.03 * s, sn * 0.03 * s - 0.08 * s, -0.04 * s), edge);
		}
	}

	/** A small planet with a tilted ring and a moon going round it. */
	private static void planet(Geo geo, Pose3 pose, float s, int rgb, float time) {
		int dark = Colors.shade(rgb, -0.45f);
		geo.glowDot(pose.x, pose.y, pose.z, 0.26f * s, rgb, 0.22f);
		// A ball: a disc facing the camera, darker towards the rim, with a soft highlight
		float[] basis = geo.billboardBasis(pose.x, pose.y, pose.z);
		float cx = geo.rx(pose.x);
		float cy = geo.ry(pose.y);
		float cz = geo.rz(pose.z);
		geo.band(cx, cy, cz, basis[0], basis[1], basis[2], basis[3], basis[4], basis[5], 0f, 0.12f * s, 20, 0f, (float) (Math.PI * 2), t -> rgb, 1f, 1f, 0f);
		geo.band(cx, cy, cz, basis[0], basis[1], basis[2], basis[3], basis[4], basis[5], 0.07f * s, 0.12f * s, 20, 0f, (float) (Math.PI * 2), t -> dark, 0f, 0.7f, 0f);
		geo.band(cx + basis[3] * 0.03f * s - basis[0] * 0.03f * s, cy + basis[4] * 0.03f * s - basis[1] * 0.03f * s, cz + basis[5] * 0.03f * s - basis[2] * 0.03f * s,
				basis[0], basis[1], basis[2], basis[3], basis[4], basis[5], 0f, 0.06f * s, 12, 0f, (float) (Math.PI * 2), t -> 0xFFFFFF, 0.25f, 0f, 0f);
		double tilt = 0.45;
		float ux = (float) Math.cos(time * 0.2);
		float uz = (float) Math.sin(time * 0.2);
		float vx = -uz * (float) Math.cos(tilt);
		float vy = (float) Math.sin(tilt);
		float vz = ux * (float) Math.cos(tilt);
		geo.glowRing(cx, cy, cz, ux, 0, uz, vx, vy, vz, 0.2f * s, 0.035f * s, 0.02f * s, 40, 0f, (float) (Math.PI * 2), t -> Colors.mixRgb(rgb, 0xFFFFFF, 0.5f), 0.6f);
		double moon = time * 1.4;
		geo.glowDot(pose.x + (ux * Math.cos(moon) + vx * Math.sin(moon)) * 0.3 * s, pose.y + vy * Math.sin(moon) * 0.3 * s,
				pose.z + (uz * Math.cos(moon) + vz * Math.sin(moon)) * 0.3 * s, 0.03f * s, 0xEDEDF2, 1f);
	}

	/** A slowly turning gem with a glow and two small shards orbiting it. */
	private static void crystal(Geo geo, Pose3 pose, float s, int rgb, float time) {
		geo.glowDot(pose.x, pose.y, pose.z, 0.32f * s, rgb, 0.28f);
		Shapes.gem(geo, pose.x, pose.y, pose.z, 0.08f * s, 0.15f * s, time * 0.9f, rgb, 0.9f);

		for (int k = 0; k < 2; k++) {
			double a = time * 1.6 + k * Math.PI;
			Shapes.gem(geo, pose.x + Math.cos(a) * 0.2 * s, pose.y + Math.sin(time * 2 + k) * 0.05, pose.z + Math.sin(a) * 0.2 * s,
					0.025f * s, 0.05f * s, -time * 2f, Colors.mixRgb(rgb, 0xFFFFFF, 0.4f), 0.9f);
		}
	}

	/** A koi swimming circles inside a floating bubble of water. */
	private void koiBubble(Geo geo, Pose3 pose, float s, int rgb, float time) {
		if (koiLook != 0) {
			Koi.pattern(0, 4242L, koiColors);
			koiLook = 0;
		}

		int water = Colors.mixRgb(0x6FD3FF, rgb, 0.25f);
		float radius = 0.22f * s;
		geo.glowDot(pose.x, pose.y, pose.z, radius * 1.35f, water, 0.35f);
		geo.billboardRing(pose.x, pose.y, pose.z, radius, 0.012f * s, 0.03f * s, 32, t -> Colors.mixRgb(water, 0xFFFFFF, 0.5f), 0.55f);
		double a = time * 1.1;
		double fx = pose.x + Math.cos(a) * radius * 0.45;
		double fz = pose.z + Math.sin(a) * radius * 0.45;
		float h = (float) (a + Math.PI / 2);
		float dirX = (float) Math.cos(h);
		float dirZ = (float) Math.sin(h);
		double fy = pose.y + Math.sin(time * 1.7) * 0.03 * s;
		// Two crossing planes so the fish reads from every angle
		Koi.draw(geo, fx, fy, fz, dirX, 0f, dirZ, -dirZ, 0f, dirX, 0.2f * s, time, 0f, 0.7f, koiColors, 0.95f, 0, 0, 0);
		Koi.draw(geo, fx, fy, fz, dirX, 0f, dirZ, 0f, 1f, 0f, 0.2f * s, time, 0f, 0.7f, koiColors, 0.8f, 0, 0, 0);
		// Little bubbles rising
		for (int k = 0; k < 3; k++) {
			float rise = (time * 0.5f + k / 3f) % 1f;
			geo.glowDot(pose.x + Math.cos(k * 2.1) * radius * 0.4, pose.y - radius * 0.6 + rise * radius * 1.3, pose.z + Math.sin(k * 2.1) * radius * 0.4,
					0.012f * s, 0xFFFFFF, 0.7f * (1f - rise));
		}
	}

	/** The pet's color, or a natural default while the color is left on the theme. */
	private int natural(int standard, int rgb) {
		return color.get() == ColorSetting.THEME ? standard : rgb;
	}

	private final float[] lineX = new float[10];
	private final float[] lineY = new float[10];
	private final float[] lineZ = new float[10];
	private final float[] lineW = new float[10];
	private final int[] lineC = new int[10];

	/** A see-through jellyfish: a softly pulsing bell with long, swaying tentacles. */
	private void jellyfish(Geo geo, Pose3 pose, float s, int rgb, float time) {
		float pulse = (float) Math.max(0.0, Math.sin(time * 2.0));
		float bellRadius = 0.15f * s * (1f - 0.14f * pulse);
		float bellHeight = 0.12f * s * (1f + 0.12f * pulse);
		double bx = pose.x;
		double by = pose.y + pulse * 0.03 * s;
		double bz = pose.z;
		geo.glowDot(bx, by + bellHeight * 0.4, bz, 0.38f * s, rgb, 0.22f);
		int bands = 5;
		int segments = Theme.detail(16);

		for (int i = 0; i < bands; i++) {
			double lat0 = i / (double) bands * Math.PI / 2;
			double lat1 = (i + 1) / (double) bands * Math.PI / 2;
			double r0 = bellRadius * Math.cos(lat0);
			double r1 = bellRadius * Math.cos(lat1);
			double y0 = by + bellHeight * Math.sin(lat0);
			double y1 = by + bellHeight * Math.sin(lat1);
			int c0 = Colors.argb(0.38f + 0.1f * i, Colors.mixRgb(rgb, 0xFFFFFF, 0.3f + 0.12f * i));
			int c1 = Colors.argb(0.38f + 0.1f * (i + 1), Colors.mixRgb(rgb, 0xFFFFFF, 0.3f + 0.12f * (i + 1)));

			for (int j = 0; j < segments; j++) {
				double a0 = j * Math.PI * 2 / segments;
				double a1 = (j + 1) * Math.PI * 2 / segments;
				geo.quadWorld(bx + Math.cos(a0) * r0, y0, bz + Math.sin(a0) * r0, c0,
						bx + Math.cos(a1) * r0, y0, bz + Math.sin(a1) * r0, c0,
						bx + Math.cos(a1) * r1, y1, bz + Math.sin(a1) * r1, c1,
						bx + Math.cos(a0) * r1, y1, bz + Math.sin(a0) * r1, c1);
			}
		}

		geo.groundRing(bx, by, bz, bellRadius, 0.012f * s, 0.02f * s, segments, time * 0.3f, (float) (Math.PI * 2), t -> Colors.mixRgb(rgb, 0xFFFFFF, 0.6f), 0.6f);

		// Tentacles from the rim, and four wider arms from the middle
		for (int k = 0; k < 11; k++) {
			boolean arm = k >= 7;
			double angle = arm ? (k - 7) * Math.PI / 2 + 0.4 : k * Math.PI * 2 / 7;
			double start = arm ? bellRadius * 0.25 : bellRadius * 0.85;
			float length = (arm ? 0.24f : 0.42f) * s;
			int n = lineX.length;

			for (int j = 0; j < n; j++) {
				float t = j / (float) (n - 1);
				double sway = Math.sin(time * 1.8 - j * 0.55 + k) * 0.035 * s * t;
				double x = bx + Math.cos(angle) * (start + sway) + Math.sin(time + k) * 0.01 * t;
				double z = bz + Math.sin(angle) * (start + sway);
				double y = by - t * length * (1f - 0.15f * pulse);
				lineX[j] = geo.rx(x);
				lineY[j] = geo.ry(y);
				lineZ[j] = geo.rz(z);
				lineW[j] = (arm ? 0.016f : 0.005f) * s * (1f - t * 0.6f);
				lineC[j] = Colors.argb((arm ? 0.5f : 0.55f) * (1f - t), Colors.mixRgb(rgb, 0xFFFFFF, 0.45f));
			}

			geo.ribbon(lineX, lineY, lineZ, lineW, lineC, n);
		}
	}

	/** A small whale gliding slowly in a big circle around you, high in the air. */
	private void whale(Geo geo, float s, int rgb, float time) {
		float ws = s * 1.4f;
		double angle = time * 0.22;
		double radius = 2.7;
		double wx = px + Math.cos(angle) * radius;
		double wz = pz + Math.sin(angle) * radius;
		double wy = py + height + 0.9 + Math.sin(time * 0.6) * 0.25;
		// Facing along the circle
		Pose3 body = Pose3.at(wx, wy, wz, (float) Math.toDegrees(angle)).pitch((float) Math.cos(time * 0.6) * -7f);
		int back = natural(0x4F6F96, rgb);
		int belly = 0xDDE6EE;
		geo.beginModel();
		geo.box(body, 0.13f * ws, 0.12f * ws, 0.22f * ws, back, 1f);
		geo.box(body.copy().move(0, -0.01 * ws, 0.27 * ws), 0.115f * ws, 0.1f * ws, 0.08f * ws, back, 1f);
		geo.box(body.copy().move(0, -0.1 * ws, 0.08 * ws), 0.105f * ws, 0.03f * ws, 0.26f * ws, belly, 1f);

		for (int side = -1; side <= 1; side += 2) {
			geo.box(body.copy().move(side * 0.116 * ws, 0.0, 0.3 * ws), 0.006f * ws, 0.015f * ws, 0.015f * ws, 0x141418, 1f);
			Pose3 fin = body.copy().move(side * 0.14 * ws, -0.07 * ws, 0.1 * ws).roll(side * (30 + (float) Math.sin(time * 1.5) * 10)).move(side * 0.07 * ws, 0, 0);
			geo.box(fin, 0.07f * ws, 0.01f * ws, 0.035f * ws, back, 1f);
		}

		// Tail sweeps up and down
		Pose3 tail = body.copy().move(0, 0.01 * ws, -0.22 * ws);

		for (int i = 0; i < 3; i++) {
			tail.pitch((float) Math.sin(time * 1.6 - i * 0.7) * 12f).move(0, 0, -0.06 * ws);
			geo.box(tail, (0.095f - i * 0.02f) * ws, (0.085f - i * 0.02f) * ws, 0.06f * ws, back, 1f);
			tail.move(0, 0, -0.06 * ws);
		}

		tail.pitch((float) Math.sin(time * 1.6 - 2.4) * 18f).move(0, 0, -0.03 * ws);
		geo.box(tail, 0.17f * ws, 0.012f * ws, 0.05f * ws, back, 1f);
		geo.endModel();

		// Sparkles left behind along the circle
		for (int k = 1; k <= 8; k++) {
			double a = angle - k * 0.06;
			float fade = 1f - k / 9f;
			geo.glowDot(px + Math.cos(a) * radius, wy - 0.05 + Math.sin(time * 2 + k) * 0.05, pz + Math.sin(a) * radius, 0.04f * ws * fade, Colors.mixRgb(back, 0xFFFFFF, 0.6f), 0.6f * fade);
		}
	}

	/** An origami crane with folded wings that flap slowly. */
	private static void crane(Geo geo, Pose3 pose, float s, int rgb, float time) {
		int paper = Colors.mixRgb(rgb, 0xF6F3EC, 0.6f);
		int light = Colors.argb(0.98f, paper);
		int shadow = Colors.argb(0.98f, Colors.shade(paper, -0.18f));
		double flap = Math.toRadians(15 + Math.sin(time * 4.5f) * 35);
		float c = (float) Math.cos(flap);
		float sn = (float) Math.sin(flap);
		Pose3 p = pose.copy().move(0, Math.sin(time * 4.5f) * -0.01 * s, 0);
		tri(geo, p, s, light, 0, 0, 0.12, 0, 0.04, 0, 0, 0, -0.1);
		tri(geo, p, s, shadow, 0, 0, 0.12, 0, 0, -0.1, 0, -0.035, 0);
		// Neck and head, tail
		tri(geo, p, s, light, 0, 0, 0.07, 0, 0.02, 0.1, 0, 0.17, 0.2);
		tri(geo, p, s, shadow, 0, 0.17, 0.2, 0, 0.145, 0.19, 0, 0.13, 0.27);
		tri(geo, p, s, shadow, 0, 0, -0.06, 0, 0.02, -0.09, 0, 0.16, -0.2);

		for (int side = -1; side <= 1; side += 2) {
			tri(geo, p, s, side < 0 ? light : shadow, 0, 0.03, 0.05, 0, 0.03, -0.06, side * c * 0.25, 0.03 + sn * 0.25, -0.03);
			tri(geo, p, s, side < 0 ? shadow : light, 0, 0.03, 0.05, side * c * 0.25, 0.03 + sn * 0.25, -0.03, side * c * 0.12, 0.03 + sn * 0.12, 0.07);
		}
	}

	private static void tri(Geo geo, Pose3 p, float s, int color, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2) {
		geo.triWorld(p.wx(x0 * s, y0 * s, z0 * s), p.wy(x0 * s, y0 * s, z0 * s), p.wz(x0 * s, y0 * s, z0 * s), color,
				p.wx(x1 * s, y1 * s, z1 * s), p.wy(x1 * s, y1 * s, z1 * s), p.wz(x1 * s, y1 * s, z1 * s), color,
				p.wx(x2 * s, y2 * s, z2 * s), p.wy(x2 * s, y2 * s, z2 * s), p.wz(x2 * s, y2 * s, z2 * s), color);
	}

	/** A pink axolotl paddling through the air, with feathery gills and a wavy tail. */
	private void axolotl(Geo geo, Pose3 pose, float s, int rgb, float time) {
		int pink = natural(0xF5A9C4, rgb);
		int gill = Colors.mixRgb(pink, 0xD9385F, 0.6f);
		int bellyColor = Colors.mixRgb(pink, 0xFFFFFF, 0.35f);
		geo.beginModel();
		Pose3 head = pose.copy().move(0, 0, 0.09 * s);
		geo.box(head, 0.085f * s, 0.055f * s, 0.07f * s, pink, 1f);

		for (int side = -1; side <= 1; side += 2) {
			geo.box(head.copy().move(side * 0.05 * s, 0.015 * s, 0.07 * s), 0.01f * s, 0.01f * s, 0.004f * s, 0x141418, 1f);

			for (int k = 0; k < 3; k++) {
				float wave = (float) Math.sin(time * 3 + k + side) * 8f;
				Pose3 frond = head.copy().move(side * 0.08 * s, (0.035 - k * 0.03) * s, -0.03 * s).roll(side * (60 - k * 30) + wave).move(0, 0.035 * s, 0);
				geo.box(frond, 0.008f * s, 0.035f * s, 0.014f * s, gill, 1f);
			}
		}

		Pose3 body = pose.copy();

		for (int i = 0; i < 3; i++) {
			body.yaw((float) Math.sin(time * 3 - i * 0.8) * 10f);
			geo.box(body, (0.06f - i * 0.008f) * s, (0.048f - i * 0.006f) * s, 0.045f * s, pink, 1f);
			geo.box(body.copy().move(0, -0.04 * s, 0), (0.05f - i * 0.008f) * s, 0.01f * s, 0.04f * s, bellyColor, 1f);

			if (i == 0 || i == 2) {
				for (int side = -1; side <= 1; side += 2) {
					Pose3 leg = body.copy().move(side * 0.06 * s, -0.035 * s, 0).pitch((float) Math.sin(time * 6 + i + side) * 25f).move(0, -0.02 * s, 0);
					geo.box(leg, 0.01f * s, 0.022f * s, 0.01f * s, pink, 1f);
				}
			}

			body.move(0, 0, -0.08 * s);
		}

		body.yaw((float) Math.sin(time * 3 - 2.6) * 18f).move(0, 0, -0.03 * s);
		geo.box(body, 0.008f * s, 0.045f * s, 0.07f * s, Colors.mixRgb(pink, 0xFFFFFF, 0.2f), 0.9f);
		geo.endModel();
	}

	private static void slime(Geo geo, Pose3 pose, float s, int rgb, float time) {
		float bounce = (float) Math.abs(Math.sin(time * 3.2f));
		float squash = 1f + (1f - bounce) * 0.18f;
		float stretch = 1f / squash;
		Pose3 body = pose.copy().move(0, bounce * 0.06 * s - 0.04 * s, 0);
		geo.beginModel();
		geo.box(body.copy().move(0, -0.01 * s, 0), 0.05f * s * squash, 0.05f * s * stretch, 0.05f * s * squash, Colors.shade(rgb, -0.2f), 0.95f);
		eyes(geo, body, s, 0.098f * squash, 0.04f, 0.03f);
		geo.box(body, 0.1f * s * squash, 0.1f * s * stretch, 0.1f * s * squash, rgb, 0.5f);
		geo.endModel();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float feetY = Previews.stage(g, px, py, w, h, px + w * 0.4f, 0);
		float cx = px + w * 0.68f;
		float cy = feetY - 38 + (float) Math.sin(time * 2.2f) * 2;
		int rgb = color.rgb(time * 0.1f);

		switch (kind.index()) {
			case 4 -> {
				Gui2D.glowDot(g, cx, cy, 3, rgb, 1f);
				float wing = 2 + Math.abs((float) Math.sin(time * 20)) * 3;
				Gui2D.fill(g, Math.round(cx - 2 - wing), Math.round(cy - 4), Math.round(cx - 2), Math.round(cy), Colors.argb(0.5f, Colors.mixRgb(rgb, 0xFFFFFF, 0.6f)));
				Gui2D.fill(g, Math.round(cx + 2), Math.round(cy - 4), Math.round(cx + 2 + wing), Math.round(cy), Colors.argb(0.5f, Colors.mixRgb(rgb, 0xFFFFFF, 0.6f)));
			}
			case 5 -> {
				Gui2D.glowDot(g, cx, cy, 4, rgb, 1f);
				Gui2D.ellipse(g, cx, cy, 10, 2.5f, 20, 1.2f, Colors.mixRgb(rgb, 0xFFFFFF, 0.5f), 0.8f, time * 0.5f, true);
			}
			case 6 -> {
				Gui2D.glowDot(g, cx, cy, 3, rgb, 0.6f);
				Gui2D.fill(g, Math.round(cx - 2), Math.round(cy - 6), Math.round(cx + 2), Math.round(cy + 6), Colors.argb(0.95f, rgb));
			}
			case 8 -> {
				Gui2D.glowDot(g, cx, cy, 5, rgb, 0.4f);
				Gui2D.ellipse(g, cx, cy - 1, 6, 4, 16, 1.4f, Colors.mixRgb(rgb, 0xFFFFFF, 0.4f), 0.8f, (float) Math.PI, false);

				for (int k = 0; k < 5; k++) {
					for (int j = 0; j < 5; j++) {
						Gui2D.dot(g, cx - 4 + k * 2 + (float) Math.sin(time * 2 - j * 0.6f + k) * 1.2f, cy + 2 + j * 2, 1f, Colors.argb(0.6f * (1f - j / 5f), Colors.mixRgb(rgb, 0xFFFFFF, 0.5f)));
					}
				}
			}
			case 9 -> {
				float wx = cx + (float) Math.cos(time * 0.6f) * 12;
				Gui2D.rounded(g, Math.round(wx - 8), Math.round(cy - 3), Math.round(wx + 6), Math.round(cy + 3), 0xFF4F6F96);
				Gui2D.fill(g, Math.round(wx - 7), Math.round(cy + 1), Math.round(wx + 5), Math.round(cy + 3), 0xFFDDE6EE);
				Gui2D.fill(g, Math.round(wx - 12), Math.round(cy - 3 + (float) Math.sin(time * 2) * 2), Math.round(wx - 8), Math.round(cy - 1 + (float) Math.sin(time * 2) * 2), 0xFF4F6F96);
			}
			case 10 -> {
				float wing = (float) Math.sin(time * 4.5f) * 4;
				Gui2D.fill(g, Math.round(cx - 3), Math.round(cy - 1), Math.round(cx + 3), Math.round(cy + 1), 0xFFF6F3EC);
				Gui2D.fill(g, Math.round(cx - 8), Math.round(cy - 2 - wing), Math.round(cx - 2), Math.round(cy), 0xFFE2DED4);
				Gui2D.fill(g, Math.round(cx + 2), Math.round(cy - 2 - wing), Math.round(cx + 8), Math.round(cy), 0xFFE2DED4);
				Gui2D.fill(g, Math.round(cx + 3), Math.round(cy - 6), Math.round(cx + 4), Math.round(cy - 1), 0xFFF6F3EC);
			}
			case 11 -> {
				Gui2D.rounded(g, Math.round(cx - 6), Math.round(cy - 2), Math.round(cx + 5), Math.round(cy + 2), 0xFFF5A9C4);
				Gui2D.rounded(g, Math.round(cx + 3), Math.round(cy - 4), Math.round(cx + 9), Math.round(cy + 2), 0xFFF5A9C4);
				Gui2D.fill(g, Math.round(cx + 2), Math.round(cy - 6), Math.round(cx + 3), Math.round(cy - 3), 0xFFD9385F);
				Gui2D.fill(g, Math.round(cx + 9), Math.round(cy - 6), Math.round(cx + 10), Math.round(cy - 3), 0xFFD9385F);
			}
			case 7 -> {
				Gui2D.glowDot(g, cx, cy, 6, 0x6FD3FF, 0.4f);
				Gui2D.ellipse(g, cx, cy, 8, 8, 24, 1f, 0xCFF3FF, 0.7f, 0, false);
				Gui2D.dot(g, cx + (float) Math.cos(time) * 3, cy + (float) Math.sin(time) * 1.5f, 2.5f, 0xFFE8552B);
			}
			case 2 -> {
				Gui2D.glowDot(g, cx, cy, 5, rgb, 1f);
				Gui2D.ellipse(g, cx, cy, 9, 3, 18, 1.2f, rgb, 0.9f, time * 1.3f, true);
			}
			case 3 -> {
				float bounce = (float) Math.abs(Math.sin(time * 3.2f)) * 3;
				Gui2D.rounded(g, Math.round(cx - 6), Math.round(cy - 5 - bounce), Math.round(cx + 6), Math.round(cy + 6 - bounce), Colors.argb(0.6f, rgb));
				Gui2D.fill(g, Math.round(cx - 3), Math.round(cy - 2 - bounce), Math.round(cx - 1), Math.round(cy - bounce), 0xFF101014);
				Gui2D.fill(g, Math.round(cx + 1), Math.round(cy - 2 - bounce), Math.round(cx + 3), Math.round(cy - bounce), 0xFF101014);
			}
			case 1 -> {
				Gui2D.rounded(g, Math.round(cx - 5), Math.round(cy - 3), Math.round(cx + 5), Math.round(cy + 4), Colors.argb(1f, rgb));
				Gui2D.rounded(g, Math.round(cx + 3), Math.round(cy - 7), Math.round(cx + 9), Math.round(cy - 1), Colors.argb(1f, rgb));
				float flap = (float) Math.sin(time * 7) * 3;
				Gui2D.fill(g, Math.round(cx - 4), Math.round(cy - 6 - flap), Math.round(cx + 2), Math.round(cy - 4 - flap), Colors.argb(1f, Colors.shade(rgb, -0.3f)));
			}
			default -> {
				Gui2D.glowDot(g, cx, cy, 6, rgb, 0.3f);
				Gui2D.rounded(g, Math.round(cx - 6), Math.round(cy - 7), Math.round(cx + 6), Math.round(cy + 7), 0xEEF4F4FF);
				Gui2D.fill(g, Math.round(cx - 3), Math.round(cy - 3), Math.round(cx - 1), Math.round(cy), 0xFF101014);
				Gui2D.fill(g, Math.round(cx + 1), Math.round(cy - 3), Math.round(cx + 3), Math.round(cy), 0xFF101014);
			}
		}
	}
}
