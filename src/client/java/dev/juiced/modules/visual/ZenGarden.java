package dev.juiced.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/**
 * Walking over sand or gravel leaves raked lines like in a Japanese rock garden.
 * Stand still on it and circles slowly form around you.
 */
public final class ZenGarden extends Module {
	private static final int CAPACITY = 300;
	private static final double STEP = 0.3;
	private static final int MAX_CIRCLES = 6;
	private static final int[][] MATERIALS = {
			// {groove, highlight}
			{0xB39A62, 0xF6E9C4},
			{0x8E4520, 0xE6A06A},
			{0x57524F, 0xB0AAA4},
	};

	private final SliderSetting duration = add(new SliderSetting("duration", "Dauer", 10, 120, 5, 45, v -> Math.round(v) + "s"));
	private final SliderSetting width = add(SliderSetting.percent("width", "Breite", 0.6, 1.6, 1.0));

	private final double[] x = new double[CAPACITY];
	private final double[] y = new double[CAPACITY];
	private final double[] z = new double[CAPACITY];
	private final double[] born = new double[CAPACITY];
	private final byte[] material = new byte[CAPACITY];
	/** True if this point continues the line from the point before it. */
	private final boolean[] joined = new boolean[CAPACITY];
	private int head;
	private boolean onGarden;
	private double lastX;
	private double lastZ;
	private double stillSince;

	private final double[] circleX = new double[MAX_CIRCLES];
	private final double[] circleY = new double[MAX_CIRCLES];
	private final double[] circleZ = new double[MAX_CIRCLES];
	private final double[] circleBorn = new double[MAX_CIRCLES];
	private final byte[] circleMaterial = new byte[MAX_CIRCLES];
	private int circleHead;
	private boolean circleDone;

	public ZenGarden() {
		super("zen_garden", "Zen-Garten", Category.VISUALS, true,
				"Auf Sand und Kies hinterlässt du geharkte Linien wie in einem japanischen Steingarten. Bleibst du stehen, entstehen Kreise um dich.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(born, 0);
		java.util.Arrays.fill(circleBorn, 0);
	}

	/** -1 if the block under you isn't raked, otherwise the material (sand, red sand, gravel). */
	private static int materialAt(ClientLevel level, double px, double py, double pz) {
		BlockState state = level.getBlockState(BlockPos.containing(px, py - 0.2, pz));

		if (state.is(Blocks.RED_SAND)) {
			return 1;
		}

		if (state.is(Blocks.GRAVEL)) {
			return 2;
		}

		return state.is(BlockTags.SAND) ? 0 : -1;
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null) {
			return;
		}

		double px = player.getX();
		double pz = player.getZ();
		int kind = player.onGround() ? materialAt(minecraft.level, px, player.getY(), pz) : -1;
		double now = Clock.t();
		double moved = Math.sqrt((px - lastX) * (px - lastX) + (pz - lastZ) * (pz - lastZ));

		if (kind < 0) {
			onGarden = false;
			lastX = px;
			lastZ = pz;
			stillSince = now;
			circleDone = false;
			return;
		}

		double surface = Math.floor(player.getY() - 0.2) + 1.0 + 0.006;

		if (!onGarden || moved >= STEP) {
			x[head] = px;
			y[head] = surface;
			z[head] = pz;
			born[head] = now;
			material[head] = (byte) kind;
			joined[head] = onGarden && moved < STEP * 4;
			head = (head + 1) % CAPACITY;
			lastX = px;
			lastZ = pz;
			onGarden = true;
		}

		if (moved > 0.02) {
			stillSince = now;
			circleDone = false;
		} else if (!circleDone && now - stillSince > 2.0) {
			// Standing still: rings form around you
			circleX[circleHead] = px;
			circleY[circleHead] = surface;
			circleZ[circleHead] = pz;
			circleBorn[circleHead] = now;
			circleMaterial[circleHead] = (byte) kind;
			circleHead = (circleHead + 1) % MAX_CIRCLES;
			circleDone = true;
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		double now = Clock.t();
		float life = duration.f();
		float w = width.f();

		for (int n = 1; n < CAPACITY; n++) {
			int i = (head - n + CAPACITY * 2) % CAPACITY;
			int previous = (i - 1 + CAPACITY) % CAPACITY;

			if (born[i] == 0 || !joined[i] || born[previous] == 0) {
				continue;
			}

			float age = (float) (now - born[i]);

			if (age > life) {
				continue;
			}

			if (geo.distanceSq(x[i], y[i], z[i]) > 32 * 32) {
				continue;
			}

			float alpha = Math.min(1f, age / 0.3f) * (age > life - 8f ? (life - age) / 8f : 1f);
			rake(geo, x[previous], y[previous], z[previous], x[i], y[i], z[i], MATERIALS[material[i]], alpha, w);
		}

		for (int c = 0; c < MAX_CIRCLES; c++) {
			if (circleBorn[c] == 0) {
				continue;
			}

			float age = (float) (now - circleBorn[c]);

			if (age > life) {
				continue;
			}

			float fade = age > life - 8f ? (life - age) / 8f : 1f;
			int[] colors = MATERIALS[circleMaterial[c]];

			// Rings appear one after another, from the inside out
			for (int ring = 0; ring < 4; ring++) {
				float appear = Colors.clamp01((age - ring * 0.6f) / 0.8f);

				if (appear <= 0f) {
					continue;
				}

				float radius = (0.55f + ring * 0.22f) * w;
				geo.groundRing(circleX[c], circleY[c], circleZ[c], radius, 0.02f * w, 0f, Theme.detail(40), 0f, (float) (Math.PI * 2),
						t -> colors[0], 0.5f * appear * fade);
				geo.groundRing(circleX[c], circleY[c] + 0.001, circleZ[c], radius + 0.025f * w, 0.012f * w, 0f, Theme.detail(40), 0f, (float) (Math.PI * 2),
						t -> colors[1], 0.4f * appear * fade);
			}
		}
	}

	/** Parallel grooves between two path points, with light ridges along the sides. */
	private static void rake(Geo geo, double x0, double y0, double z0, double x1, double y1, double z1, int[] colors, float alpha, float w) {
		double dx = x1 - x0;
		double dz = z1 - z0;
		double length = Math.sqrt(dx * dx + dz * dz);

		if (length < 1.0e-4) {
			return;
		}

		double sideX = -dz / length;
		double sideZ = dx / length;
		int groove = Colors.argb(0.5f * alpha, colors[0]);
		int light = Colors.argb(0.42f * alpha, colors[1]);

		// Four grooves; the light ridges between them are only drawn at the outer edges to keep it cheap
		for (int k = 0; k < 4; k++) {
			double offset = (k - 1.5) * 0.08 * w;
			quad(geo, x0, y0, z0, x1, y1, z1, sideX, sideZ, offset, 0.012 * w, groove);
		}

		quad(geo, x0, y0 + 0.001, z0, x1, y1 + 0.001, z1, sideX, sideZ, -0.15 * w, 0.01 * w, light);
		quad(geo, x0, y0 + 0.001, z0, x1, y1 + 0.001, z1, sideX, sideZ, 0.15 * w, 0.01 * w, light);
	}

	private static void quad(Geo geo, double x0, double y0, double z0, double x1, double y1, double z1, double sideX, double sideZ, double offset, double half, int color) {
		double a = offset - half;
		double b = offset + half;
		geo.quadWorld(x0 + sideX * a, y0, z0 + sideZ * a, color, x0 + sideX * b, y0, z0 + sideZ * b, color,
				x1 + sideX * b, y1, z1 + sideZ * b, color, x1 + sideX * a, y1, z1 + sideZ * a, color);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		Gui2D.fill(g, px + 4, py + 4, px + w - 4, py + h - 4, 0xFFE9D9A8);

		for (int k = 0; k < 5; k++) {
			for (int i = 0; i < w - 12; i += 2) {
				int yy = py + 12 + k * 6 + Math.round((float) Math.sin((i + time * 8) * 0.06f) * 3);
				Gui2D.fill(g, px + 6 + i, yy, px + 8 + i, yy + 1, 0xFFB39A62);
			}
		}

		float cx = px + w * 0.7f;
		float cy = py + h * 0.7f;

		for (int ring = 0; ring < 3; ring++) {
			Gui2D.ellipse(g, cx, cy, 6 + ring * 4, 3 + ring * 2, 20 + ring * 6, 1f, 0xB39A62, 0.9f, 0, false);
		}

		Gui2D.rounded(g, Math.round(cx - 3), Math.round(cy - 3), Math.round(cx + 3), Math.round(cy + 2), 0xFF7A7570);
	}
}
