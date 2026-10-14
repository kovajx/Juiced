package dev.juiced.modules.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.render.Fx;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sparks;

public final class KillEffects extends Module {
	private static final int BEAM_POINTS = 14;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Blitz", "Seelen", "Feuerwerk", "Implosion"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));

	private final List<Effect> effects = new ArrayList<>();
	private final float[] bx = new float[BEAM_POINTS];
	private final float[] by = new float[BEAM_POINTS];
	private final float[] bz = new float[BEAM_POINTS];
	private final float[] bw = new float[BEAM_POINTS];
	private final int[] bc = new int[BEAM_POINTS];

	public KillEffects() {
		super("kill_effects", "Kill-Effekte", Category.COMBAT, true,
				"Ein großer Moment, wenn dein Gegner fällt: Blitz, aufsteigende Seelen, Feuerwerk oder Implosion.");
	}

	@Override
	public void onKill(LivingEntity target) {
		double x = target.getX();
		double y = target.getY();
		double z = target.getZ();
		float h = target.getBbHeight();
		Geo.Tint tint = t -> color.rgb(t);

		if (effects.size() < 8) {
			effects.add(new Effect(style.index(), x, y, z, h, Clock.t(), ThreadLocalRandom.current().nextLong()));
		}

		switch (style.index()) {
			case 0 -> {
				Sparks.burst(x, y + 0.2, z, 30, 5f, 0.05f, 0.5f, tint, Sparks.STREAK, 9f, 0.6f);
				playSound("entity.lightning_bolt.impact", 0.6f, 1.1f);
			}
			case 1 -> {
				ThreadLocalRandom random = ThreadLocalRandom.current();

				for (int i = 0; i < 16; i++) {
					Sparks.spawn(x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * h, z + (random.nextDouble() - 0.5) * 0.6,
							(random.nextFloat() - 0.5f) * 0.5f, 0.8f + random.nextFloat() * 1.2f, (random.nextFloat() - 0.5f) * 0.5f,
							1.6f, 0.11f, color.rgb(random.nextFloat()), Sparks.DOT, -0.4f, 0.6f);
				}

				Fx.particles(ParticleTypes.SOUL, x, y + h / 2, z, 10, 0.4, 0.05);
				playSound("particle.soul_escape", 1f, 1f);
			}
			case 2 -> {
				Sparks.burst(x, y + h + 0.6, z, 70, 6f, 0.07f, 1.3f, t -> Theme.rainbow(t), Sparks.STAR, 5f, 0f);
				Fx.particles(ParticleTypes.FIREWORK, x, y + h + 0.6, z, 20, 0.3, 0.25);
				playSound("entity.firework_rocket.blast", 0.8f, 1f);
				playSound("entity.firework_rocket.twinkle", 0.6f, 1f);
			}
			default -> playSound("block.beacon.deactivate", 0.7f, 1.6f);
		}
	}

	private void playSound(String id, float volume, float pitch) {
		if (sound.on()) {
			Fx.sound(id, volume, pitch);
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		if (effects.isEmpty()) {
			return;
		}

		double now = Clock.t();

		for (int i = effects.size() - 1; i >= 0; i--) {
			Effect effect = effects.get(i);
			float age = (float) (now - effect.start);
			boolean alive = switch (effect.style) {
				case 0 -> drawBeam(geo, effect, age);
				case 3 -> drawImplosion(geo, effect, age);
				default -> age < 0.1f;
			};

			if (!alive) {
				effects.remove(i);
			}
		}
	}

	private boolean drawBeam(Geo geo, Effect e, float age) {
		float life = 0.55f;

		if (age >= life) {
			return false;
		}

		float t = age / life;
		float fade = 1f - t;
		// Re-roll the zigzag a few times so the bolt flickers.
		long seed = e.seed + (long) (age / 0.06f);
		java.util.Random random = new java.util.Random(seed);
		float top = 16f;

		for (int i = 0; i < BEAM_POINTS; i++) {
			float f = i / (float) (BEAM_POINTS - 1);
			double jitter = i == 0 ? 0 : 0.45 * Math.sqrt(f);
			bx[i] = geo.rx(e.x + (random.nextDouble() - 0.5) * jitter);
			by[i] = geo.ry(e.y + f * top);
			bz[i] = geo.rz(e.z + (random.nextDouble() - 0.5) * jitter);
		}

		for (int i = 0; i < BEAM_POINTS; i++) {
			bw[i] = 0.32f;
			bc[i] = Colors.argb(0.35f * fade * (1f - i / (float) BEAM_POINTS * 0.5f), color.rgb(i / (float) BEAM_POINTS));
		}

		geo.ribbon(bx, by, bz, bw, bc, BEAM_POINTS);

		for (int i = 0; i < BEAM_POINTS; i++) {
			bw[i] = 0.06f;
			bc[i] = Colors.argb(fade, 0xFFFFFF);
		}

		geo.ribbon(bx, by, bz, bw, bc, BEAM_POINTS);
		geo.groundRing(e.x, e.y + 0.03, e.z, 0.3f + Clock.easeOut(t) * 2.4f, 0.08f * fade + 0.01f, 0.25f, Theme.detail(48), 0f, (float) (Math.PI * 2), s -> color.rgb(s), fade);
		return true;
	}

	private boolean drawImplosion(Geo geo, Effect e, float age) {
		float life = 0.8f;

		if (age >= life) {
			return false;
		}

		double cy = e.y + e.height / 2;
		int segments = Theme.detail(40);

		if (age < 0.5f) {
			float t = age / 0.5f;

			for (int ring = 0; ring < 3; ring++) {
				float radius = (1f - Clock.easeOut(t)) * 2.2f + ring * 0.35f * (1f - t);
				float alpha = Math.min(1f, t * 3f) * (0.9f - ring * 0.2f);
				geo.groundRing(e.x, cy - 0.3 + ring * 0.3, e.z, radius, 0.05f, 0.15f, segments, Clock.tf() * 2, (float) (Math.PI * 2), s -> color.rgb(s), alpha);
			}
		} else {
			float t = (age - 0.5f) / 0.3f;

			if (!e.burst) {
				e.burst = true;
				Sparks.burst(e.x, cy, e.z, 40, 6f, 0.06f, 0.6f, s -> color.rgb(s), Sparks.STREAK, 4f, 0f);
			}

			geo.glowDot(e.x, cy, e.z, 0.3f + t * 1.4f, color.rgb(), (1f - t) * 0.9f);
			geo.billboardRing(e.x, cy, e.z, 0.2f + Clock.easeOut(t) * 1.8f, 0.05f * (1f - t), 0.2f, segments, s -> color.rgb(s), 1f - t);
		}

		return true;
	}

	@Override
	protected void onDisable() {
		effects.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cycle = Previews.cycle(time, 1.6f);
		float feetY = Previews.stage(g, x, y, w, h, x + w / 2f, 0);
		float cx = x + w / 2f;

		if (cycle < 0.35f) {
			float t = cycle / 0.35f;
			float alpha = 1f - t;
			java.util.Random random = new java.util.Random((long) (time / 0.06f));
			float px = cx;
			float py = y + 2;

			for (int i = 1; i <= 8; i++) {
				float nx = cx + (random.nextFloat() - 0.5f) * 10;
				float ny = y + 2 + (feetY - y - 2) * i / 8f;
				int steps = 4;

				for (int s = 0; s < steps; s++) {
					float f = s / (float) steps;
					Gui2D.glowDot(g, px + (nx - px) * f, py + (ny - py) * f, 2, color.rgb(), alpha);
				}

				px = nx;
				py = ny;
			}

			Gui2D.ellipse(g, cx, feetY, 6 + t * 30, 2 + t * 8, 24, 2, color.rgb(), alpha, 0, true);
		}
	}

	private static final class Effect {
		final int style;
		final double x;
		final double y;
		final double z;
		final float height;
		final double start;
		final long seed;
		boolean burst;

		Effect(int style, double x, double y, double z, float height, double start, long seed) {
			this.style = style;
			this.x = x;
			this.y = y;
			this.z = z;
			this.height = height;
			this.start = start;
			this.seed = seed;
		}
	}
}
