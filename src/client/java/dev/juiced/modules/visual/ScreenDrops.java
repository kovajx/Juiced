package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;
import dev.juiced.render.NearbyBlocks;

/**
 * Water drops on your screen: after you come out of the water, and when you look up into the rain.
 * They slide down slowly, stop for a moment, slide on and fade.
 */
public final class ScreenDrops extends Module {
	private static final int CAPACITY = 70;

	private final BoolSetting afterWater = add(new BoolSetting("after_water", "Nach dem Auftauchen", true));
	private final BoolSetting rain = add(new BoolSetting("rain", "Bei Regen nach oben schauen", true));

	private final float[] x = new float[CAPACITY];
	private final float[] y = new float[CAPACITY];
	private final float[] size = new float[CAPACITY];
	private final float[] speed = new float[CAPACITY];
	private final float[] age = new float[CAPACITY];
	private final float[] life = new float[CAPACITY];
	private final float[] pause = new float[CAPACITY];
	private final boolean[] alive = new boolean[CAPACITY];
	private boolean wasUnderWater;
	private float rainBudget;

	public ScreenDrops() {
		super("screen_drops", "Wassertropfen", Category.VISUALS, true,
				"Wassertropfen auf dem Bildschirm, wenn du aus dem Wasser kommst oder bei Regen nach oben schaust. Sie rinnen langsam herunter.");
	}

	@Override
	protected void onDisable() {
		java.util.Arrays.fill(alive, false);
	}

	/** Drops only come from surfacing or from looking up into the rain, neither of which the menu can do for you. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		for (boolean drop : alive) {
			if (drop) {
				return null;
			}
		}

		return "Bei Regen oder Auftauchen";
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			return;
		}

		boolean under = player.isUnderWater();

		if (wasUnderWater && !under && afterWater.on() && minecraft.options.getCameraType().isFirstPerson()) {
			for (int i = 0; i < 26; i++) {
				spawn(0.1f + ThreadLocalRandom.current().nextFloat() * 0.8f);
			}
		}

		wasUnderWater = under;

		if (rain.on() && level.isRaining() && player.getXRot() < -25f && minecraft.options.getCameraType().isFirstPerson()) {
			int top = NearbyBlocks.surface(level, player.getBlockX(), player.getBlockZ(), false);

			// Only under the open sky, where rain can reach you
			if (top <= player.getEyeY() + 0.5 && level.getBiome(player.blockPosition()).value().hasPrecipitation()) {
				rainBudget += 0.25f * Math.min(1f, (-player.getXRot() - 25f) / 40f);

				while (rainBudget >= 1f) {
					rainBudget -= 1f;
					spawn(0.4f + ThreadLocalRandom.current().nextFloat() * 0.6f);
				}
			}
		}
	}

	private void spawn(float strength) {
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < CAPACITY; i++) {
			if (!alive[i]) {
				x[i] = random.nextFloat();
				y[i] = random.nextFloat() * 0.85f;
				size[i] = (2f + random.nextFloat() * 4f) * strength + 1f;
				speed[i] = 0.02f + random.nextFloat() * 0.05f;
				age[i] = 0f;
				life[i] = 2.5f + random.nextFloat() * 3.5f;
				pause[i] = random.nextFloat() * 0.8f;
				alive[i] = true;
				return;
			}
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		float dt = Math.min(0.1f, Clock.dt());
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < CAPACITY; i++) {
			if (!alive[i]) {
				continue;
			}

			age[i] += dt;

			if (age[i] >= life[i]) {
				alive[i] = false;
				continue;
			}

			// Stick-and-slip: a drop holds still for a moment, then runs on
			if (pause[i] > 0f) {
				pause[i] -= dt;
			} else {
				y[i] += speed[i] * dt * (size[i] / 4f);

				if (random.nextFloat() < dt * 0.8f) {
					pause[i] = 0.2f + random.nextFloat() * 0.7f;
				}
			}

			float alpha = Math.min(1f, (life[i] - age[i]) / 1.2f) * 0.55f;
			float px = x[i] * hud.width;
			float py = y[i] * hud.height;
			float r = size[i] / 2f;
			// Faint wet trail above the drop, the drop itself, and a small highlight
			Gui2D.fill(g, Math.round(px - r * 0.35f), Math.round(py - r * 4), Math.round(px + r * 0.35f), Math.round(py - r), Colors.argb(alpha * 0.25f, 0xBFDDF2));
			Gui2D.rounded(g, Math.round(px - r), Math.round(py - r * 1.1f), Math.round(px + r), Math.round(py + r), Colors.argb(alpha * 0.6f, 0x9FC6E4));
			Gui2D.fill(g, Math.round(px - r * 0.5f), Math.round(py - r * 0.7f), Math.round(px - r * 0.5f) + 1, Math.round(py - r * 0.7f) + 1, Colors.argb(alpha * 1.4f, 0xFFFFFF));
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int px, int py, int w, int h, float time) {
		Gui2D.fill(g, px + 4, py + 4, px + w - 4, py + h - 4, 0x332F4D68);
		java.util.Random random = new java.util.Random(12);

		for (int i = 0; i < 7; i++) {
			float dx = px + 10 + random.nextFloat() * (w - 20);
			float t = (time * 0.15f + random.nextFloat()) % 1f;
			float dy = py + 8 + t * (h - 16);
			float r = 1.5f + random.nextFloat() * 2f;
			Gui2D.fill(g, Math.round(dx), Math.round(dy - r * 4), Math.round(dx) + 1, Math.round(dy - r), 0x44BFDDF2);
			Gui2D.rounded(g, Math.round(dx - r), Math.round(dy - r), Math.round(dx + r), Math.round(dy + r), 0xAA9FC6E4);
		}
	}
}
