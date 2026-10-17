package dev.juiced.modules.tweaks;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.ChoiceSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** Lowers (or removes) the fire on your screen while you're burning. */
public final class LowFire extends Module {
	public static LowFire INSTANCE;

	private static boolean pushed;

	private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Modus", 0, "Niedriger", "Aus"));
	private final SliderSetting height = add(SliderSetting.percent("height", "Absenken", 0.15, 0.9, 0.5));
	private final SliderSetting size = add(SliderSetting.percent("size", "Größe", 0.4, 1.0, 1.0));

	public LowFire() {
		super("low_fire", "Low Fire", Category.TWEAKS, true,
				"Das Feuer auf deinem Bildschirm sitzt tiefer, ist kleiner oder ganz weg, damit du beim Brennen weiter alles siehst.");
		INSTANCE = this;
	}

	/** The menu never sets you on fire for a demo; that would be real game state the server fights over. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		return player != null && player.isOnFire() ? null : "Nur wenn du brennst";
	}

	/** Called at the start of the fire overlay. Either cancels it or moves it down. */
	public static void before(PoseStack poseStack, CallbackInfo ci) {
		pushed = false;
		LowFire module = INSTANCE;

		if (module == null || !module.isEnabled()) {
			return;
		}

		if (module.mode.index() == 1) {
			ci.cancel();
			return;
		}

		poseStack.pushPose();
		poseStack.translate(0f, -module.height.f(), 0f);
		float scale = module.size.f();

		if (scale < 0.999f) {
			// Shrink towards the bottom edge of the flames, so smaller fire stays at the bottom of the screen
			poseStack.translate(0f, -0.8f, 0f);
			poseStack.scale(scale, scale, 1f);
			poseStack.translate(0f, 0.8f, 0f);
		}

		pushed = true;
	}

	/** Called at the end of the fire overlay. */
	public static void after(PoseStack poseStack) {
		if (pushed) {
			poseStack.popPose();
			pushed = false;
		}
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float lowered = mode.index() == 1 ? 1f : Math.min(1f, height.f() / 0.9f + (1f - size.f()) * 0.4f);
		int fireTop = y + Math.round(h * (0.3f + 0.6f * lowered));

		for (int i = 0; i < 9; i++) {
			int fx = x + 8 + i * (w - 16) / 9;
			float flicker = 0.7f + 0.3f * (float) Math.sin(time * 9 + i * 1.7f);
			int top = fireTop + Math.round((1f - flicker) * 10);
			Gui2D.fill(g, fx, top, fx + (w - 16) / 9 - 1, y + h, Colors.argb(0.85f, Colors.mixRgb(0xFFC23D, 0xFF4D1A, (i % 3) / 3f)));
		}

		Gui2D.centered(g, "↓", x + w / 2f, y + 8, 0xFFFFFFFF, true, 1.5f);
	}
}
