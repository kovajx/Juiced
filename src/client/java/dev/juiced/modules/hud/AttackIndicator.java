package dev.juiced.modules.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.MenuState;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

public final class AttackIndicator extends Module {
	private static final int DOTS = 28;
	/** A gap in the menu's probe ticks longer than this means the menu stopped showing the indicator. */
	private static final double PROBE_GRACE = 0.25;
	/** The menu's made-up swings: one every 1.4 s, recharged after 0.9 s. */
	private static final double DEMO_SECONDS = 1.4;
	private static final float DEMO_CHARGE = 0.9f;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Ring", "Balken"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.7, 1.6, 1.0));
	private final BoolSetting flash = add(new BoolSetting("flash", "Bereit-Blitz", true));
	private final BoolSetting hideReady = add(new BoolSetting("hide_ready", "Ausblenden wenn bereit", true));

	private boolean wasReady = true;
	private double readyAt = -10;
	private double probeSeen = -10;
	private double probeStart;
	private boolean demoLastFrame;

	public AttackIndicator() {
		super("attack_indicator", "Angriffs-Anzeige", Category.HUD, false,
				"Ring ums Fadenkreuz, der sich mit deiner Schlagkraft füllt und aufblitzt, wenn der volle Schlag bereit ist.");
	}

	/**
	 * You can't swing while the menu is open, so while it shows the indicator the ring acts out a swing every
	 * 1.4 s: it empties, fills up and flashes. Your real attack timer is never touched.
	 */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		double now = Clock.t();

		if (now - probeSeen > PROBE_GRACE) {
			probeStart = now;
		}

		probeSeen = now;
	}

	/** True while the menu shows this module; ends at once when the menu closes and gives the HUD back. */
	private boolean probeShown() {
		return MenuState.hidesHud() && Clock.t() - probeSeen <= PROBE_GRACE;
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		LocalPlayer player = hud.minecraft.player;

		if (player == null) {
			return;
		}

		float strength = Colors.clamp01(player.getAttackStrengthScale(0f));
		boolean demo = probeShown();

		if (demo != demoLastFrame) {
			// Starting or ending the demo is no real swing, so no flash for it
			demoLastFrame = demo;
			wasReady = true;
			readyAt = -10;
		}

		if (demo) {
			// The menu hides the crosshair, and the ring sits around it
			Gui2D.fill(g, hud.centerX() - 4, hud.centerY(), hud.centerX() + 5, hud.centerY() + 1, 0x99FFFFFF);
			Gui2D.fill(g, hud.centerX(), hud.centerY() - 4, hud.centerX() + 1, hud.centerY() + 5, 0x99FFFFFF);

			if (strength >= 1f) {
				strength = Colors.clamp01((float) ((Clock.t() - probeStart) % DEMO_SECONDS) / DEMO_CHARGE);
			}
		}

		boolean ready = strength >= 1f;
		double now = Clock.t();

		if (ready && !wasReady) {
			readyAt = now;
		}

		wasReady = ready;
		float flashT = (float) ((now - readyAt) / 0.18);
		boolean flashing = flash.on() && flashT < 1f;

		if (ready && hideReady.on() && !flashing) {
			return;
		}

		int accent = Theme.accent();
		float cx = hud.centerX() + 0.5f;
		float cy = hud.centerY() + 0.5f;
		float s = size.f();

		if (style.index() == 0) {
			float radius = 10f * s + (flashing ? flashT * 4f : 0f);
			int lit = Math.round(strength * DOTS);

			for (int i = 0; i < DOTS; i++) {
				double angle = -Math.PI / 2 + i * Math.PI * 2 / DOTS;
				float dx = (float) Math.cos(angle) * radius;
				float dy = (float) Math.sin(angle) * radius;
				int color;

				if (flashing) {
					color = Colors.argb(1f - flashT, Colors.shade(accent, 0.4f));
				} else {
					color = i < lit ? Colors.argb(0.95f, accent) : 0x40FFFFFF;
				}

				Gui2D.dot(g, cx + dx, cy + dy, 1.5f, color);
			}
		} else {
			int w = Math.round(24 * s);
			int x = Math.round(cx - w / 2f);
			int y = Math.round(cy + 9);
			int color = flashing ? Colors.argb(1f - flashT * 0.5f, Colors.shade(accent, 0.4f)) : Colors.argb(0.95f, accent);
			Gui2D.bar(g, x, y, w, 2, flashing ? 1f : strength, color, 0x40FFFFFF);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		float cycle = (time % 1.3f) / 1.3f;
		float strength = Math.min(1f, cycle / 0.8f);
		boolean flashing = cycle > 0.8f;
		int accent = Theme.accent();
		Gui2D.fill(g, Math.round(cx) - 4, Math.round(cy), Math.round(cx) + 5, Math.round(cy) + 1, 0xCCFFFFFF);
		Gui2D.fill(g, Math.round(cx), Math.round(cy) - 4, Math.round(cx) + 1, Math.round(cy) + 5, 0xCCFFFFFF);
		float radius = 16 + (flashing ? (cycle - 0.8f) / 0.2f * 6 : 0);

		for (int i = 0; i < DOTS; i++) {
			double angle = -Math.PI / 2 + i * Math.PI * 2 / DOTS;
			int color = flashing ? Colors.argb(1f - (cycle - 0.8f) / 0.2f, Colors.shade(accent, 0.4f))
					: i < strength * DOTS ? Colors.argb(1f, accent) : 0x40FFFFFF;
			Gui2D.dot(g, cx + (float) Math.cos(angle) * radius, cy + (float) Math.sin(angle) * radius, 2, color);
		}
	}
}
