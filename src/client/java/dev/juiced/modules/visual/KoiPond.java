package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Koi;

/**
 * Koi swim behind you in a little stream on the ground and leave small ponds with lily pads
 * along your way. Stand still and they gather in a pond around your feet and circle you.
 */
public final class KoiPond extends Module {
	private static final int PATH = 200;
	private static final double PATH_STEP = 0.2;
	private static final int MAX_FISH = 6;
	private static final int MAX_RIPPLES = 48;
	private static final int MAX_PONDS = 14;
	private static final float POND_LIFE = 11f;
	private static final float RIPPLE_LIFE = 2.2f;
	private static final double TAU = Math.PI * 2;

	private final ChoiceSetting look = add(new ChoiceSetting("look", "Fische", 0, Koi.LOOKS));
	private final SliderSetting count = add(new SliderSetting("count", "Anzahl", 1, MAX_FISH, 1, 3, v -> String.valueOf(Math.round(v))));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final ColorSetting water = add(new ColorSetting("water", "Wasserfarbe", 0, true));
	private final BoolSetting stream = add(new BoolSetting("stream", "Wasser unter den Fischen", true));
	private final BoolSetting ponds = add(new BoolSetting("ponds", "Teiche als Spur", true));
	private final BoolSetting lilies = add(new BoolSetting("lilies", "Seerosen & Lotus", true));

	// Your path on the ground, newest at pathHead - 1
	private final double[] pathX = new double[PATH];
	private final double[] pathY = new double[PATH];
	private final double[] pathZ = new double[PATH];
	private int pathHead;
	private int pathCount;
	private double groundY;
	private boolean hasGround;
	private double lastX;
	private double lastZ;
	private double stillSince;
	private float idle;
	private double sincePond;

	private final double[] fishX = new double[MAX_FISH];
	private final double[] fishY = new double[MAX_FISH];
	private final double[] fishZ = new double[MAX_FISH];
	private final float[] heading = new float[MAX_FISH];
	private final float[] swim = new float[MAX_FISH];
	private final float[] phase = new float[MAX_FISH];
	private final double[] nextRipple = new double[MAX_FISH];
	private final int[][] colors = new int[MAX_FISH][Koi.SEGMENTS + 1];
	private int patternLook = -1;
	private boolean placed;

	private final double[] rippleX = new double[MAX_RIPPLES];
	private final double[] rippleY = new double[MAX_RIPPLES];
	private final double[] rippleZ = new double[MAX_RIPPLES];
	private final double[] rippleBorn = new double[MAX_RIPPLES];
	private final float[] rippleSize = new float[MAX_RIPPLES];
	private int rippleHead;

	private final double[] pondX = new double[MAX_PONDS];
	private final double[] pondY = new double[MAX_PONDS];
	private final double[] pondZ = new double[MAX_PONDS];
	private final double[] pondBorn = new double[MAX_PONDS];
	private final float[] pondRadius = new float[MAX_PONDS];
	private final float[] pondSeed = new float[MAX_PONDS];
	private final byte[] pondFlags = new byte[MAX_PONDS];
	private final double[] pondNextRipple = new double[MAX_PONDS];
	private final int[] pondFish = new int[Koi.SEGMENTS + 1];
	private int pondHead;

	private final double[] point = new double[5];

	public KoiPond() {
		super("koi_pond", "Koi-Teich", Category.VISUALS, false,
				"Koi schwimmen hinter dir in einem kleinen Bach über den Boden und lassen Teiche mit Seerosen zurück. "
						+ "Bleibst du stehen, sammeln sie sich in einem Teich um dich und ziehen ruhig ihre Kreise.");
	}

	@Override
	protected void onDisable() {
		placed = false;
		pathCount = 0;
		hasGround = false;
		java.util.Arrays.fill(rippleBorn, 0);
		java.util.Arrays.fill(pondBorn, 0);
	}

	// ---- path ------------------------------------------------------------------------------

	private void record(double x, double y, double z) {
		if (pathCount > 0) {
			int newest = (pathHead - 1 + PATH) % PATH;
			double dx = x - pathX[newest];
			double dz = z - pathZ[newest];

			if (dx * dx + dz * dz < PATH_STEP * PATH_STEP) {
				return;
			}
		}

		pathX[pathHead] = x;
		pathY[pathHead] = y;
		pathZ[pathHead] = z;
		pathHead = (pathHead + 1) % PATH;
		pathCount = Math.min(PATH, pathCount + 1);
	}

	/**
	 * The point {@code distance} blocks back along your path, starting at (x, y, z).
	 * Writes {x, y, z, dirX, dirZ} (direction towards you) and returns false if the path ran out.
	 */
	private boolean along(double x, double y, double z, double distance, double[] out) {
		double ax = x;
		double ay = y;
		double az = z;
		double remaining = distance;
		double dirX = out[3];
		double dirZ = out[4];

		for (int n = 0; n < pathCount; n++) {
			int index = (pathHead - 1 - n + PATH * 2) % PATH;
			double bx = pathX[index];
			double by = pathY[index];
			double bz = pathZ[index];
			double sx = ax - bx;
			double sz = az - bz;
			double segment = Math.sqrt(sx * sx + sz * sz);

			if (segment > 1.0e-6) {
				dirX = sx / segment;
				dirZ = sz / segment;

				if (segment >= remaining) {
					double t = remaining / segment;
					out[0] = ax - sx * t;
					out[1] = ay + (by - ay) * t;
					out[2] = az - sz * t;
					out[3] = dirX;
					out[4] = dirZ;
					return true;
				}

				remaining -= segment;
			}

			ax = bx;
			ay = by;
			az = bz;
		}

		out[0] = ax;
		out[1] = ay;
		out[2] = az;
		out[3] = dirX;
		out[4] = dirZ;
		return false;
	}

	private double fishDistance(int i, float s) {
		return (0.9 + i * 0.7) * s;
	}

	// ---- simulation and drawing ---------------------------------------------------------------

	@Override
	public void renderWorld(Geo geo) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		double now = Clock.t();
		float time = Clock.tf();
		float dt = Math.min(0.1f, Clock.dt());
		float s = size.f();
		int n = Math.max(1, Math.min(MAX_FISH, count.i()));
		double x = geo.x(player);
		double y = geo.y(player);
		double z = geo.z(player);

		if (player.onGround() || !hasGround) {
			groundY = y;
			hasGround = true;
		}

		double moved = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ));

		// Teleported or just switched on: start fresh
		if (!placed || moved > 8) {
			pathCount = 0;
			stillSince = now;

			for (int i = 0; i < MAX_FISH; i++) {
				fishX[i] = x - 0.5 - i * 0.4;
				fishY[i] = groundY;
				fishZ[i] = z;
				phase[i] = i * 2.1f;
			}

			placed = true;
			moved = 0;
		}

		if (patternLook != look.index()) {
			for (int i = 0; i < MAX_FISH; i++) {
				Koi.pattern(look.index(), i * 7919L + 13, colors[i]);
			}

			patternLook = look.index();
		}

		lastX = x;
		lastZ = z;

		if (moved > 0.004) {
			stillSince = now;
		}

		record(x, groundY, z);
		boolean resting = now - stillSince > 0.9;
		idle = Clock.approach(idle, resting ? 1f : 0f, resting ? 1.2f : 3f);

		int waterRgb = Colors.mixRgb(water.rgb(), 0x0E3550, 0.4f);
		int lightRgb = Colors.mixRgb(water.rgb(), 0xFFFFFF, 0.65f);

		// Leave a pond every few blocks
		sincePond += moved;

		if (ponds.on() && sincePond > 2.4 && player.onGround() && !player.isInWater()) {
			spawnPond(x, groundY, z, s);
			sincePond = 0;
		}

		drawPonds(geo, now, time, s, waterRgb, lightRgb);

		if (idle > 0.01f) {
			drawRestingPond(geo, x, groundY, z, s, time, waterRgb, lightRgb);
		}

		if (stream.on() && idle < 0.99f) {
			drawStream(geo, x, groundY, z, n, s, time, waterRgb, (1f - idle));
		}

		updateFish(x, groundY, z, n, s, now, time, dt);
		drawRipples(geo, now, lightRgb);

		boolean ghost = look.index() == 2;

		for (int i = 0; i < n; i++) {
			float fx = (float) Math.cos(heading[i]);
			float fz = (float) Math.sin(heading[i]);
			float alpha = ghost ? 0.62f : 0.96f;

			if (ghost) {
				geo.glowDot(fishX[i], fishY[i] + 0.05, fishZ[i], 0.22f * s, 0x8FE3FF, 0.3f);
			}

			Koi.draw(geo, fishX[i], fishY[i] + 0.03, fishZ[i], fx, 0f, fz, -fz, 0f, fx,
					0.42f * s, time, phase[i], swim[i], colors[i], alpha, 0.035 * s, -0.012, 0.03 * s);
		}
	}

	private void updateFish(double x, double y, double z, int n, float s, double now, float time, float dt) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float follow = 1f - (float) Math.exp(-2.6f * dt);

		for (int i = 0; i < n; i++) {
			// Where this fish wants to be: in line behind you, or circling you while you rest
			point[3] = 1;
			point[4] = 0;
			along(x, y, z, fishDistance(i, s), point);
			double perpX = -point[4];
			double perpZ = point[3];
			double weave = Math.sin(time * 0.9 + phase[i]) * 0.26 * s;
			double followX = point[0] + perpX * weave;
			double followZ = point[2] + perpZ * weave;
			double angle = time * 0.42 + i * TAU / n;
			double radius = (1.0 + (i % 2) * 0.28) * s;
			double circleX = x + Math.cos(angle) * radius;
			double circleZ = z + Math.sin(angle) * radius;
			double targetX = followX + (circleX - followX) * idle;
			double targetZ = followZ + (circleZ - followZ) * idle;
			double targetY = point[1] + (y - point[1]) * idle;

			double oldX = fishX[i];
			double oldZ = fishZ[i];
			fishX[i] += (targetX - fishX[i]) * follow;
			fishY[i] += (targetY - fishY[i]) * follow;
			fishZ[i] += (targetZ - fishZ[i]) * follow;

			double vx = fishX[i] - oldX;
			double vz = fishZ[i] - oldZ;
			double speed = Math.sqrt(vx * vx + vz * vz) / Math.max(1.0e-3, dt);
			float wantHeading = heading[i];

			if (speed > 0.06) {
				wantHeading = (float) Math.atan2(vz, vx);
			} else if (idle > 0.5f) {
				wantHeading = (float) (angle + Math.PI / 2);
			}

			float delta = (float) ((wantHeading - heading[i] + Math.PI * 3) % TAU - Math.PI);
			heading[i] += delta * (1f - (float) Math.exp(-5f * dt));
			swim[i] = Clock.approach(swim[i], (float) Math.max(0.35, Math.min(1.5, speed / 1.6)), 3f);

			if (now > nextRipple[i]) {
				spawnRipple(fishX[i], fishY[i], fishZ[i], s * 0.9f);
				nextRipple[i] = now + 1.3 + random.nextDouble() * 1.6;
			}
		}
	}

	private void spawnRipple(double x, double y, double z, float strength) {
		rippleX[rippleHead] = x;
		rippleY[rippleHead] = y;
		rippleZ[rippleHead] = z;
		rippleBorn[rippleHead] = Clock.t();
		rippleSize[rippleHead] = strength;
		rippleHead = (rippleHead + 1) % MAX_RIPPLES;
	}

	private void drawRipples(Geo geo, double now, int lightRgb) {
		int segments = Theme.detail(28);

		for (int i = 0; i < MAX_RIPPLES; i++) {
			if (rippleBorn[i] == 0) {
				continue;
			}

			float t = (float) ((now - rippleBorn[i]) / RIPPLE_LIFE);

			if (t >= 1f || t < 0f) {
				continue;
			}

			float eased = 1f - (1f - t) * (1f - t);
			float radius = (0.05f + 0.5f * eased) * rippleSize[i];
			float alpha = 0.5f * (1f - t);
			geo.groundRing(rippleX[i], rippleY[i] + 0.024, rippleZ[i], radius, 0.014f, 0f, segments, 0f, (float) TAU, c -> lightRgb, alpha);

			// A second, smaller ring a moment later
			if (t > 0.18f) {
				float t2 = (t - 0.18f) / 0.82f;
				float r2 = (0.03f + 0.32f * (1f - (1f - t2) * (1f - t2))) * rippleSize[i];
				geo.groundRing(rippleX[i], rippleY[i] + 0.024, rippleZ[i], r2, 0.01f, 0f, segments, 0f, (float) TAU, c -> lightRgb, alpha * 0.6f);
			}
		}
	}

	/** A soft round pool of water: flat inside, fading out at the rim. */
	private static void pool(Geo geo, double x, double y, double z, float radius, int rgb, float alpha) {
		int segments = Theme.detail(32);
		float px = geo.rx(x);
		float py = geo.ry(y);
		float pz = geo.rz(z);
		geo.band(px, py, pz, 1, 0, 0, 0, 0, 1, 0f, radius * 0.72f, segments, 0f, (float) TAU, c -> rgb, alpha, alpha, 0f);
		geo.band(px, py, pz, 1, 0, 0, 0, 0, 1, radius * 0.72f, radius, segments, 0f, (float) TAU, c -> rgb, alpha, 0f, 0f);
	}

	private void spawnPond(double x, double y, double z, float s) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int i = pondHead;
		pondX[i] = x + (random.nextDouble() - 0.5) * 0.3;
		pondY[i] = y;
		pondZ[i] = z + (random.nextDouble() - 0.5) * 0.3;
		pondBorn[i] = Clock.t();
		pondRadius[i] = (0.45f + random.nextFloat() * 0.25f) * s;
		pondSeed[i] = random.nextFloat() * 1000f;
		int flags = 0;

		if (random.nextFloat() < 0.75f) {
			flags |= 1; // little koi
		}

		if (random.nextFloat() < 0.6f) {
			flags |= 2; // lily pad
		}

		if (random.nextFloat() < 0.45f) {
			flags |= 4; // lotus on the pad
		}

		pondFlags[i] = (byte) flags;
		pondNextRipple[i] = Clock.t() + 0.5;
		pondHead = (pondHead + 1) % MAX_PONDS;
	}

	private void drawPonds(Geo geo, double now, float time, float s, int waterRgb, int lightRgb) {
		for (int i = 0; i < MAX_PONDS; i++) {
			if (pondBorn[i] == 0) {
				continue;
			}

			float age = (float) (now - pondBorn[i]);

			if (age > POND_LIFE || age < 0f) {
				continue;
			}

			// Grow in, then slowly fade away
			float grow = Math.min(1f, age / 0.6f);
			float fade = age > POND_LIFE - 3.5f ? (POND_LIFE - age) / 3.5f : 1f;
			float radius = pondRadius[i] * (0.6f + 0.4f * Clock.easeOut(grow));
			float alpha = grow * fade;
			pool(geo, pondX[i], pondY[i] + 0.012, pondZ[i], radius, waterRgb, 0.5f * alpha);

			if ((pondFlags[i] & 1) != 0) {
				float seed = pondSeed[i];
				float direction = ((int) seed) % 2 == 0 ? 1f : -1f;
				double a = time * 0.75 * direction + seed;
				double r = radius * 0.5;
				double fx = pondX[i] + Math.cos(a) * r;
				double fz = pondZ[i] + Math.sin(a) * r;
				float h = (float) (a + direction * Math.PI / 2);
				Koi.pattern(look.index(), (long) seed, pondFish);
				Koi.draw(geo, fx, pondY[i] + 0.026, fz, (float) Math.cos(h), 0f, (float) Math.sin(h), -(float) Math.sin(h), 0f, (float) Math.cos(h),
						0.26f * s, time, seed, 0.6f, pondFish, alpha * (look.index() == 2 ? 0.6f : 0.95f), 0.02 * s, -0.01, 0.02 * s);
			}

			if (lilies.on() && (pondFlags[i] & 2) != 0) {
				double la = pondSeed[i] * 0.37;
				double lx = pondX[i] + Math.cos(la) * radius * 0.45;
				double lz = pondZ[i] + Math.sin(la) * radius * 0.45;
				lilyPad(geo, lx, pondY[i] + 0.03, lz, 0.13f * s, (float) (la * 2.3), alpha);

				if ((pondFlags[i] & 4) != 0) {
					lotus(geo, lx, pondY[i] + 0.035, lz, 0.9f * s, (float) la, alpha);
				}
			}

			if (now > pondNextRipple[i] && fade > 0.5f) {
				double a = pondSeed[i] + now * 1.7;
				spawnRipple(pondX[i] + Math.cos(a) * radius * 0.35, pondY[i], pondZ[i] + Math.sin(a) * radius * 0.35, s * 0.6f);
				pondNextRipple[i] = now + 1.8 + ThreadLocalRandom.current().nextDouble() * 2.0;
			}
		}
	}

	private void drawRestingPond(Geo geo, double x, double y, double z, float s, float time, int waterRgb, int lightRgb) {
		float radius = 1.75f * s * (0.5f + 0.5f * Clock.easeOut(idle));
		pool(geo, x, y + 0.012, z, radius, waterRgb, 0.5f * idle);
		geo.groundRing(x, y + 0.016, z, radius * 0.86f, 0.012f, 0.03f, Theme.detail(40), time * 0.05f, (float) TAU, c -> lightRgb, 0.22f * idle);

		if (lilies.on()) {
			for (int k = 0; k < 3; k++) {
				double a = k * 2.1 + 0.6;
				double r = radius * (0.72 + 0.08 * k);
				double lx = x + Math.cos(a) * r;
				double lz = z + Math.sin(a) * r;
				lilyPad(geo, lx, y + 0.03, lz, (0.13f + k * 0.02f) * s, (float) (a * 3), idle);

				if (k != 1) {
					lotus(geo, lx, y + 0.035, lz, s, (float) a, idle);
				}
			}
		}
	}

	private void drawStream(Geo geo, double x, double y, double z, int n, float s, float time, int waterRgb, float strength) {
		double end = fishDistance(n - 1, s) + 0.9 * s;
		float halfWidth = 0.34f * s;
		double prevLX = 0;
		double prevLZ = 0;
		double prevRX = 0;
		double prevRZ = 0;
		double prevOLX = 0;
		double prevOLZ = 0;
		double prevORX = 0;
		double prevORZ = 0;
		double prevY = 0;
		float prevAlpha = 0;
		boolean hasPrev = false;
		point[3] = 1;
		point[4] = 0;
		int j = 0;

		for (double d = 0; d <= end; d += 0.25, j++) {
			boolean ok = along(x, y, z, d, point);
			double cx = point[0];
			double cz = point[2];
			double cy = point[1] + 0.012;
			double px = -point[4];
			double pz = point[3];
			float in = (float) Math.min(1.0, d / 0.6);
			float out = (float) Math.min(1.0, (end - d) / 1.1);
			float alpha = 0.45f * strength * in * out;
			double lx = cx + px * halfWidth;
			double lz = cz + pz * halfWidth;
			double rx = cx - px * halfWidth;
			double rz = cz - pz * halfWidth;
			double olx = cx + px * halfWidth * 1.55;
			double olz = cz + pz * halfWidth * 1.55;
			double orx = cx - px * halfWidth * 1.55;
			double orz = cz - pz * halfWidth * 1.55;

			if (hasPrev) {
				int c0 = Colors.argb(prevAlpha, waterRgb);
				int c1 = Colors.argb(alpha, waterRgb);
				int e0 = Colors.argb(0f, waterRgb);
				geo.quadWorld(prevLX, prevY, prevLZ, c0, prevRX, prevY, prevRZ, c0, rx, cy, rz, c1, lx, cy, lz, c1);
				geo.quadWorld(prevOLX, prevY, prevOLZ, e0, prevLX, prevY, prevLZ, c0, lx, cy, lz, c1, olx, cy, olz, e0);
				geo.quadWorld(prevRX, prevY, prevRZ, c0, prevORX, prevY, prevORZ, e0, orx, cy, orz, e0, rx, cy, rz, c1);
			}

			// Little glints of light on the water
			float glint = (float) Math.sin(time * 2.1 + j * 1.7);

			if (glint > 0.93f && alpha > 0.1f) {
				double side = Math.sin(j * 3.3) * halfWidth * 0.6;
				geo.star(cx + px * side, cy + 0.01, cz + pz * side, 0.05f * s, time + j, 0xFFFFFF, (glint - 0.93f) / 0.07f * 0.7f * strength);
			}

			prevLX = lx;
			prevLZ = lz;
			prevRX = rx;
			prevRZ = rz;
			prevOLX = olx;
			prevOLZ = olz;
			prevORX = orx;
			prevORZ = orz;
			prevY = cy;
			prevAlpha = alpha;
			hasPrev = true;

			if (!ok) {
				break;
			}
		}
	}

	/** Round green leaf with a notch, lying on the water. */
	static void lilyPad(Geo geo, double x, double y, double z, float radius, float notch, float alpha) {
		int segments = 14;
		float gap = 0.35f;
		float start = notch + gap;
		float step = (float) ((TAU - gap * 2) / segments);
		int center = Colors.argb(alpha * 0.95f, 0x6DBA5E);
		int rim = Colors.argb(alpha * 0.95f, 0x2F7A3A);

		for (int k = 0; k < segments; k++) {
			double a0 = start + k * step;
			double a1 = a0 + step;
			geo.triWorld(x, y, z, center, x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius, rim, x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, rim);
		}
	}

	/** A small pink lotus flower: an outer and an inner ring of petals around a yellow middle. */
	static void lotus(Geo geo, double x, double y, double z, float s, float rotation, float alpha) {
		int base = Colors.argb(alpha, 0xF07AA8);
		int tip = Colors.argb(alpha, 0xFFE6F0);

		for (int ring = 0; ring < 2; ring++) {
			int petals = ring == 0 ? 8 : 5;
			double reach = (ring == 0 ? 0.11 : 0.065) * s;
			double lift = (ring == 0 ? 0.045 : 0.08) * s;
			double width = (ring == 0 ? 0.035 : 0.028) * s;

			for (int k = 0; k < petals; k++) {
				double a = rotation + k * TAU / petals + ring * 0.4;
				double cos = Math.cos(a);
				double sin = Math.sin(a);
				double midX = x + cos * reach * 0.5;
				double midZ = z + sin * reach * 0.5;
				double midY = y + lift * 0.4;
				geo.quadWorld(x, y, z, base,
						midX - sin * width, midY, midZ + cos * width, base,
						x + cos * reach, y + lift, z + sin * reach, tip,
						midX + sin * width, midY, midZ - cos * width, base);
			}
		}

		geo.glowDot(x, y + 0.03 * s, z, 0.03f * s, 0xFFD54F, alpha);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int waterRgb = Colors.mixRgb(water.rgb(), 0x0E3550, 0.4f);
		float cx = x + w / 2f;
		float cy = y + h / 2f + 2;
		Gui2D.rounded2(g, Math.round(cx - 34), Math.round(cy - 15), Math.round(cx + 34), Math.round(cy + 15), Colors.argb(0.55f, waterRgb));
		Gui2D.ellipse(g, cx, cy, 30 * (0.3f + (time * 0.4f) % 0.7f), 11 * (0.3f + (time * 0.4f) % 0.7f), 24, 1f, Colors.mixRgb(water.rgb(), 0xFFFFFF, 0.6f), 0.5f, 0, false);
		int[] fish = new int[Koi.SEGMENTS + 1];

		for (int i = 0; i < 2; i++) {
			Koi.pattern(look.index(), i * 7919L + 13, fish);
			double a = time * 0.8 + i * Math.PI;

			for (int k = 0; k < 6; k++) {
				double b = a - k * 0.16;
				float fx = cx + (float) Math.cos(b) * 22;
				float fy = cy + (float) Math.sin(b) * 8 + (float) Math.sin(time * 6 - k) * 0.6f;
				Gui2D.dot(g, fx, fy, k == 0 ? 3.2f : 3.4f - k * 0.45f, Colors.argb(1f, fish[Math.min(Koi.SEGMENTS, k + 1)]));
			}
		}

		Gui2D.glowDot(g, cx + 20, cy - 9, 2.5f, 0x6DBA5E, 0.9f);
	}
}
