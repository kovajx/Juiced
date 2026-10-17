package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;

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

/** Leaves drift down from the trees around you, tumble in the air and lie on the ground for a while. */
public final class FallingLeaves extends Module {
	private static final int CAPACITY = 140;

	private final SliderSetting amount = add(SliderSetting.percent("amount", "Menge", 0.2, 2.5, 1.0));
	private final BoolSetting autumn = add(new BoolSetting("autumn", "Herbstfarben", false));

	private final double[] x = new double[CAPACITY];
	private final double[] y = new double[CAPACITY];
	private final double[] z = new double[CAPACITY];
	private final double[] ground = new double[CAPACITY];
	private final float[] fall = new float[CAPACITY];
	private final float[] phase = new float[CAPACITY];
	private final float[] spin = new float[CAPACITY];
	private final float[] landed = new float[CAPACITY];
	private final int[] color = new int[CAPACITY];
	private final boolean[] alive = new boolean[CAPACITY];
	private float budget;

	private static final int[] AUTUMN = {0xD9531E, 0xE8A33A, 0xB8321F, 0xC98B4A, 0xE07A2E};

	public FallingLeaves() {
		super("falling_leaves", "Fallende Blätter", Category.VISUALS, true,
				"Unter Bäumen schweben Blätter herab, drehen sich in der Luft und bleiben eine Weile am Boden liegen. Kirschbäume rosa, Birken hellgrün.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(alive, false);
	}

	@Override
	public void tick(Minecraft minecraft) {
		NearbyBlocks.update(minecraft);
	}

	/** A lone leaf block drops a leaf now and then; it takes a tree for the menu to show something. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return NearbyBlocks.count(NearbyBlocks.LEAVES) >= 12 ? null : "Nur unter Bäumen";
	}

	@Override
	public void renderWorld(Geo geo) {
		ClientLevel level = Minecraft.getInstance().level;
		int leaves = NearbyBlocks.count(NearbyBlocks.LEAVES);

		if (level == null) {
			return;
		}

		float dt = Math.min(0.1f, Clock.dt());
		float time = Clock.tf();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		budget += dt * Math.min(12f, leaves * 0.12f) * amount.f() * (Theme.quality() + 1) / 3f;

		while (budget >= 1f && leaves > 0) {
			budget -= 1f;
			int l = random.nextInt(leaves);
			int bx = NearbyBlocks.x(NearbyBlocks.LEAVES, l);
			int by = NearbyBlocks.y(NearbyBlocks.LEAVES, l);
			int bz = NearbyBlocks.z(NearbyBlocks.LEAVES, l);
			int floor = NearbyBlocks.surface(level, bx, bz, true);

			if (floor >= by || by - floor > 20) {
				continue;
			}

			for (int i = 0; i < CAPACITY; i++) {
				if (!alive[i]) {
					x[i] = bx + random.nextDouble();
					y[i] = by - 0.05;
					z[i] = bz + random.nextDouble();
					ground[i] = floor + 0.015;
					fall[i] = 0.45f + random.nextFloat() * 0.35f;
					phase[i] = random.nextFloat() * 6.28f;
					spin[i] = (random.nextFloat() - 0.5f) * 2.5f;
					landed[i] = -1f;
					int base = autumn.on() ? AUTUMN[random.nextInt(AUTUMN.length)] : NearbyBlocks.data(NearbyBlocks.LEAVES, l);
					color[i] = Colors.shade(base, (random.nextFloat() - 0.5f) * 0.3f);
					alive[i] = true;
					break;
				}
			}
		}

		for (int i = 0; i < CAPACITY; i++) {
			if (!alive[i]) {
				continue;
			}

			float alpha = 1f;

			if (landed[i] < 0f) {
				float sway = (float) Math.sin(time * 1.6f + phase[i]);
				x[i] += sway * 0.35f * dt;
				z[i] += (float) Math.cos(time * 1.3f + phase[i]) * 0.25f * dt;
				y[i] -= fall[i] * dt * (0.7f + 0.3f * Math.abs(sway));

				if (y[i] <= ground[i]) {
					y[i] = ground[i];
					landed[i] = 0f;
				}
			} else {
				landed[i] += dt;

				if (landed[i] > 6f) {
					alive[i] = false;
					continue;
				}

				alpha = landed[i] > 4.5f ? (6f - landed[i]) / 1.5f : 1f;
			}

			if (geo.distanceSq(x[i], y[i], z[i]) > 40 * 40) {
				continue;
			}

			leaf(geo, i, time, alpha);
		}
	}

	/** A small pointed leaf with a darker middle vein; it rocks back and forth while falling. */
	private void leaf(Geo geo, int i, float time, float alpha) {
		boolean falling = landed[i] < 0f;
		float yaw = phase[i] + (falling ? time * spin[i] : 0f);
		float rock = falling ? (float) Math.sin(time * 3f + phase[i]) * 0.9f : 0f;
		float ax = (float) Math.cos(yaw);
		float az = (float) Math.sin(yaw);
		float c = (float) Math.cos(rock);
		float s = (float) Math.sin(rock);
		float bx = -az * c;
		float by = s;
		float bz = ax * c;
		float length = 0.075f;
		float width = 0.04f;
		int base = Colors.argb(alpha * 0.95f, color[i]);
		int tip = Colors.argb(alpha * 0.95f, Colors.shade(color[i], 0.15f));
		double cx = x[i];
		double cy = y[i];
		double cz = z[i];
		geo.quadWorld(cx - ax * length, cy, cz - az * length, base,
				cx + bx * width, cy + by * width, cz + bz * width, base,
				cx + ax * length, cy, cz + az * length, tip,
				cx - bx * width, cy - by * width, cz - bz * width, base);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		Gui2D.rounded2(g, px + 10, py + 4, px + w - 10, py + 16, 0xFF3F6B2A);
		java.util.Random random = new java.util.Random(4);

		for (int i = 0; i < 8; i++) {
			float t = (time * 0.25f + random.nextFloat()) % 1f;
			float lx = px + 14 + random.nextFloat() * (w - 28) + (float) Math.sin(time * 1.6f + i) * 4;
			float ly = py + 16 + t * (h - 24);
			int rgb = autumn.on() ? AUTUMN[i % AUTUMN.length] : (i % 3 == 0 ? 0xF4A6C6 : 0x5E8F3A);
			Gui2D.fill(g, Math.round(lx - 2), Math.round(ly), Math.round(lx + 2), Math.round(ly + 1 + Math.abs((float) Math.sin(time * 3 + i)) * 2), Colors.argb(1f, rgb));
		}
	}
}
