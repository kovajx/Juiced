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
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * Petals or leaves drift down around you, tumble in the wind and settle on the ground,
 * where they slowly fade. Walking stirs up a few more.
 */
public final class Petals extends Module {
	private static final int CAPACITY = 180;

	/** {base, tip} color pairs per style. */
	private static final int[][][] PALETTES = {
			{{0xF6A5C0, 0xFFE4EE}, {0xF28DB2, 0xFFD3E3}, {0xFFC2D6, 0xFFFFFF}},
			{{0xD9531E, 0xF2A33A}, {0xB8321F, 0xE8743B}, {0xE8A33A, 0xF7D46B}, {0x8F5A2A, 0xC98B4A}},
			{{0x9C7BD9, 0xD9C8FF}, {0xB59AE8, 0xEDE3FF}},
			{{0xC8102E, 0xFF5470}, {0xA0001E, 0xE8334F}},
			{{0xFFFFFF, 0xDDEBFF}, {0xEAF2FF, 0xFFFFFF}},
	};

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Art", 0, "Kirschblüten", "Herbstlaub", "Lavendel", "Rosen", "Schnee"));
	private final SliderSetting amount = add(SliderSetting.percent("amount", "Menge", 0.2, 2.0, 1.0));
	private final SliderSetting radius = add(new SliderSetting("radius", "Radius", 3, 12, 1, 6, v -> Math.round(v) + " Blöcke"));
	private final SliderSetting wind = add(SliderSetting.percent("wind", "Wind", 0.0, 2.0, 0.6));
	private final BoolSetting stir = add(new BoolSetting("stir", "Beim Laufen mehr", true));
	private final BoolSetting settle = add(new BoolSetting("settle", "Bleiben am Boden liegen", true));

	private final double[] x = new double[CAPACITY];
	private final double[] y = new double[CAPACITY];
	private final double[] z = new double[CAPACITY];
	private final double[] ground = new double[CAPACITY];
	private final float[] fall = new float[CAPACITY];
	private final float[] spin = new float[CAPACITY];
	private final float[] tumble = new float[CAPACITY];
	private final float[] phase = new float[CAPACITY];
	private final float[] size = new float[CAPACITY];
	private final float[] landed = new float[CAPACITY];
	private final float[] restYaw = new float[CAPACITY];
	private final byte[] colorIndex = new byte[CAPACITY];
	private final boolean[] alive = new boolean[CAPACITY];
	private float spawnBudget;
	private double groundY;
	private double lastX;
	private double lastZ;

	public Petals() {
		super("petals", "Blütenblätter", Category.VISUALS, false,
				"Kirschblüten, Herbstlaub, Lavendel, Rosenblätter oder Schnee rieseln ruhig um dich herab und bleiben kurz am Boden liegen.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(alive, false);
	}

	private void spawn(double cx, double cy, double cz, double r, boolean nearFeet) {
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < CAPACITY; i++) {
			if (alive[i]) {
				continue;
			}

			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * (nearFeet ? 1.2 : r);
			x[i] = cx + Math.cos(angle) * distance;
			z[i] = cz + Math.sin(angle) * distance;
			y[i] = nearFeet ? cy + 0.2 + random.nextDouble() * 0.8 : cy + 2.5 + random.nextDouble() * 2.5;
			ground[i] = groundY;
			fall[i] = 0.25f + random.nextFloat() * 0.25f;
			spin[i] = (random.nextFloat() - 0.5f) * 3f;
			tumble[i] = 1.5f + random.nextFloat() * 2.5f;
			phase[i] = random.nextFloat() * 6.28f;
			size[i] = 0.7f + random.nextFloat() * 0.6f;
			landed[i] = -1f;
			restYaw[i] = random.nextFloat() * 6.28f;
			colorIndex[i] = (byte) random.nextInt(8);
			alive[i] = true;
			return;
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		float dt = Math.min(0.1f, Clock.dt());
		float time = Clock.tf();
		double px = geo.x(player);
		double py = geo.y(player);
		double pz = geo.z(player);

		if (player.onGround()) {
			groundY = py;
		}

		double r = radius.get();
		double moved = Math.sqrt((px - lastX) * (px - lastX) + (pz - lastZ) * (pz - lastZ));
		lastX = px;
		lastZ = pz;

		// Calm, steady drizzle; a few more swirl up from your feet when you walk
		float rate = amount.f() * (float) (r * r) * 0.12f * (Theme.quality() + 1) / 3f;
		spawnBudget += rate * dt;

		while (spawnBudget >= 1f) {
			spawn(px, py, pz, r, false);
			spawnBudget -= 1f;
		}

		if (stir.on() && moved > 0.02 && moved < 3 && player.onGround() && ThreadLocalRandom.current().nextFloat() < moved * 1.5 * amount.f()) {
			spawn(px, groundY, pz, r, true);
		}

		int[][] palette = PALETTES[Math.min(PALETTES.length - 1, style.index())];
		boolean snow = style.index() == 4;
		float windX = (float) Math.cos(time * 0.05) * wind.f() * 0.35f;
		float windZ = (float) Math.sin(time * 0.05 + 1) * wind.f() * 0.35f;

		for (int i = 0; i < CAPACITY; i++) {
			if (!alive[i]) {
				continue;
			}

			double dx = x[i] - px;
			double dz = z[i] - pz;

			if (dx * dx + dz * dz > r * r * 2.5) {
				alive[i] = false;
				continue;
			}

			float alpha;

			if (landed[i] < 0f) {
				// Falling: drift with the wind and sway from side to side
				float sway = (float) Math.sin(time * 1.3f + phase[i]);
				x[i] += (windX + sway * 0.25f) * dt;
				z[i] += (windZ + (float) Math.cos(time * 1.1f + phase[i]) * 0.2f) * dt;
				y[i] -= fall[i] * dt;
				alpha = Math.min(1f, (float) (y[i] - ground[i]) * 2f);

				if (y[i] <= ground[i] + 0.02) {
					y[i] = ground[i] + 0.02;
					landed[i] = 0f;

					if (!settle.on()) {
						alive[i] = false;
						continue;
					}
				}
			} else {
				landed[i] += dt;
				float rest = snow ? 2f : 5f;

				if (landed[i] > rest) {
					alive[i] = false;
					continue;
				}

				alpha = landed[i] > rest - 1.5f ? (rest - landed[i]) / 1.5f : 1f;
			}

			int[] colors = palette[colorIndex[i] % palette.length];
			float s = size[i] * (snow ? 0.6f : 1f);

			if (snow) {
				geo.glowDot(x[i], y[i], z[i], 0.04f * s, colors[0], alpha * 0.9f);
			} else if (landed[i] < 0f) {
				drawFalling(geo, i, time, s, colors, alpha);
			} else {
				drawLanded(geo, i, s, colors, alpha * 0.9f);
			}
		}
	}

	/** A petal tumbling in the air: its flat side turns over and over. */
	private void drawFalling(Geo geo, int i, float time, float s, int[] colors, float alpha) {
		float yaw = phase[i] + time * spin[i];
		float flip = time * tumble[i] + phase[i];
		// Long axis lies horizontal, the petal flips around it
		float ax = (float) Math.cos(yaw);
		float az = (float) Math.sin(yaw);
		float c = (float) Math.cos(flip);
		float sn = (float) Math.sin(flip);
		// Width axis rotates around the long axis between horizontal and vertical
		float bx = -az * c;
		float by = sn;
		float bz = ax * c;
		petal(geo, x[i], y[i], z[i], ax, 0f, az, bx, by, bz, s, colors, alpha);
	}

	private void drawLanded(Geo geo, int i, float s, int[] colors, float alpha) {
		float ax = (float) Math.cos(restYaw[i]);
		float az = (float) Math.sin(restYaw[i]);
		petal(geo, x[i], y[i], z[i], ax, 0f, az, -az, 0f, ax, s, colors, alpha);
	}

	/** Kite-shaped petal along axis a with width along b. */
	private void petal(Geo geo, double cx, double cy, double cz, float ax, float ay, float az, float bx, float by, float bz, float s, int[] colors, float alpha) {
		boolean leaf = style.index() == 1;
		float length = (leaf ? 0.09f : 0.06f) * s;
		float width = (leaf ? 0.05f : 0.04f) * s;
		int base = Colors.argb(alpha, colors[0]);
		int tip = Colors.argb(alpha, colors[1]);
		double backX = cx - ax * length;
		double backY = cy - ay * length;
		double backZ = cz - az * length;
		double frontX = cx + ax * length;
		double frontY = cy + ay * length;
		double frontZ = cz + az * length;
		double midX = cx - ax * length * 0.2;
		double midY = cy - ay * length * 0.2;
		double midZ = cz - az * length * 0.2;
		geo.quadWorld(backX, backY, backZ, base,
				midX + bx * width, midY + by * width, midZ + bz * width, base,
				frontX, frontY, frontZ, tip,
				midX - bx * width, midY - by * width, midZ - bz * width, base);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		int[][] palette = PALETTES[Math.min(PALETTES.length - 1, style.index())];
		java.util.Random random = new java.util.Random(3);

		for (int i = 0; i < 16; i++) {
			float bx = px + 6 + random.nextFloat() * (w - 12);
			float ph = random.nextFloat() * 6.28f;
			float speed = 6 + random.nextFloat() * 6;
			float fy = py + ((random.nextFloat() * h + time * speed) % h);
			float fx = bx + (float) Math.sin(time * 1.3f + ph) * 4;
			float flip = Math.abs((float) Math.sin(time * 2.5f + ph));
			int[] colors = palette[i % palette.length];
			int c = Colors.argb(1f, Colors.mixRgb(colors[0], colors[1], flip * 0.6f));
			Gui2D.fill(g, Math.round(fx - 2), Math.round(fy - 1), Math.round(fx + 2), Math.round(fy - 1 + Math.max(1, flip * 3)), c);
		}
	}
}
