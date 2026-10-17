package dev.juiced.modules.tweaks;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.MenuState;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.FreeCamera;
import dev.juiced.render.Gui2D;

/**
 * When you take damage, a soft ink-brush arc appears around the crosshair on the side the hit came
 * from, then fades like ink drying on paper.
 *
 * <p>The direction is {@code getHurtDir()}: the yaw the server sends with a hit for the vanilla hurt-cam
 * tilt, relative to where you were looking. Damage without a direction (fire, falling, most explosions)
 * never updates it, so those get no arc, or an open ink circle if wanted. The attacker's position is never read.
 */
public final class HitDirection extends Module {
	private static final int MAX = 4;
	/** Radius at 100% size, in GUI pixels. */
	private static final float RADIUS = 24f;
	private static final float SPAN = (float) Math.toRadians(76);
	/** The brush is drawn in half GUI pixels so its edges stay fine. */
	private static final float SUB = 2f;
	/**
	 * The point along the stroke that sits on the hit direction: between where the brush presses hardest
	 * (0.18) and the middle of the ink (0.42), so the arc reads as centered on it.
	 */
	private static final float BELLY = 0.3f;
	private static final float DRAW_SECONDS = 0.12f;
	/** Two hurt signals this close together are one hit (damage and hurt-animation packets can arrive a tick apart). */
	private static final int SAME_HIT_TICKS = 4;

	private static final int MELEE = 0xE5383B;
	private static final int PROJECTILE = 0xF4F1EA;
	private static final int FIRE = 0xFF7A1F;
	private static final int EXPLOSION = 0xFFB020;
	private static final int OTHER = 0xA3A3AE;

	// Card preview: hits from the left, from behind and from the right (sword, arrow, fireball)
	private static final float[] PREVIEW_ANGLES = {-1.75f, 3.0f, 1.4f};
	private static final int[] PREVIEW_COLORS = {MELEE, PROJECTILE, FIRE};
	/** Menu try-out: one made-up hit of each kind in turn. */
	private static final int[] PROBE_COLORS = {MELEE, PROJECTILE, FIRE, EXPLOSION};
	/** A gap in the menu's probe ticks longer than this means the menu stopped showing the arcs. */
	private static final double PROBE_GRACE = 0.25;

	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.5, 2.0, 1.0));
	private final SliderSetting duration = add(new SliderSetting("duration", "Dauer", 0.6, 2.5, 0.1, 1.2, v -> String.format("%.1fs", v)));
	private final SliderSetting opacity = add(SliderSetting.percent("opacity", "Deckkraft", 0.2, 1.0, 0.85));
	private final BoolSetting undirected = add(new BoolSetting("undirected", "Schaden ohne Richtung als Kreis", false));

	// One slot per arc. yaw is the world yaw you would face to look at where the hit came from.
	private final float[] yaw = new float[MAX];
	private final double[] start = new double[MAX];
	private final int[] rgb = new int[MAX];
	private final int[] seed = new int[MAX];
	private final boolean[] circle = new boolean[MAX];
	private final boolean[] alive = new boolean[MAX];
	/** Made up by the menu; such an arc must never reach the real HUD after the menu closes. */
	private final boolean[] fake = new boolean[MAX];

	private boolean primed;
	private int lastHurtTime;
	private float lastHurtDir;
	private long ticks;
	private long lastHitTick = -100;
	private int newest = -1;
	private int seedCounter;
	private double probeSeen = -10;
	private int probeAt;
	private int probeHits;

	public HitDirection() {
		super("hit_direction", "Treffer-Richtung", Category.TWEAKS, true,
				"Ein Pinselbogen am Fadenkreuz zeigt, woher ein Treffer kam, und verblasst wie Tinte. Rot für Nahkampf, weiß für Geschosse, "
						+ "orange für Feuer, bernsteinfarben für Explosionen. Nutzt nur die Richtung, die auch die Hurt-Cam bekommt.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;

		if (player == null) {
			primed = false;
			return;
		}

		ticks++;
		int hurtTime = player.hurtTime;
		float hurtDir = player.getHurtDir();

		if (!primed) {
			primed = true;
			lastHurtTime = hurtTime;
			lastHurtDir = hurtDir;
			return;
		}

		boolean justHurt = hurtTime > lastHurtTime && !player.isDeadOrDying();
		boolean dirChanged = hurtDir != lastHurtDir;
		lastHurtTime = hurtTime;
		lastHurtDir = hurtDir;
		boolean sameHit = ticks - lastHitTick <= SAME_HIT_TICKS;

		if (justHurt && !sameHit) {
			lastHitTick = ticks;
			newest = -1;
			show(player, hurtDir, dirChanged);
		} else if (sameHit && (justHurt || dirChanged)) {
			// The second packet of the same hit: take its direction and damage type as they are now
			show(player, hurtDir, dirChanged);
		}
	}

	/** Shows (or updates) the arc for the current hit. */
	private void show(LocalPlayer player, float hurtDir, boolean dirChanged) {
		DamageSource source = player.getLastDamageSource();
		// The server only sends a fresh direction with damage that can knock you back
		boolean directed = source == null ? dirChanged : !source.is(DamageTypeTags.NO_KNOCKBACK);

		if (!directed && !undirected.on()) {
			if (newest >= 0) {
				alive[newest] = false;
				newest = -1;
			}

			return;
		}

		if (newest < 0) {
			newest = freeSlot();
			start[newest] = Clock.t();
			seed[newest] = ++seedCounter * 0x9E3779B1;
		}

		alive[newest] = true;
		fake[newest] = false;
		circle[newest] = !directed;
		rgb[newest] = colorOf(source);
		// hurtDir is the hit's direction minus your yaw, with 90 meaning straight ahead
		yaw[newest] = player.getYRot() + hurtDir - 90f;
	}

	/** A free slot, or the oldest arc's. */
	private int freeSlot() {
		int slot = 0;

		for (int i = 0; i < MAX; i++) {
			if (!alive[i]) {
				return i;
			}

			if (start[i] < start[slot]) {
				slot = i;
			}
		}

		return slot;
	}

	/**
	 * The menu can't hurt you, so while it shows Treffer-Richtung it makes up a hit every 1.6 s (longer if the arcs
	 * last longer), from a direction that turns round you and in the colours of sword, arrow, fire and explosion.
	 */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		LocalPlayer player = minecraft.player;
		probeSeen = Clock.t();

		if (player == null) {
			return;
		}

		if (ticks < probeAt) {
			// Tuned in again: start over with the first kind
			probeAt = 0;
			probeHits = 0;
		}

		int every = Math.max(32, Math.round((duration.f() + 0.3f) * 20f));

		// The first one waits a moment for the camera to arrive in your view
		if (ticks >= 10 && (probeHits == 0 || ticks - probeAt >= every)) {
			probeAt = ticks;
			previewHit(minecraft, player, probeHits++);
		}
	}

	private void previewHit(Minecraft minecraft, LocalPlayer player, int n) {
		int slot = freeSlot();

		if (slot == newest) {
			newest = -1;
		}

		alive[slot] = true;
		fake[slot] = true;
		start[slot] = Clock.t();
		seed[slot] = ++seedCounter * 0x9E3779B1;
		rgb[slot] = PROBE_COLORS[n % PROBE_COLORS.length];
		// Every 4th (the explosion) shows the circle for hits without a direction, if that is switched on
		circle[slot] = undirected.on() && n % 4 == 3;
		// Steps of the golden angle, so the side keeps changing and doesn't repeat soon
		yaw[slot] = viewYaw(minecraft, player, 1f) - 100f + n * 137.5f;
	}

	/** True while the menu shows this module; ends at once when the menu closes and gives the HUD back. */
	private boolean probeShown() {
		return MenuState.hidesHud() && Clock.t() - probeSeen <= PROBE_GRACE;
	}

	/** The yaw the camera looks along (Freelook, the menu camera and front view turn it away from the player's). */
	private static float viewYaw(Minecraft minecraft, LocalPlayer player, float partialTick) {
		float viewYaw = FreeCamera.cameraYaw(player.getYRot(partialTick));

		if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			viewYaw += 180f;
		}

		return viewYaw;
	}

	private static int colorOf(@Nullable DamageSource source) {
		if (source == null) {
			return OTHER;
		}

		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return EXPLOSION;
		}

		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE;
		}

		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return PROJECTILE;
		}

		return source.getEntity() != null && source.isDirect() ? MELEE : OTHER;
	}

	@Override
	public void renderHud(GuiGraphicsExtractor g, Hud hud) {
		LocalPlayer player = hud.minecraft.player;

		if (player == null) {
			return;
		}

		double now = Clock.t();
		float life = duration.f();
		// Relative to the camera, so the arc stays right while looking around with Freelook or in front view
		float viewYaw = viewYaw(hud.minecraft, player, hud.partialTick);
		float cx = hud.centerX() + 0.5f;
		float cy = hud.centerY() + 0.5f;
		boolean menu = probeShown();

		if (menu) {
			// The menu hides the crosshair, and the arcs only make sense around it
			crosshair(g, hud.centerX(), hud.centerY(), 0x99FFFFFF);
		}

		for (int i = 0; i < MAX; i++) {
			if (!alive[i]) {
				continue;
			}

			if (fake[i] && !menu) {
				alive[i] = false;
				continue;
			}

			float age = (float) (now - start[i]);

			if (age >= life) {
				alive[i] = false;
				continue;
			}

			float radius = RADIUS * size.f();

			if (circle[i]) {
				stroke(g, cx, cy, radius * 0.72f, hash(seed[i], 3, 5) * 6.2832f, (float) Math.toRadians(320), 0.5f, rgb[i], opacity.f() * 0.8f, age, life, seed[i]);
			} else {
				float angle = (float) Math.toRadians(yaw[i] - viewYaw);
				stroke(g, cx, cy, radius, angle, SPAN, 1f, rgb[i], opacity.f(), age, life, seed[i]);
			}
		}
	}

	/**
	 * One brush stroke along a circle around (cx, cy). {@code angle} is the screen direction of the
	 * heaviest part (0 = up, clockwise). The stroke enters with a point, swells, then runs out into a
	 * dry, streaky tail. While it fades, the thin end dries first and the edges bleed a little.
	 */
	private static void stroke(GuiGraphicsExtractor g, float cx, float cy, float radius, float angle, float span, float weight,
			int rgb, float alpha, float age, float life, int seed) {
		float drawn = Clock.easeOut(age / DRAW_SECONDS);
		float fade = Colors.clamp01((age - life * 0.3f) / (life * 0.7f));

		if (alpha <= 0.01f || fade >= 1f) {
			return;
		}

		float r = radius * SUB;
		float width = Math.max(3f, r * 0.14f) * weight;
		float step = Math.max(Theme.quality() == 0 ? 2.4f : Theme.quality() == 1 ? 1.8f : 1.4f, width * 0.3f);
		int steps = Math.max(10, Math.min(90, Math.round(r * span / step)));
		float arcStep = r * span / steps;
		int ink = Colors.shade(rgb, -0.08f);
		int bleed = Colors.shade(rgb, -0.6f);
		// Light inks (arrows, gray) get a stronger dark rim so they still read against a bright sky or snow
		float light = (((rgb >> 16) & 0xFF) * 0.3f + ((rgb >> 8) & 0xFF) * 0.59f + (rgb & 0xFF) * 0.11f) / 255f;
		float rim = 0.12f + 0.22f * light * light;
		float wobblePhase = ((seed >>> 8) & 0xFF) / 40f;

		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(1f / SUB);

		for (int i = 0; i < steps; i++) {
			float u = i / (float) (steps - 1);

			if (u > drawn) {
				break;
			}

			// Pointed entry, full belly, long thinning tail
			float press = smooth(u / 0.18f) * (float) Math.pow(1f - u, 0.6);
			float w = width * press;

			if (w < 0.6f) {
				continue;
			}

			// The thin tail dries first, the heavy start last
			float a = alpha * Colors.clamp01(1f - fade * (1f + 0.9f * u));

			if (a <= 0.01f) {
				continue;
			}

			double phi = angle + (u - BELLY) * span;
			float rr = r + width * 0.22f * (float) Math.sin(u * 5.3f + wobblePhase);
			float sin = (float) Math.sin(phi);
			float cos = (float) Math.cos(phi);
			float x = sin * rr;
			float y = -cos * rr;

			// Bleed: a faint dark rim that slowly spreads as the ink dries
			if (i % 2 == 0 && u < 0.8f) {
				float spread = w * (1.6f + fade * 0.9f);
				Gui2D.dot(g, x, y, spread, Colors.argb(a * rim * (1f - u * 0.6f), bleed));
			}

			// Dabs overlap; this alpha makes the overlap add up to roughly 'a'
			float overlap = Math.max(1f, w / arcStep);

			if (u < 0.55f) {
				Gui2D.dot(g, x, y, w, Colors.argb(layer(a, overlap), ink));
			} else {
				// Dry brush: the stroke splits into bristles with gaps that grow towards the end
				float bristle = Math.max(0.8f, w * 0.42f);
				float gaps = (u - 0.55f) * 1.4f;
				float bristleAlpha = layer(a, Math.max(1f, bristle / arcStep));

				for (int k = -1; k <= 1; k++) {
					if (hash(seed, i / 2, k) < gaps) {
						continue;
					}

					float off = k * w * 0.32f;
					Gui2D.dot(g, x - sin * off, y + cos * off, bristle, Colors.argb(bristleAlpha, ink));
				}
			}
		}

		// A few specks flicked off where the brush pressed down
		if (drawn > 0.4f && weight >= 1f) {
			float speckAlpha = alpha * Colors.clamp01(1f - fade * 1.3f);

			for (int k = 0; k < 4; k++) {
				float u = 0.08f + hash(seed, 31, k) * 0.4f;
				double phi = angle + (u - BELLY) * span;
				float side = hash(seed, 37, k) < 0.5f ? -1f : 1f;
				float rr = r + side * (width * 0.9f + hash(seed, 41, k) * width * 1.1f);
				float speck = 1f + hash(seed, 43, k) * 1.6f;
				Gui2D.dot(g, (float) Math.sin(phi) * rr, -(float) Math.cos(phi) * rr, speck, Colors.argb(speckAlpha * 0.8f, ink));
			}
		}

		g.pose().popMatrix();
	}

	/** Alpha for one of {@code count} overlapping layers so that together they reach {@code alpha}. */
	private static float layer(float alpha, float count) {
		return 1f - (float) Math.pow(1f - Math.min(0.98f, alpha), 1f / count);
	}

	private static float smooth(float t) {
		t = Colors.clamp01(t);
		return t * t * (3f - 2f * t);
	}

	/** Stable pseudo-random number in 0..1, so the brush texture doesn't flicker between frames. */
	private static float hash(int seed, int a, int b) {
		int h = seed ^ (a * 0x27D4EB2D) ^ (b * 0x165667B1);
		h ^= h >>> 15;
		h *= 0x85EBCA77;
		h ^= h >>> 13;
		return (h >>> 8) / (float) (1 << 24);
	}

	@Override
	protected void onDisable() {
		Arrays.fill(alive, false);
		primed = false;
		newest = -1;
		probeSeen = -10;
	}

	private static void crosshair(GuiGraphicsExtractor g, int cx, int cy, int color) {
		Gui2D.fill(g, cx - 4, cy, cx + 5, cy + 1, color);
		Gui2D.fill(g, cx, cy - 4, cx + 1, cy + 5, color);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		crosshair(g, Math.round(cx), Math.round(cy), 0xCCFFFFFF);

		float period = 1.5f;
		int index = (int) (time / period);
		float age = Previews.cycle(time, period) * period;
		int k = Math.floorMod(index, PREVIEW_ANGLES.length);
		float radius = Math.min(w, h) * 0.34f;
		stroke(g, cx, cy, radius, PREVIEW_ANGLES[k], SPAN, 1f, PREVIEW_COLORS[k], 0.95f, age, period, 7 + k * 0x3F1);
	}
}
