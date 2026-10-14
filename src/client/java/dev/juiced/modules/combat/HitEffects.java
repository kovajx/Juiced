package dev.juiced.modules.combat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sparks;

public final class HitEffects extends Module {
	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Funken", "Sterne", "Schockwelle", "Seele", "Magie"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting amount = add(SliderSetting.percent("amount", "Menge", 0.25, 2.0, 1.0));
	private final BoolSetting critBoost = add(new BoolSetting("crit_boost", "Crits stärker", true));

	private final List<Shockwave> shockwaves = new ArrayList<>();

	public HitEffects() {
		super("hit_effects", "Hit-Effekte", Category.COMBAT, true,
				"Farbige Effekte genau da, wo dein Schlag trifft. Crits knallen stärker.");
	}

	@Override
	public void onHit(LivingEntity target, boolean crit) {
		Vec3 at = Fx.impactPoint(target);
		float boost = crit && critBoost.on() ? 1.6f : 1f;
		float n = amount.f() * boost;
		Geo.Tint tint = t -> color.rgb(t);

		switch (style.index()) {
			case 0 -> {
				Sparks.burst(at.x, at.y, at.z, Math.round(14 * n), 4.5f, 0.05f, 0.45f, tint, Sparks.STREAK, 9f, 0.25f);
				Fx.particles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, Math.round(3 * n), 0.1, 0.25);
			}
			case 1 -> Sparks.burst(at.x, at.y, at.z, Math.round(8 * n), 2.6f, 0.09f * boost, 0.8f, tint, Sparks.STAR, 3f, 0.4f);
			case 2 -> {
				if (shockwaves.size() < 12) {
					shockwaves.add(new Shockwave(at.x, at.y, at.z, Clock.t(), boost));
				}

				Sparks.burst(at.x, at.y, at.z, Math.round(6 * n), 3.5f, 0.04f, 0.35f, tint, Sparks.STREAK, 6f, 0.1f);
			}
			case 3 -> {
				Fx.particles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, Math.round(5 * n), 0.15, 0.04);

				for (int i = 0; i < Math.round(6 * n); i++) {
					Sparks.spawn(at.x, at.y, at.z, (float) (Math.random() - 0.5) * 0.8f, 0.6f + (float) Math.random() * 0.9f,
							(float) (Math.random() - 0.5) * 0.8f, 1.1f, 0.08f, color.rgb((float) Math.random()), Sparks.DOT, -0.6f, 0.8f);
				}
			}
			default -> {
				Fx.particles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, Math.round(10 * n), 0.2, 0.3);
				Sparks.burst(at.x, at.y, at.z, Math.round(6 * n), 1.8f, 0.06f, 0.7f, tint, Sparks.STAR, 1f, 0.5f);
			}
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		if (shockwaves.isEmpty()) {
			return;
		}

		double now = Clock.t();
		int segments = Theme.detail(36);
		float spin = Clock.tf() * 0.3f;

		for (int i = shockwaves.size() - 1; i >= 0; i--) {
			Shockwave wave = shockwaves.get(i);
			float t = (float) ((now - wave.start) / 0.35);

			if (t >= 1f) {
				shockwaves.remove(i);
				continue;
			}

			float radius = 0.12f + Clock.easeOut(t) * 0.85f * wave.size;
			float alpha = (1f - t) * 0.95f;
			geo.billboardRing(wave.x, wave.y, wave.z, radius, 0.06f * (1f - t) + 0.012f, 0.14f, segments, s -> color.rgb(s + spin), alpha);
		}
	}

	@Override
	protected void onDisable() {
		shockwaves.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float figureX = x + w * 0.62f;
		float feetY = Previews.stage(g, x, y, w, h, figureX, 0);
		float cycle = Previews.cycle(time, 1.1f);
		float hx = figureX - 6;
		float hy = feetY - 26;

		for (int i = 0; i < 10; i++) {
			double angle = i * Math.PI * 2 / 10 + 0.3;
			float dist = 4 + Clock.easeOut(cycle) * 22;
			float alpha = 1f - cycle;
			int rgb = color.rgb(i / 10f);
			float px = hx + (float) Math.cos(angle) * dist;
			float py = hy + (float) Math.sin(angle) * dist + cycle * cycle * 10;
			Gui2D.glowDot(g, px, py, 2, rgb, alpha);
		}

		if (cycle < 0.15f) {
			Gui2D.glowDot(g, hx, hy, 4, 0xFFFFFF, 1f - cycle / 0.15f);
		}

		Gui2D.fill(g, Math.round(hx) - 1, Math.round(hy) - 6, Math.round(hx) + 1, Math.round(hy) + 6, Colors.argb(0.1f, 0xFFFFFF));
	}

	private record Shockwave(double x, double y, double z, double start, float size) {
	}
}
