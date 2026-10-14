package dev.juiced.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** A compass strip like in shooters. Also points at your current target. */
public final class Compass extends HudModule {
	private static final String[] LABELS = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};

	private final SliderSetting widthSetting = add(new SliderSetting("width", "Breite", 120, 300, 10, 180, v -> Math.round(v) + "px"));
	private final SliderSetting fov = add(new SliderSetting("fov", "Sichtbereich", 90, 240, 10, 160, v -> Math.round(v) + "°"));
	private final BoolSetting degrees = add(new BoolSetting("degrees", "Gradzahl", true));
	private final BoolSetting target = add(new BoolSetting("target", "Gegner markieren", true));

	private float yaw;

	public Compass() {
		super("compass", "Kompass", false, 0.5f, 0f,
				"Kompass-Leiste oben am Bildschirm. Ein Pfeil zeigt, wo dein aktueller Gegner steht.");
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		LocalPlayer player = hud.minecraft.player;

		if (player == null) {
			return false;
		}

		yaw = player.getYRot(hud.partialTick);
		width = widthSetting.i();
		height = degrees.on() ? 24 : 16;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		int accent = Theme.accent();
		float bg = Theme.hudBackground();

		if (bg > 0.01f) {
			Gui2D.rounded2(g, 0, 0, width, 16, Colors.argb(bg * 0.95f, 0x0D0D13));
		}

		float half = fov.f() / 2f;
		float pixelsPerDegree = width / fov.f();
		float heading = ((yaw % 360f) + 360f) % 360f;

		// Ticks every 15 degrees, labels every 45
		for (int deg = 0; deg < 360; deg += 15) {
			float delta = wrap(deg - heading);

			if (Math.abs(delta) > half) {
				continue;
			}

			float x = width / 2f + delta * pixelsPerDegree;
			float edgeFade = 1f - Math.abs(delta) / half;
			float alpha = Math.min(1f, edgeFade * 2.2f);

			if (deg % 45 == 0) {
				String label = LABELS[deg / 45];
				boolean north = deg == 180;
				int color = north ? Colors.argb(alpha, 0xFF5A5A) : Colors.argb(alpha, 0xFFFFFF);
				Gui2D.centered(g, label, x, 4.5f, color, true, label.length() > 1 ? 0.75f : 0.95f);
			} else {
				Gui2D.fill(g, Math.round(x), 6, Math.round(x) + 1, 10, Colors.argb(alpha * 0.5f, 0xFFFFFF));
			}
		}

		// Center marker
		Gui2D.fill(g, width / 2, 0, width / 2 + 1, 3, Colors.argb(1f, accent));

		LivingEntity enemy = CombatEvents.target();
		LocalPlayer player = hud.minecraft.player;

		if (target.on() && enemy != null && player != null) {
			double dx = enemy.getX() - player.getX();
			double dz = enemy.getZ() - player.getZ();
			float angle = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float delta = wrap(angle - heading);
			float clamped = Math.max(-half, Math.min(half, delta));
			float x = width / 2f + clamped * pixelsPerDegree;
			int red = Colors.argb(1f, 0xFF4D4D);
			// Small triangle at the bottom edge of the strip, pointing up
			for (int i = 0; i < 4; i++) {
				Gui2D.fill(g, Math.round(x) - i, 12 + i, Math.round(x) + i + 1, 13 + i, red);
			}
		}

		if (degrees.on()) {
			Gui2D.centered(g, Math.round(heading) + "°", width / 2f, 17, Colors.argb(0.9f, accent), true, 0.7f);
		}
	}

	private static float wrap(float degrees) {
		float d = degrees % 360f;

		if (d > 180f) {
			d -= 360f;
		} else if (d < -180f) {
			d += 360f;
		}

		return d;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		int stripW = w - 16;
		int sx = x + 8;
		int sy = y + h / 2 - 8;
		float heading = (time * 30) % 360;
		Gui2D.rounded2(g, sx, sy, sx + stripW, sy + 16, 0x900D0D13);

		for (int deg = 0; deg < 360; deg += 45) {
			float delta = wrap(deg - heading);

			if (Math.abs(delta) > 80) {
				continue;
			}

			float px = sx + stripW / 2f + delta * stripW / 160f;
			Gui2D.centered(g, LABELS[deg / 45], px, sy + 4, deg == 180 ? 0xFFFF5A5A : 0xFFFFFFFF, true, 0.8f);
		}

		Gui2D.fill(g, sx + stripW / 2, sy, sx + stripW / 2 + 1, sy + 3, Colors.argb(1f, Theme.accent()));
	}
}
