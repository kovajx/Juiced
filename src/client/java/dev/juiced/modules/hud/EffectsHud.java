package dev.juiced.modules.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

public final class EffectsHud extends HudModule {
	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
	private static final int WIDTH = 124;
	private static final int ROW = 15;

	private final BoolSetting blink = add(new BoolSetting("blink", "Warnblinken", true));
	private final BoolSetting bars = add(new BoolSetting("bars", "Restzeit-Balken", true));

	/** Longest duration seen per effect, so the bar shows how much is left. */
	private final Map<MobEffect, Integer> longest = new HashMap<>();
	private final List<Row> rows = new ArrayList<>();

	public EffectsHud() {
		super("effects_hud", "Effekt-HUD", false, 0f, 0.35f,
				"Alle Tränke mit Restzeit und Balken. Blinkt in den letzten 5 Sekunden, damit du rechtzeitig nachtrinkst.");
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		rows.clear();
		LocalPlayer player = hud.minecraft.player;

		if (player != null) {
			for (MobEffectInstance instance : player.getActiveEffects()) {
				MobEffect effect = instance.getEffect().value();
				int duration = instance.getDuration();
				boolean infinite = instance.isInfiniteDuration();
				int max = longest.merge(effect, duration, Math::max);
				int amplifier = Math.max(0, Math.min(ROMAN.length - 1, instance.getAmplifier()));
				String name = effect.getDisplayName().getString() + ROMAN[amplifier];
				float left = infinite || max <= 0 ? 1f : duration / (float) max;
				rows.add(new Row(name, infinite ? "∞" : formatTicks(duration), effect.getColor() & 0xFFFFFF, left, !infinite && duration < 100));
			}
		}

		if (rows.isEmpty()) {
			longest.clear();

			if (!editing) {
				return false;
			}

			float t = (Clock.tf() * 0.08f) % 1f;
			rows.add(new Row("Stärke II", "1:24", 0x9B4DFF, 1f - t, false));
			rows.add(new Row("Feuerschutz", "4:51", 0xFF6B3D, 0.8f, false));
			rows.add(new Row("Tempo II", "0:04", 0x4DC3FF, 0.05f, true));
		}

		width = WIDTH;
		height = rows.size() * ROW - 1;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		float pulse = 0.5f + 0.5f * (float) Math.sin(Clock.tf() * 9);
		float bg = Theme.hudBackground();

		for (int i = 0; i < rows.size(); i++) {
			Row row = rows.get(i);
			int y = i * ROW;
			float alpha = row.ending && blink.on() ? 0.45f + 0.55f * pulse : 1f;

			if (bg > 0.01f) {
				Gui2D.rounded(g, 0, y, WIDTH, y + ROW - 1, Colors.argb(bg * 1.05f * alpha, 0x0D0D13));
			}

			Gui2D.fill(g, 0, y + 2, 2, y + ROW - 3, Colors.argb(alpha, row.color));
			int timeWidth = Gui2D.width(row.time, 0.85f);
			Gui2D.text(g, row.name, 6, y + 3, Colors.argb(alpha, 0xFFFFFF), true, 0.85f);
			Gui2D.text(g, row.time, WIDTH - 4 - timeWidth, y + 3, Colors.argb(alpha, row.ending ? 0xFF5A5A : 0xCFCFDA), true, 0.85f);

			if (bars.on()) {
				Gui2D.bar(g, 6, y + ROW - 3, WIDTH - 10, 1, row.left, Colors.argb(0.9f * alpha, row.color), 0x22FFFFFF);
			}
		}
	}

	private static String formatTicks(int ticks) {
		int seconds = ticks / 20;
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int[] colors = {0x9B4DFF, 0xFF6B3D, 0x4DC3FF};
		String[] names = {"Stärke II", "Feuerschutz", "Tempo"};
		int rowH = 14;
		int top = y + (h - rowH * 3) / 2;

		for (int i = 0; i < 3; i++) {
			float left = 1f - ((time * 0.12f + i * 0.33f) % 1f);
			boolean ending = left < 0.15f;
			float alpha = ending ? 0.5f + 0.5f * (float) Math.sin(time * 9) : 1f;
			int ry = top + i * rowH;
			Gui2D.rounded(g, x + 8, ry, x + w - 8, ry + rowH - 2, Colors.argb(0.5f * alpha, 0x000000));
			Gui2D.fill(g, x + 8, ry + 2, x + 10, ry + rowH - 4, Colors.argb(alpha, colors[i]));
			Gui2D.text(g, names[i], x + 13, ry + 2, Colors.argb(alpha, 0xFFFFFF), false, 0.7f);
			Gui2D.bar(g, x + 13, ry + rowH - 4, w - 24, 1, left, Colors.argb(alpha, colors[i]), 0x22FFFFFF);
		}
	}

	private record Row(String name, String time, int color, float left, boolean ending) {
	}
}
