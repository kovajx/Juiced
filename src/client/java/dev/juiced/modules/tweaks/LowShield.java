package dev.juiced.modules.tweaks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.juiced.core.Category;
import dev.juiced.core.Colors;
import dev.juiced.core.Module;
import dev.juiced.core.setting.BoolSetting;
import dev.juiced.core.setting.SliderSetting;
import dev.juiced.render.Gui2D;

/** Moves the shield (and optionally the totem) further down in first person. */
public final class LowShield extends Module {
	public static LowShield INSTANCE;

	private final SliderSetting height = add(SliderSetting.percent("height", "Absenken", 0.05, 0.5, 0.2));
	private final BoolSetting totem = add(new BoolSetting("totem", "Totem auch tiefer", false));

	public LowShield() {
		super("low_shield", "Low Shield", Category.TWEAKS, true,
				"Dein Schild sitzt in der Ego-Sicht tiefer und verdeckt nicht mehr den halben Bildschirm. Optional auch das Totem.");
		INSTANCE = this;
	}

	/** How far to move this item down (0 = not at all). */
	public static float offsetFor(ItemStack stack) {
		LowShield module = INSTANCE;

		if (module == null || !module.isEnabled() || stack == null || stack.isEmpty()) {
			return 0f;
		}

		if (stack.is(Items.SHIELD) || (module.totem.on() && stack.is(Items.TOTEM_OF_UNDYING))) {
			return module.height.f();
		}

		return 0f;
	}

	@Override
	public void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h, float time) {
		float drop = height.f() / 0.5f * (h * 0.35f);
		int sx = x + 12;
		int sy = Math.round(y + h * 0.3f + drop);
		int sw = 30;
		int sh = 40;
		Gui2D.rounded2(g, sx, sy, sx + sw, sy + sh, 0xFF6B4A2E);
		Gui2D.rounded(g, sx + 3, sy + 3, sx + sw - 3, sy + sh - 3, 0xFF8A6440);
		Gui2D.fill(g, sx + sw / 2 - 1, sy + 3, sx + sw / 2 + 1, sy + sh - 3, 0xFF5A5A62);
		// Crosshair to show the free view
		int cx = x + w / 2 + 10;
		int cy = y + h / 2;
		Gui2D.fill(g, cx - 4, cy, cx + 5, cy + 1, Colors.argb(0.9f, 0xFFFFFF));
		Gui2D.fill(g, cx, cy - 4, cx + 1, cy + 5, Colors.argb(0.9f, 0xFFFFFF));
	}
}
