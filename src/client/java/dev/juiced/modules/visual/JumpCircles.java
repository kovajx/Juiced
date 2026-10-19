package dev.juiced.modules.visual;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Geo;
import dev.juiced.render.Gui2D;

public final class JumpCircles extends Module {
	private static final int MAX_CIRCLES = 24;
	private static final float FULL = (float) (Math.PI * 2);
	/** Ticks between the menu's pretend jumps: one ring has faded before the next one starts. */
	private static final int PROBE_PERIOD = 28;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Ring", "Doppelring", "Welle", "Scheibe"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe"));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.5, 2.0, 1.0));
	private final SliderSetting duration = add(new SliderSetting("duration", "Dauer", 0.3, 1.5, 0.05, 0.8, v -> String.format("%.2fs", v)));
	private final BoolSetting landing = add(new BoolSetting("landing", "Auch beim Landen", false));
	private final BoolSetting others = add(new BoolSetting("others", "Andere Spieler", false));

	private final List<Circle> circles = new ArrayList<>();

	public JumpCircles() {
		super("jump_circles", "Jump Circles", Category.VISUALS, true,
				"Ein leuchtender Ring breitet sich aus, wenn du springst. Optional auch beim Landen und für andere.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		CombatEvents.trackOtherJumps = others.on();
	}

	@Override
	public void onJump(Entity entity) {
		if (entity == Minecraft.getInstance().player || others.on()) {
			add(entity, 1f);
		}
	}

	@Override
	public void onLand(Entity entity, float fallHeight) {
		if (landing.on() && fallHeight > 0.6f && (entity == Minecraft.getInstance().player || others.on())) {
			add(entity, Math.min(2f, 0.6f + fallHeight * 0.15f));
		}
	}

	/** You can't jump while the menu is open, so it jumps for you: a ring every 1.4 s, and a landing ring after it if wanted. */
	@Override
	public void probeTick(Minecraft minecraft, int ticks) {
		LocalPlayer self = minecraft.player;

		if (self == null) {
			return;
		}

		int beat = ticks % PROBE_PERIOD;

		if (beat == 2) {
			onJump(self);
		} else if (beat == 11) {
			// Landing as if from a fall of a block and a half
			onLand(self, 1.5f);
		}
	}

	private void add(Entity entity, float strength) {
		if (circles.size() >= MAX_CIRCLES) {
			circles.remove(0);
		}

		circles.add(new Circle(entity.getX(), entity.getY() + 0.02, entity.getZ(), Clock.t(), strength));
	}

	@Override
	public void renderWorld(Geo geo) {
		if (circles.isEmpty()) {
			return;
		}

		double now = Clock.t();
		float life = duration.f();
		int segments = Theme.detail(56);
		float spin = Clock.tf() * 0.25f;
		Geo.Tint tint = t -> color.rgb(t + spin);

		for (int i = circles.size() - 1; i >= 0; i--) {
			Circle c = circles.get(i);
			float t = (float) ((now - c.start) / life);

			if (t >= 1.3f) {
				circles.remove(i);
				continue;
			}

			float maxRadius = 1.1f * size.f() * c.strength;

			switch (style.index()) {
				case 1 -> {
					ring(geo, c, t, maxRadius, segments, tint, 1f);
					ring(geo, c, t - 0.12f, maxRadius * 0.7f, segments, tint, 0.8f);
				}
				case 2 -> {
					for (int k = 0; k < 3; k++) {
						ring(geo, c, t - k * 0.15f, maxRadius * (1f - k * 0.12f), segments, tint, 0.85f - k * 0.2f);
					}
				}
				case 3 -> {
					if (t >= 0f && t < 1f) {
						float r = 0.15f + Clock.easeOut(t) * maxRadius;
						geo.disc(c.x, c.y, c.z, r, segments, tint, 0.32f * (1f - t), 0f);
					}

					ring(geo, c, t, maxRadius, segments, tint, 0.9f);
				}
				default -> ring(geo, c, t, maxRadius, segments, tint, 1f);
			}
		}
	}

	private static void ring(Geo geo, Circle c, float t, float maxRadius, int segments, Geo.Tint tint, float strength) {
		if (t < 0f || t >= 1f) {
			return;
		}

		float radius = 0.12f + Clock.easeOut(t) * maxRadius;
		float fade = (float) Math.pow(1f - t, 1.2);
		float width = 0.065f * (1f - t) + 0.012f;
		geo.groundRing(c.x, c.y, c.z, radius, width, 0.16f * (1f - t * 0.5f), segments, 0f, FULL, tint, fade * 0.95f * strength);
	}

	@Override
	protected void onDisable() {
		circles.clear();
		CombatEvents.trackOtherJumps = false;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float cycle = Previews.cycle(time, 1.2f);
		float jump = cycle < 0.5f ? (float) Math.sin(cycle / 0.5f * Math.PI) * 14 : 0;
		float feetY = Previews.stage(g, x, y, w, h, jump);
		float cx = x + w / 2f;
		float t = cycle;
		float r = 6 + Clock.easeOut(t) * (w * 0.32f);
		int rgb = color.rgb(time * 0.2f);
		Gui2D.ellipse(g, cx, feetY + 1, r, r * 0.28f, 32, 2, rgb, (1f - t) * 0.95f, 0, true);

		if (style.index() > 0) {
			float t2 = Math.max(0, t - 0.15f);
			Gui2D.ellipse(g, cx, feetY + 1, 6 + Clock.easeOut(t2) * w * 0.22f, (6 + Clock.easeOut(t2) * w * 0.22f) * 0.28f, 28, 2, rgb, (1f - t2) * 0.7f, 0, true);
		}
	}

	private record Circle(double x, double y, double z, double start, float strength) {
	}
}
