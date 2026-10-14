package dev.juiced.modules.visual;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** Glowing footprints that fade behind you as you walk. */
public final class Footprints extends Module {
	private static final int CAPACITY = 96;
	private static final double STRIDE = 0.62;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Leuchtend", "Wasserringe", "Blumen", "Pfoten", "Wolken"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting duration = add(new SliderSetting("duration", "Dauer", 1, 10, 0.5, 4, v -> String.format("%.1fs", v)));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.6, 1.6, 1.0));
	private final BoolSetting others = add(new BoolSetting("others", "Andere Spieler", false));

	private final double[] fx = new double[CAPACITY];
	private final double[] fy = new double[CAPACITY];
	private final double[] fz = new double[CAPACITY];
	private final float[] yaw = new float[CAPACITY];
	private final double[] born = new double[CAPACITY];
	private int head;
	private final Map<Integer, Walker> walkers = new HashMap<>();

	public Footprints() {
		super("footprints", "Fußspuren", Category.VISUALS, false,
				"Spuren, wo du läufst: leuchtende Abdrücke, Wasserringe wie auf einem stillen See, aufblühende Blumen, Pfotenabdrücke oder kleine Wölkchen, als würdest du auf Wolken laufen.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self == null || minecraft.level == null) {
			walkers.clear();
			return;
		}

		step(self);

		if (others.on()) {
			for (Player player : minecraft.level.players()) {
				if (player != self && player.distanceToSqr(self) < 32 * 32) {
					step(player);
				}
			}
		}

		walkers.values().removeIf(w -> Clock.t() - w.seen > 2);
	}

	private void step(Entity entity) {
		Walker walker = walkers.computeIfAbsent(entity.getId(), id -> new Walker(entity.getX(), entity.getZ()));
		walker.seen = Clock.t();
		double dx = entity.getX() - walker.lastX;
		double dz = entity.getZ() - walker.lastZ;
		double moved = Math.sqrt(dx * dx + dz * dz);

		if (!entity.onGround() || entity.isInWater()) {
			walker.lastX = entity.getX();
			walker.lastZ = entity.getZ();
			return;
		}

		if (moved < STRIDE) {
			return;
		}

		float direction = (float) Math.atan2(dz, dx);
		walker.left = !walker.left;
		double side = walker.left ? 0.14 : -0.14;
		// Perpendicular to the walking direction
		double px = -Math.sin(direction) * side;
		double pz = Math.cos(direction) * side;
		fx[head] = entity.getX() + px;
		fy[head] = entity.getY() + 0.015;
		fz[head] = entity.getZ() + pz;
		yaw[head] = direction;
		born[head] = Clock.t();
		head = (head + 1) % CAPACITY;
		walker.lastX = entity.getX();
		walker.lastZ = entity.getZ();
	}

	@Override
	public void renderWorld(Geo geo) {
		double now = Clock.t();
		float life = duration.f();
		float s = size.f();

		for (int i = 0; i < CAPACITY; i++) {
			if (born[i] == 0) {
				continue;
			}

			float t = (float) ((now - born[i]) / life);

			if (t >= 1f || t < 0f) {
				continue;
			}

			float alpha = (t < 0.1f ? t / 0.1f : 1f - (t - 0.1f) / 0.9f) * 0.85f;
			int rgb = color.rgb(i / (float) CAPACITY);

			switch (style.index()) {
				case 1 -> ripples(geo, fx[i], fy[i], fz[i], t, life, s, rgb);
				case 2 -> flower(geo, fx[i], fy[i], fz[i], yaw[i], (float) (now - born[i]), t, s, rgb, i);
				case 3 -> paw(geo, fx[i], fy[i], fz[i], yaw[i], s, Colors.argb(alpha, rgb));
				case 4 -> cloud(geo, fx[i], fy[i], fz[i], (float) (now - born[i]), s, i);
				default -> print(geo, fx[i], fy[i], fz[i], yaw[i], s, Colors.argb(alpha, rgb), Colors.argb(alpha * 0.3f, rgb));
			}
		}
	}

	/** Rings spreading out from a step, like walking on a still lake. */
	private static void ripples(Geo geo, double x, double y, double z, float t, float life, float s, int rgb) {
		int light = Colors.mixRgb(rgb, 0xFFFFFF, 0.55f);
		int segments = Theme.detail(22);
		// The rings take about two seconds no matter how long the step stays
		float seconds = t * life;

		for (int ring = 0; ring < 2; ring++) {
			float local = (seconds - ring * 0.35f) / 2.2f;

			if (local <= 0f || local >= 1f) {
				continue;
			}

			float eased = 1f - (1f - local) * (1f - local);
			float radius = (0.04f + 0.42f * eased) * s * (1f - ring * 0.18f);
			geo.groundRing(x, y + 0.004 * ring, z, radius, 0.016f * s, 0f, segments, 0f, (float) (Math.PI * 2), c -> light, 0.6f * (1f - local));
		}

		float pool = Math.max(0f, 1f - t * 1.4f);

		if (pool > 0f) {
			geo.disc(x, y - 0.002, z, 0.16f * s, 12, c -> rgb, 0.18f * pool, 0f);
		}
	}

	/** A small flower that opens where you stepped, then fades. */
	private static void flower(Geo geo, double x, double y, double z, float direction, float age, float t, float s, int rgb, int index) {
		float open = Math.min(1f, age / 0.45f);
		open = 1f - (1f - open) * (1f - open);
		float alpha = t > 0.7f ? (1f - t) / 0.3f : 1f;
		// Soft pastel shades of the chosen color
		int petalBase = Colors.mixRgb(rgb, 0xFFFFFF, 0.25f + (index % 3) * 0.12f);
		int petalTip = Colors.mixRgb(rgb, 0xFFFFFF, 0.7f);
		int base = Colors.argb(alpha * 0.95f, petalBase);
		int tip = Colors.argb(alpha * 0.95f, petalTip);
		int petals = 5;
		double reach = 0.075 * s * open;
		double width = 0.03 * s * open;

		for (int k = 0; k < petals; k++) {
			double a = direction + k * Math.PI * 2 / petals;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			double midX = x + cos * reach * 0.5;
			double midZ = z + sin * reach * 0.5;
			geo.quadWorld(x, y, z, base, midX - sin * width, y, midZ + cos * width, base,
					x + cos * reach, y, z + sin * reach, tip, midX + sin * width, y, midZ - cos * width, base);
		}

		// Two leaves underneath
		int leaf = Colors.argb(alpha * 0.85f, 0x5FA85A);

		for (int sign = -1; sign <= 1; sign += 2) {
			double a = direction + Math.PI / 2 + sign * 0.9;
			double lx = x + Math.cos(a) * 0.09 * s * open;
			double lz = z + Math.sin(a) * 0.09 * s * open;
			double wx = -Math.sin(a) * 0.022 * s * open;
			double wz = Math.cos(a) * 0.022 * s * open;
			geo.quadWorld(x, y - 0.002, z, leaf, (x + lx) / 2 + wx, y - 0.002, (z + lz) / 2 + wz, leaf, lx, y - 0.002, lz, leaf, (x + lx) / 2 - wx, y - 0.002, (z + lz) / 2 - wz, leaf);
		}

		geo.disc(x, y + 0.002, z, 0.018f * s * open, 8, c -> 0xFFD54F, alpha, alpha);
	}

	/** A small puff of cloud that swells up, drifts a little higher and fades. */
	private static void cloud(Geo geo, double x, double y, double z, float age, float s, int index) {
		// Clouds are short-lived no matter how long the other footprints stay
		float t = age / 2.4f;

		if (t >= 1f) {
			return;
		}

		float grow = Math.min(1f, t * 5f);
		float alpha = (1f - t) * 0.75f;
		double rise = t * 0.18;

		for (int k = 0; k < 3; k++) {
			double a = index * 1.3 + k * Math.PI * 2 / 3;
			double spread = 0.07 * s * grow;
			float size = (k == 0 ? 0.13f : 0.09f) * s * (0.6f + 0.4f * grow);
			geo.glowDot(x + Math.cos(a) * spread * (k == 0 ? 0 : 1), y + 0.06 + rise + (k == 0 ? 0.03 : 0), z + Math.sin(a) * spread * (k == 0 ? 0 : 1),
					size, 0xF4F7FF, alpha);
		}
	}

	/** A cat paw: one big pad and four toe beans, pointing where you walked. */
	private static void paw(Geo geo, double x, double y, double z, float direction, float s, int color) {
		float alpha = (color >>> 24) / 255f;
		int rgb = color & 0xFFFFFF;
		double cos = Math.cos(direction);
		double sin = Math.sin(direction);
		geo.disc(x - cos * 0.02 * s, y, z - sin * 0.02 * s, 0.045f * s, 10, c -> rgb, alpha, alpha * 0.8f);

		for (int k = 0; k < 4; k++) {
			double a = direction + (k - 1.5) * 0.55;
			double tx = x + Math.cos(a) * 0.065 * s;
			double tz = z + Math.sin(a) * 0.065 * s;
			geo.disc(tx, y, tz, 0.019f * s, 6, c -> rgb, alpha, alpha * 0.8f);
		}
	}

	/** One footprint: a sole and a heel, rotated along the walking direction. */
	private static void print(Geo geo, double x, double y, double z, float direction, float s, int core, int edge) {
		float cos = (float) Math.cos(direction);
		float sin = (float) Math.sin(direction);
		float cx = geo.rx(x);
		float cy = geo.ry(y);
		float cz = geo.rz(z);
		// Sole: in front of the center
		rect(geo, cx, cy, cz, cos, sin, 0.05f * s, 0.06f * s, 0.045f * s, core);
		// Heel: behind the center
		rect(geo, cx, cy, cz, cos, sin, -0.08f * s, 0.035f * s, 0.035f * s, core);
		// Soft glow around both
		rect(geo, cx, cy, cz, cos, sin, -0.01f * s, 0.14f * s, 0.07f * s, edge);
	}

	private static void rect(Geo geo, float cx, float cy, float cz, float cos, float sin, float forward, float halfLength, float halfWidth, int c) {
		float ox = cx + cos * forward;
		float oz = cz + sin * forward;
		float lx = cos * halfLength;
		float lz = sin * halfLength;
		float wx = -sin * halfWidth;
		float wz = cos * halfWidth;
		geo.quad(ox - lx - wx, cy, oz - lz - wz, c,
				ox + lx - wx, cy, oz + lz - wz, c,
				ox + lx + wx, cy, oz + lz + wz, c,
				ox - lx + wx, cy, oz - lz + wz, c);
	}

	@Override
	protected void onDisable() {
		walkers.clear();
		java.util.Arrays.fill(born, 0);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float speed = 1.5f;

		for (int i = 0; i < 8; i++) {
			float age = (time * speed - i * 0.5f) % 4f;

			if (age < 0) {
				continue;
			}

			float t = i / 8f;
			float px = x + w - 14 - i * (w - 28) / 8f;
			float py = y + h / 2f + (i % 2 == 0 ? -5 : 5);
			float alpha = Math.max(0f, 1f - t * 1.1f);
			int rgb = color.rgb(t);

			switch (style.index()) {
				case 1 -> Gui2D.ellipse(g, px, py, 2 + (age % 1.5f) * 4, 1 + (age % 1.5f) * 1.5f, 14, 1f, Colors.mixRgb(rgb, 0xFFFFFF, 0.5f), alpha * 0.8f, 0, false);
				case 2 -> {
					for (int k = 0; k < 5; k++) {
						double a = k * Math.PI * 2 / 5 + i;
						Gui2D.dot(g, px + (float) Math.cos(a) * 2.2f, py + (float) Math.sin(a) * 1.6f, 2f, Colors.argb(alpha, Colors.mixRgb(rgb, 0xFFFFFF, 0.35f)));
					}

					Gui2D.dot(g, px, py, 1.5f, Colors.argb(alpha, 0xFFD54F));
				}
				case 4 -> {
					Gui2D.glowDot(g, px, py, 2.5f, 0xF4F7FF, alpha);
					Gui2D.glowDot(g, px + 2, py - 1, 2f, 0xF4F7FF, alpha);
				}
				case 3 -> {
					Gui2D.dot(g, px - 1, py, 3f, Colors.argb(alpha, rgb));

					for (int k = 0; k < 4; k++) {
						Gui2D.dot(g, px + 2.5f, py - 3 + k * 2f, 1.2f, Colors.argb(alpha, rgb));
					}
				}
				default -> {
					Gui2D.rounded(g, Math.round(px - 4), Math.round(py - 2), Math.round(px + 2), Math.round(py + 2), Colors.argb(alpha, rgb));
					Gui2D.rounded(g, Math.round(px + 3), Math.round(py - 1), Math.round(px + 6), Math.round(py + 2), Colors.argb(alpha, rgb));
				}
			}
		}
	}

	private static final class Walker {
		double lastX;
		double lastZ;
		boolean left;
		double seen;

		Walker(double x, double z) {
			this.lastX = x;
			this.lastZ = z;
		}
	}
}
