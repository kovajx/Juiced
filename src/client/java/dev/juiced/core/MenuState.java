package dev.juiced.core;

/**
 * What the Weltprobe menu currently asks of the rest of the mod. The menu shows the world as its own stage,
 * so the vanilla HUD and the Juiced HUD step aside while it is open (also while its colour picker is on top).
 */
public final class MenuState {
	private static boolean hudHidden;

	private MenuState() {
	}

	public static boolean hidesHud() {
		return hudHidden;
	}

	public static void setHudHidden(boolean hidden) {
		hudHidden = hidden;
	}
}
