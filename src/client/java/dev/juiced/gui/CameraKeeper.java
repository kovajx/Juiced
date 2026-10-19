package dev.juiced.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A screen that changed the camera (the deck's front view, the wardrobe's orbit) and keeps it
 * while one of its child screens (the color picker) is open, so you still see your cosmetics live.
 */
public interface CameraKeeper {
	/** A child screen closed without going back to this screen (disconnect, death): give the camera back now. */
	void releaseCamera();

	/** Every client tick while a child screen is open on top, so live previews keep moving. */
	default void tickBehind() {
	}

	/** Every frame while a child screen is open on top, before the child draws: the parent's live stage, without its chrome. */
	default void drawBehind(GuiGraphicsExtractor graphics, float partialTick) {
	}
}
