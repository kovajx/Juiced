package dev.juiced.modules.combat;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;
import dev.juiced.render.Sparks;

/**
 * After your hit, the target pulls a short comet tail along its knockback path: gold when you hit
 * with full sprint knockback, blue when the sprint bonus was lost. Where it lands, a dust ring and the
 * distance it flew appear. Positions come from your client, so the distance is a close estimate.
 */
public final class KnockbackComet extends Module {
	private static final int SLOTS = 6;
	/** One position per tick, enough for the whole flight. */
	private static final int SAMPLES = 34;
	private static final int FLIGHT_TICKS = 30;
	/** After touchdown the drawn position still glides towards the server's for a tick or two. */
	private static final int SETTLE_TICKS = 3;
	private static final float TAIL_SECONDS = 0.32f;
	private static final float RESULT_SECONDS = 1.5f;
	private static final float FULL = (float) (Math.PI * 2);
	private static final int BLUE = 1;
	private static final int DUST = 0xD9CDB4;

	private final BoolSetting tail = add(new BoolSetting("tail", "Schweif", true));
	private final BoolSetting showDistance = add(new BoolSetting("distance", "Weite anzeigen", true));
	private final ColorSetting sprintColor = add(new ColorSetting("sprint_color", "Mit Sprint", Colors.GOLD, true));
	private final ColorSetting normalColor = add(new ColorSetting("normal_color", "Ohne Sprint", BLUE, true));
	private final BoolSetting playersOnly = add(new BoolSetting("players_only", "Nur Spieler", false));

	private final Comet[] comets = new Comet[SLOTS];
	private final Geo.Tint sprintTint = t -> sprintColor.rgb(t);
	private final Geo.Tint normalTint = t -> normalColor.rgb(t);
	private final Geo.Tint dustTint = t -> DUST;
	private final float[] px = new float[SAMPLES + 2];
	private final float[] py = new float[SAMPLES + 2];
	private final float[] pz = new float[SAMPLES + 2];
	private final float[] pf = new float[SAMPLES + 2];
	private final float[] hw = new float[SAMPLES + 2];
	private final int[] colors = new int[SAMPLES + 2];

	public KnockbackComet() {
		super("knockback_comet", "Rückstoß-Komet", Category.COMBAT, false,
				"Dein Gegner zieht nach deinem Treffer einen kurzen Schweif. Gold bedeutet vollen Sprint-Rückstoß, blau bedeutet verschenkt. "
						+ "Beim Landen siehst du, wie weit er geflogen ist (gemessen auf deinem Client). So lernst du W-Tapping nebenbei.");

		for (int i = 0; i < SLOTS; i++) {
			comets[i] = new Comet();
		}
	}

	@Override
	public void onHit(LivingEntity target, boolean crit) {
		if (playersOnly.on() && !(target instanceof Player)) {
			return;
		}

		double now = Clock.t();
		Comet slot = null;

		for (Comet comet : comets) {
			// A new hit cuts the running flight short; its tail fades on its own
			if (comet.active && comet.flying && comet.entity == target) {
				comet.cut(now);
			}

			if (!comet.active && slot == null) {
				slot = comet;
			}
		}

		if (slot == null) {
			slot = comets[0];

			for (Comet comet : comets) {
				if (comet.hitTime < slot.hitTime) {
					slot = comet;
				}
			}
		}

		slot.start(target, CombatEvents.lastHitSprint(), now);
	}

	@Override
	public void tick(Minecraft minecraft) {
		double now = Clock.t();

		for (Comet comet : comets) {
			if (!comet.active || !comet.flying) {
				continue;
			}

			LivingEntity entity = comet.entity;

			if (entity == null || entity.isRemoved() || entity.isDeadOrDying()) {
				comet.cut(now);
				continue;
			}

			comet.ticks++;
			comet.record(entity, now);
			boolean onGround = entity.onGround();

			if (!onGround) {
				comet.airborne = true;
			}

			if (comet.settle > 0) {
				if (--comet.settle == 0 || comet.lastStep() < 0.03) {
					land(comet, entity, now);
				}
			} else if (onGround && (comet.airborne || (comet.ticks >= 6 && comet.lastStep() < 0.01))) {
				comet.settle = SETTLE_TICKS;
			} else if (comet.ticks >= FLIGHT_TICKS) {
				comet.cut(now);
			} else if (Theme.quality() > 0 && comet.lastStep() > 0.08) {
				sparkle(comet, entity);
			}
		}
	}

	/** A few sparks shed along the path, so the flight reads from every side. */
	private void sparkle(Comet comet, LivingEntity entity) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int rgb = comet.sprint ? sprintColor.rgb(random.nextFloat()) : normalColor.rgb(random.nextFloat());
		int count = Theme.quality() > 1 ? 2 : 1;

		for (int i = 0; i < count; i++) {
			Sparks.spawn(entity.getX() + (random.nextDouble() - 0.5) * 0.4, entity.getY() + comet.height * (0.25 + random.nextDouble() * 0.5),
					entity.getZ() + (random.nextDouble() - 0.5) * 0.4,
					(random.nextFloat() - 0.5f) * 0.4f, random.nextFloat() * 0.3f, (random.nextFloat() - 0.5f) * 0.4f,
					0.35f + random.nextFloat() * 0.2f, 0.045f, Colors.shade(rgb, 0.3f), Sparks.DOT, 0.6f, 2.5f);
		}
	}

	private void land(Comet comet, LivingEntity entity, double now) {
		comet.flying = false;
		comet.landed = true;
		comet.endTime = now;
		comet.endX = entity.getX();
		comet.endY = entity.getY();
		comet.endZ = entity.getZ();
		comet.entity = null;
		double dx = comet.endX - comet.startX;
		double dz = comet.endZ - comet.startZ;
		comet.label = String.format(Locale.ROOT, "%.1f", Math.sqrt(dx * dx + dz * dz));

		// Dust kicked up along the ground
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int base = comet.sprint ? sprintColor.rgb() : normalColor.rgb();
		int count = Theme.detail(12);

		for (int i = 0; i < count; i++) {
			double angle = (i + random.nextDouble() * 0.6) * Math.PI * 2 / count;
			float speed = 0.9f + random.nextFloat() * 0.9f;
			int rgb = Colors.mixRgb(DUST, base, random.nextFloat() * 0.35f);
			Sparks.spawn(comet.endX + Math.cos(angle) * 0.25, comet.endY + 0.08, comet.endZ + Math.sin(angle) * 0.25,
					(float) Math.cos(angle) * speed, 0.25f + random.nextFloat() * 0.35f, (float) Math.sin(angle) * speed,
					0.45f + random.nextFloat() * 0.3f, 0.07f + random.nextFloat() * 0.04f, rgb, Sparks.DOT, 1.2f, 3.5f);
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		double now = Clock.t();
		double range = Theme.effectRange();

		for (Comet comet : comets) {
			if (!comet.active) {
				continue;
			}

			float sinceEnd = comet.flying ? 0f : (float) (now - comet.endTime);

			if (!comet.flying && sinceEnd > (comet.landed ? RESULT_SECONDS : TAIL_SECONDS)) {
				comet.clear();
				continue;
			}

			if (geo.distanceSq(comet.startX, comet.startY, comet.startZ) > range * range) {
				continue;
			}

			if (tail.on()) {
				drawTail(geo, comet, now);
			}

			if (comet.landed) {
				drawLanding(geo, comet, sinceEnd / RESULT_SECONDS);
			}
		}
	}

	private void drawTail(Geo geo, Comet comet, double now) {
		LivingEntity entity = comet.entity;
		double cutoff = now - TAIL_SECONDS;
		int n = 0;
		int newest = comet.count - 1;

		if (comet.flying && entity != null) {
			// The head is the drawn entity; it sits between the last two samples, so skip the newest
			px[0] = geo.rx(geo.x(entity));
			py[0] = geo.ry(geo.y(entity) + comet.height * 0.5);
			pz[0] = geo.rz(geo.z(entity));
			pf[0] = 0f;
			n = 1;
			newest--;
		}

		for (int i = newest; i >= 0; i--) {
			double time = comet.time[i];

			if (time >= cutoff) {
				px[n] = geo.rx(comet.x[i]);
				py[n] = geo.ry(comet.y[i]);
				pz[n] = geo.rz(comet.z[i]);
				pf[n] = (float) ((now - time) / TAIL_SECONDS);
				n++;
				continue;
			}

			// Ends exactly at the cutoff, between this sample and the next newer one, so the tail shrinks smoothly
			if (i + 1 < comet.count && n > 0) {
				float k = (float) ((cutoff - time) / Math.max(1.0e-4, comet.time[i + 1] - time));
				px[n] = geo.rx(comet.x[i] + (comet.x[i + 1] - comet.x[i]) * k);
				py[n] = geo.ry(comet.y[i] + (comet.y[i + 1] - comet.y[i]) * k);
				pz[n] = geo.rz(comet.z[i] + (comet.z[i + 1] - comet.z[i]) * k);
				pf[n] = 1f;
				n++;
			}

			break;
		}

		if (n < 2) {
			return;
		}

		float spin = Clock.tf() * 0.2f;

		// Soft glow first, then a hot core that turns white towards the head
		for (int i = 0; i < n; i++) {
			float f = Colors.clamp01(pf[i]);
			hw[i] = 0.3f * (1f - f) + 0.02f;
			colors[i] = Colors.argb(0.26f * (1f - f), color(comet, f * 0.5f + spin));
		}

		geo.ribbon(px, py, pz, hw, colors, n);

		for (int i = 0; i < n; i++) {
			float f = Colors.clamp01(pf[i]);
			hw[i] = 0.11f * (float) Math.pow(1f - f, 0.7);
			colors[i] = Colors.argb(0.92f * (1f - f * f), Colors.shade(color(comet, f * 0.5f + spin), 0.22f * (1f - f)));
		}

		geo.ribbon(px, py, pz, hw, colors, n);
	}

	private void drawLanding(Geo geo, Comet comet, float t) {
		Geo.Tint tint = comet.sprint ? sprintTint : normalTint;
		int segments = Theme.detail(44);
		float ringT = t / 0.4f;

		if (ringT < 1f) {
			float radius = 0.25f + Clock.easeOut(ringT) * 1.15f;
			float fade = 1f - ringT;
			geo.disc(comet.endX, comet.endY + 0.02, comet.endZ, radius * 0.95f, segments, dustTint, 0.22f * fade, 0f);
			geo.groundRing(comet.endX, comet.endY + 0.03, comet.endZ, radius, 0.07f * fade + 0.015f, 0.18f, segments, 0f, FULL, tint, fade * 0.9f);
		}

		// A small mark stays where it landed while the number is up
		float mark = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
		geo.groundRing(comet.endX, comet.endY + 0.03, comet.endZ, 0.3f, 0.025f, 0.06f, segments / 2, 0f, FULL, tint, 0.55f * mark);

		if (showDistance.on()) {
			float pop = t < 0.06f ? 1.45f - t / 0.06f * 0.45f : 1f;
			geo.pixelText(comet.endX, comet.endY + comet.height + 0.45 + Clock.easeOut(t) * 0.25, comet.endZ,
					comet.label, 0.055f * pop, color(comet, 0f), mark, true);
		}
	}

	private int color(Comet comet, float offset) {
		return comet.sprint ? sprintColor.rgb(offset) : normalColor.rgb(offset);
	}

	@Override
	protected void onDisable() {
		for (Comet comet : comets) {
			comet.clear();
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		// Alternates a sprint hit (gold, far) and a lazy hit (blue, short)
		float period = 2.4f;
		boolean sprint = ((int) (time / period)) % 2 == 0;
		float cycle = Previews.cycle(time, period);
		float attackerX = x + w * 0.2f;
		float feetY = Previews.stage(g, x, y, w, h, attackerX, 0);
		int rgb = sprint ? sprintColor.rgb(time * 0.1f) : normalColor.rgb(time * 0.1f);
		float startX = attackerX + w * 0.08f;
		float distance = (sprint ? 0.52f : 0.24f) * w;
		float arc = sprint ? h * 0.32f : h * 0.18f;
		float flight = 0.38f;
		float u = Math.min(1f, cycle / flight);
		float s = 0.8f;

		// Tail: earlier points of the same flight
		for (int k = 10; k >= 1; k--) {
			float pu = u - k * 0.035f;

			if (pu <= 0f || cycle > flight + 0.15f) {
				continue;
			}

			float fade = (1f - k / 11f) * (cycle > flight ? 1f - (cycle - flight) / 0.15f : 1f);
			float tx = startX + distance * pu;
			float ty = feetY - 13 * s - arc * 4f * pu * (1f - pu);
			Gui2D.glowDot(g, tx, ty, 3f * (1f - k / 12f) + 1f, rgb, fade);
		}

		float bodyX = startX + distance * u;
		float bodyY = feetY - arc * 4f * u * (1f - u);
		Gui2D.figure(g, bodyX, bodyY, s, Colors.argb(1f, Colors.mixRgb(0xD8D8E0, rgb, 0.25f)));

		if (cycle > flight) {
			float t = (cycle - flight) / (1f - flight);
			float ring = Math.min(1f, t / 0.45f);
			Gui2D.ellipse(g, bodyX, feetY + 1, 5 + Clock.easeOut(ring) * w * 0.12f, (5 + Clock.easeOut(ring) * w * 0.12f) * 0.3f, 24, 2, rgb, (1f - ring) * 0.9f, 0, true);
			float alpha = t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f;
			Gui2D.centered(g, sprint ? "5.2" : "2.4", bodyX, feetY - 30 * s - 10 - t * 3, Colors.argb(alpha, rgb), true, 1f);
		}
	}

	/** One tracked flight. Fixed arrays, reused for the next hit. */
	private static final class Comet {
		final double[] x = new double[SAMPLES];
		final double[] y = new double[SAMPLES];
		final double[] z = new double[SAMPLES];
		final double[] time = new double[SAMPLES];
		@Nullable LivingEntity entity;
		boolean active;
		boolean flying;
		boolean airborne;
		boolean landed;
		boolean sprint;
		int count;
		int ticks;
		int settle;
		float height;
		double hitTime = Double.NEGATIVE_INFINITY;
		double startX;
		double startY;
		double startZ;
		double endTime;
		double endX;
		double endY;
		double endZ;
		String label = "";

		void start(LivingEntity target, boolean sprintHit, double now) {
			entity = target;
			active = true;
			flying = true;
			landed = false;
			airborne = !target.onGround();
			sprint = sprintHit;
			count = 0;
			ticks = 0;
			settle = 0;
			height = target.getBbHeight();
			hitTime = now;
			startX = target.getX();
			startY = target.getY();
			startZ = target.getZ();
			record(target, now);
		}

		void record(LivingEntity target, double now) {
			if (count == SAMPLES) {
				return;
			}

			x[count] = target.getX();
			y[count] = target.getY() + height * 0.5;
			z[count] = target.getZ();
			time[count] = now;
			count++;
		}

		/** Horizontal distance between the last two samples. */
		double lastStep() {
			if (count < 2) {
				return 0;
			}

			double dx = x[count - 1] - x[count - 2];
			double dz = z[count - 1] - z[count - 2];
			return Math.sqrt(dx * dx + dz * dz);
		}

		void cut(double now) {
			flying = false;
			landed = false;
			endTime = now;
			entity = null;
		}

		void clear() {
			active = false;
			flying = false;
			landed = false;
			entity = null;
		}
	}
}
