package dev.juiced.modules.visual;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Pose3;

/**
 * Atmosphere around you. Fireflies are drawn by Juiced itself (cheap glowing dots that wander),
 * the other styles use the game's own particles.
 */
public final class AmbientParticles extends Module {
	private static final int MAX_FLIES = 64;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Glühwürmchen", "Schnee", "Asche", "Seelen", "Sporen", "Laternen", "Schmetterlinge", "Sternschnuppen", "Pusteblumen"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe (Glühwürmchen, Laternen, Schmetterlinge)", Colors.GOLD, true));
	private final SliderSetting density = add(SliderSetting.percent("density", "Dichte", 0.2, 2.0, 1.0));
	private final SliderSetting radius = add(new SliderSetting("radius", "Radius", 4, 16, 1, 8, v -> Math.round(v) + " Blöcke"));

	private final double[] fx = new double[MAX_FLIES];
	private final double[] fy = new double[MAX_FLIES];
	private final double[] fz = new double[MAX_FLIES];
	private final float[] vx = new float[MAX_FLIES];
	private final float[] vy = new float[MAX_FLIES];
	private final float[] vz = new float[MAX_FLIES];
	private final float[] phase = new float[MAX_FLIES];
	private final float[] age = new float[MAX_FLIES];
	private final float[] life = new float[MAX_FLIES];
	private int flies;
	private int drawnStyle = -1;

	public AmbientParticles() {
		super("ambient", "Ambient-Partikel", Category.VISUALS, false,
				"Glühwürmchen, Schnee, Asche, Seelen, Sporen, aufsteigende Laternen oder Schmetterlinge um dich herum. Läuft komplett auf deinem Client.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null || selfDrawn()) {
			return;
		}

		ParticleOptions type = switch (style.index()) {
			case 1 -> ParticleTypes.SNOWFLAKE;
			case 2 -> ParticleTypes.WHITE_ASH;
			case 3 -> ParticleTypes.SOUL;
			default -> ParticleTypes.SPORE_BLOSSOM_AIR;
		};
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float perTick = density.f() * (style.index() == 3 ? 0.6f : 2.5f) * (Theme.quality() + 1) / 3f;
		int count = (int) perTick + (random.nextFloat() < perTick % 1f ? 1 : 0);
		double r = radius.get();

		for (int i = 0; i < count; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 2 * r;
			double y = player.getY() - 1 + random.nextDouble() * 7;
			double z = player.getZ() + (random.nextDouble() - 0.5) * 2 * r;
			double fall = style.index() == 1 ? -0.05 : 0;
			minecraft.level.addParticle(type, x, y, z, (random.nextDouble() - 0.5) * 0.02, fall, (random.nextDouble() - 0.5) * 0.02);
		}
	}

	/** Fireflies, lanterns and butterflies are drawn by Juiced; the rest are game particles. */
	private boolean selfDrawn() {
		int index = style.index();
		return index == 0 || index >= 5;
	}

	@Override
	public void renderWorld(Geo geo) {
		if (!selfDrawn()) {
			flies = 0;
			return;
		}

		if (drawnStyle != style.index()) {
			drawnStyle = style.index();
			flies = 0;
		}

		int kind = style.index();

		LocalPlayer player = Minecraft.getInstance().player;

		if (player == null) {
			return;
		}

		double cx = geo.x(player);
		double cy = geo.y(player);
		double cz = geo.z(player);
		double r = radius.get();
		int base = switch (kind) {
			case 5 -> Theme.detail(10);
			case 6 -> Theme.detail(12);
			case 7 -> 4;
			case 8 -> Theme.detail(20);
			default -> Theme.detail(36);
		};
		int target = Math.min(MAX_FLIES, Math.round(base * density.f()));
		ThreadLocalRandom random = ThreadLocalRandom.current();
		float dt = Clock.dt();

		while (flies < target) {
			spawn(flies++, cx, cy, cz, r, random);
		}

		flies = Math.min(flies, target);
		float time = Clock.tf();

		for (int i = 0; i < flies; i++) {
			age[i] += dt;
			double dx = fx[i] - cx;
			double dz = fz[i] - cz;

			boolean far = kind != 7 && (dx * dx + dz * dz > r * r * 1.6 || fy[i] - cy > (kind == 5 ? 16 : 8) || cy - fy[i] > 8);

			if (age[i] > life[i] || far) {
				spawn(i, cx, cy, cz, r, random);
			}

			if (kind == 7) {
				// Shooting stars wait (negative age), then streak across the sky
				if (age[i] >= 0f) {
					fx[i] += vx[i] * dt;
					fy[i] += vy[i] * dt;
					fz[i] += vz[i] * dt;
					meteor(geo, i);
				}

				continue;
			}

			if (kind == 8) {
				// Dandelion seeds float with the breeze and bob gently
				vx[i] = 0.25f + (float) Math.sin(time * 0.3f + phase[i]) * 0.12f;
				vz[i] = 0.1f + (float) Math.cos(time * 0.25f + phase[i]) * 0.12f;
				vy[i] = (float) Math.sin(time * 0.8f + phase[i]) * 0.08f;
			} else if (kind == 5) {
				// Lanterns rise slowly and sway a little
				vx[i] = (float) Math.sin(time * 0.4f + phase[i]) * 0.08f;
				vz[i] = (float) Math.cos(time * 0.35f + phase[i]) * 0.08f;
				vy[i] = 0.12f + (phase[i] % 1f) * 0.08f;
			} else {
				// Wander: small random steering, gentle speed limit
				float steer = kind == 6 ? 3.5f : 1.2f;
				vx[i] += (random.nextFloat() - 0.5f) * steer * dt;
				vy[i] += (random.nextFloat() - 0.5f) * steer * 0.6f * dt;
				vz[i] += (random.nextFloat() - 0.5f) * steer * dt;

				if (kind == 6) {
					// Butterflies stay between knee and head height
					double height = fy[i] - cy;
					vy[i] += (float) (height < 0.4 ? 0.8 : height > 2.6 ? -0.8 : 0) * dt;
				}

				float limit = kind == 6 ? 0.9f : 0.35f;
				float speed = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]);

				if (speed > limit) {
					vx[i] *= limit / speed;
					vy[i] *= limit / speed;
					vz[i] *= limit / speed;
				}
			}

			fx[i] += vx[i] * dt;
			fy[i] += vy[i] * dt;
			fz[i] += vz[i] * dt;

			float lifeFade = Math.min(1f, Math.min(age[i], life[i] - age[i]) / (kind == 5 ? 3f : 1.5f));

			if (kind == 8) {
				seed(geo, i, time, lifeFade);
			} else if (kind == 5) {
				lantern(geo, i, time, lifeFade);
			} else if (kind == 6) {
				butterfly(geo, i, time, lifeFade);
			} else {
				float blink = 0.3f + 0.7f * Math.max(0f, (float) Math.sin(time * 1.7f + phase[i]));
				geo.glowDot(fx[i], fy[i], fz[i], 0.11f, color.rgb(phase[i] / 6.28f), blink * lifeFade * 0.95f);
			}
		}
	}

	/** A shooting star: bright head, long fading tail, far away in the sky. */
	private void meteor(Geo geo, int i) {
		float t = age[i] / life[i];
		float alpha = (float) Math.sin(t * Math.PI);
		int head = Colors.argb(alpha, 0xFFFFFF);
		int tail = Colors.argb(0f, color.rgb(phase[i] / 6.28f));
		double length = 0.16;
		geo.line(fx[i], fy[i], fz[i], fx[i] - vx[i] * length, fy[i] - vy[i] * length, fz[i] - vz[i] * length, 0.14f, head, tail);
		geo.glowDot(fx[i], fy[i], fz[i], 0.6f, color.rgb(phase[i] / 6.28f), 0.5f * alpha);
	}

	/** A dandelion seed: a tiny stem with a white tuft of fine hairs on top. */
	private void seed(Geo geo, int i, float time, float fade) {
		float sway = (float) Math.sin(time * 1.4f + phase[i]) * 0.15f;
		int stem = Colors.argb(0.8f * fade, 0x9C8A6A);
		int hair = Colors.argb(0.75f * fade, 0xFFFFFF);
		double x = fx[i];
		double y = fy[i];
		double z = fz[i];
		geo.line(x, y - 0.05, z, x, y, z, 0.004f, stem, stem);

		for (int k = 0; k < 7; k++) {
			double a = k * Math.PI * 2 / 7 + phase[i];
			double ox = Math.cos(a) * 0.035 + sway * 0.02;
			double oz = Math.sin(a) * 0.035;
			geo.line(x, y, z, x + ox, y + 0.035, z + oz, 0.003f, hair, Colors.fade(hair, 0.4f));
		}

		geo.glowDot(x, y + 0.02, z, 0.06f, 0xFFFFFF, 0.2f * fade);
	}

	/** A paper lantern: warm glow, a slowly turning paper body with dark rims, a flame inside. */
	private void lantern(Geo geo, int i, float time, float fade) {
		float flicker = 0.85f + 0.1f * (float) Math.sin(time * 9f + phase[i]) + 0.05f * (float) Math.sin(time * 23f + phase[i] * 2);
		int paper = Colors.mixRgb(color.rgb(), 0xFF7A2A, 0.55f);
		geo.glowDot(fx[i], fy[i], fz[i], 0.5f, paper, 0.25f * fade * flicker);
		Pose3 pose = Pose3.at(fx[i], fy[i], fz[i], phase[i] * 57f + time * 10f);
		geo.beginModel();
		geo.box(pose, 0.1f, 0.14f, 0.1f, Colors.shade(paper, 0.2f), 0.85f * fade);
		geo.box(pose.copy().move(0, 0.15, 0), 0.065f, 0.012f, 0.065f, 0x4A2410, fade);
		geo.box(pose.copy().move(0, -0.15, 0), 0.075f, 0.012f, 0.075f, 0x4A2410, fade);
		geo.endModel();
		geo.glowDot(fx[i], fy[i] - 0.04, fz[i], 0.1f, 0xFFE6A0, 0.75f * fade * flicker);
	}

	/** A butterfly flapping its wings, facing where it flies. */
	private void butterfly(Geo geo, int i, float time, float fade) {
		float yaw = (float) Math.toDegrees(Math.atan2(-vx[i], vz[i]));
		double bob = Math.sin(time * 2.5f + phase[i]) * 0.05;
		Pose3 pose = Pose3.at(fx[i], fy[i] + bob, fz[i], yaw);
		double flap = Math.toRadians(20 + Math.sin(time * 15f + phase[i]) * 55);
		int rgb = Colors.mixRgb(color.rgb(phase[i] / 6.28f), Colors.hsv(phase[i] / 6.28f, 0.5f, 1f), 0.5f);
		int base = Colors.argb(0.92f * fade, Colors.shade(rgb, -0.15f));
		int tip = Colors.argb(0.92f * fade, Colors.mixRgb(rgb, 0xFFFFFF, 0.45f));
		int hind = Colors.argb(0.9f * fade, Colors.shade(rgb, -0.3f));
		float c = (float) Math.cos(flap);
		float sn = (float) Math.sin(flap);

		for (int side = -1; side <= 1; side += 2) {
			geo.triWorld(pose.wx(0, 0, 0.03), pose.wy(0, 0, 0.03), pose.wz(0, 0, 0.03), base,
					pose.wx(0, 0, -0.01), pose.wy(0, 0, -0.01), pose.wz(0, 0, -0.01), base,
					pose.wx(side * c * 0.09, sn * 0.09, 0.05), pose.wy(side * c * 0.09, sn * 0.09, 0.05), pose.wz(side * c * 0.09, sn * 0.09, 0.05), tip);
			geo.triWorld(pose.wx(0, 0, -0.005), pose.wy(0, 0, -0.005), pose.wz(0, 0, -0.005), hind,
					pose.wx(0, 0, -0.045), pose.wy(0, 0, -0.045), pose.wz(0, 0, -0.045), hind,
					pose.wx(side * c * 0.07, sn * 0.07, -0.05), pose.wy(side * c * 0.07, sn * 0.07, -0.05), pose.wz(side * c * 0.07, sn * 0.07, -0.05), base);
		}

		int body = Colors.argb(fade, 0x2A2A30);
		geo.line(pose.wx(0, 0, 0.035), pose.wy(0, 0, 0.035), pose.wz(0, 0, 0.035), pose.wx(0, 0, -0.04), pose.wy(0, 0, -0.04), pose.wz(0, 0, -0.04), 0.007f, body, body);
	}

	private void spawn(int i, double cx, double cy, double cz, double r, ThreadLocalRandom random) {
		int kind = style.index();
		fx[i] = cx + (random.nextDouble() - 0.5) * 2 * r;
		fy[i] = switch (kind) {
			case 5 -> cy - 0.5 + random.nextDouble() * 2.5;
			case 6 -> cy + 0.5 + random.nextDouble() * 1.8;
			case 8 -> cy + 0.3 + random.nextDouble() * 2.5;
			default -> cy + random.nextDouble() * 3.5 - 0.3;
		};
		fz[i] = cz + (random.nextDouble() - 0.5) * 2 * r;
		vx[i] = 0;
		vy[i] = 0;
		vz[i] = 0;
		phase[i] = random.nextFloat() * 6.28f;
		age[i] = 0;
		life[i] = kind == 5 ? 22f + random.nextFloat() * 10f : kind == 8 ? 12f + random.nextFloat() * 8f : 8f + random.nextFloat() * 8f;

		if (kind == 7) {
			// Far away and high up, waiting a few seconds before it appears
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = 60 + random.nextDouble() * 30;
			fx[i] = cx + Math.cos(angle) * distance;
			fy[i] = cy + 35 + random.nextDouble() * 25;
			fz[i] = cz + Math.sin(angle) * distance;
			double heading = random.nextDouble() * Math.PI * 2;
			float speed = 28f + random.nextFloat() * 14f;
			vx[i] = (float) Math.cos(heading) * speed;
			vz[i] = (float) Math.sin(heading) * speed;
			vy[i] = -6f - random.nextFloat() * 8f;
			life[i] = 0.8f + random.nextFloat() * 0.6f;
			age[i] = -(2f + random.nextFloat() * 8f) / Math.max(0.2f, density.f());
		}
	}

	@Override
	protected void onDisable() {
		flies = 0;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		java.util.Random random = new java.util.Random(7);

		for (int i = 0; i < 14; i++) {
			float bx = x + 8 + random.nextFloat() * (w - 16);
			float by = y + 6 + random.nextFloat() * (h - 12);
			float ph = random.nextFloat() * 6.28f;
			float wx = bx + (float) Math.sin(time * 0.7f + ph) * 6;
			float wy = by + (float) Math.cos(time * 0.5f + ph) * 4;

			if (style.index() == 1) {
				wy = y + ((by - y + time * 10 + ph * 10) % h);
			} else if (style.index() == 5) {
				wy = y + h - ((y + h - by + time * 5 + ph * 10) % h);
			}

			float blink = 0.3f + 0.7f * Math.max(0f, (float) Math.sin(time * 1.7f + ph));
			int rgb = style.index() == 0 || style.index() >= 5 ? color.rgb() : style.index() == 3 ? 0x6FE3FF : 0xEDEDF2;

			if (style.index() == 5) {
				rgb = Colors.mixRgb(rgb, 0xFF7A2A, 0.55f);
				Gui2D.glowDot(g, wx, wy, 2.5f, rgb, 0.5f);
				Gui2D.fill(g, Math.round(wx - 1.5f), Math.round(wy - 2), Math.round(wx + 1.5f), Math.round(wy + 2), Colors.argb(0.95f, Colors.shade(rgb, 0.2f)));
			} else if (style.index() == 6) {
				float flapW = 1 + Math.abs((float) Math.sin(time * 12 + ph)) * 2.5f;
				Gui2D.fill(g, Math.round(wx - flapW), Math.round(wy - 1), Math.round(wx), Math.round(wy + 1), Colors.argb(0.9f, Colors.hsv(ph / 6.28f, 0.5f, 1f)));
				Gui2D.fill(g, Math.round(wx), Math.round(wy - 1), Math.round(wx + flapW), Math.round(wy + 1), Colors.argb(0.9f, Colors.hsv(ph / 6.28f, 0.5f, 1f)));
			} else {
				Gui2D.glowDot(g, wx, wy, 2, rgb, style.index() == 0 ? blink : 0.8f);
			}
		}
	}
}
