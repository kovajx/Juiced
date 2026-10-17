package dev.juiced.modules.visual;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

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
import dev.juiced.render.Sparks;

public final class Trails extends Module {
	private static final int CAPACITY = 61;
	private static final int MAX_OTHERS = 8;
	/** Key of the path the menu walks around you while it shows the trail (you stand still while it is open). */
	private static final int PROBE_KEY = Integer.MIN_VALUE;
	private static final double PROBE_RADIUS = 1.1;
	/** Radians per tick: about one lap in 2.6 s, fast enough that the trail stays fully visible. */
	private static final double PROBE_SPEED = 0.12;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Band", "Linie", "Funken"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", ColorSetting.RAINBOW, true));
	private final SliderSetting length = add(new SliderSetting("length", "Länge", 6, 60, 1, 24, v -> Math.round(v) + ""));
	private final SliderSetting width = add(new SliderSetting("width", "Breite", 0.04, 0.5, 0.01, 0.16, v -> String.format("%.2f", v)));
	private final ChoiceSetting height = add(new ChoiceSetting("height", "Höhe", 0, "Hüfte", "Füße", "Schultern"));
	private final BoolSetting firstPerson = add(new BoolSetting("first_person", "In Ego-Sicht", true));
	private final BoolSetting others = add(new BoolSetting("others", "Andere Spieler", false));

	private final Map<Integer, History> histories = new HashMap<>();
	private final float[] px = new float[CAPACITY + 1];
	private final float[] py = new float[CAPACITY + 1];
	private final float[] pz = new float[CAPACITY + 1];
	private final float[] hw = new float[CAPACITY + 1];
	private final int[] colors = new int[CAPACITY + 1];
	private boolean probeFed;

	public Trails() {
		super("trails", "Trails", Category.VISUALS, false,
				"Eine leuchtende Spur hinter dir, die beim Stehen langsam verblasst. Als Band, Linie oder Funken.");
	}

	private float heightOffset(Entity entity) {
		return switch (height.index()) {
			case 1 -> 0.08f;
			case 2 -> entity.getBbHeight() * 0.75f;
			default -> entity.getBbHeight() * 0.45f;
		};
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer self = minecraft.player;

		if (self == null || minecraft.level == null) {
			histories.clear();
			return;
		}

		sample(self);

		if (others.on()) {
			int added = 0;

			for (Player player : minecraft.level.players()) {
				if (player == self || added >= MAX_OTHERS || player.distanceToSqr(self) > 32 * 32) {
					continue;
				}

				sample(player);
				added++;
			}
		}

		// The menu's path fades like a trail you stopped walking once the menu no longer feeds it
		History probe = histories.get(PROBE_KEY);

		if (probe != null && !probeFed) {
			probe.visibility = Math.max(0f, probe.visibility - 0.06f);
		}

		probeFed = false;
		histories.values().removeIf(h -> h.lastSeen < Clock.t() - 1.0);
	}

	/** The menu walks a circle around you, at the height you set, so the trail has a path to follow. */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		LocalPlayer self = minecraft.player;

		if (self == null) {
			return;
		}

		History history = histories.computeIfAbsent(PROBE_KEY, id -> new History());
		history.entity = null;
		// Counted on rather than taken from the ticks, so tuning back in quickly doesn't jump across the circle
		history.angle += PROBE_SPEED;
		probeFed = true;
		push(history, self.getX() + Math.cos(history.angle) * PROBE_RADIUS, self.getY() + heightOffset(self),
				self.getZ() + Math.sin(history.angle) * PROBE_RADIUS);
	}

	private void sample(Entity entity) {
		History history = histories.computeIfAbsent(entity.getId(), id -> new History());
		history.entity = entity;
		push(history, entity.getX(), entity.getY() + heightOffset(entity), entity.getZ());
	}

	private void push(History history, double x, double y, double z) {
		history.lastSeen = Clock.t();
		boolean moved = history.size == 0 || history.distanceSqToNewest(x, y, z) > 0.0004;
		history.push(x, y, z);
		history.visibility = Math.max(0f, Math.min(1f, history.visibility + (moved ? 0.25f : -0.06f)));

		if (style.index() == 2 && moved && history.size > 1) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Sparks.spawn(x, y, z, (random.nextFloat() - 0.5f) * 0.3f, random.nextFloat() * 0.3f, (random.nextFloat() - 0.5f) * 0.3f,
					0.7f, width.f() * 0.4f, color.rgb(random.nextFloat()), Sparks.DOT, 0.5f, 1.5f);
		}
	}

	@Override
	public void renderWorld(Geo geo) {
		Minecraft minecraft = Minecraft.getInstance();
		boolean hideSelf = !firstPerson.on() && minecraft.options.getCameraType().isFirstPerson();

		for (History history : histories.values()) {
			if (history.visibility <= 0.01f || history.size < 2) {
				continue;
			}

			// A null entity is the menu's path around you, which is your trail too
			if ((history.entity == minecraft.player || history.entity == null) && hideSelf) {
				continue;
			}

			draw(geo, history);
		}
	}

	private void draw(Geo geo, History history) {
		Entity entity = history.entity;
		int count = Math.min(history.size, length.i());

		if (entity != null) {
			px[0] = geo.rx(geo.x(entity));
			py[0] = geo.ry(geo.y(entity) + heightOffset(entity));
			pz[0] = geo.rz(geo.z(entity));
		} else {
			// The menu's path: the head runs on along the circle between ticks, so it glides like a real walk
			LocalPlayer self = Minecraft.getInstance().player;

			if (self == null) {
				return;
			}

			double angle = history.angle + PROBE_SPEED * geo.partialTick();
			px[0] = geo.rx(geo.x(self) + Math.cos(angle) * PROBE_RADIUS);
			py[0] = geo.ry(geo.y(self) + heightOffset(self));
			pz[0] = geo.rz(geo.z(self) + Math.sin(angle) * PROBE_RADIUS);
		}

		int n = 1;

		for (int i = 0; i < count; i++) {
			int index = history.index(i);
			px[n] = geo.rx(history.x[index]);
			py[n] = geo.ry(history.y[index]);
			pz[n] = geo.rz(history.z[index]);
			n++;
		}

		float baseHalf = style.index() == 1 ? 0.012f : width.f() / 2f;
		float spin = Clock.tf() * 0.15f;
		float visibility = history.visibility;
		boolean sparks = style.index() == 2;

		// Soft glow first, then the bright core on top
		if (Theme.quality() > 0 || sparks) {
			float glowScale = style.index() == 1 ? 5f : 2.4f;

			for (int i = 0; i < n; i++) {
				float f = i / (float) (n - 1);
				hw[i] = baseHalf * (float) Math.pow(1f - f, 0.8) * glowScale + (sparks ? 0.02f : 0f);
				colors[i] = Colors.argb(0.28f * (1f - f) * visibility, color.rgb(f * 0.6f + spin));
			}

			geo.ribbon(px, py, pz, hw, colors, n);
		}

		if (!sparks) {
			for (int i = 0; i < n; i++) {
				float f = i / (float) (n - 1);
				hw[i] = baseHalf * (float) Math.pow(1f - f, 0.8);
				colors[i] = Colors.argb(0.9f * (1f - f) * visibility, color.rgb(f * 0.6f + spin));
			}

			geo.ribbon(px, py, pz, hw, colors, n);
		}
	}

	@Override
	protected void onDisable() {
		histories.clear();
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int points = 36;
		float headX = x + w * 0.78f;

		for (int i = 0; i < points; i++) {
			float f = i / (float) points;
			float px = headX - f * w * 0.62f;
			float py = y + h * 0.5f + (float) Math.sin(time * 3 - f * 6) * h * 0.18f * (1 - f * 0.3f);
			float size = 4f * (1f - f) + 1;
			Gui2D.glowDot(g, px, py, size, color.rgb(f * 0.6f + time * 0.15f), 1f - f);
		}

		Gui2D.glowDot(g, headX, y + h * 0.5f + (float) Math.sin(time * 3) * h * 0.18f, 4, 0xFFFFFF, 1f);
	}

	private static final class History {
		final double[] x = new double[CAPACITY];
		final double[] y = new double[CAPACITY];
		final double[] z = new double[CAPACITY];
		int head;
		int size;
		float visibility;
		double lastSeen;
		/** The entity this trail follows, or null for the menu's path around you. */
		@Nullable Entity entity;
		/** Where on its circle the menu's path is, at the newest sample. */
		double angle;

		void push(double px, double py, double pz) {
			head = (head + 1) % CAPACITY;
			x[head] = px;
			y[head] = py;
			z[head] = pz;
			size = Math.min(CAPACITY, size + 1);
		}

		/** Index of the i-th newest sample. */
		int index(int i) {
			return Math.floorMod(head - i, CAPACITY);
		}

		double distanceSqToNewest(double px, double py, double pz) {
			double dx = x[head] - px;
			double dy = y[head] - py;
			double dz = z[head] - pz;
			return dx * dx + dy * dy + dz * dz;
		}
	}
}
