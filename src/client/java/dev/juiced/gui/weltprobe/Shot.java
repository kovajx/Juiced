package dev.juiced.gui.weltprobe;

/** Where the menu camera stands while a channel is tuned. */
public enum Shot {
	/** Third person, front three-quarter: you and the straw dummy (yaw = body + 180 + 35, pitch 14, distance 4.2). */
	BUEHNE(false),
	/** Close to you from the front, swaying a little (body + 180 + 35, pitch 10, distance 3.2): head, ears, aura, pet. */
	NAH_FRONT(false),
	/** Close from behind, rear three-quarter (body - 35, pitch 12, distance 3.2): cape, wings, tail, blades. */
	NAH_REAR(false),
	/** Close from above (body + 180 + 35, pitch 40, distance 3.2): things on the ground around you. */
	NAH_TOP(false),
	/** First person looking up at the sky (pitch -50 unless the channel says otherwise). */
	HIMMEL(true),
	/** First person, your normal view: crosshair, hand, screen effects, HUD elements. */
	EGO(true),
	/** Don't move the camera (2D-only channels). */
	KEEP(false);

	/** True for the first-person shots. */
	public final boolean firstPerson;

	Shot(boolean firstPerson) {
		this.firstPerson = firstPerson;
	}
}
