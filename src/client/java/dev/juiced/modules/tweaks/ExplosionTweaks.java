package dev.juiced.modules.tweaks;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.Previews;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

/** Hides the explosion clouds from end crystals, respawn anchors, TNT and so on. */
public final class ExplosionTweaks extends Module {
	public static ExplosionTweaks INSTANCE;

	private static final long SMOKE_WINDOW_NANOS = 1_500_000_000L;
	private static volatile long lastExplosion;

	private final BoolSetting explosions = add(new BoolSetting("explosions", "Explosions-Wolken aus", true));
	private final BoolSetting smoke = add(new BoolSetting("smoke", "Rauch & Staub danach aus", true));

	public ExplosionTweaks() {
		super("explosions", "Explosionen aus", Category.TWEAKS, true,
				"Keine Explosions-Partikel von Kristallen, Ankern und TNT mehr. Du siehst den Gegner, statt eine weiße Wolke.");
		INSTANCE = this;
	}

	/** Called from ClientPacketListenerMixin whenever the server reports an explosion. */
	public static void onExplosion() {
		lastExplosion = System.nanoTime();
	}

	/** What it does is a cloud that doesn't appear, which only shows when something explodes. */
	@Override
	public @Nullable String probeLiveReason(Minecraft minecraft) {
		return "Wirkt bei Explosionen";
	}

	/** Called from ParticleEngineMixin for every particle the game wants to spawn. */
	public static boolean shouldHide(ParticleOptions options) {
		ExplosionTweaks module = INSTANCE;

		if (module == null || !module.isEnabled() || options == null) {
			return false;
		}

		ParticleType<?> type = options.getType();

		if (module.explosions.on() && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER)) {
			return true;
		}

		if (module.smoke.on() && System.nanoTime() - lastExplosion < SMOKE_WINDOW_NANOS) {
			return type == ParticleTypes.POOF || type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE;
		}

		return false;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float feetY = Previews.stage(g, x, y, w, h, x + w * 0.62f, 0);
		float cx = x + w * 0.3f;
		float cy = feetY - 8;
		// Crystal
		Gui2D.fill(g, Math.round(cx - 4), Math.round(cy - 10), Math.round(cx + 4), Math.round(cy - 2), 0xFFE070FF);
		Gui2D.fill(g, Math.round(cx - 6), Math.round(cy - 2), Math.round(cx + 6), Math.round(cy), 0xFF3A3A44);
		// A cloud that gets crossed out
		float cycle = Previews.cycle(time, 2f);
		float alpha = cycle < 0.5f ? 0.6f : 0.15f;

		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3 + time * 0.5;
			Gui2D.dot(g, cx + (float) Math.cos(angle) * 8, cy - 8 + (float) Math.sin(angle) * 6, 9, Colors.argb(alpha, 0xDDDDDD));
		}

		if (cycle >= 0.5f) {
			for (int i = -10; i <= 10; i++) {
				Gui2D.dot(g, cx + i, cy - 8 + i, 2, 0xFFFF4D4D);
			}
		}
	}
}
