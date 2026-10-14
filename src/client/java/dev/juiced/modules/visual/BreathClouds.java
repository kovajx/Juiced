package dev.juiced.modules.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** In the cold (on snow and ice) you see your breath as little clouds, in a calm rhythm. */
public final class BreathClouds extends Module {
	private static final int CAPACITY = 32;
	private static final float BREATH = 3.4f;
	private static final float LIFE = 1.7f;

	private final ChoiceSetting when = add(new ChoiceSetting("when", "Wann", 0, "In der Kälte", "Immer"));
	private final BoolSetting firstPerson = add(new BoolSetting("first_person", "In Ego-Sicht zeigen", true));

	private final double[] x = new double[CAPACITY];
	private final double[] y = new double[CAPACITY];
	private final double[] z = new double[CAPACITY];
	private final float[] vx = new float[CAPACITY];
	private final float[] vy = new float[CAPACITY];
	private final float[] vz = new float[CAPACITY];
	private final double[] born = new double[CAPACITY];
	private int head;
	private boolean cold;
	private double lastPuff;

	public BreathClouds() {
		super("breath", "Atemwolken", Category.VISUALS, true,
				"In der Kälte (auf Schnee und Eis) siehst du deinen Atem als kleine Wölkchen, in einem ruhigen Rhythmus.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(born, 0);
	}

	private static boolean isCold(ClientLevel level, LocalPlayer player) {
		BlockPos feet = player.blockPosition();
		BlockState at = level.getBlockState(feet);
		BlockState below = level.getBlockState(feet.below());
		return at.is(Blocks.SNOW) || at.is(Blocks.POWDER_SNOW) || below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.SNOW) || below.is(Blocks.POWDER_SNOW)
				|| below.is(Blocks.ICE) || below.is(Blocks.PACKED_ICE) || below.is(Blocks.BLUE_ICE);
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null) {
			return;
		}

		cold = when.index() == 1 || isCold(minecraft.level, player);
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null) {
			return;
		}

		double now = Clock.t();
		boolean visible = firstPerson.on() || !minecraft.options.getCameraType().isFirstPerson();

		// Breathe out for a moment every few seconds: a few puffs in a row
		float phase = (float) (now % BREATH);

		if (cold && visible && !player.isUnderWater() && phase < 0.8f && now - lastPuff > 0.12) {
			lastPuff = now;
			double yaw = Math.toRadians(player.getYHeadRot());
			double pitch = Math.toRadians(player.getXRot());
			float fx = (float) (-Math.sin(yaw) * Math.cos(pitch));
			float fy = (float) -Math.sin(pitch);
			float fz = (float) (Math.cos(yaw) * Math.cos(pitch));
			x[head] = geo.x(player) + fx * 0.32;
			y[head] = geo.y(player) + player.getEyeHeight() - 0.14 + fy * 0.3;
			z[head] = geo.z(player) + fz * 0.32;
			vx[head] = fx * 0.35f;
			vy[head] = fy * 0.35f + 0.08f;
			vz[head] = fz * 0.35f;
			born[head] = now;
			head = (head + 1) % CAPACITY;
		}

		float dt = Clock.dt();

		for (int i = 0; i < CAPACITY; i++) {
			if (born[i] == 0) {
				continue;
			}

			float t = (float) ((now - born[i]) / LIFE);

			if (t >= 1f || t < 0f) {
				continue;
			}

			x[i] += vx[i] * dt;
			y[i] += vy[i] * dt;
			z[i] += vz[i] * dt;
			vx[i] *= 1f - dt * 1.5f;
			vy[i] = vy[i] * (1f - dt * 1.5f) + dt * 0.05f;
			vz[i] *= 1f - dt * 1.5f;
			float size = 0.05f + 0.16f * (float) Math.sqrt(t);
			geo.glowDot(x[i], y[i], z[i], size, 0xF2F6FF, 0.28f * (1f - t) * Math.min(1f, t * 8f));
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		float cx = px + w * 0.35f;
		float cy = py + h * 0.45f;
		Gui2D.figure(g, cx - 6, py + h - 6, 0.9f, 0xFFEDEDF2);

		for (int i = 0; i < 4; i++) {
			float t = ((time * 0.5f) + i * 0.12f) % 1f;
			Gui2D.glowDot(g, cx + 4 + t * 26, cy - t * 6, 2 + t * 4, 0xF2F6FF, 0.6f * (1f - t));
		}
	}
}
