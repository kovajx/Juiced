package dev.juiced.core;

import com.google.gson.JsonObject;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD element you can drag anywhere in the HUD editor.
 *
 * <p>The position is saved as a fraction of the free space (0 = left/top edge, 1 = right/bottom
 * edge), so elements stick to the same edge or the middle on every window size. Elements that
 * grow (like the effect list) grow away from the edge they sit at.
 */
public abstract class HudModule extends Module {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2.5f;
	/** Distance kept from the screen edges at position 0 and 1. */
	public static final int PAD = 4;

	private final float defaultX;
	private final float defaultY;
	public float posX;
	public float posY;
	public float scale = 1f;

	/** Size of the content in unscaled pixels, set by {@link #prepare}. */
	protected int width;
	protected int height;

	protected HudModule(String id, String name, boolean defaultEnabled, float defaultX, float defaultY, String description) {
		this(id, name, Category.HUD, defaultEnabled, defaultX, defaultY, description);
	}

	protected HudModule(String id, String name, Category category, boolean defaultEnabled, float defaultX, float defaultY, String description) {
		super(id, name, category, defaultEnabled, description);
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		this.posX = defaultX;
		this.posY = defaultY;
	}

	/**
	 * Works out what to show this frame and sets {@link #width} and {@link #height}.
	 *
	 * @param editing true inside the HUD editor: show sample content when there is nothing real to show
	 * @return false if there is nothing to draw right now
	 */
	protected abstract boolean prepare(Hud hud, boolean editing);

	/** Draws the content with its top-left corner at (0, 0). */
	protected abstract void draw(GuiGraphicsExtractor g, Hud hud, boolean editing);

	@Override
	public final void renderHud(GuiGraphicsExtractor g, Hud hud) {
		if (prepare(hud, false)) {
			drawAtPosition(g, hud, false);
		}
	}

	/** Used by the HUD editor: prepares with sample content and draws. Returns false if empty. */
	public boolean renderForEditor(GuiGraphicsExtractor g, Hud hud) {
		if (!prepare(hud, true)) {
			return false;
		}

		drawAtPosition(g, hud, true);
		return true;
	}

	private void drawAtPosition(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		g.pose().pushMatrix();
		g.pose().translate(screenX(hud.width), screenY(hud.height));
		g.pose().scale(scale);
		draw(g, hud, editing);
		g.pose().popMatrix();
	}

	public int scaledWidth() {
		return Math.round(width * scale);
	}

	public int scaledHeight() {
		return Math.round(height * scale);
	}

	public int screenX(int screenWidth) {
		return PAD + Math.round(posX * Math.max(0, screenWidth - 2 * PAD - scaledWidth()));
	}

	public int screenY(int screenHeight) {
		return PAD + Math.round(posY * Math.max(0, screenHeight - 2 * PAD - scaledHeight()));
	}

	/** Moves the element so its top-left corner is at (x, y) on screen. */
	public void moveTo(float x, float y, int screenWidth, int screenHeight) {
		float freeX = screenWidth - 2 * PAD - scaledWidth();
		float freeY = screenHeight - 2 * PAD - scaledHeight();
		posX = freeX <= 0 ? 0 : Colors.clamp01((x - PAD) / freeX);
		posY = freeY <= 0 ? 0 : Colors.clamp01((y - PAD) / freeY);
	}

	public void setScale(float value) {
		scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
	}

	public void resetPosition() {
		posX = defaultX;
		posY = defaultY;
		scale = 1f;
	}

	@Override
	public void resetToDefaults() {
		super.resetToDefaults();
		resetPosition();
	}

	public JsonObject positionToJson() {
		JsonObject json = new JsonObject();
		json.addProperty("x", posX);
		json.addProperty("y", posY);
		json.addProperty("scale", scale);
		return json;
	}

	public void positionFromJson(JsonObject json) {
		if (json == null) {
			return;
		}

		try {
			if (json.has("x")) {
				posX = Colors.clamp01(json.get("x").getAsFloat());
			}

			if (json.has("y")) {
				posY = Colors.clamp01(json.get("y").getAsFloat());
			}

			if (json.has("scale")) {
				setScale(json.get("scale").getAsFloat());
			}
		} catch (RuntimeException ignored) {
			// broken value: keep the default
		}
	}
}
