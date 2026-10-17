package dev.juiced.gui.weltprobe;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import dev.juiced.gui.CameraKeeper;

/**
 * The Weltprobe menu (Right Shift): a remote control for your world. TODO(ui): the whole screen.
 */
public class WeltprobeScreen extends Screen implements CameraKeeper {
	public WeltprobeScreen() {
		super(Component.literal("Juiced"));
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the world running so the try-outs, the dummy and the particles move
		return false;
	}

	@Override
	public void releaseCamera() {
		// TODO(ui)
	}
}
