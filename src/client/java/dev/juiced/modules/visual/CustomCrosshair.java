package dev.juiced.modules.visual;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.MenuState;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.ColorSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** Your own crosshair instead of the vanilla one. */
public final class CustomCrosshair extends Module {
	public static CustomCrosshair INSTANCE;

	private final ChoiceSetting style = add(new ChoiceSetting("style", "Stil", 0, "Kreuz", "Kreuz + Punkt", "Punkt", "Kreis", "T-Form", "Winkel"));
	private final ColorSetting color = add(new ColorSetting("color", "Farbe", 9, true));
	private final SliderSetting size = add(new SliderSetting("size", "Länge", 2, 10, 1, 4, v -> Math.round(v) + "px"));
	private final SliderSetting gap = add(new SliderSetting("gap", "Abstand", 0, 6, 1, 2, v -> Math.round(v) + "px"));
	private final SliderSetting thickness = add(new SliderSetting("thickness", "Dicke", 1, 3, 1, 1, v -> Math.round(v) + "px"));
	private final BoolSetting outline = add(new BoolSetting("outline", "Schwarzer Rand", true));
	private final BoolSetting targetColor = add(new BoolSetting("target_color", "Rot auf Gegnern", true));
	private final BoolSetting dynamic = add(new BoolSetting("dynamic", "Öffnet sich beim Laufen", false));

	private float spread;

	public CustomCrosshair() {
		super("crosshair", "Fadenkreuz", Category.VISUALS, false,
				"Dein eigenes Fadenkreuz: 6 Stile, Farbe, Größe, Abstand und Rand. Wird rot, wenn du auf einen Gegner zielst.");
		INSTANCE = this;
	}

	public static boolean replacesVanilla() {
		return INSTANCE != null && INSTANCE.isEnabled();
	}

	/** Called instead of the vanilla crosshair (see JuicedClient). */
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		drawLive(g);
	}

	/**
	 * The Weltprobe menu hides the game's HUD, crosshair included, and draws the tuned module's HUD itself.
	 * Normally the crosshair comes through {@link #render}, so this only draws while the menu hides the HUD.
	 */
	@Override
	public void renderHud(GuiGraphicsExtractor graphics, Hud hud) {
		if (MenuState.hidesHud()) {
			drawLive(graphics);
		}
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.isSpectator() ? "Nicht im Zuschauermodus" : null;
	}

	private void drawLive(GuiGraphicsExtractor g) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;

		if (player == null || !minecraft.options.getCameraType().isFirstPerson() || player.isSpectator()) {
			return;
		}

		int rgb = color.rgb(Clock.tf() * 0.1f);

		if (targetColor.on() && minecraft.crosshairPickEntity instanceof LivingEntity) {
			rgb = 0xFF4D4D;
		}

		float moving = 0f;

		if (dynamic.on()) {
			double speed = player.getDeltaMovement().horizontalDistance();
			moving = (float) Math.min(1.0, speed * 6);
		}

		spread = Clock.approach(spread, moving * 3f, 12f);
		draw(g, g.guiWidth() / 2, g.guiHeight() / 2, rgb, 1f);
	}

	private void draw(GuiGraphicsExtractor g, int cx, int cy, int rgb, float scale) {
		int len = Math.round(size.i() * scale);
		int gp = Math.round(gap.i() * scale + spread);
		int t = Math.max(1, Math.round(thickness.i() * scale));
		int c = Colors.argb(1f, rgb);

		if (outline.on()) {
			shape(g, cx, cy, len, gp, t, 0xC0000000, 1);
		}

		shape(g, cx, cy, len, gp, t, c, 0);
	}

	/** Draws the selected style. {@code grow} makes everything 1px bigger on each side (for the outline). */
	private void shape(GuiGraphicsExtractor g, int cx, int cy, int len, int gp, int t, int c, int grow) {
		int lo = -(t / 2);
		int hi = lo + t;

		switch (style.index()) {
			case 2 -> rect(g, cx + lo, cy + lo, cx + hi, cy + hi, c, grow + 0);
			case 3 -> {
				int r = Math.max(3, len + gp);

				for (int i = 0; i < 32; i++) {
					double angle = i * Math.PI * 2 / 32;
					int px = cx + (int) Math.round(Math.cos(angle) * r);
					int py = cy + (int) Math.round(Math.sin(angle) * r);
					rect(g, px + lo, py + lo, px + hi, py + hi, c, grow);
				}

				rect(g, cx + lo, cy + lo, cx + hi, cy + hi, c, grow);
			}
			case 5 -> {
				int o = gp + len;
				// Four corner angles around the center
				rect(g, cx - o, cy - o, cx - o + len, cy - o + t, c, grow);
				rect(g, cx - o, cy - o, cx - o + t, cy - o + len, c, grow);
				rect(g, cx + o - len, cy - o, cx + o, cy - o + t, c, grow);
				rect(g, cx + o - t, cy - o, cx + o, cy - o + len, c, grow);
				rect(g, cx - o, cy + o - t, cx - o + len, cy + o, c, grow);
				rect(g, cx - o, cy + o - len, cx - o + t, cy + o, c, grow);
				rect(g, cx + o - len, cy + o - t, cx + o, cy + o, c, grow);
				rect(g, cx + o - t, cy + o - len, cx + o, cy + o, c, grow);
			}
			default -> {
				// Left, right, bottom always; top unless T shape
				rect(g, cx - gp - len, cy + lo, cx - gp, cy + hi, c, grow);
				rect(g, cx + gp + 1, cy + lo, cx + gp + 1 + len, cy + hi, c, grow);
				rect(g, cx + lo, cy + gp + 1, cx + hi, cy + gp + 1 + len, c, grow);

				if (style.index() != 4) {
					rect(g, cx + lo, cy - gp - len, cx + hi, cy - gp, c, grow);
				}

				if (style.index() == 1) {
					rect(g, cx + lo, cy + lo, cx + hi, cy + hi, c, grow);
				}
			}
		}
	}

	private static void rect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int c, int grow) {
		Gui2D.fill(g, x1 - grow, y1 - grow, x2 + grow, y2 + grow, c);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + h / 2f);
		g.pose().scale(2.5f);
		draw(g, 0, 0, color.rgb(time * 0.1f), 1f);
		g.pose().popMatrix();
	}
}
