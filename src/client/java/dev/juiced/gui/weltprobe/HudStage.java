package dev.juiced.gui.weltprobe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.JuicedClient;
import dev.juiced.core.Clock;
import dev.juiced.core.Config;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Module;
import dev.juiced.core.ModuleManager;
import dev.juiced.gui.HudSnap;

/**
 * What the tuned channel draws on the screen itself while the HUD layer is hidden: a HUD element at its real place
 * (with sample content, an outline, and dragging), or the module's own HUD drawing (hit arcs, the attack ring,
 * the low-health heart, screen drops, the fishing card, the crosshair).
 */
final class HudStage {
	private final Set<String> failed = new HashSet<>();
	private final List<int[]> ghosts = new ArrayList<>();
	private @Nullable HudModule dragging;
	private float grabX;
	private float grabY;
	private int guideX = -1;
	private int guideY = -1;
	/** The tuned element's box this frame (x, y, w, h), or null if there is none. */
	private int @Nullable [] box;
	/** The tuned channel is a HUD element but has nothing to show right now. */
	private boolean empty;

	int @Nullable [] box() {
		return box;
	}

	boolean empty() {
		return empty;
	}

	boolean dragging() {
		return dragging != null;
	}

	/** True if the tuned element sits in the right half, so the drawer opens on the left instead. */
	boolean rightHalf(int width) {
		int[] b = box;
		return b != null && b[0] + b[2] / 2 > width / 2;
	}

	/** True if the box overlaps the tuned element, so that panel fades to 30%. */
	boolean covers(int x1, int y1, int x2, int y2) {
		int[] b = box;
		return b != null && Ui.overlaps(x1, y1, x2, y2, b[0] - 3, b[1] - 3, b[0] + b[2] + 3, b[1] + b[3] + 3);
	}

	void draw(GuiGraphicsExtractor g, @Nullable Channel channel, Hud hud, int margin) {
		box = null;
		empty = false;
		ghosts.clear();

		if (channel == null) {
			return;
		}

		HudModule element = channel.hudModule();

		if (element == null) {
			// Not a HUD element: the module draws its own screen part, as the hidden HUD layer would
			if (channel.module.isEnabled()) {
				safely(channel.module, () -> channel.module.renderHud(g, hud));
			}

			return;
		}

		for (Module module : ModuleManager.all()) {
			if (module instanceof HudModule other && other != element && other.isSwitchedOn()) {
				int w = other.scaledWidth();
				int h = other.scaledHeight();

				if (w > 0 && h > 0) {
					int x = other.screenX(hud.width);
					int y = other.screenY(hud.height);
					ghosts.add(new int[] {x, y, w, h});
					Ui.frame(g, x, y, x + w, y + h, 0x1FFFFFFF);
				}
			}
		}

		boolean[] drawn = new boolean[1];
		safely(element, () -> drawn[0] = element.renderForEditor(g, hud));

		if (!drawn[0]) {
			empty = true;
			return;
		}

		int x = element.screenX(hud.width);
		int y = element.screenY(hud.height);
		int w = element.scaledWidth();
		int h = element.scaledHeight();
		box = new int[] {x, y, w, h};

		float pulse = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(Clock.t() * Math.PI * 2.0));
		Ui.frame(g, x - 3, y - 3, x + w + 3, y + h + 3, Ui.accent(dragging != null ? 1f : pulse));

		if (dragging != null) {
			if (guideX >= 0) {
				Ui.fill(g, guideX, 0, guideX + 1, hud.height, Ui.accent(0.85f));
			}

			if (guideY >= 0) {
				Ui.fill(g, 0, guideY, hud.width, guideY + 1, Ui.accent(0.85f));
			}

			return;
		}

		String tag = "Ziehen zum Verschieben";
		int tw = Ui.width(tag) + 8;
		int ty = y - 3 - 13 >= margin + Layout.BUTTON_H + 4 ? y - 3 - 13 : y + h + 5;
		int tx = Math.max(margin, Math.min(hud.width - margin - tw, x - 3));
		Ui.fill(g, tx, ty, tx + tw, ty + 12, Ui.PANEL);
		Ui.text(g, tag, tx + 4, ty + 2, Ui.TEXT);
	}

	/** Starts dragging the tuned element if the point is on it. */
	boolean grab(@Nullable Channel channel, double x, double y) {
		int[] b = box;
		HudModule element = channel == null ? null : channel.hudModule();

		if (b == null || element == null || !Ui.inside(x, y, b[0] - 2, b[1] - 2, b[0] + b[2] + 2, b[1] + b[3] + 2)) {
			return false;
		}

		dragging = element;
		grabX = (float) x - b[0];
		grabY = (float) y - b[1];
		return true;
	}

	void drag(double mouseX, double mouseY, int width, int height, boolean free) {
		HudModule element = dragging;
		int[] b = box;

		if (element == null || b == null) {
			return;
		}

		int x = Math.round((float) mouseX - grabX);
		int y = Math.round((float) mouseY - grabY);
		HudSnap.Result result = HudSnap.snap(element, x, y, b[2], b[3], width, height, ghosts, free);
		guideX = result.guideX();
		guideY = result.guideY();
		element.moveTo(result.x(), result.y(), width, height);
	}

	/** Lets go of the element and saves its place. Returns false if nothing was dragged. */
	boolean release() {
		if (dragging == null) {
			return false;
		}

		dragging = null;
		guideX = -1;
		guideY = -1;
		Config.save();
		return true;
	}

	/** Runs a module's own drawing; a module that throws is logged once and then left out, so the menu keeps working. */
	private void safely(Module module, Runnable draw) {
		if (failed.contains(module.id)) {
			return;
		}

		try {
			draw.run();
		} catch (RuntimeException e) {
			failed.add(module.id);
			JuicedClient.LOGGER.warn("Juiced: {} failed to draw in the Weltprobe menu", module.id, e);
		}
	}
}
