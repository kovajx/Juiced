package dev.juiced.modules.tweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.render.Gui2D;

/**
 * Sprints whenever you walk forward, so you never have to hold the sprint key.
 * Works like the "Sprint: Toggle" accessibility option, just always on.
 */
public final class ToggleSprint extends HudModule {
	private final BoolSetting indicator = add(new BoolSetting("indicator", "Anzeige im HUD", true));

	private boolean forced;
	private boolean sprinting;

	public ToggleSprint() {
		super("toggle_sprint", "Toggle Sprint", Category.TWEAKS, false, 0f, 1f,
				"Du sprintest automatisch, sobald du vorwärts läufst. Nie wieder Strg gedrückt halten.");
	}

	@Override
	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		Options options = minecraft.options;

		if (player == null) {
			return;
		}

		boolean forward = options.keyUp.isDown() && minecraft.gui.screen() == null;

		if (forward && !player.isShiftKeyDown()) {
			if (!options.keySprint.isDown()) {
				options.keySprint.setDown(true);
				forced = true;
			}
		} else if (forced) {
			options.keySprint.setDown(false);
			forced = false;
		}

		sprinting = player.isSprinting();
	}

	@Override
	protected void onDisable() {
		if (forced) {
			Minecraft.getInstance().options.keySprint.setDown(false);
			forced = false;
		}
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		if (!indicator.on()) {
			return false;
		}

		width = Gui2D.width("Sprint • aktiv", 0.85f) + 12;
		height = 13;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		Gui2D.panel(g, 0, 0, width, height);
		boolean active = sprinting || editing;
		Gui2D.text(g, "Sprint ", 6, 3, Colors.argb(1f, Theme.accent()), true, 0.85f);
		Gui2D.text(g, active ? "• aktiv" : "• bereit", 6 + Gui2D.width("Sprint ", 0.85f), 3,
				active ? 0xFF8CFF9A : 0xFFB8B8C6, true, 0.85f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float run = (time * 2.2f) % 1f;
		int feet = y + h - 14;

		for (int i = 0; i < 4; i++) {
			float t = (run + i / 4f) % 1f;
			int lx = x + 10 + Math.round(t * (w - 20));
			Gui2D.fill(g, lx, feet + 4, lx + 6, feet + 5, Colors.argb(1f - t, 0xFFFFFF));
		}

		Gui2D.centered(g, "»»", x + w / 2f, y + h / 2f - 10, Colors.argb(1f, Theme.accent()), true, 2f);
	}
}
