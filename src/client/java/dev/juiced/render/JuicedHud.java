package dev.juiced.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.core.Clock;
import dev.juiced.core.Hud;
import dev.juiced.core.ModuleManager;
import dev.juiced.gui.DeckScreen;
import dev.juiced.gui.HudEditorScreen;

/** The single HUD layer that draws all HUD modules. */
public final class JuicedHud {
	private JuicedHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();

		Clock.frame();
		FrameCounter.frame();

		if (minecraft.player == null || minecraft.gui.screen() instanceof DeckScreen || minecraft.gui.screen() instanceof HudEditorScreen) {
			return;
		}

		Hud hud = new Hud(minecraft, graphics.guiWidth(), graphics.guiHeight(), deltaTracker.getGameTimeDeltaPartialTick(true));
		ModuleManager.renderHud(graphics, hud);
	}
}
