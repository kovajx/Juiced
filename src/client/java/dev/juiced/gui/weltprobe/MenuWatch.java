package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Safety net for the Weltprobe menu. The screen gives back everything it borrowed when it closes (the camera, the
 * try-out, the straw dummy); if that is ever skipped (a crash inside the screen, a screen replaced in a way that
 * doesn't reach it), this notices two ticks later that no screen is open any more, or no world, and cleans up, so
 * you never get stuck with a third-person camera or a module running that you didn't switch on.
 */
final class MenuWatch {
	private static boolean registered;
	private static int idleTicks;
	private static @Nullable MenuCamera camera;
	private static @Nullable ProbeController probe;

	private MenuWatch() {
	}

	static void watch(MenuCamera opened) {
		register();
		camera = opened;
	}

	static void unwatch(MenuCamera closed) {
		if (camera == closed) {
			camera = null;
		}
	}

	/** Only one menu tries modules out at a time: an older controller that was never stopped stops now. */
	static void watch(ProbeController running) {
		register();
		ProbeController old = probe;
		probe = running;

		if (old != null && old != running) {
			old.stop();
		}
	}

	static void unwatch(ProbeController stopped) {
		if (probe == stopped) {
			probe = null;
		}
	}

	private static void register() {
		if (!registered) {
			registered = true;
			ClientTickEvents.END_CLIENT_TICK.register(MenuWatch::tick);
		}
	}

	private static void tick(Minecraft minecraft) {
		if (camera == null && probe == null) {
			idleTicks = 0;
			return;
		}

		if (minecraft.gui.screen() != null && minecraft.level != null && minecraft.player != null) {
			idleTicks = 0;
			return;
		}

		if (++idleTicks < 2) {
			return;
		}

		idleTicks = 0;
		ProbeController abandonedProbe = probe;
		MenuCamera abandonedCamera = camera;

		if (abandonedProbe != null) {
			abandonedProbe.stop();
		}

		if (abandonedCamera != null) {
			abandonedCamera.close();
		}

		probe = null;
		camera = null;
	}
}
