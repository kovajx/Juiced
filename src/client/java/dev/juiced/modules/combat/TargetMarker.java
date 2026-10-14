package dev.juiced.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class TargetMarker extends Module {
	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Ring", "Orbit", "Lock-On"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final SliderSetting speed = add(SliderSetting.percent("speed", "Tempo", 0.2, 2.0, 1.0));

	private LivingEntity shown;
	private float visibility;

	public TargetMarker() {
		super("target_marker", "Target-Marker", Category.COMBAT, true,
				"Markiert deinen aktuellen Gegner, damit du ihn im Getümmel nie verlierst. Blitzt bei jedem Treffer auf.");
	}

	@Override
	public void renderWorld(Geo geo) {
		LivingEntity target = CombatEvents.target();

		if (target != null) {
			shown = target;
		}

		visibility = Clock.approach(visibility, target != null ? 1f : 0f, 10f);

		if (shown == null || visibility < 0.01f || shown.isRemoved()) {
			if (visibility < 0.01f) {
				shown = null;
			}

			return;
		}

		if (shown == Minecraft.getInstance().player || geo.distanceSq(shown.getX(), shown.getY(), shown.getZ()) > Theme.effectRange() * Theme.effectRange()) {
			return;
		}

		double x = geo.x(shown);
		double y = geo.y(shown);
		double z = geo.z(shown);
		float width = Math.max(0.5f, shown.getBbWidth());
		float height = shown.getBbHeight();
		float time = Clock.tf() * speed.f();
		long sinceHit = CombatEvents.ticksSinceHit(shown);
		float flash = sinceHit < 5 ? 1f - (sinceHit + geo.partialTick()) / 5f : 0f;
		float alpha = visibility;
		Geo.Tint tint = t -> Colors.mixRgb(color.rgb(t), 0xFFFFFF, Math.max(0f, flash) * 0.8f);
		float s = size.f();

		switch (style.index()) {
			case 0 -> {
				float radius = width * 0.95f * s + flash * 0.12f;
				int segments = Theme.detail(12);

				for (int k = 0; k < 4; k++) {
					float start = time * 2f + k * (float) (Math.PI / 2);
					geo.groundRing(x, y + 0.03, z, radius, 0.06f, 0.12f, segments, start, (float) (Math.PI / 2) * 0.7f, tint, alpha * 0.95f);
				}

				geo.groundRing(x, y + 0.03, z, radius * 1.18f, 0.015f, 0.05f, Theme.detail(48), 0f, (float) (Math.PI * 2), tint, alpha * 0.3f);
			}
			case 1 -> {
				float radius = width * 1.0f * s;

				for (int k = 0; k < 3; k++) {
					for (int j = 0; j < 5; j++) {
						double angle = time * 2.2 + k * Math.PI * 2 / 3 - j * 0.14;
						double oy = height * (0.5 + 0.3 * Math.sin(time * 2 - j * 0.14 + k * 2));
						float a = alpha * (j == 0 ? 1f : 0.5f * (1f - j / 5f));
						float r = j == 0 ? 0.09f * s : 0.05f * s;
						geo.glowDot(x + Math.cos(angle) * radius, y + oy, z + Math.sin(angle) * radius, r + flash * 0.05f, tint.rgb(k / 3f), a);
					}
				}
			}
			default -> drawLockOn(geo, x, y + height / 2, z, width, height, time, tint, alpha, flash);
		}
	}

	private void drawLockOn(Geo geo, double cx, double cy, double cz, float width, float height, float time, Geo.Tint tint, float alpha, float flash) {
		float[] basis = geo.billboardBasis(cx, cy, cz);
		float pulse = 0.06f * (float) Math.sin(time * 4) + flash * 0.15f;
		float halfW = width * 0.75f * size.f() + pulse;
		float halfH = height * 0.6f * size.f() + pulse;
		float arm = Math.min(halfW, halfH) * 0.45f;
		int c = Colors.argb(alpha, tint.rgb(0f));

		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				double px = cx + basis[0] * halfW * sx + basis[3] * halfH * sy;
				double py = cy + basis[1] * halfW * sx + basis[4] * halfH * sy;
				double pz = cz + basis[2] * halfW * sx + basis[5] * halfH * sy;
				// horizontal arm, pointing inwards
				geo.line(px, py, pz, px - basis[0] * arm * sx, py - basis[1] * arm * sx, pz - basis[2] * arm * sx, 0.025f, c, Colors.fade(c, 0.4f));
				// vertical arm
				geo.line(px, py, pz, px - basis[3] * arm * sy, py - basis[4] * arm * sy, pz - basis[5] * arm * sy, 0.025f, c, Colors.fade(c, 0.4f));
			}
		}

		geo.glowDot(cx, cy, cz, 0.05f, tint.rgb(0.5f), alpha * 0.8f);
	}

	@Override
	protected void onDisable() {
		shown = null;
		visibility = 0f;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, 0);
		float cx = x + w / 2f;
		int rgb = color.rgb();

		switch (style.index()) {
			case 0 -> {
				for (int k = 0; k < 4; k++) {
					double start = time * 2 + k * Math.PI / 2;

					for (int i = 0; i < 6; i++) {
						double a = start + i * 0.18;
						Gui2D.dot(g, cx + (float) Math.cos(a) * 18, feetY + (float) Math.sin(a) * 5, 2, Colors.argb(0.9f, rgb));
					}
				}
			}
			case 1 -> {
				for (int k = 0; k < 3; k++) {
					double a = time * 2.2 + k * Math.PI * 2 / 3;
					float depth = (float) Math.sin(a);
					Gui2D.glowDot(g, cx + (float) Math.cos(a) * 16, feetY - 18 + depth * 4, 2, rgb, 0.6f + 0.4f * depth);
				}
			}
			default -> {
				float pulse = 2 * (float) Math.sin(time * 4);
				float hw = 12 + pulse;
				float top = feetY - 40 - pulse;
				float bottom = feetY + 4 + pulse;
				int c = Colors.argb(0.95f, rgb);
				int l = Math.round(cx - hw);
				int r = Math.round(cx + hw);
				int t = Math.round(top);
				int b = Math.round(bottom);
				Gui2D.fill(g, l, t, l + 6, t + 1, c);
				Gui2D.fill(g, l, t, l + 1, t + 6, c);
				Gui2D.fill(g, r - 6, t, r, t + 1, c);
				Gui2D.fill(g, r - 1, t, r, t + 6, c);
				Gui2D.fill(g, l, b - 1, l + 6, b, c);
				Gui2D.fill(g, l, b - 6, l + 1, b, c);
				Gui2D.fill(g, r - 6, b - 1, r, b, c);
				Gui2D.fill(g, r - 1, b - 6, r, b, c);
			}
		}
	}
}
