package dev.juiced.modules.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Gui2D;

public final class LowHealthWarning extends Module {
	private final SliderSetting threshold = add(new SliderSetting("threshold", "Warnen ab", 2, 14, 1, 6, v -> String.format("%.1f ❤", v / 2)));
	private final BoolSetting sound = add(new BoolSetting("sound", "Herzschlag-Sound", true));
	private final SliderSetting volume = add(SliderSetting.percent("volume", "Lautstärke", 0.1, 1.0, 0.5));

	private double nextBeat;

	public LowHealthWarning() {
		super("low_health", "Low-HP-Warnung", Category.HUD, false,
				"Ein pochendes Herz unter dem Fadenkreuz und leiser Herzschlag, wenn dein Leben knapp wird. Je weniger, desto schneller.");
	}

	private float danger(LocalPlayer player) {
		float hp = player.getHealth();

		if (player.isDeadOrDying() || hp > threshold.get()) {
			return -1f;
		}

		return 1f - hp / threshold.f();
	}

	/** Seconds between heartbeats: 70 bpm at the threshold, up to 140 bpm near death. */
	private static double beatInterval(float danger) {
		return 60.0 / (70 + danger * 70);
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || !sound.on()) {
			return;
		}

		float danger = danger(player);
		double now = Clock.t();

		if (danger < 0f) {
			nextBeat = now;
			return;
		}

		if (now >= nextBeat) {
			Fx.sound("entity.warden.heartbeat", volume.f(), 1f);
			nextBeat = now + beatInterval(danger);
		}
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		LocalPlayer player = hud.minecraft.player;

		if (player == null) {
			return;
		}

		float danger = danger(player);

		if (danger < 0f) {
			return;
		}

		double interval = beatInterval(danger);
		float phase = (float) ((Clock.t() % interval) / interval);
		float beat = phase < 0.15f ? phase / 0.15f : phase < 0.4f ? 1f - (phase - 0.15f) / 0.25f : 0f;
		float scale = 1.3f + 0.45f * beat;
		int rgb = Colors.mixRgb(0xFF4D4D, 0xFFFFFF, beat * 0.3f);
		float cx = hud.centerX();
		float cy = hud.centerY() + 18;
		Gui2D.glowDot(g, cx, cy + 4, 6 * scale, 0xFF2020, 0.35f + 0.4f * beat);
		Gui2D.centered(g, "❤", cx, cy, Colors.argb(1f, rgb), true, scale);
		Gui2D.centered(g, String.format("%.1f", player.getHealth()), cx, cy + 15, Colors.argb(0.85f, 0xFFB0B0), true, 0.75f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		double interval = 60.0 / 120;
		float phase = (float) ((time % interval) / interval);
		float beat = phase < 0.15f ? phase / 0.15f : phase < 0.4f ? 1f - (phase - 0.15f) / 0.25f : 0f;
		float scale = 2.4f + 0.8f * beat;
		Gui2D.glowDot(g, x + w / 2f, y + h / 2f, 10 * scale / 2.4f, 0xFF2020, 0.3f + 0.4f * beat);
		Gui2D.centered(g, "❤", x + w / 2f, y + h / 2f - 4 * scale, 0xFFFF4D4D, true, scale);
	}
}
