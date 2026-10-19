package dev.juiced.core;

import dev.juiced.modules.system.PerformanceModule;
import dev.juiced.modules.system.ThemeModule;

/** Quick access to the theme colors and quality level from anywhere. */
public final class Theme {
	static ThemeModule theme;
	static PerformanceModule performance;

	private Theme() {
	}

	/** Theme color as 0xRRGGBB. {@code offset} (0..1) moves along the gradient or rainbow. */
	public static int accent(float offset) {
		if (theme == null) {
			return Colors.PALETTE[0];
		}

		int first = theme.accent.rgb(offset);

		if (!theme.gradient.on()) {
			return first;
		}

		float t = offset - (float) Math.floor(offset);
		float pingPong = t < 0.5f ? t * 2f : 2f - t * 2f;
		return Colors.mixRgb(first, theme.secondary.rgb(offset), pingPong);
	}

	/** True if the player picked the older card deck as the Shift menu instead of the Weltprobe. */
	public static boolean classicMenu() {
		return theme != null && theme.menuStyle.is(1);
	}

	/** How often the Weltprobe menu has been opened (counts up to 99). */
	public static int menuOpens() {
		return theme == null ? 99 : theme.menuOpens.i();
	}

	/** Counts one more opening of the Weltprobe menu. */
	public static void countMenuOpen() {
		if (theme != null && theme.menuOpens.i() < 99) {
			theme.menuOpens.set(theme.menuOpens.get() + 1);
		}
	}

	public static int accent() {
		return accent(0f);
	}

	public static int secondary() {
		return theme == null ? Colors.PALETTE[3] : theme.secondary.rgb();
	}

	public static int rainbow(float offset) {
		double speed = theme == null ? 0.25 : theme.rainbowSpeed.get() * 0.5;
		return Colors.hsv((float) ((Clock.t() * speed + offset) % 1.0), 0.65f, 1f);
	}

	/** Opacity (0..1) of HUD element backgrounds. */
	public static float hudBackground() {
		return theme == null ? 0.55f : theme.hudBackground.f();
	}

	public static boolean hudAccent() {
		return theme == null || theme.hudAccent.on();
	}

	/** 0 = low, 1 = medium, 2 = high. */
	public static int quality() {
		return performance == null ? 2 : performance.quality.index();
	}

	/** Scales a segment/particle count by the quality level. */
	public static int detail(int high) {
		return switch (quality()) {
			case 0 -> Math.max(4, high / 2);
			case 1 -> Math.max(6, high * 3 / 4);
			default -> high;
		};
	}

	public static int maxSparks() {
		return performance == null ? 250 : performance.maxParticles.i();
	}

	public static double effectRange() {
		return performance == null ? 48 : performance.range.get();
	}
}
