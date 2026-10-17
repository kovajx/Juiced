package dev.juiced.gui.weltprobe;

import org.jspecify.annotations.Nullable;

/**
 * Runs the tuned channel live in the world (spec S2, S4, S6, S12, S13).
 *
 * <p>Tuning a channel stops the previous one ({@code setProbe(false)}, probe focus -1, dummy away if the new channel
 * has none) and starts the new one: if it is switched off and allowed here, {@code module.setProbe(true)} (with
 * {@code ModuleGroup.setProbeFocus(focus)} for a part or entry hit); then the camera aims at the channel's shot,
 * falling back to EGO when a third-person shot doesn't fit (cosmetics then fall back to the 2D preview with
 * "Zu eng für die Kamera"). While the module runs, every client tick calls {@code module.probeTick} and drives the
 * straw dummy for the channel's {@link DummyMode}; {@code module.probeLiveReason} is checked about once a second
 * and decides between the live view and the 2D preview.
 */
public final class ProbeController {
	public ProbeController(MenuCamera camera) {
		// TODO(logic)
	}

	/**
	 * Tunes a channel. {@code focus} is a part (bundle) or entry (choice card) index from a search hit, else -1.
	 * {@code deliberate} moves the camera at once instead of after the zapping pause.
	 */
	public void tune(Channel channel, int focus, boolean deliberate) {
		// TODO(logic)
	}

	public @Nullable Channel channel() {
		return null; // TODO(logic)
	}

	public int focus() {
		return -1; // TODO(logic)
	}

	/** Stops the probe and takes the dummy away (closing, or handing over to the wardrobe or HUD editor). Leaves the camera alone. */
	public void stop() {
		// TODO(logic)
	}

	/** Every client tick while the menu is open (Screen.tick). */
	public void tick() {
		// TODO(logic)
	}

	/** Every frame before the menu draws (dummy wobble, smooth dummy motion). */
	public void frame(float partialTick) {
		// TODO(logic)
	}

	public ProbeStatus status() {
		return ProbeStatus.AUS; // TODO(logic)
	}

	/**
	 * Caption for the framed 2D preview ("Nur nachts", "Wirkt beim Laufen", "Auf diesem Server gesperrt"), or null
	 * when the channel shows live (HUD channels are live: the menu draws their element itself).
	 */
	public @Nullable String fallbackReason() {
		return null; // TODO(logic)
	}

	/**
	 * The player switched the tuned channel on or off in the menu. Off stops the try-out so the effect visibly stops;
	 * it runs again after tuning away and back. On while a try-out runs changes nothing on screen.
	 */
	public void toggled(boolean on) {
		// TODO(logic)
	}

	/** A style or setting changed (Up/Down, drawer): re-sync the try-out so it shows the change in the same frame. */
	public void restyled() {
		// TODO(logic)
	}

	/** True while the straw dummy stands in the world. */
	public boolean dummyPlaced() {
		return false; // TODO(logic)
	}
}
