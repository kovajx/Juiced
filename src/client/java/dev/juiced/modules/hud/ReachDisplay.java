package dev.juiced.modules.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Clock;
import dev.juiced.core.Colors;
import dev.juiced.core.Hud;
import dev.juiced.core.HudModule;
import dev.juiced.core.Theme;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

public final class ReachDisplay extends HudModule {
	private static final float[] SAMPLES = {2.87f, 2.41f, 3.06f, 2.68f};

	private final SliderSetting hideAfter = add(new SliderSetting("hide_after", "Ausblenden nach", 0, 10, 1, 3, v -> v == 0 ? "nie" : Math.round(v) + "s"));
	private final BoolSetting colored = add(new BoolSetting("colored", "Farbig nach Distanz", true));

	public ReachDisplay() {
		super("reach", "Reach", false, 0.5f, 0.66f,
				"Zeigt, aus wie vielen Blöcken dein letzter Treffer gelandet ist.");
	}

	private boolean visible() {
		if (CombatEvents.lastReach() < 0) {
			return false;
		}

		return hideAfter.get() == 0 || CombatEvents.ticksSinceLastHit() < hideAfter.get() * 20;
	}

	@Override
	protected boolean prepare(Hud hud, boolean editing) {
		if (!visible() && !editing) {
			return false;
		}

		width = Gui2D.width("Reach 3.00", 0.9f) + 12;
		height = 14;
		return true;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, Hud hud, boolean editing) {
		// Sample: a new made-up hit every 1.6 s, so the colours by distance show too
		float reach = visible() ? CombatEvents.lastReach() : SAMPLES[(int) (Clock.t() / 1.6) % SAMPLES.length];
		Gui2D.panel(g, 0, 0, width, height);
		int rgb = 0xFFFFFF;

		if (colored.on()) {
			rgb = reach <= 2.5f ? 0x8CFF9A : reach <= 2.9f ? 0xFFD84D : 0xFF7A4D;
		}

		Gui2D.text(g, "Reach ", 6, 3.5f, Colors.argb(1f, Theme.accent()), true, 0.9f);
		Gui2D.text(g, String.format("%.2f", reach), 6 + Gui2D.width("Reach ", 0.9f), 3.5f, Colors.argb(1f, rgb), true, 0.9f);
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float reach = 2.5f + 0.5f * (float) Math.abs(Math.sin(time * 0.8f));
		int rgb = reach <= 2.5f ? 0x8CFF9A : reach <= 2.9f ? 0xFFD84D : 0xFF7A4D;
		Gui2D.centered(g, String.format("%.2f", reach), x + w / 2f, y + h / 2f - 10, Colors.argb(1f, rgb), true, 2f);
		Gui2D.centered(g, "Blöcke", x + w / 2f, y + h / 2f + 10, 0xFF8A8A99, false, 0.8f);
	}
}
