package dev.juiced.modules.visual;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import dev.juiced.JuicedClient;
import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;
import dev.juiced.render.PostEffect;

/**
 * Color saturation and motion blur for the whole picture, done by a small shader after the world is drawn.
 *
 * <p>Shader settings can't change while the game runs, so every combination of saturation step and
 * blur strength is its own file in assets/juiced/post_effect ({@code look_s150_b2.json} = 150%, blur 2).
 */
public final class ScreenEffects extends Module {
	private final SliderSetting saturation = add(new SliderSetting("saturation", "Sättigung", 0, 2, 0.25, 1.25, v -> Math.round(v * 100) + "%"));
	private final ChoiceSetting motionBlur = add(new ChoiceSetting("motion_blur", "Motion Blur", 0, "Aus", "Leicht", "Mittel", "Stark"));

	public ScreenEffects() {
		super("screen_effects", "Bildeffekte", Category.VISUALS, false,
				"Kräftigere oder blassere Farben (Sättigung von Schwarzweiß bis doppelt so bunt) und Motion Blur, "
						+ "der schnelle Bewegungen weich verwischt. Das HUD bleibt scharf.");
	}

	/** The effect file for the current settings, or null when both are neutral. */
	private @Nullable Identifier effect() {
		int percent = (int) Math.round(saturation.get() * 4) * 25;
		int blur = motionBlur.index();

		if (percent == 100 && blur == 0) {
			return null;
		}

		return Identifier.fromNamespaceAndPath(JuicedClient.MOD_ID, String.format(Locale.ROOT, "look_s%03d_b%d", percent, blur));
	}

	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		if (effect() == null) {
			return "Alles neutral eingestellt";
		}

		return minecraft.getCameraEntity() == minecraft.player ? null : "Nur aus deiner Sicht";
	}

	@Override
	public void tick(Minecraft minecraft) {
		// While you spectate another entity (creeper, spider) the game shows its own effect in that slot
		boolean ownView = minecraft.getCameraEntity() == minecraft.player;
		PostEffect.show(minecraft.gameRenderer, ownView ? effect() : null);
	}

	@Override
	protected void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.gameRenderer != null) {
			PostEffect.show(minecraft.gameRenderer, null);
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float s = saturation.f();
		int[] hues = {0xFF4D4D, 0xFFC23D, 0x8CFF4F, 0x35D0FF, 0xA77BFF};
		int barW = (w - 24) / hues.length;
		int blurSteps = motionBlur.index() * 2;
		float sway = (float) Math.sin(time * 2.2f) * 10f;

		for (int i = 0; i < hues.length; i++) {
			int rgb = saturate(hues[i], s);
			int bx = x + 12 + i * barW;
			int by = y + 8 + Math.round(sway * (i % 2 == 0 ? 1 : -1));
			int bh = h - 26;

			// Fading copies behind each bar show the blur
			for (int k = blurSteps; k >= 1; k--) {
				int offset = Math.round(k * 2.5f * (float) Math.cos(time * 2.2f) * (i % 2 == 0 ? -1 : 1));
				Gui2D.rounded(g, bx + 2, by + offset, bx + barW - 2, by + offset + bh, Colors.argb(0.5f / (k + 1), rgb));
			}

			Gui2D.rounded(g, bx + 2, by, bx + barW - 2, by + bh, Colors.argb(0.95f, rgb));
		}

		Gui2D.centered(g, Math.round(s * 100) + "%  ·  Blur: " + motionBlur.name(), x + w / 2f, y + h - 11, 0xAAFFFFFF, false, 0.75f);
	}

	/** Same math as the shader, for the preview. */
	private static int saturate(int rgb, float amount) {
		float r = ((rgb >> 16) & 0xFF) / 255f;
		float gr = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float gray = 0.2126f * r + 0.7152f * gr + 0.0722f * b;
		r = Colors.clamp01(gray + (r - gray) * amount);
		gr = Colors.clamp01(gray + (gr - gray) * amount);
		b = Colors.clamp01(gray + (b - gray) * amount);
		return (Math.round(r * 255f) << 16) | (Math.round(gr * 255f) << 8) | Math.round(b * 255f);
	}
}
