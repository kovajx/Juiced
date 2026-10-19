package dev.juiced.modules.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

/** Glowing trails behind arrows, pearls, tridents and wind charges, so you see where they came from. */
public final class ProjectileTrails extends Module {
	private static final int CAPACITY = 40;
	private static final int MAX_TRACKED = 48;
	/** The menu fires a pretend shot every 1.6 s, each 18 ticks in the air. */
	private static final int PROBE_PERIOD = 32;
	private static final int PROBE_FLIGHT = 18;
	/** Arrow, ender pearl, wind charge: the colors "Nach Typ" gives them. */
	private static final int[] PROBE_TYPES = {0xFFFFFF, 0xA77BFF, 0xBFF4FF};

	private final ChoiceSetting coloring = add(new ChoiceSetting("coloring", "Färbung", 0, "Nach Typ", "Eine Farbe"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting length = add(new SliderSetting("length", "Länge", 6, 40, 1, 20, v -> Math.round(v) + ""));
	private final BoolSetting mine = add(new BoolSetting("mine", "Eigene Projektile", true));

	private final Map<Integer, Track> tracks = new HashMap<>();
	private final Set<Integer> seen = new HashSet<>();
	/** Arcs the menu fires while it shows the trails; you can't throw anything while it is open. */
	private final List<ProbeShot> probeShots = new ArrayList<>();
	private final float[] px = new float[CAPACITY + 1];
	private final float[] py = new float[CAPACITY + 1];
	private final float[] pz = new float[CAPACITY + 1];
	private final float[] hw = new float[CAPACITY + 1];
	private final int[] colors = new int[CAPACITY + 1];

	public ProjectileTrails() {
		super("projectile_trails", "Projektil-Trails", Category.VISUALS, false,
				"Leuchtende Spuren hinter Pfeilen, Perlen, Dreizacken und Windkugeln. Du siehst sofort, woher sie kommen.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null || minecraft.level == null) {
			tracks.clear();
			probeShots.clear();
			return;
		}

		// Shots already fired fly on and fade even after the menu moves on
		for (ProbeShot shot : probeShots) {
			shot.fly();
		}

		probeShots.removeIf(shot -> shot.track.gone && shot.track.fade <= 0f);

		seen.clear();
		double range = Theme.effectRange();

		for (Entity entity : minecraft.level.entitiesForRendering()) {
			if (!(entity instanceof Projectile projectile) || entity.distanceToSqr(player) > range * range) {
				continue;
			}

			if (!mine.on() && projectile.getOwner() == player) {
				continue;
			}

			Track track = tracks.get(entity.getId());

			if (track == null) {
				if (tracks.size() >= MAX_TRACKED) {
					continue;
				}

				track = new Track(typeColor(entity));
				tracks.put(entity.getId(), track);
			}

			seen.add(entity.getId());
			track.entity = entity;
			track.push(entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ());
		}

		// Projectiles that vanished fade out instead of disappearing at once
		for (Map.Entry<Integer, Track> entry : tracks.entrySet()) {
			if (!seen.contains(entry.getKey())) {
				entry.getValue().gone = true;
			}
		}

		tracks.values().removeIf(track -> track.fade <= 0f);
	}

	/**
	 * Fires an arc from your chest out to the side of the camera view, landing about five blocks away,
	 * so the trail crosses the picture. The type colors take turns, the side alternates.
	 */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		LocalPlayer player = minecraft.player;

		if (player == null || ticks % PROBE_PERIOD != 4 || probeShots.size() >= 4) {
			return;
		}

		int shot = ticks / PROBE_PERIOD;
		double yaw = Math.toRadians(FreeCamera.cameraYaw(player.getYRot()));
		// Right and forward of the camera view
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double side = shot % 2 == 0 ? -4.2 : 4.2;
		double sx = player.getX();
		double sy = player.getEyeY() - 0.3;
		double sz = player.getZ();
		double ex = sx + rightX * side + forwardX * 2.5;
		double ez = sz + rightZ * side + forwardZ * 2.5;
		probeShots.add(new ProbeShot(PROBE_TYPES[shot % PROBE_TYPES.length], sx, sy, sz, ex, player.getY() + 0.15, ez));
	}

	private static int typeColor(Entity entity) {
		if (entity instanceof ThrownEnderpearl) {
			return 0xA77BFF;
		}

		if (entity instanceof AbstractWindCharge) {
			return 0xBFF4FF;
		}

		if (entity instanceof AbstractArrow) {
			return 0xFFFFFF;
		}

		return -1;
	}

	@Override
	public void renderWorld(Geo geo) {
		float dt = Clock.dt();

		for (Track track : tracks.values()) {
			Entity e = track.entity;

			if (!track.gone && e != null && !track.stuck()) {
				draw(geo, track, dt, true, geo.x(e), geo.y(e) + e.getBbHeight() / 2, geo.z(e));
			} else {
				draw(geo, track, dt, false, 0, 0, 0);
			}
		}

		for (ProbeShot shot : probeShots) {
			// The head flies on between ticks, so the arc is smooth at any frame rate
			float u = Math.min(1f, (shot.age + geo.partialTick()) / PROBE_FLIGHT);
			draw(geo, shot.track, dt, !shot.track.gone, shot.x(u), shot.y(u), shot.z(u));
		}
	}

	/** One trail, newest point first. With {@code head} the line starts at the drawn position of the projectile. */
	private void draw(Geo geo, Track track, float dt, boolean head, double headX, double headY, double headZ) {
		if (track.gone || track.stuck()) {
			track.fade -= dt * 2.5f;
		} else {
			track.fade = Math.min(1f, track.fade + dt * 6f);
		}

		if (track.size < 2 || track.fade <= 0f) {
			return;
		}

		int count = Math.min(track.size, length.i());
		int n = 0;

		if (head) {
			px[n] = geo.rx(headX);
			py[n] = geo.ry(headY);
			pz[n] = geo.rz(headZ);
			n++;
		}

		for (int i = 0; i < count; i++) {
			int index = track.index(i);
			px[n] = geo.rx(track.x[index]);
			py[n] = geo.ry(track.y[index]);
			pz[n] = geo.rz(track.z[index]);
			n++;
		}

		boolean byType = coloring.index() == 0 && track.color >= 0;

		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < n; i++) {
				float f = i / (float) Math.max(1, n - 1);
				int rgb = byType ? track.color : color.rgb(f * 0.5f);
				float alpha = (1f - f) * track.fade;
				hw[i] = (pass == 0 ? 0.09f : 0.025f) * (1f - f * 0.7f);
				colors[i] = Colors.argb(alpha * (pass == 0 ? 0.25f : 0.9f), pass == 0 ? rgb : Colors.shade(rgb, 0.3f));
			}

			geo.ribbon(px, py, pz, hw, colors, n);
		}
	}

	@Override
	protected void onDisable() {
		tracks.clear();
		probeShots.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float t = (time % 1.6f) / 1.6f;
		float headX = x + 10 + t * (w - 20);
		float headY = y + h * 0.7f - (float) Math.sin(t * Math.PI) * h * 0.45f;

		for (int i = 0; i < 18; i++) {
			float back = t - i * 0.02f;

			if (back < 0) {
				break;
			}

			float bx = x + 10 + back * (w - 20);
			float by = y + h * 0.7f - (float) Math.sin(back * Math.PI) * h * 0.45f;
			Gui2D.glowDot(g, bx, by, 2.5f * (1f - i / 18f) + 0.5f, 0xA77BFF, 1f - i / 18f);
		}

		Gui2D.glowDot(g, headX, headY, 3, 0xFFFFFF, 1f);
	}

	private static final class Track {
		final double[] x = new double[CAPACITY];
		final double[] y = new double[CAPACITY];
		final double[] z = new double[CAPACITY];
		final int color;
		int head;
		int size;
		int stillTicks;
		float fade;
		boolean gone;
		Entity entity;

		Track(int color) {
			this.color = color;
		}

		void push(double px, double py, double pz) {
			if (size > 0) {
				double dx = px - x[head];
				double dy = py - y[head];
				double dz = pz - z[head];

				if (dx * dx + dy * dy + dz * dz < 0.0004) {
					stillTicks++;
					return;
				}
			}

			stillTicks = 0;
			head = (head + 1) % CAPACITY;
			x[head] = px;
			y[head] = py;
			z[head] = pz;
			size = Math.min(CAPACITY, size + 1);
		}

		boolean stuck() {
			return stillTicks > 2;
		}

		int index(int i) {
			return Math.floorMod(head - i, CAPACITY);
		}
	}

	/** A pretend shot of the menu: a ballistic arc with a little hop, sampled once per tick like a real projectile. */
	private static final class ProbeShot {
		final Track track;
		final double sx;
		final double sy;
		final double sz;
		final double ex;
		final double ey;
		final double ez;
		int age;

		ProbeShot(int color, double sx, double sy, double sz, double ex, double ey, double ez) {
			this.track = new Track(color);
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.ex = ex;
			this.ey = ey;
			this.ez = ez;
			track.push(sx, sy, sz);
		}

		/** One tick of flight; lands after {@code PROBE_FLIGHT} ticks and fades like a projectile that hit something. */
		void fly() {
			if (track.gone) {
				return;
			}

			age++;
			float u = Math.min(1f, age / (float) PROBE_FLIGHT);
			track.push(x(u), y(u), z(u));
			track.gone = age >= PROBE_FLIGHT;
		}

		double x(float u) {
			return sx + (ex - sx) * u;
		}

		double y(float u) {
			return sy + (ey - sy) * u + 1.5 * 4 * u * (1 - u);
		}

		double z(float u) {
			return sz + (ez - sz) * u;
		}
	}
}
