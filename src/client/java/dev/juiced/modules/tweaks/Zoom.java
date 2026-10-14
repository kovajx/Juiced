package dev.juiced.modules.tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** Hold the zoom key (C by default) to zoom in, like OptiFine. */
public final class Zoom extends Module {
	public static Zoom INSTANCE;

	private final SliderSetting strength = add(new SliderSetting("strength", "Stärke", 2, 10, 0.5, 4, v -> String.format("%.1fx", v)));
	private final BoolSetting smooth = add(new BoolSetting("smooth", "Weicher Übergang", true));
	private final BoolSetting scroll = add(new BoolSetting("scroll", "Mausrad zum Zoomen", true));
	private final BoolSetting cinematic = add(new BoolSetting("cinematic", "Filmische Kamera beim Zoomen", false));

	private float current = 1f;
	private float extra = 1f;
	private boolean wasZooming;
	private boolean previousSmoothCamera;

	public Zoom() {
		super("zoom", "Zoom", Category.TWEAKS, true,
				"Halte C (änderbar unter Steuerung) zum Reinzoomen. Mit dem Mausrad zoomst du weiter rein oder raus.");
		INSTANCE = this;
	}

	private boolean zooming() {
		return isEnabled() && JuicedClient.ZOOM_KEY != null && JuicedClient.ZOOM_KEY.isDown()
				&& Minecraft.getInstance().gui.screen() == null;
	}

	@Override
	public void tick(Minecraft minecraft) {
		boolean now = zooming();

		if (now && !wasZooming) {
			extra = 1f;

			if (cinematic.on()) {
				previousSmoothCamera = minecraft.options.smoothCamera;
				minecraft.options.smoothCamera = true;
			}
		} else if (!now && wasZooming && cinematic.on()) {
			minecraft.options.smoothCamera = previousSmoothCamera;
		}

		wasZooming = now;
	}

	@Override
	protected void onDisable() {
		if (wasZooming && cinematic.on()) {
			Minecraft.getInstance().options.smoothCamera = previousSmoothCamera;
		}

		wasZooming = false;
		current = 1f;
	}

	/** Called from CameraMixin: multiplier for the field of view (1 = no zoom). */
	public static float fovMultiplier() {
		Zoom module = INSTANCE;

		if (module == null) {
			return 1f;
		}

		float target = module.zooming() ? module.strength.f() * module.extra : 1f;

		if (module.smooth.on()) {
			module.current = Clock.approach(module.current, target, 14f);

			if (Math.abs(module.current - target) < 0.002f) {
				module.current = target;
			}
		} else {
			module.current = target;
		}

		return 1f / module.current;
	}

	/** Called from MouseHandlerMixin. Returns true if the scroll was used for zooming. */
	public static boolean handleScroll(double amount) {
		Zoom module = INSTANCE;

		if (module == null || !module.scroll.on() || !module.zooming() || amount == 0) {
			return false;
		}

		module.extra = Math.max(0.5f, Math.min(4f, module.extra * (amount > 0 ? 1.15f : 1f / 1.15f)));
		return true;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float t = 0.5f + 0.5f * (float) Math.sin(time * 1.4f);
		float r = 10 + t * 14;
		int accent = Theme.accent();
		Gui2D.ellipse(g, x + w / 2f, y + h / 2f, r, r, 32, 1.5f, accent, 0.9f, 0, false);
		Gui2D.fill(g, Math.round(x + w / 2f + r * 0.7f), Math.round(y + h / 2f + r * 0.7f), Math.round(x + w / 2f + r * 0.7f) + 10, Math.round(y + h / 2f + r * 0.7f) + 3, Colors.argb(1f, accent));
		Gui2D.centered(g, String.format("%.1fx", 1 + t * (strength.f() - 1)), x + w / 2f, y + h / 2f - 4, 0xFFFFFFFF, true, 0.9f);
	}
}
