package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.NearbyBlocks;

/**
 * Sparks rise from campfires and the air above them shimmers with heat; candles and lanterns
 * get a soft, flickering glow around them.
 */
public final class CozyLights extends Module {
	private static final int MAX_SPARKS = 160;

	private final SliderSetting sparks = add(SliderSetting.percent("sparks", "Funken", 0.0, 2.0, 1.0));
	private final BoolSetting haze = add(new BoolSetting("haze", "Hitzeflimmern", true));
	private final BoolSetting candles = add(new BoolSetting("candles", "Kerzen & Laternen leuchten", true));

	private final double[] sx = new double[MAX_SPARKS];
	private final double[] sy = new double[MAX_SPARKS];
	private final double[] sz = new double[MAX_SPARKS];
	private final float[] svx = new float[MAX_SPARKS];
	private final float[] svy = new float[MAX_SPARKS];
	private final float[] svz = new float[MAX_SPARKS];
	private final float[] sAge = new float[MAX_SPARKS];
	private final float[] sLife = new float[MAX_SPARKS];
	private final boolean[] sSoul = new boolean[MAX_SPARKS];
	private int sHead;
	private float budget;

	private final float[] hazeX = new float[8];
	private final float[] hazeY = new float[8];
	private final float[] hazeZ = new float[8];
	private final float[] hazeW = new float[8];
	private final int[] hazeC = new int[8];

	public CozyLights() {
		super("cozy_lights", "Lichter & Lagerfeuer", Category.VISUALS, true,
				"Funken steigen aus Lagerfeuern, darüber flimmert die Luft vor Wärme, und Kerzen und Laternen bekommen einen weichen, flackernden Lichtschein.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(sLife, 0f);
	}

	@Override
	public void tick(Minecraft minecraft) {
		NearbyBlocks.update(minecraft);
	}

	@Override
	public void renderWorld(Geo geo) {
		float time = Clock.tf();
		float dt = Math.min(0.1f, Clock.dt());
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int fires = NearbyBlocks.count(NearbyBlocks.CAMPFIRE);
		budget += dt * 7f * sparks.f() * fires * (Theme.quality() + 1) / 3f;

		while (budget >= 1f && fires > 0) {
			budget -= 1f;
			int f = random.nextInt(fires);
			int i = sHead;
			sx[i] = NearbyBlocks.x(NearbyBlocks.CAMPFIRE, f) + 0.3 + random.nextDouble() * 0.4;
			sy[i] = NearbyBlocks.y(NearbyBlocks.CAMPFIRE, f) + 0.5;
			sz[i] = NearbyBlocks.z(NearbyBlocks.CAMPFIRE, f) + 0.3 + random.nextDouble() * 0.4;
			svx[i] = (random.nextFloat() - 0.5f) * 0.3f;
			svy[i] = 0.7f + random.nextFloat() * 0.8f;
			svz[i] = (random.nextFloat() - 0.5f) * 0.3f;
			sAge[i] = 0f;
			sLife[i] = 1.3f + random.nextFloat() * 1.4f;
			sSoul[i] = NearbyBlocks.data(NearbyBlocks.CAMPFIRE, f) == 1;
			sHead = (sHead + 1) % MAX_SPARKS;
		}

		for (int i = 0; i < MAX_SPARKS; i++) {
			if (sLife[i] <= 0f) {
				continue;
			}

			sAge[i] += dt;

			if (sAge[i] >= sLife[i]) {
				sLife[i] = 0f;
				continue;
			}

			float t = sAge[i] / sLife[i];
			// Sparks wander as they rise and slow down
			svx[i] += (random.nextFloat() - 0.5f) * dt * 1.6f;
			svz[i] += (random.nextFloat() - 0.5f) * dt * 1.6f;
			svy[i] *= 1f - dt * 0.6f;
			sx[i] += svx[i] * dt;
			sy[i] += svy[i] * dt;
			sz[i] += svz[i] * dt;
			float flicker = 0.6f + 0.4f * (float) Math.sin(time * 25f + i);
			int rgb = sSoul[i] ? Colors.mixRgb(0x7FE8FF, 0x2E7FA8, t) : Colors.mixRgb(0xFFD36B, 0xE8461C, t);
			geo.glowDot(sx[i], sy[i], sz[i], 0.035f * (1f - t * 0.5f), rgb, (1f - t) * flicker);
		}

		for (int f = 0; f < fires; f++) {
			double x = NearbyBlocks.x(NearbyBlocks.CAMPFIRE, f) + 0.5;
			double y = NearbyBlocks.y(NearbyBlocks.CAMPFIRE, f);
			double z = NearbyBlocks.z(NearbyBlocks.CAMPFIRE, f) + 0.5;

			if (geo.distanceSq(x, y, z) > 40 * 40) {
				continue;
			}

			boolean soul = NearbyBlocks.data(NearbyBlocks.CAMPFIRE, f) == 1;
			float flicker = 0.8f + 0.12f * (float) Math.sin(time * 7f + f) + 0.08f * (float) Math.sin(time * 17f + f * 2);
			geo.glowDot(x, y + 0.6, z, 1.3f, soul ? 0x5FD8FF : 0xFF9A3D, 0.16f * flicker);

			if (haze.on()) {
				heatHaze(geo, x, y + 0.9, z, time + f * 3.1f, soul);
			}
		}

		if (candles.on()) {
			int count = NearbyBlocks.count(NearbyBlocks.CANDLE);

			for (int c = 0; c < count; c++) {
				float flicker = 0.75f + 0.25f * (float) Math.sin(time * 11f + c * 1.7f) * (float) Math.sin(time * 5.3f + c);
				geo.glowDot(NearbyBlocks.x(NearbyBlocks.CANDLE, c) + 0.5, NearbyBlocks.y(NearbyBlocks.CANDLE, c) + 0.55, NearbyBlocks.z(NearbyBlocks.CANDLE, c) + 0.5,
						0.32f, 0xFFC56B, 0.28f * flicker);
			}

			count = NearbyBlocks.count(NearbyBlocks.LANTERN);

			for (int l = 0; l < count; l++) {
				boolean soul = NearbyBlocks.data(NearbyBlocks.LANTERN, l) == 1;
				float flicker = 0.85f + 0.15f * (float) Math.sin(time * 6f + l * 2.3f);
				geo.glowDot(NearbyBlocks.x(NearbyBlocks.LANTERN, l) + 0.5, NearbyBlocks.y(NearbyBlocks.LANTERN, l) + 0.4, NearbyBlocks.z(NearbyBlocks.LANTERN, l) + 0.5,
						0.75f, soul ? 0x7FE8FF : 0xFFB45A, 0.18f * flicker);
			}
		}
	}

	/** A few faint, wavy columns of warm air that make it look like the air is shimmering. */
	private void heatHaze(Geo geo, double x, double y, double z, float time, boolean soul) {
		int rgb = soul ? 0x9FEFFF : 0xFFE2B0;

		for (int column = 0; column < 3; column++) {
			double offset = (column - 1) * 0.18;
			int n = hazeX.length;

			for (int j = 0; j < n; j++) {
				float t = j / (float) (n - 1);
				double sway = Math.sin(time * 3.2f + j * 0.9f + column * 2f) * 0.08 * t;
				hazeX[j] = geo.rx(x + offset + sway);
				hazeY[j] = geo.ry(y + t * 1.4);
				hazeZ[j] = geo.rz(z + Math.cos(time * 2.7f + j + column) * 0.05 * t);
				hazeW[j] = 0.11f * (1f - t * 0.4f);
				hazeC[j] = Colors.argb(0.06f * (float) Math.sin(t * Math.PI), rgb);
			}

			geo.ribbon(hazeX, hazeY, hazeZ, hazeW, hazeC, n);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float cx = px + w / 2f;
		float base = py + h - 10;
		Gui2D.glowDot(g, cx, base - 6, 10, 0xFF9A3D, 0.3f);
		Gui2D.fill(g, Math.round(cx - 10), Math.round(base - 2), Math.round(cx + 10), Math.round(base + 1), 0xFF6B4A2E);

		for (int i = 0; i < 4; i++) {
			float flick = (float) Math.sin(time * 12 + i) * 1.5f;
			Gui2D.fill(g, Math.round(cx - 6 + i * 3), Math.round(base - 9 - flick - (i % 2) * 3), Math.round(cx - 4 + i * 3), Math.round(base - 2), 0xFFFFB347);
		}

		java.util.Random random = new java.util.Random(9);

		for (int i = 0; i < 8; i++) {
			float t = (time * 0.5f + random.nextFloat()) % 1f;
			float sx = cx + (random.nextFloat() - 0.5f) * 14 + (float) Math.sin(time * 3 + i) * 3;
			Gui2D.dot(g, sx, base - 10 - t * (h - 20), 1.5f, Colors.argb(1f - t, Colors.mixRgb(0xFFD36B, 0xE8461C, t)));
		}
	}
}
