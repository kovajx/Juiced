package dev.juiced.gui;

/**
 * A screen that changed the camera (the deck's front view, the wardrobe's orbit) and keeps it
 * while one of its child screens (the color picker) is open, so you still see your cosmetics live.
 */
public interface CameraKeeper {
	/** A child screen closed without going back to this screen (disconnect, death): give the camera back now. */
	void releaseCamera();
}
