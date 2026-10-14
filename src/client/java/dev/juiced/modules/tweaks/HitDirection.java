package dev.juiced.modules.tweaks;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
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
	/** Where along the stroke the brush presses hardest; this part sits on the hit direction. */
	private static final float BELLY = 0.3f;
	private static final float DRAW_SECONDS = 0.12f;
	/** Two hurt signals this close together are one hit (damage and hurt-animation packets can arrive a tick apart). */
	private static final int SAME_HIT_TICKS = 4;

	private static final int MELEE = 0xE5383B;
	private static final int PROJECTILE = 0xF4F1EA;
	private static final int FIRE = 0xFF7A1F;
	private static final int EXPLOSION = 0xFFB020;
	private static final int OTHER = 0xA3A3AE;

	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.5, 2.0, 1.0));
	private final SliderSetting duration = add(new SliderSetting("duration", "Dauer", 0.6, 2.5, 0.1, 1.2, v -> String.format("%.1fs", v)));
	private final SliderSetting opacity = add(SliderSetting.percent("opacity", "Deckkraft", 0.2, 1.0, 0.85));
	private final BoolSetting undirected = add(new BoolSetting("undirected", "Ohne Richtung als Kreis", false));

	// One slot per arc. yaw is the world yaw you would face to look at where the hit came from.
	private final float[] yaw = new float[MAX];
	private final double[] start = new double[MAX];
	private final int[] rgb = new int[MAX];
	private final int[] seed = new int[MAX];
	private final boolean[] circle = new boolean[MAX];
	private final boolean[] alive = new boolean[MAX];

	private boolean primed;
	private int lastHurtTime;
	private float lastHurtDir;
	private long ticks;
	private long lastHitTick = -100;
	private int newest = -1;
	private int seedCounter;

	public HitDirection() {
		super("hit_direction", "Treffer-Richtung", Category.TWEAKS, true,
				"Ein Pinselbogen am Fadenkreuz zeigt, woher ein Treffer kam, und verblasst wie Tinte. Rot für Nahkampf, weiß für Geschosse, "
						+ "orange für Feuer, bernstein für Explosionen. Nutzt nur die Richtung, die auch die Hurt-Cam bekommt.");
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
		float viewYaw = player.getYRot(hud.partialTick);
		float cx = hud.centerX() + 0.5f;
		float cy = hud.centerY() + 0.5f;

		for (int i = 0; i < MAX; i++) {
			if (!alive[i]) {
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
				Gui2D.dot(g, x, y, spread, Colors.argb(a * 0.12f * (1f - u * 0.6f), bleed));
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
		java.util.Arrays.fill(alive, false);
		primed = false;
		newest = -1;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cx = x + w / 2f;
		float cy = y + h / 2f;
		int cross = 0xCCFFFFFF;
		Gui2D.fill(g, Math.round(cx) - 4, Math.round(cy), Math.round(cx) + 5, Math.round(cy) + 1, cross);
		Gui2D.fill(g, Math.round(cx), Math.round(cy) - 4, Math.round(cx) + 1, Math.round(cy) + 5, cross);

		// Hits from the left, from behind and from the right: sword, arrow, fireball
		float period = 1.5f;
		int index = (int) (time / period);
		float age = Previews.cycle(time, period) * period;
		float[] angles = {-1.75f, 3.0f, 1.4f};
		int[] colors = {MELEE, PROJECTILE, FIRE};
		int k = Math.floorMod(index, 3);
		float radius = Math.min(w, h) * 0.34f;
		stroke(g, cx, cy, radius, angles[k], SPAN, 1f, colors[k], 0.95f, age, period, 7 + k * 0x3F1);
	}
}
